package dev.weft.neoforge.regiontick;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * RFC-0006 <b>hazard 25</b>: entity types whose AI reads the world at
 * <em>arbitrary</em> distance, and which therefore cannot be handed to a region
 * worker no matter how wide a neighbourhood check is. They tick in the serial
 * tail, on the server thread, where a lazy chunk load is legal — the same
 * treatment {@link WideReachBlockEntities} gives block entities whose reach
 * exceeds the shard colouring's gap.
 *
 * <p><b>Why a radius check cannot cover this.</b> Hazard 24 added a readiness
 * gate: a unit reaches a worker only when its radius-1 read neighbourhood is
 * live. That is the right shape for a block entity, whose {@code setChanged}
 * neighbour-signal path reaches exactly one block. It is useless against
 * {@code Brain}-based AI, because a Brain remembers <em>positions</em> and reads
 * them directly:
 *
 * <pre>
 *   IllegalStateException: Weft region worker requires chunk [-65, 22] ...
 *       and no generated FULL view exists either
 *     at Level.getBlockState
 *     at SleepInBed.checkExtraStartConditions        (SleepInBed:45)
 *     at Behavior.tryStart → Brain.startEachNonRunningBehavior
 *     at RegionizedTicking.tickEntitySection
 * </pre>
 *
 * <p>A villager checking whether it can sleep reads the block at its remembered
 * bed. That bed is wherever the villager last slept — tens or hundreds of blocks
 * away, in a chunk that may have been unloaded since. The same is true of job
 * sites, meeting points, hives and nest positions. There is no radius N that
 * bounds it, so the answer is not a bigger radius; it is not putting these on a
 * worker at all.
 *
 * <p><b>Type-based rather than memory-inspecting</b>, deliberately. Asking each
 * entity whether its brain currently holds a position memory would be both more
 * precise and wrong to rely on: the memory set changes at runtime, so a mob
 * could be bucketed on one tick and unsafe on the next, and the check would run
 * per entity per tick on the hot path. A type list is a stable, cheap,
 * conservative overapproximation — the same trade {@link WideReachBlockEntities}
 * makes.
 *
 * <p><b>Cost, stated plainly:</b> every listed type ticks serially, so a
 * village-heavy world loses the parallel win on its villagers. On the profile
 * that motivated this, villagers were 2.7–4.7% of attributed tick cost. That is
 * the price of not crashing, and it is the honest kind of price — bounded, and
 * visible in {@code unreadyUnits}.
 *
 * <p>Modded Brain users are <em>not</em> listed and therefore bucket. The
 * fail-loud guard is what catches them, and a trip is the signal to add an entry
 * here — a bug until it is, exactly as with block entities.
 */
final class MemoryReachEntities {

    private MemoryReachEntities() {}

    /**
     * Vanilla 1.21.1 {@code Brain} users that hold position memories. Each is a
     * reach fact rather than a guess:
     *
     * <ul>
     *   <li>{@code VILLAGER}, {@code ZOMBIE_VILLAGER} — HOME (bed), JOB_SITE,
     *       MEETING_POINT, POTENTIAL_JOB_SITE. {@code SleepInBed} is the
     *       observed crash.</li>
     *   <li>{@code PIGLIN}, {@code PIGLIN_BRUTE}, {@code HOGLIN}, {@code ZOGLIN}
     *       — nearest-repellent and home positions, read as block states.</li>
     *   <li>{@code ALLAY} — LIKED_NOTEBLOCK, a remembered block position it
     *       returns to.</li>
     *   <li>{@code WARDEN} — DISTURBANCE_LOCATION and its vibration listener.</li>
     *   <li>{@code FROG}, {@code AXOLOTL}, {@code GOAT}, {@code CAMEL},
     *       {@code SNIFFER}, {@code BREEZE}, {@code ARMADILLO}, {@code BOGGED},
     *       {@code TADPOLE} — Brain-driven, and the set of memories each holds
     *       is vanilla's to change between versions; listing the whole family is
     *       cheaper than tracking which subset is currently position-bearing.</li>
     *   <li>{@code BEE} — hive and flower positions, held as fields rather than
     *       a Brain, and read as block states at arbitrary range. Same hazard,
     *       so same treatment.</li>
     * </ul>
     */
    private static final Set<EntityType<?>> SERIAL = Set.of(
            EntityType.VILLAGER,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.HOGLIN,
            EntityType.ZOGLIN,
            EntityType.ALLAY,
            EntityType.WARDEN,
            EntityType.FROG,
            EntityType.TADPOLE,
            EntityType.AXOLOTL,
            EntityType.GOAT,
            EntityType.CAMEL,
            EntityType.SNIFFER,
            EntityType.BREEZE,
            EntityType.ARMADILLO,
            EntityType.BOGGED,
            EntityType.BEE);

    /** True when this entity must tick on the server thread (hazard 25). */
    static boolean isMemoryReach(Entity entity) {
        return SERIAL.contains(entity.getType());
    }
}
