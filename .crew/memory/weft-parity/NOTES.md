# weft-parity memory

## Update — 2026-08-22: PR #14 deterministic p2fuse gate landed, suite green x2
- Addressed the 2026-08-20 recovery checkpoint's BLOCK reasons for PR #14
  (head `432f831` -> `43e9595` on `crew/neoforge-inc7`):
  - `p2fuse` now has a real cross-region **stage-overlap** assertion (new
    `RegionizedTicking.lastFusedStageStartNanos/EndNanos`, wall-clock interval
    capture per fused region task; asserts >=1 genuinely overlapping pair,
    not just distinct thread names).
  - `p2fuse` now has a **pending-unit** assertion (fused BE stage's per-region
    `PendingUnits` containers hold >=2 persistent tickers in the final
    section - proves RFC-0007 sec 4 item 1's containers are the live path).
  - New `p2fusefallback` hard gametest is the **forced entity/BE fallback**
    assertion: places a fresh block entity under sustained fan-out, asserts
    `fusedSerialFallbacks` moves, `fusedFreshOnLoadCalls` (new regression
    counter for the `1aff76c` bug) moves by exactly 1, and fan-out resumes
    (transient, not latching).
  - `Entity is already tracked!` did NOT reproduce across two clean full-suite
    runs (isolated clone, see weft-neoforge NOTES.md for the isolation
    methodology and root-cause history from issue #10). Fused code path
    (read, confirmed) makes zero calls into `ChunkMap`/
    `PersistentEntitySectionManager`; any recurrence is issue #10's known
    intermittent class, not a regression from this PR.
  - Along the way found and fixed a STRUCTURAL (not flaky) bug in the
    pre-existing `p2memoryreach` gate: it asserted an outcome
    (villager-only region appears as a distinct bucket) that was
    unsatisfiable by its own rig's construction, since every villager there
    is unconditionally deferred to the serial tail. Fixed with a decoy
    always-bucketable entity; the actual hazard-25 assertion is unchanged.
- **Full suite verified GREEN twice in a row**: 27/27 required tests, two
  separate `runGameTestServer` invocations in a clean isolated clone
  (`~/weft-pr14-testrun`, checked out to `crew/neoforge-inc7` @ `43e9595`).
  `singleJoinTick` stayed default OFF throughout (test-only flag flips via
  `RegionizedTicking.setSingleJoin`, never touched in shipped config).
- Still true from the 2026-08-20 checkpoint and NOT addressed by this pass:
  issues #3 (`p2navdefer` status unverified this session), #6
  (`p2evictionchurn` still doesn't exist), #16 (Moonrise/parallel posture
  field data still not fan-out proof). This pass was scoped to PR #14's own
  "still blocking review/merge" list only.

## Recovery checkpoint — 2026-08-20
- PR #14 parity review is BLOCK at head `432f831`: fused hazard-24 readiness
  failure counts `unreadyUnits` but still runs BE ticker on worker; fused entity
  tail runs after region BE stages; `p2fuse` lacks deterministic
  overlap/pending/fallback proof. Review id: `4979884858`.
- Focused #3 patch adds `p2navdefer` counters and villager/door navigation
  invalidation under two-region fan-out. Compile gate passes. First full suite
  exposed indirect-trigger flaw (fixed) plus disk exhaustion; final revision
  has not completed GameTest, so #3 stays OPEN.
- #6 stays OPEN pending `p2evictionchurn`. #16 keeps Moonrise + parallel
  posture at yield/refuse; field ON/OFF data is not fan-out proof because owned
  parallel work was often zero.
- Full report: `.crew/memory/_session/parity-recovery-report-2026-08-20.md`.

## Standing notes
- Full gametest suite (`./gradlew :weft-neoforge:runGameTestServer -PwithNeoForge`)
  is the load-bearing hard gate: 24/24 required tests. After #10 harness fix,
  two consecutive runs passed at `crew/parity-close` (2026-08-19/20).

- Engine unit tests (`./gradlew :weft-engine:test`): 118 tests, 0 failures.
- Both re-run clean 2026-08-19 as part of hazard 21-25 verification.

## Open threads
- #3 hazard 21 stays open pending `p2navdefer`: door/Brain trigger, real
  fan-out, deferred call observed after join on server thread.
- #6 hazard 24 stays open pending `p2evictionchurn`: boundary block entity,
  neighbour eviction, `unreadyUnits > 0`, `unmappedUnits == 0`, serial-tail
  completion and zero worker guard trip.
- #10 root-caused: parity harness rewound JVM-global entity numeric-id counter
  while async chunk entity loads retained previously allocated ids. ChunkMap is
  keyed by numeric id, so later replay collided despite distinct UUIDs. Fixed
  by fixed per-scenario ids without rewinding allocator; production accessor
  removed. Full report: `.crew/memory/_session/parity-issues-report.md`.
- Hazards 19/20 (RFC-0006 default-ON exit criteria) not re-verified this pass -
  separate from 21-25, still gating default-ON along with soak/chaos/real-pack.
- No CI gate for hazard 21 (door/Brain interaction) or hazard 24 (mid-section
  eviction) specifically - both rely on mixin presence + historical/script-only
  reproduction, not a standing automated test. Candidate future gates.
- Hazard 23's root mechanism (ForkJoinPool: task never dequeued, no starvation
  observed) is still undiagnosed; policy fix only ("one submission level per
  section"). Next nested-submission feature (entity sharding, RFC-0008 Â§5)
  needs its own combination gate before shipping - do not let it ship bare.
- Modded Brain-entities not in `MemoryReachEntities.SERIAL` fail-loud crash
  under parallelRegions rather than degrade - real compat risk, watch issue
  reports for this specific stack shape and add to the list on sight.

## Lessons

- 2026-08-20 (#6 / `p2evictionchurn` attempt, FAILED - read before retrying):
  five consecutive rig designs could not make a chunk absent at radius 1 next to
  a ticking chunk inside a GameTest. Every attempt failed the SAME way, on the
  precondition rather than the assertion: "west neighbour is resident".
  Tried, all rejected by evidence:
    1. neighbour inside `WeftBenchGameTests.forceChunks` radius-10 grid -> the
       grid's own FORCED ticket keeps it resident; releasing ours changes nothing
    2. boundary moved to the grid's west edge -> still resident
    3. release ticket + `chunkSource.tick(() -> false, true)` to force a sweep
       -> still resident within the phase window
    4. own radius-`GRID` forced grid extending east/south only, 4096 blocks away
       from other arenas -> still resident
  Root cause is structural, and RFC-0006 hazard 22 already states it: vanilla
  guarantees radius-2 generated `ChunkStatus.FULL` around any entity-ticking
  chunk. A ticking rig therefore KEEPS its radius-2 neighbourhood loaded by
  construction. Hazard 24's real precondition is a chunk that was resident and
  then evicted *while the owner kept ticking* - which needs the ticket churn of
  a teleport or a pre-generator sweep, i.e. exactly what the issue reported and
  what a forced-grid gametest cannot express.
  Do NOT weaken the assertion to make it pass; a gate that cannot reach its own
  precondition proves nothing. Next attempt should drive real ticket churn (a
  moving player ticket, or Chunky-style sweep) or move to a soak-style harness.
  `unreadyBlockEntityUnits()` was added and kept - hazard 24's own counter,
  separate from the 3-cause `unreadyUnits` total, so whatever gate finally lands
  can assert THIS cause. #6 stays OPEN.
- 2026-08-20 (review of own #3 gate): the first `p2navdefer` revision asserted
  the villager serial tail by reading `unreadyUnits`, which is incremented by
  THREE causes - memory-reach entities, entities whose read neighbourhood is not
  live, and block entities in the same state. On an arena with border chunks the
  neighbourhood cause alone can clear a `>= VILLAGERS` threshold with zero
  villagers ever classified, i.e. a vacuous pass in a gate written specifically
  to be non-vacuous. Split out `memoryReachUnits` and asserted that instead.
  Generalise: before asserting a threshold on a counter, grep every increment
  site. A shared counter can only prove the union of its causes. This is the
  same mistake the unmapped/unready split already fixed one counter earlier -
  which means the pattern, not the counter, is the recurring defect.
- 2026-08-20 fair equal-heap worldgen ON/OFF was tied (MSPT ~39 vs ~40). Do not
  treat worldgen Stressmark as fan-out/default-ON evidence. Keep #3/#6 open until
  dedicated gates prove real multi-bucket fan-out; keep singleJoinTick OFF.
- 2026-08-20: hazard-21 `p2navdefer` completed `compileGametestJava` and the full
  25/25 required GameTest suite. This makes #3 reviewable; it does not supply
  #6 evidence or justify default-ON.
- 2026-08-20: a non-vacuous hazard-21 gate can drive the real
  `sendBlockUpdated` mixin seam from an owned worker bucket, but must separately
  prove two-region fan-out, Brain serial-tail engagement, callback completion,
  and server-thread drain. Keep issue open until compile + full GameTest pass.
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: verified GH issues #3-#7 (RFC-0006 hazards 21-25) against code +
  a fresh 24/24 gametest suite run (x2) + 118/118 engine unit tests. All 5
  hazards' fixes are present in code exactly as each issue/commit describes,
  and each has a non-vacuous automated gate except 21 and 24 (soak/script-only
  regression coverage for those two specifically). Commented/recommended CLOSE
  on all five with evidence; full detail in
  `.crew/memory/_session/parity-hazards-report.md`. Green gates != soak;
  did not run soak/chaos/real-pack this session - said so explicitly in the
  report and in each issue comment.
- 2026-08-20: never rewind Minecraft's JVM-global entity-id allocator while
  async chunk loads can be pending. Pin test entities directly before adding
  them; keep allocator monotonic. `ChunkMap.addEntity` duplicate means numeric
  id collision, not necessarily same UUID/entity.

## Next
- Q1/Q2: after increment-7 7c lands, add `p2fuse`; then build `p2navdefer` and
  `p2evictionchurn` so #3/#6 can close honestly without lead intervention.

## 2026-08-22 — PR #14 unblocked: p2fuse deterministic assertions + full suite green x2

- Added to `p2fuse`: a real wall-clock STAGE-OVERLAP assertion (region tasks'
  mail-drain-start to BE-stage-end intervals, index-aligned with
  `lastEntityPartition()`, exposed via new `lastFusedStageStartNanos()` /
  `lastFusedStageEndNanos()`) — proves at least one pair of the final
  section's region tasks genuinely overlapped in time, not just "ran on
  different threads" (two buckets can be handed to the pool sequentially with
  no free worker and never overlap while still passing every prior
  assertion). Also a PENDING-UNIT assertion (`lastBlockEntityUnits() >= 2`)
  proving the fused BE stage's per-region `PendingUnits` containers are the
  live path carrying real persistent tickers, not dead code that happens to
  compile.
- New hard gametest `p2fusefallback`: forces the fused path's serial
  fallback (place a fresh block entity mid-run under sustained fan-out) and
  asserts fan-out was engaged before, `fusedSerialFallbacks` moved during,
  the new `fusedFreshOnLoadCalls` counter moved by EXACTLY 1 (not 0 = seam
  never fired, not >1 = the `1aff76c` regression where `onLoad()` re-fires
  every tick forever), and fan-out resumed after (transient, not latching).
  This extends and applies the previously-held-back
  `.crew/wip/p2fusefallback-positive-control.patch`.
- Found and fixed a REAL, non-vacuous bug in `p2memoryreach`
  (`brainMobsNeverReachAWorker`), not just the previously-suspected
  batch-order flake: every villager in that test is memory-reach and is
  THEREFORE unconditionally diverted to the serial tail in
  `tickEntitySection`, never into a bucket. Region B (villagers only) could
  therefore NEVER appear in `lastEntityPartition()` no matter how correctly
  hazard 25 is enforced — the "both regions present as distinct buckets"
  assertion was unsatisfiable by the rig's own construction. Fixed by adding
  one non-Mob armour-stand decoy to region B (a guaranteed-bucketable unit)
  and sampling fan-out across the whole run window instead of one end
  snapshot (defends against the real, separate batch-order/global-probe
  contamination issue too). Villager deferral assertion unchanged/unweakened.
- Full build green (`./gradlew build -PwithNeoForge`).
  `./gradlew :weft-neoforge:runGameTestServer -PwithNeoForge` run TWICE
  back-to-back: both 27/27 required tests green, `p2fuse` non-vacuous
  (fusedTicks=211, fannedOut=211, standDown=0), `p2fusefallback` non-vacuous
  (fallback engaged mid-run, onLoad=1, resumed after), `p2memoryreach` green.
  The historically-flaky `blockentityshardingbelowthreshold` (optional,
  non-required) failed both runs — pre-existing timing-margin bench, not
  touched by this work, does not gate the build.
  The previously-reported `parallelregionsentitysection` "Entity is already
  tracked!" tracker crash and `ws1entityphasereduction` timing-margin miss
  did NOT reproduce in either of these two runs — consistent with prior notes
  describing both as intermittent/timing-sensitive, not structural.
- PR #14 (`crew/neoforge-inc7`) pushed with these fixes. `singleJoinTick`
  stays default OFF; no shipping-posture change.
