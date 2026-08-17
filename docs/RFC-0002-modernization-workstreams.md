# RFC-0002: Modernization Workstreams — Modern Java & Server-Tech Roadmap

**Status:** Draft 1
**Depends on:** RFC-0001 (design authority; nothing here overrides its tenets)
**Scope:** What to build *around and inside* the Weft engine to make modded
Minecraft servers faster using current JVM technology and proven server-tech
methodology. Ordered by leverage-per-risk.

---

## 0. Selection principles

1. **Profiler-led.** The P0 profiler decides what's worth building for real
   packs. Every workstream below must cite the profiler signal that justifies
   it before implementation starts.
2. **Correctness tenets hold.** Nothing here weakens RFC-0001 §0: unknown mod
   code is never silently parallelized; saves stay vanilla-compatible.
3. **Each workstream ships standalone value** even if later ones never land.
4. **Benchmarked or it didn't happen.** Every claim gets a before/after in the
   benchmark harness (WS-8) once it exists; until then, manual A/B on the
   benchmark world.

---

## Tier 1 — user-feelable wins, low threading risk

### WS-1: Entity activation scheduling ("DAB" for NeoForge)
Entities far from any player tick their expensive parts (AI/brain, pathfinding
triggers, sensors) at reduced frequency — e.g. full rate within 32 blocks,
1/4 rate to 64, 1/20 beyond — while movement/physics stay per-tick so nothing
visibly freezes. Pufferfish's *dynamic activation of brain* proved this class
of optimization on Paper; no clean NeoForge equivalent exists.

- Home: `weft-services` (new module) + mixin hooks in `weft-neoforge`.
- Engine side: pure-Java `ActivationScheduler` — distance tiers, per-type
  overrides, exemption list (raid mobs, bosses, anything targeting a player).
- Compat posture: per-type opt-out via config + compat manifests; ships
  default-conservative.
- Profiler tie-in: P0 already attributes cost per entity type; the report
  gains a "projected WS-1 savings" line.
- Acceptance: benchmark world with 2k passive + 500 hostile entities shows
  ≥30% entity-phase reduction with no visible behavior change within 32
  blocks of a player; parity suite green.

### WS-2: Async read-mostly services (the RFC-0001 P1 items)
Pathfinding and spawn-density scanning move off-thread behind the Weft API.
Pathfinding modernization: hierarchical A* (HPA*-style chunk-level abstraction)
for long paths, flow-field caching when many mobs path to the same target
(raids, farms).

- Acceptance: pathfinding requests served off-thread with results applied at
  the next tick boundary; zero guard trips; measurable main-thread reduction
  on a 300-zombie stress world.

### WS-3: Redstone network compilation (first native graph-layer citizen)
Treat a redstone network as a compiled graph (Alternate Current proved the
approach) and run it through Weft's snapshot → compute → commit pipeline.
Strategic double-win: big vanilla-content speedup *and* the first real
exercise of the graph layer before any mod adapter exists.

- Wire changes (Phase 1, region-owned) emit `GraphTopologyDelta` mail exactly
  as RFC-0001 §5.2 prescribes; power-level propagation happens in graph
  compute; block-state updates apply via commit log.
- Cross-region redstone stops forcing region merges once networks live in the
  graph layer (revisits RFC-0001 §6.6's merge rule — document the ordering
  contract change explicitly).
- Acceptance: classic torch-clock / comparator-farm benchmarks ≥5x; vanilla
  ordering parity suite for single-region contraptions.

---

## Tier 2 — modern JVM platform work

### WS-4: SIMD services via the Vector API
Vectorize the hot math kernels using `jdk.incubator.vector` (AVX2/AVX-512/
NEON via one portable API), with scalar fallbacks:
1. **Worldgen noise** (Perlin/simplex octaves) — the "server dies while
   players explore" cost. Batch-evaluate noise lattices per chunk section.
2. **Entity broad-phase collision** — AABB overlap tests against the WS-5
   SoA mirror, N-lane at a time.
3. **Light propagation batches** where vanilla's engine leaves room.

- Home: `weft-simd` module (isolated because incubator modules need
  `--add-modules`; degrade gracefully when absent).
- Acceptance: JMH shows ≥3x on noise kernels vs vanilla scalar; end-to-end
  chunk-gen throughput ≥1.5x on the exploration benchmark.

### WS-5: Data-oriented entity mirror + off-heap chunk storage (FFM)
Structure-of-arrays mirror of hot entity state (position, velocity, AABB,
flags) maintained at tick boundaries — feeds WS-4 collision, the entity
tracker, and spawn scans with cache-friendly linear reads. Second stage:
block-state palette storage in off-heap `MemorySegment`s and mmap'd region
file IO via the Foreign Function & Memory API — less GC pressure, explicit
layout control.

