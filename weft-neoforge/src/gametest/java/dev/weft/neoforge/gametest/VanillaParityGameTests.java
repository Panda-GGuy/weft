package dev.weft.neoforge.gametest;

import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.parity.WorldDigest;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * The P2 vanilla-parity suite (RFC-0001 §10/§11, RFC-0005) — the exit
 * criterion P2's tick-ownership increments are judged by, built before the
 * first increment was allowed to change anything (the P1 lesson: the harness
 * exists before the change it must judge).
 *
 * <p>Three runs of the identical fixed-seed scenario ({@link ParityScenario}),
 * same world, same server:
 * <ol>
 *   <li><b>Control A</b> — vanilla ticking.</li>
 *   <li><b>Control B</b> — vanilla ticking again. Digests must match A
 *       bit-identically; this proves the scenario + digest are deterministic,
 *       so any later mismatch is attributable to Weft, not noise. A control
 *       failure fails the suite loudly: a nondeterministic harness that
 *       "passes" would be worse than none.</li>
 *   <li><b>Weft-owned</b> — {@code regionizedTicking} active: every entity and
 *       block-entity section routed through the engine. Digest must match
 *       Control A, and the engine must actually have owned the sections
 *       (vacuous-run guard), so a silently-inert flag cannot pass. The legacy
 *       lane (increment 3) is active too: with only vanilla content present,
 *       its classification wrap must extract <em>zero</em> units and leave
 *       the digest bit-identical — the lane's zero-residue-on-vanilla
 *       claim. Partitioned ticking (increment 4) is active as well: the
 *       arena occupies a single region, so per-region bucket execution must
 *       preserve vanilla order exactly (still E0), with the partition
 *       engagement and zero-unmapped guards making a silently-inert or leaky
 *       partitioner unable to pass.</li>
 * </ol>
 *
 * <p>Hard gate ({@code required = true}): increment 1 is bit-identical by
 * construction (same thread, same order), so any mismatch is a real bug in
 * the ownership seam. Later increments that legitimately change interleaving
 * (parallel regions, WS-10) get their own equivalence classes per RFC-0005
 * §4 — this test stays the increment-1 anchor.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class VanillaParityGameTests {

    /** Ticks before the first build: lets the batch's world settle. */
    private static final int SETTLE_TICKS = 10;
    /** Ticks each run simulates between build and digest. */
    private static final int RUN_TICKS = 300;
    /** Differences rendered before truncating a failure report. */
    private static final int DIFF_LIMIT = 12;

    @GameTest(template = "empty", batch = "p2parity", timeoutTicks = 1600)
    public void regionizedTickingVanillaParity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Force-load the arena chunks BEFORE reading the heightmap: getHeight
        // on an unloaded column returns the world minimum, which once built
        // this whole suite at bedrock level (floor below build height, mobs
        // dropped into the void) while the control phase happily agreed with
        // itself. ParityScenario.build refuses such a base outright now.
        BlockPos column = arenaColumn(helper);
        WeftBenchGameTests.forceChunks(level, column, true);
        BlockPos base = new BlockPos(column.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()),
                column.getZ());
        BenchmarkWorld.configure(level);

        // Isolate the variable under test (RFC-0005 §3): every optimization
        // module is forced inert for all three runs; only regionizedTicking
        // differs between them. Config-resolved states restore at teardown.
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        List<SortedMap<String, String>> digests = new ArrayList<>();
        long[] sectionsAtActivation = new long[5];

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ParityScenario.reset(level, base);
            ParityScenario.build(level, base);
            // Post-build probe: the arena must visibly contain its population
            // before the run starts, or every digest is comparing emptiness.
            int spawned = level.getEntities((net.minecraft.world.entity.Entity) null,
                    ParityScenario.entityBounds(base),
                    e -> !(e instanceof net.minecraft.world.entity.player.Player)).size();
            if (spawned < 15) {
                tearDown(level, base);
                helper.fail("Post-build probe: only " + spawned
                        + " entities in arena bounds right after build (expected mobs+items >= 15)");
            }
        });


        // Control A done; start Control B.
        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS, () -> {
            digests.add(ParityScenario.capture(level, base));
            ParityScenario.reset(level, base);
            ParityScenario.build(level, base);
        });

        // Control B done: the determinism gate. Then start the Weft-owned run.
        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS, () -> {
            digests.add(ParityScenario.capture(level, base));
            assertScenarioAlive(helper, level, base, digests.get(0));
            List<String> controlDiff = WorldDigest.diff(digests.get(0), digests.get(1), DIFF_LIMIT);
            if (!controlDiff.isEmpty()) {
                tearDown(level, base);
                helper.fail("Parity harness control failed: two identical VANILLA runs diverged, so "
                        + "the scenario/digest is nondeterministic and cannot judge Weft (RFC-0005 §3). "
                        + "First differences:\n" + String.join("\n", controlDiff));
            }
            RegionizedTicking.setActive(true);
            // Increment 3 rides along: all-vanilla content, so the lane must
            // stay empty while its classification wrap is live. Increment 4
            // too: a single-region arena partitions to one bucket in vanilla
            // order — still bit-identical, or the partitioner is buggy.
            LegacyRouting.setActive(true);
            RegionizedTicking.setPartitioned(true);
            sectionsAtActivation[0] = RegionizedTicking.entitySections();
            sectionsAtActivation[1] = RegionizedTicking.blockEntitySections();
            sectionsAtActivation[2] =
                    LegacyRouting.deferredBlockEntities() + LegacyRouting.deferredEntities();
            sectionsAtActivation[3] = RegionizedTicking.partitionedSections();
            sectionsAtActivation[4] = RegionizedTicking.unmappedUnits();
            ParityScenario.reset(level, base);
            ParityScenario.build(level, base);
        });

        // Weft-owned run done: the parity gate.
        helper.runAfterDelay(SETTLE_TICKS + 3 * RUN_TICKS, () -> {
            digests.add(ParityScenario.capture(level, base));
            long entitySections = RegionizedTicking.entitySections() - sectionsAtActivation[0];
            long blockEntitySections =
                    RegionizedTicking.blockEntitySections() - sectionsAtActivation[1];
            long laneExtractions = LegacyRouting.deferredBlockEntities()
                    + LegacyRouting.deferredEntities() - sectionsAtActivation[2];
            long partitionedSections =
                    RegionizedTicking.partitionedSections() - sectionsAtActivation[3];
            long unmapped = RegionizedTicking.unmappedUnits() - sectionsAtActivation[4];
            tearDown(level, base);

            if (!RegionizedTicking.hooksApplied()) {
                helper.fail("Regionized-ticking ownership mixins did not apply (and the fail-loud "
                        + "config somehow let boot continue) - hooksApplied=false");
            }
            if (!LegacyRouting.hooksApplied()) {
                helper.fail("Legacy-lane extraction mixins did not apply - hooksApplied=false");
            }
            // Zero-residue-on-vanilla guard (increment 3): vanilla content is
            // Tier 0 - with the lane active, nothing may have been extracted.
            if (laneExtractions != 0) {
                helper.fail("Legacy lane extracted " + laneExtractions + " units from an "
                        + "all-vanilla scenario - Tier-0 content routed to the lane (RFC-0001 §7.1)");
            }
            // Increment 4 guards: partitioned execution must actually have
            // engaged (2 sections per tick), and every unit must have mapped
            // to a real region (ticking chunks are a subset of loaded chunks).
            if (partitionedSections < 2L * (RUN_TICKS - 16)) {
                helper.fail("Vacuous partition run: only " + partitionedSections
                        + " partitioned sections across " + RUN_TICKS
                        + " ticks - partitionedTicking never actually engaged");
            }
            if (unmapped != 0) {
                helper.fail("Partitioner leaked " + unmapped + " units into the unmapped tail - "
                        + "a ticking chunk had no topology region (RFC-0001 §4.2 invariant)");
            }
            // Vacuous-run guard: this level ticks once per server tick, so the
            // engine must have owned at least ~RUN_TICKS sections of each kind.
            if (entitySections < RUN_TICKS - 16 || blockEntitySections < RUN_TICKS - 16) {
                helper.fail(String.format(
                        "Vacuous parity run: engine owned only %d entity / %d block-entity sections "
                                + "across %d ticks - regionizedTicking never actually engaged",
                        entitySections, blockEntitySections, RUN_TICKS));
            }
            List<String> parityDiff = WorldDigest.diff(digests.get(0), digests.get(2), DIFF_LIMIT);
            if (!parityDiff.isEmpty()) {
                helper.fail("VANILLA PARITY FAILURE: the Weft-owned run diverged from vanilla on a "
                        + "scenario the control phase proved deterministic. Increment 1 must be "
                        + "bit-identical (same thread, same order) - this is a real ownership-seam "
                        + "bug. First differences:\n" + String.join("\n", parityDiff));
            }
            helper.succeed();
        });
    }

    /**
     * A parity gate over an empty or dead arena would pass vacuously. The
     * digest must show the scenario actually ran: mobs ticking, the furnaces
     * producing output, the clocked dropper having fired. Exact values are
     * not asserted (the control phase owns exactness) — presence is.
     */
    private static void assertScenarioAlive(GameTestHelper helper, ServerLevel level,
                                            BlockPos base, SortedMap<String, String> digest) {
        long blockEntityEntries = digest.keySet().stream().filter(k -> k.startsWith("be (")).count();
        long entityEntries = digest.keySet().stream().filter(k -> k.startsWith("entity ")).count();
        boolean smelted = digest.values().stream().anyMatch(v -> v.contains("minecraft:iron_ingot"));
        boolean dropperFired = digest.values().stream()
                .anyMatch(v -> v.startsWith("minecraft:chest") && v.contains("minecraft:stick"));
        boolean mobsPresent = digest.keySet().stream()
                .filter(k -> k.contains("'p2-zombie-") || k.contains("'p2-sheep-")).count() == 10;
        if (blockEntityEntries < 15 || entityEntries < 11 || !smelted || !dropperFired || !mobsPresent) {
            dumpDigest(level, digest);
            tearDown(level, base);
            helper.fail(String.format(
                    "Parity scenario looks dead/vacuous: %d BE entries, %d entity entries "
                            + "(entity total=%s), smelted=%s, dropperFired=%s, all 10 mobs=%s - "
                            + "the digest would gate nothing (full digest in parity-digest-debug.txt)",
                    blockEntityEntries, entityEntries, digest.get("entity total"),
                    smelted, dropperFired, mobsPresent));
        }
    }

    /** Failure forensics: the whole digest, one entry per line, in the run dir. */
    private static void dumpDigest(ServerLevel level, SortedMap<String, String> digest) {
        try {
            StringBuilder sb = new StringBuilder();
            digest.forEach((k, v) -> sb.append(k).append(" = ").append(v).append('\n'));
            java.nio.file.Files.writeString(
                    level.getServer().getServerDirectory().resolve("parity-digest-debug.txt"),
                    sb.toString());
        } catch (java.io.IOException e) {
            // forensics only; the assertion message still carries the summary
        }
    }

    private static void tearDown(ServerLevel level, BlockPos base) {
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);
        ParityScenario.reset(level, base);
        ParityScenario.restoreFloor(level, base);
        WeftBenchGameTests.forceChunks(level, base, false);
        // Later batches should see shipping/config-resolved module states.
        WeftModules.resolve();
    }

    /**
     * The arena column (x/z only — y must be read AFTER the chunks are
     * force-loaded), well away from the gametest structure plot so resets
     * never touch framework blocks.
     */
    private static BlockPos arenaColumn(GameTestHelper helper) {
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        return new BlockPos(ground.getX() + 64, 0, ground.getZ() + 64);
    }
}
