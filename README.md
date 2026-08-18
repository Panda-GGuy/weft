# Weft

**A multithreaded server engine for modded Minecraft.**

In weaving, warp threads run in parallel and the weft binds them into one
fabric. Same idea here: regions of the world tick in parallel; Weft is what
makes them one consistent simulation.

Weft replaces Minecraft's single-threaded server tick with a phased parallel
pipeline — regionized world ticking (the Folia insight) plus a first-class
**graph layer** that gives cross-chunk mod systems (energy nets, logistics,
rotational networks) a correct parallel home — while a **compat sandbox** runs
unverified mods with exact single-threaded semantics so existing mods keep
working, unmodified.

Read [RFC-0001](docs/RFC-0001-weft-architecture.md) first. It is the design
authority for everything in this repo. Design notes, decisions, findings, and
the testing playbook live on the
[project wiki](https://github.com/Panda-GGuy/weft/wiki).

## Modules

| Module | What | Minecraft deps |
|---|---|---|
| `weft-engine` | Scheduler, regions, mailboxes, guards, graph scheduler | **None** (enforced at build) |
| `weft-api` | Annotations + interfaces mods target (`@WeftSafe`, `RegionScheduler`, graph API) | **None** (enforced) |
| `weft-sandbox` | Mod tier classification, legacy lane, coexistence policy (RFC-0003) | None (pure parts) |
| `weft-services` | Engine-side services for the RFC-0002 workstreams (WS-1 activation, WS-2 pathfinding) | **None** (enforced) |
| `weft-neoforge` | The actual mod: mixins, event bus adaptation, config | NeoForge 1.21.1 |
| `weft-adapters` | Per-mod graph adapters (Create, AE2, …) | Planned (P3) |

## Building

Core (engine + api + sandbox — pure Java 21, no Minecraft):

```
./gradlew build
```

Full build including the NeoForge mod (needs maven.neoforged.net):

```
./gradlew build -PwithNeoForge
```

## Status

Pre-alpha. **P0 and P1 are complete; P2 is open** (regionized vanilla
ticking + legacy lane, behind a flag — parity suite first, see below). What
ships today: the engine core with a passing concurrency test suite, the
**P0 profiler** (install on any stock server *or single-player world* and it
measures how much of your pack's tick Weft could parallelize), and the
**P1 off-thread services** — the spawn-density scan authoritative by default
and async pathfinding on by default, both fail-soft with independent kill
switches. See RFC-0001 §11 for the roadmap and §12 for the honest risk
register (start with the Amdahl one).

**P0 verified in-game** (2026-08-16, NeoForge 21.1.248 / MC 1.21.1): mod loads,
all mixins apply cleanly, `/weft report` prints the regionizability report and
writes `weft-report.txt`, the 60-second console summary fires, and profiling
overhead is negligible (~two `System.nanoTime()` calls per entity/BE tick).
Profiling is toggleable at runtime with `/weft profile on|off`; tunables live
in `config/weft-common.toml`.

**P1 complete — off-thread services are authoritative** (2026-08-17):
vanilla's spawn-density scan (`NaturalSpawner.createState` — an O(all loaded
entities) walk on the server thread, every tick, spawning enabled or not) is
now **served by the async service by default**
(`spawnDensityMode = AUTHORITATIVE`): the previous tick's off-thread result
is handed to vanilla as a real `SpawnState` (counts, per-player local caps,
spawn-potential charges); any tick the result isn't exactly one tick fresh
falls back to vanilla's synchronous scan for that tick (fail-soft, RFC-0003
R2), and every 200 ticks a verify tick runs the vanilla scan anyway, uses
it, and diffs our result against it so parity evidence keeps accumulating
in production (`/weft services`). Graduation was gated by a hard gametest
(`spawnDensityAuthoritativeParity`): **exact 100% count parity over a
converged static world**, then a live-spawning phase driving vanilla's
spawn attempts through the constructed state (70 monsters spawned, zero
service failures, zero fallback latches). That gate caught a real bug
before ship: the capture originally gated chunks with `getChunkNow`
(chunk-STATUS check) where vanilla's scan gates on the completed
full-chunk FUTURE — over-counting entities vanilla skips (400 vs 115 on a
freshly force-loaded world). The capture now queries through vanilla's own
`getFullChunk`, making the gate — quirks included — identical. Shadow mode
remains available (`spawnDensityMode = SHADOW`); the earlier 65k-entity
shadow stress run (zero failures) plus this gate are the graduation
evidence.

