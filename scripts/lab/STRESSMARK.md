# Stressmark fresh-world A/B protocol

Purpose: compare default-OFF control against opt-in P2 region fan-out during
fresh world generation. This is a bounded worldgen stress check, not Create/AE2
soak, parity clearance, or evidence for changing defaults.

## Fixed inputs

Record these once before either leg:

- Weft commit and SHA-256 of tested Weft jar.
- Java vendor/version and JVM arguments.
- SHA-256 for every mod jar. Both legs must use byte-identical mod directories.
- Complete `server.properties` and `config/weft-common.toml` for each leg.
- Stressmark command: `benchmark stress start 20 8.0`.
- Run duration: 120 seconds after command reports started.
- `view-distance=10`, `simulation-distance=10`, normal level type.
- Pinned seed: `8300982059889285399`.

Fixed Stressmark/worldgen pack used by this protocol (plus tested Weft jar):

```text
Amplified_Nether_26.2_v1.2.16.jar
Chunky-NeoForge-1.4.23.jar
ferritecore-7.0.3-neoforge.jar
lithostitched-1.8.0+beta4-neoforge-21.1.jar
modernfix-neoforge-5.27.20+mc1.21.1.jar
spark-1.10.124-neoforge.jar
stressmark-neoforge-1.21.1.jar
Terralith_1.21.1_v2.6.2_Neoforge.jar
YungsApi-1.21.1-NeoForge-5.1.7.jar
YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar
YungsBetterMineshafts-1.21.1-NeoForge-5.1.1.jar
YungsBetterNetherFortresses-1.21.1-NeoForge-3.1.5.jar
YungsBetterOceanMonuments-1.21.1-NeoForge-4.1.2.jar
```

Filename match alone is insufficient; recorded SHA-256 values are authority.
Changing this manifest creates a new experiment and must be stated in result.

Do not add or remove mods between legs. Worldgen packs and Stressmark must be
identical. Moonrise must stay absent because its region-thread coexistence is
unresolved; record that absence rather than treating it as proof of general
compatibility.

## Fresh-world rule

Use two new, never-before-booted level names, for example
`stressmark-<run-id>-off` and `stressmark-<run-id>-on`. Before first boot of each
leg, record that no directory with that exact `level-name` exists. Never point a
leg at `world`, copy one leg's world into the other, delete an existing world,
or reuse a prior Stressmark world. Set the same `level-seed` above for both.

Run OFF first, then ON. For performance claims, one pair is insufficient: run
at least three fresh pairs and alternate order on later pairs (`OFF/ON`,
`ON/OFF`, `OFF/ON`). Correctness failures block immediately regardless of
order.

## Exact flag profiles

Leave P1 shipping defaults unchanged in both legs. In particular, do not use
this protocol to disable `asyncPathfinding` or authoritative spawn density.
Keep observability settings identical; `metricsEnabled = true` is allowed for
capturing status but is not a P2 feature under comparison.

OFF control:

```toml
entitySharding = false
regionizedTicking = false
partitionedTicking = false
parallelRegions = false
blockEntitySharding = false
ownerMailRouting = false
singleJoinTick = false
legacyLane = false
```

ON candidate:

```toml
entitySharding = false
regionizedTicking = true
partitionedTicking = true
parallelRegions = true
blockEntitySharding = false
ownerMailRouting = true
singleJoinTick = false
legacyLane = false
```

`singleJoinTick` stays OFF: current PR #14 wiring is review-blocked. Entity and
block-entity sharding stay OFF so this protocol isolates cross-region fan-out.
Any different flag profile is a different experiment and must be named as such.

## Per-leg sequence

1. Write unique `level-name`, pinned `level-seed`, fixed distances, and exact
   flag profile while server is stopped. Save copies of both config files.
2. Start dedicated server. Capture log from process start. Wait for `Done` and
   then 60 seconds idle. Run `weft status` and `benchmark clear` through RCON.
3. Run `benchmark stress start 20 8.0`. Confirm response says 20 bots at 8.0
   blocks/second. Start 120-second timer only after confirmation.
4. At 60 and 120 seconds run `benchmark stress status` and `weft status`.
   Capture exporter scrape too when enabled.
5. At 120 seconds run, in order: `benchmark stress stop`, `benchmark detailed`,
   `weft status`, `save-all flush`, then `stop`. Preserve full log and command
   transcript.
6. Reboot same leg once as a recovery check. Require `Done`, run `weft status`,
   then `stop`. This reboot validates saved world; it is not another fresh run.

If server appears frozen, use `hang-triage.sh` before stopping it so thread-dump
evidence survives.

## Pass criteria

Both legs must satisfy all of these:

- Initial boot, full 120-second load, save, clean shutdown, and recovery boot
  complete without crash, permanent hang, watchdog kill, uncaught fatal error,
  ownership/domain guard trip, or chunk/save corruption.
- Stressmark starts exactly 20 bots at speed 8.0 and remains running until the
  deliberate stop. A bot-count drop, early stop, or missing final report fails
  the leg.
- `weft status` reports `0 unmapped units` and `0 domain trips` at every sample.
  Any fail-loud cross-owner error fails immediately. Deferred/unready/border-read
  counts may be nonzero, but must be recorded rather than hidden.
- Saved seed, level name, flag posture, mod hashes, Weft hash, duration, and
  command transcript match declared inputs.

Profile-specific criteria:

- OFF status must show regionized, partitioned, parallel, owner-mail, fused,
  entity-shard, and block-entity-shard execution inactive. Parallel/fused task
  engagement must remain zero.
- ON status must show regionized ticking, partitioning, parallel regions, and
  owner-mail routing active, with `singleJoinTick` inactive. It must demonstrate
  at least two live regions/buckets and nonzero parallel region-task/worker
  engagement during load. A healthy run with only one region is useful boot
  evidence but does not pass ON engagement.

Record TPS/MSPT, GC, generated/loaded chunk counts, and Stressmark warnings for
diagnosis. No fixed TPS/MSPT number is a correctness pass criterion, one A/B
pair cannot support a speedup claim, and neither green leg counts as Create/AE2
soak, R7, parity, release readiness, or default-ON clearance.
