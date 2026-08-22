# weft-parity memory

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

- 2026-08-22 (#6 / `p2evictionchurn` attempt 6, FAILED - root cause now
  structural, not empirical): tried a two-independent-ticket-source design
  (permanent FORCED ticket on the block-entity chunk + a transient LoadBot
  join/leave on the read-neighbour) specifically to dodge attempts 1-4's
  shared-forced-grid failure mode. Still failed on the same precondition
  ("west chunk still resident"), and this time read vanilla's own decompiled
  source (`net.minecraft.server.level.ChunkLevel`/`DistanceManager`) to find
  out why: chunk ticket propagation is MONOTONIC per sustained source - a
  `FORCED` (or `PLAYER`) ticket on chunk C always keeps every radius-1
  neighbour at minimum `BLOCK_TICKING` for as long as that one ticket exists,
  independent of grid shape. This generalises attempts 1-4's finding (a forced
  grid's own border regenerates) to ANY single sustained ticket source,
  forced-grid or not. Conclusion: no arena kept alive by one ticket source can
  ever see its own neighbour evicted - it structurally cannot happen. The real
  crash needs the block-entity chunk and its neighbour on TWO DIFFERENT,
  ASYMMETRICALLY-DECAYING sources (a moving player's view-distance ring is the
  natural case: trailing edge decays before the leading edge advances). That
  is inherently a multi-tick, movement-driven race, not a static two-source
  arena. PR #29 (draft) has the attempt + full writeup; issue #6 commented.
  Next attempt needs an actually-MOVING LoadBot tracing a real view-distance
  boundary crossing, not a join/leave pair - meaningfully harder to land the
  race window correctly. Given 6 attempts across two sessions, worth weighing
  against falling back to `scripts/lab/eviction-repro.py`'s manual rcon repro
  as standing evidence, with an explicit documented gap on GameTest automation,
  rather than continued agent-hours on attempt 7+.

## Next
- Q1/Q2: after increment-7 7c lands, add `p2fuse`; then build `p2navdefer` and
  `p2evictionchurn` so #3/#6 can close honestly without lead intervention.
  `p2evictionchurn` specifically may need a lead decision: continue chasing a
  moving-ticket GameTest design, or accept the manual rcon repro as evidence
  and document the automation gap (see 2026-08-22 lesson above).
