# weft-parity memory

## Standing notes
- Full gametest suite (`./gradlew :weft-neoforge:runGameTestServer -PwithNeoForge`)
  is the load-bearing hard gate: 24/24 required tests, ~2.3min, at HEAD (32e3300).
- Engine unit tests (`./gradlew :weft-engine:test`): 118 tests, 0 failures.
- Both re-run clean 2026-08-19 as part of hazard 21-25 verification.

## Open threads
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
