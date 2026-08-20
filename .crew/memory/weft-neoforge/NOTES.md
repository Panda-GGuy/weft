# weft-neoforge memory

## Standing notes
- Increment 7 loader wiring lives on `crew/neoforge-inc7`: `singleJoinTick` (default OFF) captures entity work at the ServerLevel seam, captures/owns BE ticker and NeoForge fresh-BE additions per region through `PendingUnits`, then runs `[OwnerMail.drainInto -> entity -> fresh/onLoad + BE]` with `WeftScheduler.runOwnedFused`. Serial mode stays canonical; parallel mode joins once per level tick.
- Single-join drains RFC-0006 `sectionEndTasks` once after fused join. Block-entity sharding is disabled whenever fused region fan-out is parallel, avoiding hazard-23 nested submits; serial one-region fusion may still shard.
- Status exposes fused tick/region-task counters. `p2fuse` two-island GameTest exists and passed in full suite before an unrelated later `parallelregionsentitysection` test crashed with pre-existing `Entity is already tracked!`.

## Open threads
- Full GameTest suite remains red after `p2fuse` passes: optional `parallelregionsentitysection` crashes in `ChunkMap.addEntity` with `Entity is already tracked!` during pending-load processing. Not caused inside fused task stack; needs separate test-isolation/tracker audit.
- Re-run `p2fuse` after final cross-owner/tail hardening when selective batch invocation is available; full build is green.

## Lessons
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: MixinExtras `@WrapMethod` on `Level.addBlockEntityTicker` and `addFreshBlockEntities` applies fail-loud; a direct `BlockEntity.onLoad` wrap did not match NeoForge-transformed bytecode and was removed.