- Risk note: the off-heap stage touches chunk internals — flag-gated, P3+
  timing, after the engine owns ticking. The SoA mirror is safe earlier
  (it's a read model, not the source of truth).
- Acceptance: GC allocation rate on the benchmark world measurably down;
  tracker/collision passes show linear-scan speedups; zero save-format change.

### WS-6: GC + startup engineering
1. Curated JVM flag profiles (Generational ZGC primary), shipped as documented
   presets + a `weft doctor` command that inspects the running JVM and flags
   known-bad configurations.
2. **GC attribution in the P0 profiler**: tick spikes caused by GC pauses are
   labeled as such (JFR / GarbageCollectorMXBean deltas per tick) so mod
   authors stop getting blamed for allocation storms — and allocation storms
   get attributed to their source.
3. Startup: AppCDS archives (and Leyden AOT as it matures) for the notorious
   multi-minute modded boot; measure and publish boot-time deltas.

- Acceptance: profiler report gains a GC line; documented flag preset beats
  default G1 p99 tick time on the benchmark world; boot time reduced ≥20%
  with CDS on a mid-size pack.

---

## Tier 3 — modern practice around the mod

### WS-7: Observability exporter
Prometheus/OpenTelemetry endpoint exposing: TPS/MSPT percentiles, per-phase
pipeline timings, per-mod legacy-lane cost, region count & hottest-region
share, graph compute costs, GC attribution, mail volume, guard trips. Ship a
Grafana dashboard JSON in-repo. This converts the P0 report into continuous
ops tooling and is the adoption wedge for server admins.

- Acceptance: dashboard renders all panels against a live server; overhead
  unmeasurable at 10s scrape interval.

### WS-8: Benchmark-as-CI
JMH suite for engine hot paths (mailboxes, region merge/split, LPT scheduling,
graph commit routing) plus a reproducible **benchmark world** + headless bot
load generator (GameTest-driven joins, movement, chunk loading). Nightly CI
job records timings and fails on regression beyond noise bands. Every WS-1..6
acceptance criterion becomes a tracked benchmark.

- Shipped: JMH source sets in `weft-engine`/`weft-services` gated nightly by
  `bench.yml`; benchmark world = the GameTest server's flat seed-0 world plus
  a fixed-seed population builder (`BenchmarkWorld`, the WS-1 2k+500 layout);
  load generator = `LoadBot` (FakePlayer join/movement/chunk loading) driven
  by `@GameTest`s in `weft-neoforge/src/gametest` on the headless
  `runGameTestServer` run, results tracked by the same nightly gate
  (`weft-bench.json`, `customSmallerIsBetter`).
- WS-1 acceptance runs as two gametests: the 32-block behavior-parity rule is
  a hard gate (`ws1BehaviorParityNearPlayers`, checked every activated tick);
  the >=30% entity-phase reduction is measured and tracked nightly
  (`ws1EntityPhaseReduction`, optional until met — first full run: 18.5%
  reduction with 92% of throttleable AI ticks skipped, so clearing the bar
  needs WS-1 to gate more of the mob tick, not tuning). WS-2..6 criteria join
  the same harness as they land.

### WS-9: Network egress batching
Phase 6 (EGRESS) grows: off-netty-thread packet serialization, entity-tracker
delta deduplication (don't resend unchanged metadata), chunk-send
prioritization by player facing + movement vector, compression via FFM-bound
libdeflate where available.

- Acceptance: bytes/sec and main-thread egress time down on the 50-bot
  benchmark; vanilla clients unaffected (protocol unchanged).

---

## Explicitly rejected (for now)

- **zstd / alternate region formats** — violates "vanilla-compatible saves,
  always" (RFC-0001 tenet 4). Revisit only as an explicit opt-in sidecar with
  a lossless conversion tool, never a default.
- **Speculative/ML tick prediction** — no profiler evidence it beats EWMA
  cost models for scheduling; WS-8 can host an experiment later.
- **Distributed single-world simulation** — still out of scope (RFC-0001 N4);
  Velocity sharding remains the multi-machine answer.

## Suggested sequencing

WS-8 (harness) starts immediately in parallel with WS-1 — you want regression
gates before the exciting changes land. Then WS-1 → WS-2 → WS-3 as the P1/P2
arc, with WS-6.2 (GC attribution) and WS-7 as quick wins whenever convenient.
WS-4/WS-5 begin once the harness can prove their claims. WS-9 rides the P2
pipeline work.

*End of RFC-0002 draft 1.*
