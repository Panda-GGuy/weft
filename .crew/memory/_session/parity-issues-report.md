# Parity issues report — 2026-08-19/20

Branch: `crew/parity-close`  
Base: `d269116` (main tip with hazard fixes and increment-7 scaffold)

## Decision

**BLOCK** `parallelRegions` default-ON. Hazards 21-25 fixes remain present, but
#3 and #6 still lack dedicated non-vacuous automated regressions and therefore
remain open. Green GameTests are not soak evidence; no live soak, chaos, or
real-pack matrix was run in this packet.

## #10 — `Entity is already tracked!`

### Reproduction

- Historical evidence: two identical crashes in six fresh full-suite runs,
  always during optional `p2parallelbench`, at
  `PersistentEntitySectionManager.processPendingLoads -> ChunkMap.addEntity`.
- This packet ran one unmodified baseline suite. `p2parallelbench` passed, so
  flake did not reproduce (consistent with prior intermittent rate). Suite had
  one unrelated timing-gate miss (`ws1entityphasereduction`, 8.3% vs 10%).

### Root cause

This is an **entity numeric-id collision, not double-registration of one UUID**.
`ChunkMap.addEntity` keys `entityMap` by `Entity.getId()` and throws when that
integer is already present. `PersistentEntitySectionManager.addEntity` had
already accepted the loaded entity through its UUID set, proving UUID identity
was distinct.

`ParityScenario.build` rewound JVM-global `Entity.ENTITY_COUNTER` to 5,000,000
three times per `p2parity` run. Chunk entity deserialization is asynchronous:
an entity load can allocate an id, remain in `loadingInbox`, then replay after
the global counter has been rewound and the same integer assigned to another
live entity. Which pending load completes when makes the failure intermittent;
`p2parallelbench` supplies enough later entities to expose it. This also matches
the existing comment in `ObservabilityGameTests`: the same rewind previously
broke `p1EndToEndMspt` with the same exception, but its local restore only
protected that borrower, not `p2parity` itself.

### Fix and gate

- Removed production `EntityCounterAccessor` mixin.
- Parity scenario now assigns its 15 entities fixed ids in a reserved range
  before `addFreshEntity`; global allocator remains monotonic.
- Updated RFC-0005 determinism claim to match mechanism.
- Existing `p2parity` control remains non-vacuous: it still compares two
  vanilla runs before judging Weft and checks live machinery/entity content.
- `p2parallelbench` is scale/stress coverage for collision symptom, not sole
  correctness proof.

Verification after fix:

1. `./gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge --console=plain`
   — PASS.
2. `./gradlew.bat :weft-neoforge:runGameTestServer -PwithNeoForge --console=plain`
   — PASS, 24/24, `p2parallelbench` 8 regions / 1,760 mobs / 8 workers.
3. Same full-suite command again — PASS, 24/24, same non-vacuous fan-out.

Disposition: **close #10** with above root cause, fix, and evidence. This fix is
GameTest-only behavior plus removal of an otherwise test-only production
accessor; no runtime feature or default posture changed.

## Hazards 21-25: claims versus gates

| Issue | Hazard / claim | Standing automated evidence | Disposition |
|---|---|---|---|
| #3 | 21: worker `sendBlockUpdated` must defer whole navigation update until section end | Full suite exercises mixin presence only; no villager/door fan-out trigger, no assertion that work ran after barrier | **Leave open.** Required gate: `p2navdefer`, two real regions, villager/door or direct equivalent on worker, fan-out proof, deferred-call counter/probe, navigation recompute completes on server thread after join. |
| #4 | 22: `load=true` worker read may use generated-FULL border view, while `getChunkNow` semantics stay null for unloaded chunks | `p2parallel`, `p2parallelcap`, `p2combined`, `p2memoryreach`; engagement/worker probes plus clean worker reads | **Closed.** Non-vacuous path coverage plus prior 3/3 boot and 106-round churn evidence. Border-read counter value remains observability, not soak replacement. |
| #5 | 23: never nest BE-shard submission inside parallel-region submission | `p2combined`: >=2 regions, >=2 worker threads, `shardPasses == 0` while fan-out engaged | **Closed.** Exact policy mechanism gated. Underlying ForkJoin task-loss/starvation cause remains unknown; any future nested submitter needs combination gate. |
| #6 | 24: radius-1 neighbourhood absent => unit takes serial tail, no worker guard crash | General parallel gates exercise readiness classification but never force neighbour eviction under running section | **Leave open.** Required gate: `p2evictionchurn`, boundary BE, initially live west/east neighbour then release/evict it, prove fan-out remains engaged elsewhere, `unreadyUnits > 0`, `unmappedUnits == 0`, unit completes on server-thread serial tail, zero guard trip. |
| #7 | 25: modded Brain sensor entities must not read cross-region state on workers | `p2memoryreach`: custom outside-whitelist Brain mob plus vanilla controls; multiple regions/workers; custom entity observed only on server thread | **Closed.** Exact fail-soft serialization rule gated. Unknown unregistered Brain shapes remain compatibility risk by design. |

## Residual risk

- Full suite x2 is regression evidence, not soak.
- Hazards 19/20 remain default-ON exit criteria and were not re-audited here.
- #3 and #6 closures require gates above; historical/manual soak does not make
  fresh-clone automation reproducible.
- Q1/Q2 next: gate increment-7 fuse (`p2fuse`) after 7c lands, then implement
  dedicated `p2navdefer` / `p2evictionchurn` gates.
