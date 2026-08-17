package dev.weft.neoforge.regiontick;

import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.WeftMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * P2 loader-side glue (RFC-0001 §11): routes vanilla's entity and
 * block-entity tick sections through the engine when {@code regionizedTicking}
 * is active.
 *
 * <p><b>Increment 1 — the degenerate case, by design.</b> Each
 * {@link ServerLevel} is one engine-owned region (an id reserved from the
 * {@code RegionManager}, deliberately never entered into the chunk→region
 * mapping); its entity and block-entity sections run through
 * {@link WeftScheduler#runOwnedSerial} — same thread, same vanilla iteration
 * order, bit-identical semantics by construction. What this buys is the
 * seams: the fail-loud ownership mixins, the REGION thread context around all
 * vanilla simulation ticking, and a live target for the vanilla-parity suite
 * (RFC-0005) — all proven before any increment that actually changes
 * execution (real chunk→region assignment, the legacy lane, parallel regions,
 * WS-10 sharding) is allowed to flip on.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}). R6: inactive means the wrapped call sites invoke the
 * vanilla section directly — zero behavioral residue.
 */
public final class RegionizedTicking {

    private RegionizedTicking() {}

    private static volatile boolean active;

    /** One reserved engine owner id per live ServerLevel (increment 1's "one region"). */
    private static final ConcurrentHashMap<ServerLevel, Long> ownerIds = new ConcurrentHashMap<>();

    private static final LongAdder entitySections = new LongAdder();
    private static final LongAdder blockEntitySections = new LongAdder();

    /** RFC-0003 R2 applied-check (belt-and-braces: these mixins are fail-loud). */
    public static boolean hooksApplied() {
        return RegionizedEntityTickMarker.class.isAssignableFrom(ServerLevel.class)
                && RegionizedBlockEntityTickMarker.class.isAssignableFrom(Level.class);
    }

    /** Owned by the WeftModules coexistence resolution. */
    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }

    /** Server stop: the level instances die with the server; drop their ids. */
    public static void reset() {
        ownerIds.clear();
    }

    /**
     * Called (server thread) from the wrapped {@code entityTickList.forEach}
     * call site inside {@code ServerLevel.tick}. Inactive or engine absent:
     * vanilla runs untouched.
     */
    public static void tickEntitySectionOwned(ServerLevel level, Runnable vanillaSection) {
        WeftScheduler engine = active ? WeftMod.schedulerOrNull() : null;
        if (engine == null) {
            vanillaSection.run();
            return;
        }
        entitySections.increment();
        engine.runOwnedSerial(ownerId(level), vanillaSection);
    }

    /**
     * Called (server thread) from the wrapped {@code Level.tickBlockEntities}
     * body when the level is a ServerLevel. Same contract as the entity
     * section.
     */
    public static void tickBlockEntitySectionOwned(ServerLevel level, Runnable vanillaSection) {
        WeftScheduler engine = active ? WeftMod.schedulerOrNull() : null;
        if (engine == null) {
            vanillaSection.run();
            return;
        }
        blockEntitySections.increment();
        engine.runOwnedSerial(ownerId(level), vanillaSection);
    }

    /** Engine-owned entity sections since boot (parity-suite engagement check). */
    public static long entitySections() {
        return entitySections.sum();
    }

    /** Engine-owned block-entity sections since boot. */
    public static long blockEntitySections() {
        return blockEntitySections.sum();
    }

    /** Extra detail for the posture report / {@code /weft status} (R5). */
    public static String statusDetail() {
        long e = entitySections.sum();
        long b = blockEntitySections.sum();
        if (e == 0 && b == 0) {
            return "increment 1: one region per level, serial, server thread; no sections owned yet";
        }
        return String.format(
                "increment 1: one region per level, serial, server thread; %d entity + %d block-entity sections owned",
                e, b);
    }

    private static long ownerId(ServerLevel level) {
        return ownerIds.computeIfAbsent(level, l -> WeftMod.reserveRegionOwnerId());
    }
}