**P1 exit criterion met** (RFC-0001 §11: "measurable TPS win on stock
packs; API validated by real use"): same-run end-to-end A/B on the stock
benchmark world (2500 cap-countable passive mobs, no mods), measuring
full-tick MSPT — the number spark or `/tps` shows an admin:
**25.35 → 24.08 ms/tick (−5.0%), p95 31.5 → 29.3 ms** with P1 services at
shipping defaults (spawn density authoritative incl. verify-tick overhead,
async pathfinding on) versus all-off in the same run; 298/300 ticks served
async (`p1EndToEndMspt`, tracked nightly by WS-8). Honest framing: the win
scales with how much of the tick the scan + pathfinding are. This
benchmark world is entity-tick-dominated; a real Create-pack report
(2026-08-16) itemizes `create_state` alone at **12.3% of attributed
cost — the largest single line in that pack** — and pathfinding-stressed
scenes are far above that (see WS-2 below). Cross-check on your own
server: `/weft report` itemizes the scan cost it removes and the
capture/build residual it adds, next to spark's MSPT. The API half of the
exit is met by two production services on the engine's
`AsyncService`/path-worker seams. **Next phase: P2 — regionized vanilla
ticking + legacy lane, behind a flag** — where WS-10's entity sharding
(so far proven only at the engine level, 6.5x) meets real ticking.

**P2 opened — parity suite first, then degenerate tick ownership**
(2026-08-17): applying P1's biggest lesson (the gate exists *before* the
change it judges), P2 started with the **vanilla-parity suite**
([RFC-0005](docs/RFC-0005-vanilla-parity-suite.md)): a fixed machinery+mob
arena (hopper clocks, comparators, piston+observer, clocked dropper, flowing
water pushing items into a hopper, chest-to-chest hopper chain, furnaces
mid-smelt, falling blocks, ten penned seeded mobs) is run three times on the
same server — vanilla, **vanilla again** (the control: semantic world digests
must match bit-identically, proving the harness deterministic before it may
judge Weft), then Weft-owned — and all three digests (entity state, full BE
NBT, per-chunk block hashes) must be equal, with vacuous-run guards on both
the engine (owned-section counters) and the scenario (furnaces must produce,
clocks must fire, mobs must survive). Those guards paid for themselves before
Weft was ever judged: the first "green" run was comparing identical
emptiness — the arena had built at bedrock level (heightmap read before its
chunk loaded) and the population had fallen into the void; the control
happily agreed with itself. **Tick ownership increment 1** then landed behind
`regionizedTicking` (default OFF, RFC-0003 R1): vanilla's entity and
block-entity tick sections route through the engine
(`WeftScheduler.runOwnedSerial` — one region per level, serial, on the server
thread, vanilla's own order), deliberately **bit-identical by construction**.
What it buys is the seams, all live and gated now: the fail-loud ownership
mixins (the `require = 1` case RFC-0003 R2 reserves for exactly this), the
REGION thread-context around all vanilla simulation ticking, and a real
target for the parity suite. **Parity: green at E0 (bit-identical)** on the
full three-phase protocol, locally (2026-08-17) and nightly via WS-8. Also
landed: the RFC-0001 §12 **kill -9 during-save CI harness** (`chaos.yml` —
dedicated server, forceloaded chunk plate, `save-all` then `kill -9` mid-save
×4, world must boot clean) and the **RFC-0003 R7 neighbor-boot matrix**
(`neighbors.yml` — stub modids exercise cooperate/yield/refuse postures
end-to-end, including Forgia/NeoFolia-class tick-ownership engines now
seeded as `refuse` in the registry) — **both proven green on their first
dispatched runs** (2026-08-17: chaos recovered cleanly from 4 kill -9 torn
saves; all four neighbor cells asserted their postures) and nightly from
here — plus a coexistence-policy fix the R7 work surfaced: **force-enable
can no longer out-rank a REFUSE** — R4 licenses overriding a yield, never
an ownership conflict (unit-gated in `CoexistencePolicyTest`). Next increments (the legacy lane,
parallel regions, WS-10 activation) each stay off until the parity suite is
green at their declared equivalence class (RFC-0005 §4).

**P2 increment 2 — the real chunk→region mapping** (2026-08-17, same day):
`RegionTopology` now maintains one `RegionManager` per level, fed by actual
chunk load/unload events — merge-on-load, splits recomputed between ticks
(throttled, only after removals) — with the RFC-0001 §4.2 invariants gated
by a new hard gametest (`p2regions`): far-apart forced islands resolve to
distinct regions, every loaded chunk maps to exactly one region, and
loading a chain of chunks between islands merges them into one. Surfaced
in `/weft status` (live region/chunk counts next to the profiler's
*hypothetical* partition). Honest scoping: this is always-on
**bookkeeping** — the scheduler's own region set stays empty and owner
routing still resolves to the global inbox, because region-mailbox
delivery runs on workers and is only safe once region workers own the
state a message touches; that rewiring belongs to the parallel-regions
increment (E1), not this one. Cost of the feed is a map update per chunk
load/unload; the nightly `loadgen_fresh_chunk_load` trend line guards it.

**P2 increment 3 — the legacy lane goes live** (2026-08-17, same day):
RFC-0001 §7.2's load-bearing guarantee is now real code behind `legacyLane`
(default OFF, module `legacy_lane`). Tick work owned by **Tier-2
(unverified) mods** — block entities and entities classified by registry
namespace, defaulting to legacy per §7.1 — is extracted at two fail-loud
seams (the single `TickingBlockEntity.tick()` call site inside
`tickBlockEntities`, and vanilla's own per-entity consumer inside the entity
section) and executed in the engine's **LEGACY phase** instead:
single-threaded, on the server thread, in vanilla's iteration order, between
vanilla ticks against a fully settled world, at the same game time the unit
would have seen inline — with **per-mod cost attribution** (the §9.1 "your
tick is 61% mod X" number, in `/weft status`). Deferral safety leans on
vanilla's own re-checks at execution time (removed BEs are null-tickers; the
entity consumer re-tests removal/despawn/ticking-range). Gates: the parity
suite runs its Weft-owned phase with the lane **active** and asserts zero
extractions on all-vanilla content (zero-residue guard, still bit-identical
E0), and a new hard gametest (`p2legacy`) force-classifies vanilla types as
legacy to prove the §7.2 contract end to end — per-tick engagement counters,
LEGACY thread-context + server-thread execution, per-mod attribution, and
**bit-identical furnace end state** against an inline control at equal
executed-tick counts. Honest scoping: with every tick section still
server-thread serial, the lane changes *where in the server tick* Tier-2
work runs, nothing else — its value is the seam, the accounting, and the
contract gate existing *before* parallel regions makes extraction
load-bearing. Known gap, documented for the E1 increment: a legacy passenger
riding a vanilla vehicle is ticked by the vehicle inline, not via the lane.

**P2 increment 4 — partitioned region ticking, still serial** (2026-08-17,
same day): behind `partitionedTicking` (default OFF, a sub-mode of
`regionized_ticking`), each entity and block-entity section is now grouped
by the **real chunk→region topology** and executed bucket-by-bucket in
canonical (ascending region id) order, each bucket under a REGION
thread-context carrying its **real region id** — the execution shape of
parallel regions with the concurrency removed. Vanilla order is preserved
*within* each region (RFC-0005 E0 explicitly covers "real chunk→region
assignment while still serial"); only cross-region interleaving changes,
which is unobservable for regions kept ≥ `mergeDistance` apart (RFC-0001
§4.2's load-bearing invariant). Collection leans on vanilla's own machinery:
`EntityTickList.forEach` freezes the iterated map, the per-entity consumer
re-checks liveness at execution, removed BE tickers are null-ticker no-ops —
so bucketed execution keeps vanilla's mid-tick spawn/removal semantics
exactly. Gates: the parity anchor runs with partitioning **active** (a
single-region arena must partition to one bucket in vanilla order — still
bit-identical E0, with engagement and zero-unmapped-units guards), and a new
hard gametest (`p2partition`) holds two islands 40 chunks apart to
**per-island bit-identical end states** between inline and partitioned runs
while asserting the buckets carried two distinct real region ids. What this
increment deliberately does *not* do: run buckets on workers. True
parallelism (E1) still needs the shared-structure audit — entity-section
storage mutation, cross-region teleports, `level.random` draws, packet
sends — plus owner-mail rerouting; serial partitioning has none of those
hazards by construction and exists so the partition seam is proven before
threads arrive.

**P2 increment 5 — parallel regions** (2026-08-17, same day): the buckets
now run **concurrently on engine workers** behind `parallelRegions`
(default OFF, requires `partitionedTicking`), barriered inside each vanilla
tick section — the server thread waits, vanilla macro-order is unchanged,
and single-bucket sections (solo play) take the serial path untouched. This
shipped only after the **shared-structure audit**
([RFC-0006](docs/RFC-0006-parallel-region-execution.md)) verified every
hazard against the decompiled 1.21.1 sources and closed each one: the
`getChunk` main-thread trap that would deadlock against the barrier (worker
reads go through the visible-chunk-map snapshot, bypassing the racy 4-slot
cache; unloaded access **fails loud** — ticket rings make it unreachable),
`level.random`'s ThreadingDetector hard-crash (server levels swap to
`ThreadSafeLegacyRandomSource` — identical LCG, identical single-threaded
sequence, worldgen-proven), the plain-collection registries mutated on
every spawn/death/section-move (tick list, id/uuid lookup, section index,
tracker map — surgical locks; per-section multimaps stay lock-free because
queries cannot reach across a ≥ mergeDistance gap), per-level neighbor-update
chains (thread-local collectors per worker), the sub-tick ordering counter,
BE-ticker list adds, and mid-tick `changeDimension` (worker calls defer to a
post-barrier queue, same tick). The legacy lane's drain now orders by
(region, submission) so Tier-2 extraction stays deterministic under
concurrent submitters, and Weft's own census events route to the owner
thread. Gates: the E0 parity anchor runs with **all five increments
active** (single-region arena → serial fast path, zero residue), and the
new `p2parallel` hard gametest proves the E1 claim concrete — two islands,
**every bucket of the final section executed off the server thread**
(thread-name probe), per-island end states **bit-identical** to the inline
control. Honest scoping: barriered fan-out means async-service mail keeps
applying at INGEST (with the main thread parked, global-inbox delivery *is*
owner delivery); free-running regions with true mailbox rerouting, WS-10
compounding inside big regions, and the long-tail soak (Create/AE2, chaos,
R7 under the flag) are what stand between this and default-ON.

**RFC-0002/0003 workstreams started** (2026-08-16): every Weft optimization
module now walks the [RFC-0003](docs/RFC-0003-coexistence-policy.md)
coexistence ladder at startup — independent kill switch, known-neighbor
registry (`weft-neighbors.toml`), user force-enable/disable overrides, and a
one-glance posture table in the log and `/weft status`. First entries from
[RFC-0002](docs/RFC-0002-modernization-workstreams.md):

- **WS-8 benchmark-as-CI**: JMH suites over the engine hot paths (mailboxes,
  region merge/split, pipeline scheduling, graph commit routing, the WS-1
  decision) run nightly; the `bench` workflow records results on the
  `bench-data` branch and fails on regression beyond the noise band.
  Run locally: `./gradlew :weft-engine:jmh :weft-services:jmh`.
- **WS-1 entity activation scheduling**: mobs far from every player tick
  their sensing and goal/target selectors at reduced frequency (32/64-block
  tiers, 1/4 and 1/20 rates by default) while movement, navigation, brains,
  and despawn accounting stay per-tick. Fail-soft mixin (self-disables if it
  cannot apply), per-type overrides and exemptions in config. The engine-side
  acceptance A/B (2k passive + 500 hostile on the profiled world shape,
  `ActivationPhaseBench`) shows **72% entity-phase reduction** (634 us ->
  177 us per tick, decision cost ~12 ns/mob) — but that is a *model* upper
  bound (it assumes throttled AI dominates a mob's tick cost). In-world
  same-run A/B measures **15-21.5%** across runs, under the >=30% bar, with
  92% of throttleable AI ticks skipped. Profiler **AI sub-attribution**
  (a second timing slice around `serverAiStep`) now explains the ceiling:
  the whole AI step — sensing, selectors, navigation, brain, controls — is
  only **~19-20% of the benchmark world's entity phase**; movement/physics
  is the rest, and no amount of AI-frequency gating touches it. WS-1 alone
  therefore cannot clear 30% on that population; the bar waits on WS-10
  sharding compounding (P2) or a cheaper entity base tick. Widened gating
  still landed: a throttled mob now also stretches its periodic
  path-recompute window by its AI interval (never inside the 32-block
  full-rate ring; exemptions inviolate; fail-soft, same module switch) —
  inert on the flat benchmark world (no block churn means no recompute
  traffic) but it cuts repath and WS-2 request volume in block-busy worlds.
  `/weft report` prints the AI/movement split, a **projected WS-1 savings**
  line for *your* pack, and a *measured* "what would widening buy" line, so
  the report answers "what would enabling this buy me" before you flip
  `activationScheduling = true`.
- **WS-2 async pathfinding** (the RFC-0001 P1 off-thread service): the A*
  inside `PathNavigation.createPath` runs on Weft path workers instead of
  the server thread. Node evaluation stays vanilla's own per-mob
  `PathFinder` (modded NodeEvaluators respected) over the region snapshot
  vanilla already captures on-thread; results return through the engine
  scheduler's mailbox and apply at the next tick boundary while the mob
  keeps following its previous path (no stutter, exactly-once apply —
  smoke-checked). Single-flight per mob: rapid repaths coalesce, latest
  wins. The engine-native pathfinder that takes over at P2 landed alongside
  it in `weft-services` with the WS-8 numbers to justify it: hierarchical
  chunk-level A* **30x** over flat A* on a 430-block obstructed path
  (480 us vs 14.6 ms), and a shared flow field serving a 300-mob horde
  **4.1x** cheaper than per-mob A* (10 ms vs 41 ms) even recomputing the
  flood every call. In-world acceptance (2026-08-17): the RFC's 300-zombie
  stress world is now a same-run A/B gametest (`ws2PathStressReduction` —
  sealed-keep maze, so every horde repath runs vanilla's A* to its
  visited-node budget): **~50-59% entity-phase reduction across runs**
  (e.g. 6.93 → 3.44 ms/tick with ~5k requests off-thread). The flat-world
  trend line (`ws2EntityPhaseReduction`) stays ~0-6% with ~4k requests —
  paths there are too short and cheap for async to matter much (the earlier
  cross-run "-18.3% alone" reading was mostly run-to-run variance), which
  doubles as the "async costs nothing where it can't help" watchdog.
  **Ships ON** (`asyncPathfinding = true`) on that evidence.

**WS-10 intra-region entity sharding started**
([RFC-0004](docs/RFC-0004-entity-sharding.md), engine side, same day): the
second parallelism axis, for the worlds where region-level parallelism
flatlines at 1.00x by construction (one player = one region). Big regions fan
their tickables out over shards — each shard a serial loop with its own
`SHARD` ownership context, pre-split deterministic RNG substream, and an
**entity effect log** (the entity-layer `CommitLog`: damage, item claims,
love mode, spawn/remove are recorded during the parallel pass and applied in
one deterministic (source, seq) order, so contested claims resolve
identically at any shard count). Engine benchmark on the profiled solo-play
shape (2000 tickables, one region): **1637 us serial vs 254 us sharded
(6.5x)**, tracked nightly by WS-8. **Ships off** (`entitySharding = false`)
per RFC-0004 §2.5 — within-tick interleaving is not vanilla's exact list
order — and the engine does not own real entity ticking until P2 anyway.

## Trying the P0 profiler locally

1. Build the jar: \`./gradlew :weft-neoforge:build -PwithNeoForge\`
   (or grab \`weft-neoforge-jar\` from the latest green Actions run).
2. Drop \`weft-neoforge/build/libs/weft-neoforge-*.jar\` into your NeoForge
   1.21.1 \`mods/\` folder — works in single player (integrated server) and on
   dedicated servers.
3. Play a minute, then run \`/weft report\` (needs op / cheats). You get the
   regionizability report in chat and \`weft-report.txt\` in the game dir:
   parallelizable fraction, hypothetical region count, estimated speedup at
   2/4/8/16 workers, and the top cost sources in your pack.

Dev workflow: \`./gradlew :weft-neoforge:runClient -PwithNeoForge\` launches a
dev client with the mod loaded.

## Design tenets

1. **Correctness is never opt-in.** Unknown mods are serialized, never guessed
   parallel. We accept "not faster yet"; we never accept "corrupts your world."
2. **Ownership, not locks.** Every piece of state has one owner; cross-owner
   work is mail or commit logs, applied at phase boundaries.
3. **The graph layer is the point.** Regionization alone breaks on exactly the
   mods people actually play. Networks get their own scheduler.
4. **Vanilla-compatible saves, always.**
