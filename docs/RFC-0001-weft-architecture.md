# RFC-0001: Weft — A Multithreaded Server Engine for Modded Minecraft

**Status:** Draft 1
**Target platform:** NeoForge, Minecraft 1.21.1 (Java 21)
**License intent:** LGPL-3.0 (engine), MIT (API) — final call before publishing
**Authors:** Panda-GGuy + Claude

---

## 0. Summary

Weft is a series of cooperating mods that replace Minecraft's single-threaded
server tick with a parallel execution engine, while preserving — and where
necessary re-implementing — the modding surface that existing NeoForge mods
depend on, so that unmodified mods keep working.

The engine rests on two load-bearing ideas:

1. **Regionized world ticking** (the Folia insight): the world is dynamically
   partitioned into independent regions, each ticked by its own worker, because
   most simulation work is spatially local.
2. **A first-class graph layer** (the thing Folia doesn't have): cross-chunk
   "network" systems — energy grids, item logistics, rotational machinery, the
   backbone of every major tech mod — get a dedicated scheduler with
   snapshot-read / serialized-commit semantics, so global mods have a *correct
   home* instead of being the thing that breaks.

And one load-bearing policy:

3. **Correctness is never opt-in.** Mods unknown to Weft run on a serialized
   *legacy lane* that preserves single-threaded semantics exactly. They are
   never silently parallelized. Speed is unlocked per-mod through verification,
   annotation, or adapters. This is the anti-MCMT principle: we accept "not
   faster yet" for unknown mods; we never accept "corrupts your world."

Honesty clause (Amdahl's law): a pack where 90% of tick time is unverified
modded content sees modest gains on day one — vanilla-content parallelism,
off-thread subsystems, and better scheduling. The payoff curve rises as the
compat database grows and adapters land for the top mods. The architecture is
designed so that curve *can* rise to near-full hardware utilization; nothing
short of ecosystem cooperation gets you there instantly, and any design that
claims otherwise is lying to you.

---

## 1. Goals and non-goals

### Goals

- G1. Tick the server across all available cores with near-linear scaling on
  vanilla-content workloads (entities, block ticks, fluids, redstone spread
  across regions).
- G2. Run unmodified NeoForge mods without world corruption, crashes, or
  behavior changes (legacy lane).
- G3. Provide an API that lets mod authors (or third-party adapter authors)
  incrementally unlock parallelism for a mod: annotations, region schedulers,
  graph registration, async services.
- G4. Provide migration paths for the big cross-chunk mods (Create, AE2,
  Mekanism, Pipez-style logistics) via the graph layer.
- G5. Ship tooling that *discovers* thread-safety facts about mods
  automatically (dev-mode race detector, tick profiler), feeding a
  community compat database.
- G6. Deterministic-enough simulation: a fixed seed + fixed inputs on one
  thread count should replay identically; across thread counts we guarantee
  semantic equivalence, not bit-identical entity iteration order (see §6.6).

### Non-goals (v1)

- N1. Client-side parallelism. Weft is server-first. (The client render side
  already parallelizes elsewhere; the integrated server *is* in scope since
  it's the same server code.)
- N2. Bit-identical vanilla parity in ordering-sensitive edge cases
  (documented in §6.6). Folia made the same trade.
- N3. Fabric support in v1 — the engine core is loader-agnostic by
  construction (see module layout), so a Fabric adapter is a later,
  bounded effort.
- N4. Distributed (multi-machine) simulation. One JVM, many cores. Sharding
  across machines remains the proxy/Velocity layer's job.

---

## 2. Prior art, and what we take from each

| Project | What it proved | What we take | What we do differently |
|---|---|---|---|
| **Folia** (PaperMC) | Region threading works in production | Region ownership model, region scheduler API shape, teleport-as-handoff | We add the graph layer; we target the mod ecosystem instead of abandoning it; dynamic region sizing tuned for modded chunk-loading patterns |
| **MCMT / JMT-MCMT** | Brute-force parallel ticking corrupts worlds | The failure catalog: item dupes from concurrent inventory access, chunk corruption from parallel saves, mod static-state races | Ownership + phases instead of locks sprayed on vanilla code; unknown code is serialized, never guessed at |
| **C2ME** | Chunk gen/IO parallelize cleanly | Async worldgen integration points; we must coexist or absorb | Worldgen becomes a Weft service with declared outputs, scheduled by the engine |
| **Lithium / ServerCore** | Huge single-thread wins exist | Their algorithmic improvements compose with parallelism | We coordinate mixin targets to avoid conflicts; long-term, absorb the load-bearing ones |
| **Pufferfish/Petal** | Async pathfinding & entity tracking are safe | Same subsystems as engine services | Generalized: any read-mostly query system can be a Weft service |
| **Vanilla 1.20+** | Mojang is offloading lighting, IO, networking | Don't fight vanilla's own async; schedule around it | — |
| **Java ecosystem** | Loom virtual threads, VarHandle fences, JFR | Virtual threads for blocking mod IO on the legacy lane; JFR events for the profiler | — |

---

## 3. Module layout (the "series of mods")

Weft ships as five artifacts. Separation is not cosmetic: the engine core has
**zero Minecraft imports**, which makes it unit-testable off-game, fuzzable in
CI, and portable to Fabric later.

```
weft/
├── weft-engine/     Pure Java 21. No Minecraft, no NeoForge.
│                    Scheduler, region math, phase barriers, mailboxes,
│                    graph scheduler, ownership guards, telemetry core.
├── weft-api/        Pure Java. Annotations + interfaces mods compile against.
│                    @WeftSafe, @RegionLocal, RegionScheduler, GraphNode,
│                    AsyncService, CompatManifest schema.
├── weft-neoforge/   The actual mod. Mixins into the server tick pipeline,
│                    region-aware level/chunk access, event bus adaptation,
│                    save pipeline, config, commands.
├── weft-sandbox/    Compat layer: mod classification, legacy lane executor,
│                    dev-mode race detector (instrumentation agent),
│                    compat manifest loader, shims for common unsafe patterns.
└── weft-adapters/   Per-mod adapters (separate jars eventually):
                     weft-adapter-create, weft-adapter-ae2, ... Each maps a
                     mod's global system onto the graph layer.
```

Dependency rule: `engine ← api ← neoforge ← sandbox ← adapters`. Nothing in
`engine`/`api` may import `net.minecraft.*` — enforced by a build-time check.

---

## 4. The threading model

### 4.1 Ownership, not locks

Every piece of mutable simulation state has exactly one **owner** at any
moment, and only the owner's thread may touch it. Owners are:

- **Regions** — own the chunks inside them and everything in those chunks:
  block states, block entities, entities, fluid/scheduled ticks, POI data.
- **Graphs** — own their internal network state (nodes, edges, per-network
  caches). They do *not* own world state; they interact with it via
  snapshots and commits (§5).
- **The Global lane** — owns inherently global vanilla state: player list,
  scoreboards, advancements, world border, time/weather, boss bars, server
  console/commands.
- **The Legacy lane** — owns everything belonging to unverified mods (§7).

There are no fine-grained locks on world state in steady-state execution.
Cross-owner communication happens through **mailboxes** (MPSC queues drained
at phase boundaries) or **ownership transfer** (an entity crossing a region
border is *mailed* to the destination region, exactly like Folia's teleport
handoff). Memory visibility across phases is established by the phase
barriers themselves (happens-before via the barrier's release/acquire).

### 4.2 Regions

- A region is a connected set of loaded chunk positions, grown around
  activity sources (players, chunk loaders) with a configurable merge
  distance (default: sections of 8×8 chunks, merge radius 1 section —
  tunables to be validated by the P0 profiler on real modded worlds, where
  quarry/chunk-loader patterns differ sharply from vanilla player patterns).
- Regions **merge** when their halos touch and **split** when a
  connectivity scan shows independent islands. Merge/split happens between
  ticks, never mid-tick.
- Each region carries its own: entity list, block-entity tick list,
  scheduled tick queues, random source (seeded from world seed + region
  origin for reproducibility), and event bus segment (§8.3).
- Region tick tasks are submitted to a **work-stealing pool** sized
  `cores - reserved` (reserved for graph workers, IO, and the legacy lane;
  all tunable).

### 4.3 The tick pipeline

A server tick becomes a phased pipeline. Phases are separated by barriers;
work *within* a phase is parallel.

```
Phase 0  INGEST     Drain network packets, player inputs, console commands
                    → routed as messages to owning regions/global lane.
Phase 1  REGION     All regions tick in parallel (entities, BEs, fluids,
                    redstone, scheduled ticks, mob AI, spawning).
                    Graph COMPUTE also runs here, overlapped (§5.2) —
                    graphs read the pre-tick snapshot.
Phase 2  MAIL       Barrier. Deliver cross-region mail (entity handoffs,
                    block updates at borders, teleports). Cheap, mostly
                    pointer swaps.
Phase 3  COMMIT     Graph commit queues apply their world writes, each
                    write executed by the owning region's worker
                    (parallel across regions, serialized within one).
Phase 4  LEGACY     The legacy lane runs: unverified mods' entities, BEs,
                    and event handlers tick serialized, seeing a stable,
                    fully-consistent world — i.e., exactly the world a
                    single-threaded server would show them (§7.2).
Phase 5  GLOBAL     Global lane tick: time, weather, sleep checks,
                    advancement/scoreboard flush, player list ops.
Phase 6  EGRESS     Chunk broadcast deltas, entity tracker updates
                    (parallel — trackers are read-only over settled
                    state), network flush, async save handoff.
```

The pipeline is *within one 50 ms tick*; if total work exceeds the budget,
Weft degrades exactly like vanilla (tick takes longer) — no simulation
skipping in v1. Per-region TPS isolation (a lagging region not stalling
others) is possible under this design (Folia does it) but deferred to v2:
it changes user-visible semantics (redstone clocks desyncing across
regions) and belongs behind its own flag after the core is trusted.

### 4.4 Guards

Every mutation path into world state (block set, BE access, entity list,
inventory handlers) is instrumented with an ownership assertion:
`WeftGuards.assertOwner(currentThreadContext, targetState)`. Three build
modes: **dev** (throw with forensic report: both stacks, owner history),
**server-default** (log-once + route the mutation as mail to the correct
owner — degrade to correct), **hardened** (throw). Guards are how we find
compat facts (§9) instead of finding corruption.

---

## 5. The graph layer (weft's novel contribution)

### 5.1 Why

Regionization's blind spot is state that is *deliberately* non-local:
an AE2 ME network spanning 40 chunks, a Create rotational network crossing
region borders, a Mekanism cable run. Under pure region threading these
either force giant region merges (killing parallelism — one big factory
collapses the map into one region) or race. Weft instead models them as
what they are: graphs with their own identity, ticked by their own
scheduler.

### 5.2 Execution model: snapshot → compute → commit

- **Registration:** an adapter (or a Weft-native mod) registers a
  `Graph` with the engine: its node set (block positions + typed handles),
  edge structure, and a `GraphTicker`.
- **Snapshot:** at Phase-1 start, each graph receives a read-only snapshot
  view of the world state it declared interest in (node block states,
  adjacent inventories' *published* views). Snapshots are cheap: copy-on-
  read over the previous tick's settled state, valid because regions only
  mutate their own live state during Phase 1 — they never mutate the
  settled pre-tick view a snapshot reads. Graphs therefore see the world
  as of tick start — one tick of latency for world→graph observation,
  which is the same latency vanilla block entities already exhibit for
  most cross-chunk observation.
- **Compute:** graphs tick in parallel with regions and each other. A graph
  computes its internal state transition (power distribution, item routing
  decisions, stress/speed propagation) and emits a **commit log**: an
  ordered list of world writes (insert item into inventory at pos X, set
  block state, spawn particle event).
- **Commit:** Phase 3 applies commit logs. Each write is routed to the
  owning region and applied by that region's worker. Conflicting writes
  (two graphs inserting into one inventory) are resolved by deterministic
  graph-priority order (stable graph ID), and a graph can mark writes
  *conditional* (apply only if the slot still matches the snapshot —
  compare-and-set semantics) to opt into stronger consistency where it
  matters (item dupes). The default for inventory mutations is
  conditional; a failed conditional write is returned to the graph as a
  rejection it sees next tick, which is exactly how a well-written hopper
  already handles "inventory changed under me."
- **Topology changes** (block placed/broken alters the network) are
  detected in Phase 1 by regions (they own the blocks) and mailed to the
  graph, which rebuilds affected topology at the start of its next
  compute. One-tick lag on network rewiring — imperceptible, and again
  matches what most of these mods already do with their own deferred
  network rebuild queues.

### 5.3 What this buys

Create's rotational network, AE2's channels and autocrafting, Mekanism's
grids, GregTech-style energy nets: each becomes an isolated parallel unit
whose cost scales with *its own* size, not with world layout. Two mega-
factories tick on two cores even if they interleave spatially. This is the
piece that makes "modded, multithreaded" coherent rather than doomed —
and it's precisely the piece missing from every prior attempt.

---

## 6. Reimplementing the modding surface

The end goal is that a normal NeoForge mod loads and runs. That means the
*shape* of vanilla + NeoForge APIs survives, backed by region-aware
implementations.

### 6.1 Level access
`ServerLevel` methods become context-sensitive: called from a region worker
on owned state → direct; on non-owned state → read of settled snapshot
(for reads within a declared halo) or mail (for writes). Called from the
legacy lane → direct (it sees the whole settled world, §7.2).

### 6.2 Event bus
NeoForge's event buses assume global synchronous dispatch. Weft segments
them (§8.3): events with regional provenance (BlockEvent, EntityEvent,
LevelTickEvent) dispatch on the firing region's worker to *verified*
handlers, and are queued to Phase 4 for *legacy* handlers. Global events
(ServerTickEvent, player login) fire on the global lane. A handler's tier
is its owning mod's tier — one mod's slow handler no longer blocks event
delivery to others.

### 6.3 Capabilities & data attachments
Capability lookups return owner-checked handles. `IItemHandler` views
handed across owners are published-view wrappers (read: snapshot; write:
conditional commit op). This single shim covers an enormous share of
inter-mod interaction, because item/energy handlers are *the* lingua
franca of tech mods.

### 6.4 Networking
Packet handlers declared thread-safe run on netty workers (as vanilla
increasingly does); others are routed to the owner of the player's region
or the legacy lane. `enqueueWork()` (the existing "get me on the main
thread" idiom) maps to "enqueue on my owner" — the semantics mods actually
wanted all along.

### 6.5 Saving
Region-parallel serialization into the existing region-file format
(fan-in per file), double-buffered so saves never stall the tick. The
*format* stays vanilla-compatible: a Weft world opens in vanilla and
vice versa. Non-negotiable for trust and rollback.

### 6.6 Determinism contract
Per-region RNG streams seeded from (world seed, region origin, tick).
Guarantee: same seed + same inputs + same thread count → identical replay.
Across thread counts: semantically equivalent, not bit-identical (entity
iteration order within a tick may differ where vanilla itself has no
defined cross-chunk order). Divergences from vanilla single-thread
behavior are documented per-subsystem; redstone within a region is
*exactly* vanilla-ordered (it never leaves one owner mid-tick, and
cross-region redstone forces a region merge rather than accepting
boundary reordering — correctness over parallelism at the wire).

---

## 7. The compat sandbox (weft-sandbox)

### 7.1 Classification
At load, every mod is assigned a tier:

- **Tier 0 — Engine:** vanilla content, reimplemented region-aware.
- **Tier 1 — Verified:** mod ships `@WeftSafe` annotations / a weft.toml,
  OR a community **compat manifest** (signed, versioned, matched to exact
  mod version) asserts safety per class. Runs fully parallel.
- **Tier 2 — Legacy (default for unknown):** all its tickables, event
  handlers, and scheduled work run on the legacy lane.
- **Tier 3 — Conflicting:** mods that patch the tick loop themselves
  (other threading mods, some coremods). Detected via mixin-target overlap
  scan at load; user gets a clear "choose one" report, not a mystery crash.

Granularity is per-*class*, not just per-mod: a manifest can verify a mod's
item handlers (hot path) while leaving its command handlers legacy.

### 7.2 Legacy lane semantics — the load-bearing guarantee
Phase 4 runs *after* all parallel work has settled and *before* global
state advances. From the perspective of code running in it:

- It is on a single thread, in deterministic mod-load order (matching
  vanilla's tick order for BEs/entities as closely as possible).
- The entire world is readable and writable directly — no other thread is
  touching simulation state during Phase 4 (graph workers are parked;
  region workers only execute Phase-4 mutations *on behalf of* the lane
  when NUMA locality makes it cheaper, still serialized).
- `Thread.currentThread()` name matches the pattern legacy code sometimes
  checks (`Server thread`) — yes, really; mods check this.

I.e., a Tier-2 mod experiences a single-threaded Minecraft that happens to
compute vanilla physics very fast before its turn. This is the claim we
test hardest (§10), because it is the whole ballgame for "seamless."

Known cost: Phase 4 is Amdahl's serial fraction. The profiler (§9.1) makes
it visible per-mod ("your tick is 61% XyzTech — here's the manifest to
verify next"), turning the compat database into a community speedrun.

### 7.3 Shims for common unsafe patterns
The sandbox ships targeted shims, applied by manifest opt-in per class —
never blanket heuristics (MCMT's lesson: clever guesses corrupt worlds):
static tick-scratch fields → thread-locals; `HashMap` caches on hot
paths → concurrent equivalents; direct `level.getBlockEntity` walks from
graph contexts → snapshot reads.

### 7.4 The race detector (dev tool)
A JVMTI/bytebuddy agent for dev environments: instruments field writes of
Tier-2 mod classes while a stress world runs with Weft forced into
"paranoid parallel" mode (legacy code deliberately run parallel *in a
throwaway world*) to *discover* which classes are actually safe. Output: a
draft compat manifest + a race report (field, both stack traces). This is
how the community database gets seeded with facts instead of vibes.

---

## 8. Engine internals (weft-engine)

### 8.1 Scheduler
Work-stealing pool (`java.util.concurrent` ForkJoin-style but with region
affinity — a region prefers its last worker for cache warmth). Phase
barriers via a phaser with registered worker parties. Graph workers are
the same pool with a concurrency cap so graphs can't starve regions.
Blocking mod IO on the legacy lane runs on virtual threads.

### 8.2 Mailboxes
MPSC ring-buffer queues per owner, drained at phase boundaries. Messages
are typed (EntityHandoff, BlockWrite, GraphTopologyDelta, EventEnvelope)
and pooled to keep GC flat. Ordering guarantee: FIFO per sender-receiver
pair — enough for the semantics above.

### 8.3 Event bus segmentation
The engine provides an N-lane bus abstraction; weft-neoforge maps NeoForge
buses onto it. Registration is unchanged for mods (they call the same
NeoForge API); dispatch becomes tier- and provenance-aware (§6.2).

### 8.4 Telemetry
JFR events + an in-game overlay/command: per-phase timings, per-region
cost, per-graph cost, per-mod legacy-lane cost, mail volume, guard trips.
The P0 deliverable (§11) is essentially this module running on unmodified
servers to gather the data that validates every tunable above.

---

## 9. Tooling & ecosystem strategy

1. **Profiler-first launch (P0):** ship the telemetry + guards as a
   standalone mod that runs on *stock* servers. It answers, with real
   pack data: what fraction of tick is regionizable? Graph-able? Which
   mods dominate? This de-risks every later phase and builds an audience
   before the risky code ships.
2. **Compat manifest database:** a public repo (weft-compat-db) of signed
   per-mod-version manifests, PR-driven, seeded by race-detector output.
   Loader fetches nothing at runtime by default; packs vendor manifests.
3. **Adapter SDK:** the graph API + a worked example (start with a
   mid-size energy mod, then Create — Create's kinetic graph is the
   flagship demo and the hardest test).
4. **Upstream posture:** coordinate mixin targets with Lithium/C2ME/
   ModernFix maintainers early; absorb functionality only where
   coexistence is impossible.

## 10. Testing strategy

- **Engine (no MC):** property-based tests on the scheduler (phase safety,
  mail ordering, merge/split invariants); jcstress for the concurrency
  primitives; deterministic-replay fuzzing (random op streams, replay
  must match).
- **Integration:** GameTest-based worlds per subsystem (redstone suites at
  region borders, hopper/inventory dupe gauntlets, portal/teleport
  handoffs, farm parity vs vanilla over N ticks — world-hash comparison).
- **Compat gauntlet:** CI matrix of top-50 mods, booted under Tier-2,
  automated player-bot workloads, assertion: zero guard trips escalate,
  world hash stable across restarts.
- **Chaos:** thread-count sweeps (1..cores) asserting semantic-equivalence
  invariants; kill -9 during save asserting recoverability.

## 11. Roadmap

| Phase | Deliverable | Exit criterion |
|---|---|---|
| **P0** | Telemetry + guards mod on stock servers | Real-pack data on regionizable/graphable fractions; zero-overhead when disabled |
| **P1** | Engine core + off-thread services (pathfinding, spawn scan) behind Weft API | Measurable TPS win on stock packs; API validated by real use |
| **P2** | Regionized vanilla ticking + legacy lane, behind a flag | Vanilla parity suite green; top-50 pack boots and survives the gauntlet |
| **P3** | Graph layer + first adapter (energy mod), then Create adapter | Mega-factory benchmark scales with cores |
| **P4** | Race detector + compat DB launch | ≥20 community-verified mods at Tier 1 |
| **P5** | Hardening, per-region TPS isolation (flagged), Fabric adapter eval | Production-tagged release |

Status (2026-08-17): **P0 complete** (verified in-game; profiler on stock
servers and the integrated server). **P1 complete** — the spawn-density scan
is authoritative by default (fail-soft fallback to vanilla's synchronous
scan, periodic verify ticks) and async pathfinding is on by default after
clearing its 300-zombie acceptance world at ~50% entity-phase reduction;
the exit criterion measured as a −5.0% full-tick MSPT delta (p95 −7%) on
the stock benchmark world, same-run A/B, tracked nightly. See the README
Status section for the honest numbers and caveats. **P2 open** (same day):
the vanilla-parity suite (RFC-0005 — control-run discipline, semantic world
digest, hard gametest gate) landed *before* the first ownership mixin, then
tick-ownership increment 1 behind `regionizedTicking` (default off):
entity + block-entity ticking routed through the engine as one serial
region per level on the server thread — bit-identical by construction,
parity green at RFC-0005 class E0. The §12 kill -9 save-recoverability
harness and the RFC-0003 R7 neighbor-boot matrix run in CI (first runs
green 2026-08-17). Increment 2
(same day): the real chunk→region mapping is live — per-level
RegionManagers fed by actual chunk load/unload, §4.2 invariants gated by
the p2regions gametest — as always-on bookkeeping; owner routing stays on
the global inbox until region workers own state (parallel-regions
increment). Increment 3 (same day): the §7.2 legacy lane is live behind
`legacyLane` (default off) — Tier-2 tick work extracted from the vanilla
sections at fail-loud seams, run in the LEGACY phase (single-threaded,
server thread, vanilla order, settled world) with per-mod cost
attribution; gated by the parity suite (lane active, zero extractions on
vanilla content, still bit-identical E0) plus the p2legacy contract
gametest (engagement, LEGACY context, attribution, bit-identical furnace
end state vs. inline control). Next: parallel regions + owner-mail
rerouting (E1), then WS-10 activation (E2) — each gated by the parity
suite at its declared equivalence class.

## 12. Risk register (top items)

| Risk | Severity | Mitigation |
|---|---|---|
| Legacy-lane fraction dominates real packs → users see small day-1 gains | High | Profiler-first launch sets expectations with data; per-mod cost attribution recruits verification effort where it pays |
| Mixin conflicts with performance mods | High | Tier-3 detection with clear UX; coordination with maintainers; absorb only as last resort |
| Event-ordering behavior changes break subtle mod logic | High | Legacy lane preserves ordering for unverified mods; verified mods opt in knowingly |
| Snapshot memory overhead on huge worlds | Medium | Copy-on-read + interest declarations; regions without graph interest pay zero |
| Save-format corruption bugs | Critical | Vanilla format unchanged; double-buffer; kill-tests in CI from P2 day one |
| Project scale (this is a multi-year effort) | Certain | Phasing above is ordered so every phase ships standalone value; P0/P1 are useful mods even if P2+ never lands |

## 13. Open questions

1. Region size/merge tunables — decide from P0 data, not intuition.
2. Whether integrated server (single-player) enables Weft by default or
   dedicated-only first. Lean: dedicated-only until P4.
3. Graph API shape for *stateful item transit* (items in pipes are both
   graph state and world-observable) — prototype in P3 with the energy
   adapter first, where payloads are fungible.
4. Interaction with C2ME-style async worldgen if both are present —
   coexist (schedule around) vs. require our worldgen service.

---

*End of RFC-0001 draft 1.*
