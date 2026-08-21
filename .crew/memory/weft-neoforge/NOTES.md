# weft-neoforge memory

## Standing notes
- Increment 7 loader wiring lives on `crew/neoforge-inc7`: `singleJoinTick` (default OFF) captures entity work at the ServerLevel seam, captures/owns BE ticker and NeoForge fresh-BE additions per region through `PendingUnits`, then runs `[OwnerMail.drainInto -> entity -> fresh/onLoad + BE]` with `WeftScheduler.runOwnedFused`. Serial mode stays canonical; parallel mode joins once per level tick.
- Single-join drains RFC-0006 `sectionEndTasks` once after fused join. Block-entity sharding is disabled whenever fused region fan-out is parallel, avoiding hazard-23 nested submits; serial one-region fusion may still shard.
- Fused hazard-24/25 handling cannot use old post-join serial tails: an entity can register a same-tick BE. Any unsafe entity/BE makes that tick's full fused set run serially in owner order. Fused unmapped units fail loud.
- Fresh `onLoad` work also makes that fused tick serial; ticker/fresh additions on server thread between sections enter persistent per-region containers for next BE stage. Disable fusion before flushing retained units back to vanilla, or the flush recaptures itself.
- Status exposes fused tick/region-task counters. `p2fuse` two-island GameTest exists and passed in full suite before an unrelated later `parallelregionsentitysection` test crashed with pre-existing `Entity is already tracked!`.

## Open threads
- Full GameTest suite remains red after `p2fuse` passes: optional `parallelregionsentitysection` crashes in `ChunkMap.addEntity` with `Entity is already tracked!` during pending-load processing. Not caused inside fused task stack; needs separate test-isolation/tracker audit.
- Selective batch invocation is not exposed by current Gradle run config. Final full-suite rerun after hardening reached the same earlier optional tracker crash before suite completion; full build is green.

## Lessons
- 2026-08-21: after rebasing a pushed feature branch, re-run focused NeoForge
  compile plus full build before force-pushing with lease; a clean rebase does
  not preserve remote SHA identity or its old CI evidence.
- 2026-08-20: equal-heap fair bench tied; worldgen MSPT alone is not a fan-out
  win. Keep `singleJoinTick` OFF; next useful signal is multi-bucket
  `entity_buckets > 1` under equal JVM/config, plus full GameTest isolation of
  the tracker crash after `p2fuse`.
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: MixinExtras `@WrapMethod` on `Level.addBlockEntityTicker` and `addFreshBlockEntities` applies fail-loud; a direct `BlockEntity.onLoad` wrap did not match NeoForge-transformed bytecode and was removed.
- 2026-08-20: If global Gradle metadata is incomplete, a workspace-local `GRADLE_USER_HOME` avoids that cache fault, but verify free disk first; artifact resolution can otherwise fail before NeoForge compilation.
