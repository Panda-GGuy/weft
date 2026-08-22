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
- **RESOLVED 2026-08-22: `p2fuse` deterministic assertions landed.** Added
  `lastFusedStageStartNanos`/`lastFusedStageEndNanos` (real wall-clock
  interval capture per fused region task) and a stage-overlap assertion (>=1
  pair of the final section's region tasks with genuinely overlapping
  intervals - "ran on different threads" alone does not prove the
  increment-7 "free of each other" claim; two buckets can run on different
  threads back-to-back with zero overlap). Also added a pending-unit
  assertion (fused BE stage's per-region `PendingUnits` containers hold >=2
  persistent tickers in the final section) and `fusedFreshOnLoadCalls` (a
  regression counter for the `1aff76c` bug, asserted == 1 per fresh BE
  placement in `p2fusefallback`, not 0 and not >1). `p2fusefallback` (new
  hard gametest, `.crew/wip/p2fusefallback-positive-control.patch` applied
  and extended) forces the serial-fallback stand-down and confirms it is
  transient. See commit `04335b0`.
- **RESOLVED 2026-08-22: batch-order dependence in `p2memoryreach` was a
  STRUCTURAL bug, not a flake.** Root cause: every villager in this test IS a
  memory-reach entity and is THEREFORE unconditionally diverted to the
  serial tail in `tickEntitySection`, never into a bucket. Region B,
  containing only villagers, could therefore NEVER appear in
  `RegionizedTicking.lastEntityPartition()` (which only reflects `buckets`)
  no matter how correctly hazard 25 is enforced - the "both regions present
  as distinct buckets" assertion was unsatisfiable by the rig's own
  construction. Sampling across the whole run window (first attempt) did not
  fix it, because the bug was not about timing/sampling. Fix: added one
  non-Mob armour-stand decoy to region B so it has a real, always-bucketable
  unit; the villager-deferral assertion (`unreadyUnits` floor) is unchanged
  and unweakened. Commits `b1d87ee` (sampling, necessary but insufficient
  alone) and `43e9595` (the actual fix). Verified: two consecutive full-suite
  runs in a clean isolated clone (`~/weft-pr14-testrun`), 27/27 required
  tests both times.
- **RESOLVED 2026-08-22: `Entity is already tracked!` crash did NOT
  reproduce in two clean full-suite runs post-fix**, including a run through
  `p2parallelbench` (the original crash site per `#10`'s prior root cause:
  JVM-global `Entity.ENTITY_COUNTER` rewind racing async chunk entity loads,
  already fixed by `ParityScenario` no longer rewinding the allocator - see
  `.crew/memory/_session/parity-issues-report.md`). This PR's fused code
  path does not touch `ChunkMap`/`PersistentEntitySectionManager` at all
  (confirmed by code read: no `addEntity`/`processPendingLoads` call sites
  in `RegionizedTicking`'s fused stages). Treat any recurrence as the #10
  class of bug (intermittent, load-dependent, pre-existing, orthogonal to
  RFC-0007 inc7) rather than a new regression, and check whether the fix
  landed on the branch being tested.
- **Machine-sharing pitfall (2026-08-22):** concurrent GameTest server runs
  against the SAME clone directory (two agent sessions, two branches, one
  `~/weft-pr14`) corrupt each other via `world/session.lock` collisions and
  `NoSuchFileException` on `.neoforge-tmp` save files, and can cost 400+% CPU
  contention that skews timing-sensitive gates (`ws1entityphasereduction`
  false-failed under contention, passed clean once isolated). When another
  session might be running gametest in the same directory, clone a SEPARATE
  working copy (`git clone <local-clone> <new-dir>`, checkout the same
  branch/commit, `git remote set-url origin <real-remote>`) before running
  `runGameTestServer`.
- **Unexplained `p2parallel` flake (1 of 3 runs, historical):** failed with
  `entity=[2] be=[2, 16] A=2 B=16` - entity probe saw one island, BE probe saw
  both. Passed on immediate re-run, identical tree. Same single-bucket shape as
  the pre-fix brainmobs failure; may share the same root-cause family
  (villager/memory-reach entities being the only occupants of a region in a
  given section). Watch for recurrence; the `p2memoryreach` fix does not
  necessarily cover this test's own rig.
- Selective batch invocation is not exposed by current Gradle run config.

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
