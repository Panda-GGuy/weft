package dev.weft.neoforge.parity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Event-fed conservation counters for the RFC-0005 class <b>E2</b> gate.
 *
 * <p>E0/E1 compare <em>state</em> ({@link WorldDigest}); E2 exists because
 * WS-10 sharding (RFC-0004 §2.5) deliberately gives up vanilla's exact
 * within-tick entity ordering, so per-entity end state may legitimately
 * differ while the simulation must still <em>conserve</em>: the same total
 * damage, the same births, the same creations and destructions. This class
 * captures the flow quantities a state snapshot cannot see; the paired
 * snapshot quantities (populations, item totals) live in
 * {@link WorldDigest#captureConservation}.
 *
 * <p><b>Order independence is the whole point, so every counter is an
 * associative integer.</b> Damage is accumulated as fixed-point
 * milli-damage rather than a running {@code float} sum: float addition is
 * not associative, so a genuine order change would move a float total by an
 * ULP or two and the gate could not distinguish that from a real
 * conservation break. Rounding to 1/1000 makes the total exactly
 * reproducible under any interleaving while staying far finer than any
 * damage value vanilla produces.
 *
 * <p><b>Thread safety.</b> Under sharding these events fire from shard
 * workers, so all state is {@link LongAdder}/{@link ConcurrentHashMap}.
 * Recording is off by default and gated by a volatile flag — production
 * runs pay one volatile read per event and nothing else.
 *
 * <p><b>Why spawn/removal counts mean what they say</b> (verified against
 * the 1.21.1/NeoForge decompile, not assumed): {@code EntityJoinLevelEvent}
 * is posted by {@code PersistentEntitySectionManager.addNewEntity}, while
 * chunk-load deserialization goes through {@code addNewEntityWithoutEvent}
 * and posts nothing. So these counters track genuine creation and
 * destruction, and chunk churn during a run cannot inflate them — which is
 * what makes them conserved quantities rather than traffic counts.
 */
public final class ConservationLedger {

    private ConservationLedger() {}

    private static volatile boolean recording;
    /** When set, only events inside this level+box count (see {@link #start}). */
    private static volatile ServerLevel scopeLevel;
    private static volatile AABB scopeBounds;

    private static final LongAdder damageMilli = new LongAdder();
    private static final LongAdder damageEvents = new LongAdder();
    private static final LongAdder deaths = new LongAdder();
    private static final LongAdder births = new LongAdder();
    private static final Map<String, LongAdder> spawnedByType = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> removedByType = new ConcurrentHashMap<>();

    /** Damage quantum: 1/1000 of a half-heart unit (see class doc). */
    private static final double DAMAGE_SCALE = 1000.0;

    /**
     * Begin recording events for entities inside {@code bounds} of
     * {@code level}, discarding anything previously accumulated.
     *
     * <p><b>Scoping is not optional in practice, and the control phase is
     * what proved it.</b> An unscoped ledger counts the whole server: the
     * first run of the E2 gate failed its control because three item
     * entities and an experience orb spawned somewhere else in the shared
     * gametest world during one of two otherwise identical vanilla runs. The
     * snapshot half of an E2 capture ({@link WorldDigest#captureConservation})
     * has always been arena-scoped, so leaving the event half global made the
     * two halves disagree about what "the system" even is. Both halves now
     * describe the same box.
     */
    public static void start(ServerLevel level, AABB bounds) {
        reset();
        scopeLevel = level;
        scopeBounds = bounds;
        recording = true;
    }

    /**
     * Begin recording every event on the server. Only correct when nothing
     * else in the world can move — prefer {@link #start(ServerLevel, AABB)}.
     */
    public static void startGlobal() {
        reset();
        scopeLevel = null;
        scopeBounds = null;
        recording = true;
    }

    /** Stop recording; accumulated counters stay readable for the verdict. */
    public static void stop() {
        recording = false;
    }

    /**
     * Whether an event about {@code entity} falls inside the recording scope.
     * Position is read at event time, which is the honest choice for the
     * quantities being counted: an item that spawns in the arena counts as
     * created there even if it later drifts out.
     */
    private static boolean inScope(Entity entity) {
        if (!recording) {
            return false;
        }
        AABB bounds = scopeBounds;
        if (bounds == null) {
            return true;
        }
        return entity.level() == scopeLevel && bounds.contains(entity.position());
    }

    public static void reset() {
        scopeLevel = null;
        scopeBounds = null;
        damageMilli.reset();
        damageEvents.reset();
        deaths.reset();
        births.reset();
        spawnedByType.clear();
        removedByType.clear();
    }

    /**
     * The conserved flow quantities as a digest, directly comparable with
     * {@link WorldDigest#diff}. Keys are prefixed {@code cons } so a
     * conservation entry can never collide with a state entry when the two
     * maps are merged for a combined report.
     */
    public static SortedMap<String, String> capture() {
        TreeMap<String, String> out = new TreeMap<>();
        out.put("cons damage total (milli)", Long.toString(damageMilli.sum()));
        out.put("cons damage events", Long.toString(damageEvents.sum()));
        out.put("cons deaths", Long.toString(deaths.sum()));
        out.put("cons births", Long.toString(births.sum()));
        putCounts(out, "cons spawned", spawnedByType);
        putCounts(out, "cons removed", removedByType);
        return out;
    }

    private static void putCounts(TreeMap<String, String> out, String prefix,
                                  Map<String, LongAdder> counts) {
        for (Map.Entry<String, LongAdder> e : counts.entrySet()) {
            out.put(prefix + " " + e.getKey(), Long.toString(e.getValue().sum()));
        }
    }

    // --- event listeners (registered in WeftMod) ---

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!inScope(event.getEntity())) {
            return;
        }
        damageMilli.add(Math.round(event.getNewDamage() * DAMAGE_SCALE));
        damageEvents.increment();
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!inScope(event.getEntity())) {
            return;
        }
        deaths.increment();
    }

    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!inScope(event.getParentA())) {
            return;
        }
        births.increment();
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        count(spawnedByType, event.getEntity());
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        count(removedByType, event.getEntity());
    }

    private static void count(Map<String, LongAdder> counts, Entity entity) {
        if (entity instanceof Player || !inScope(entity)) {
            return; // players join/leave for reasons unrelated to simulation
        }
        String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        counts.computeIfAbsent(type, k -> new LongAdder()).increment();
    }
}
