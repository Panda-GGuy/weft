# weft-neoforge memory

## Standing notes
- Increment 7 loader wiring lives on `crew/neoforge-inc7`: `singleJoinTick` (default OFF) captures entity work at the ServerLevel seam, captures/owns BE ticker and NeoForge fresh-BE additions per region through `PendingUnits`, then runs `[OwnerMail.drainInto -> entity -> fresh/onLoad + BE]` with `WeftScheduler.runOwnedFused`. Serial mode stays canonical; parallel mode joins once per level tick.
- Single-join drains RFC-0006 `sectionEndTasks` once after fused join. Block-entity sharding is disabled whenever fused region fan-out is parallel, avoiding hazard-23 nested submits; serial one-region fusion may still shard.
- Fused hazard-24/25 handling cannot use old post-join serial tails: an entity can register a same-tick BE. Any unsafe entity/BE makes that tick's full fused set run serially in owner order. Fused unmapped units fail loud.
- Fresh `onLoad` work also makes that fused tick serial; ticker/fresh additions on server thread between sections enter persistent per-region containers for next BE stage. Disable fusion before flushing retained units back to vanilla, or the flush recaptures itself.
- Status exposes fused tick/region-task counters. `p2fuse` two-island GameTest exists and passed in full suite before an unrelated later `parallelregionsentitysection` test crashed with pre-existing `Entity is already tracked!`.
- Fused deferral counters are split BY CAUSE, and the split is per-PATH. The
  partitioned path already split `memoryReachUnits` / `unreadyBlockEntityUnits`
  out of `unreadyUnits`; the fused path did not, so a fused hazard-24/25 gate
  read zero and would have passed vacuously. Fixed on `crew/neoforge-inc7`
  (`2651469`). When adding a tick path, port its attribution counters too.
- `fusedSerialFallbacks` counts fused ticks that STOOD DOWN from fan-out.
  Needed because `fusedTicks`/`fusedRegions` count a stood-down tick exactly
  like a fanned-out one, so neither can prove fan-out happened. `p2fuse` now
  asserts `fusedTicks - fusedSerialFallbacks >= RUN_TICKS - 32` (sustained
  fan-out), that the two partition the fused ticks, and that the hazard cause
  counters are 0 in a rig that cannot produce them. It also logs an evidence
  line (`p2fuse: fusedTicks=... standDown=...`) so a reviewer sees numbers.

## Open threads
- **Batch-order dependence in `p2memoryreach`.** Adding ANY new GameTest batch
  reshuffles batch order and makes `brainMobsNeverReachAWorker` fail with
  "Entity section ran 1 bucket(s)". It reads `lastEntityPartition()`, a global
  last-section probe, so it inherits whatever topology the previous batch left.
  Proven pre-existing, not caused by the fresh-BE fix: engine fix alone = 26/26;
  engine fix + p2fuse strengthening (no new batch) = 26/26; engine fix + new
  batch = brainmobs fails. Fix it to force its own fan-out precondition.
- **Blocked on the above:** the `fusedSerialFallbacks` positive control is
  written, verified, and held back at
  `.crew/wip/p2fusefallback-positive-control.patch` (applies clean to
  `1aff76c`). Until it lands, `p2fuse`'s sustained-fan-out assertion subtracts
  a counter that is always 0 in a healthy rig, so it cannot yet tell a working
  counter from a broken one.
- **Unexplained `p2parallel` flake (1 of 3 runs):** failed with
  `entity=[2] be=[2, 16] A=2 B=16` - entity probe saw one island, BE probe saw
  both. Passed on immediate re-run, identical tree. Same single-bucket shape as
  the brainmobs failure; may share a root cause.
- Full GameTest suite remains red after `p2fuse` passes: optional `parallelregionsentitysection` crashes in `ChunkMap.addEntity` with `Entity is already tracked!` during pending-load processing. Not caused inside fused task stack; needs separate test-isolation/tracker audit.
- Selective batch invocation is not exposed by current Gradle run config. Final full-suite rerun after hardening reached the same earlier optional tracker crash before suite completion; full build is green.

## Lessons
- 2026-08-21: **`PendingUnits.tick` is the wrong verb for one-shot work.** The
  fused stage called `units.fresh.tick(BlockEntity::isRemoved, ...)`, but
  `tick()` only prunes units whose `removed` predicate is true - and a block
  entity that loaded fine is never `isRemoved()`. So `fresh` never emptied:
  `onLoad()` re-ran every tick forever (NeoForge `onLoad` invalidates
  capabilities => per-tick thrash), and `hasFresh` stayed true, which stood the
  fused path down from fan-out PERMANENTLY after the first BE placement.
  Parallel regions silently became serial. Use `drainAll()` for consume-once
  work; `tick()` is for recurring units only.
- 2026-08-21: **a gate whose subtrahend is always zero is not a gate.** p2fuse
  computes `fannedOut = fusedTicks - fusedSerialFallbacks`; in a healthy rig
  the second term is 0, so the assertion is identical to one where the counter
  is broken. Writing the positive control that FORCES the counter to move is
  what found the fresh-BE bug above. Always ask what a new counter would read
  if it were wired up wrong.
- 2026-08-21: **isolate before attributing a test failure.** Two failures
  appeared together; the instinct was to blame the engine fix. Running the
  engine fix ALONE against HEAD gametests (26/26) exonerated it in one run and
  proved the other failure was a pre-existing order dependence the new batch
  merely revealed. One isolation run beats an hour of reasoning.
- 2026-08-21: this file's `.java` sources contain double-encoded mojibake in
  comments; an editor round-trip re-encodes them AGAIN and turns a 35-line
  change into 74+/39- of invisible churn. Patch such files by script from the
  HEAD blob with ASCII-only inserts, and assert the non-ASCII line count is
  unchanged before writing.
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
