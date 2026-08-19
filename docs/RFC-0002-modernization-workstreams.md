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
of optimization on Paper.

**Corrected 2026-08-18 (RESEARCH-0003 errata E1).** This paragraph previously
ended "no clean NeoForge equivalent exists." That is wrong as written.
ServerCore ships **Entity Activation Range** on NeoForge 1.21.1 — a
Spigot/Paper-semantics port that gates an out-of-range entity's *whole* tick
down to one full tick every `tick-interval` (default 20). The narrower and
defensible claim:

> The **vanilla-parity-preserving** variant — throttling AI frequency
> (sensing, goal/target selectors) while movement, navigation, brains and
> despawn accounting stay per-tick — has no NeoForge equivalent. The
> **behavior-diverging** whole-tick-gating variant does, and is widely
> installed.

That distinction is WS-1's actual product position, and it is what the
32-block behavior-parity gate below exists to defend. ServerCore's own docs
warn its activation range "can still slow down mobfarms and break very
specific technical contraptions"; the feature ships disabled by default.
Coexistence: `servercore` carries `activation = "yield"` in
`weft-neighbors.toml` (RFC-0003 rung 2 — one subsystem, one owner), covered by
the R7 boot matrix. See RESEARCH-0001 §1.1 for the evidence trail.

- Home: `weft-services` (new module) + mixin hooks in `weft-neoforge`.
- Engine side: pure-Java `ActivationScheduler` — distance tiers, per-type
  overrides, exemption list (raid mobs, bosses, anything targeting a player).
- Compat posture: per-type opt-out via config + compat manifests; ships
  default-conservative.
- Profiler tie-in: P0 already attributes cost per entity type; the report
  gains a "projected WS-1 savings" line.
- Acceptance, **split and signed off 2026-08-18** (see below): benchmark world
  with 2k passive + 500 hostile entities, no visible behavior change within 32
  blocks of a player, parity suite green, and a per-tier performance bar —
  - **Parity-preserving tier** (`activationScheduling`, the shipped default):
    **≥50% of the measured AI-step slice removed**, with a **≥10%
    entity-phase floor**. Both are hard-gated in `WeftBenchGameTests`.
  - **Aggressive tier** (opt-in whole-tick gating, not yet built): **≥30%
    entity-phase reduction** — the original bar, kept intact and reattached to
    the only technique that can reach it.

#### Why the criterion split (signed off 2026-08-18, RESEARCH-0003 §4.2)

The single ≥30% entity-phase bar was unreachable *by construction*, not by
underperformance. AI sub-attribution measures the whole AI step at ~19–20% of
this world's entity phase, so gating AI frequency cannot remove 30% of the
phase even at 100% effectiveness; movement and physics are the rest, and the
parity-preserving technique deliberately leaves them per-tick.
`ws1EntityPhaseReduction` tracked 15–21.5% (latest 18.6%) against the 30%
bar — i.e. it was already removing roughly **75–95% of everything it is able
to address** while reading as a failure. ServerCore is the existence proof
that ≥30% is available *only* by giving up vanilla parity.

A bar a technique cannot clear is not a quality standard, it is a mislabelled
one, and it was hiding the fact that WS-1 works. So the bar is now stated
against the pool each tier can actually address:

- **≥50% of the AI-step slice** for the parity tier. This is a **collapse
  detector, not a drift detector**. Measured directly on 2026-08-18 across four
  runs: **67.2 / 67.7 / 68.3 / 74.6%** — a ~7-point spread that is itself the
  reason the bar sits well below the mean rather than just under it. A WS-1
  that silently stops throttling — mixin unapplied, tier logic
  broken, config regressed — lands near 0%, so 50% separates working from
  broken with margin for single-run noise and for the shared CI runners
  `bench.yml` already calls noisy. Catching 67% → 62% drift is the bench-data
  regression gate's job; the exact figure is recorded every run as
  `ws1_ai_slice_reduction`. **Do not read 50% as where the implementation
  sits.**
- **≥10% entity-phase floor** alongside it, so the primary bar cannot be met
  by the AI slice shrinking for unrelated reasons (a cheaper base tick, a
  different mob population). Measured 15.7–16.4% across the same runs.
- **≥30% entity-phase** for the aggressive tier, unchanged.

One measurement was corrected in the process: the AI step was recorded as
~19–20% of the entity phase from the 2026-08-16 sub-attribution pass, and the
2026-08-18 runs put it at **16.6–16.7%**. That moves the argument's direction
not at all — it makes the ≥30% phase bar *less* reachable, not more.

**What is not yet built:** the `ActivationPolicy` SPI and the aggressive tier
itself (RESEARCH-0003 §4.2 — two shipped implementations, the aggressive one
opt-in, off by default, refusing to enable while ServerCore's own activation
range is active, R4-logged as user-chosen). Until it exists the aggressive
tier's criterion is **inert — there is nothing to measure against it**, and no
claim may be made about ≥30% on that basis. The parity tier's bar is live and
gated in `WeftBenchGameTests`.

This changes what CI asserts; it does **not** change `activationScheduling`'s
default. That stays off until WS-1 ships on its own merits.

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
3. ~~**Light propagation batches** where vanilla's engine leaves room.~~
   **CONTESTED — default posture is yield (2026-08-18, RESEARCH-0003 errata
   E10).** ScalableLux (Starlight-derived, LGPL-3.0, modid `scalablelux`)
   occupies this lane on Weft's exact platform and additionally performs
   *parallel* light updates. `weft-neighbors.toml` carries
   `ws4_light = "yield"` for it as seed data. Do not build WS-4.3 unless a
   profiler signal on a real pack says the remaining headroom justifies it;
   if it is ever built, it yields on `scalablelux` presence. Honest caveat on
   the other side: ScalableLux's NeoForge 1.21.1 builds are pre-release
   (`0.1.0+beta.1/2+neoforge`), so "mature on this platform" overstates it —
   the lane is contested, not closed.

   Separately, and independent of whether anyone installs ScalableLux: the
   light engine is an **open audit item for P2**, not just a compat note. See
   RFC-0006 §3, hazard 19 (candidate).

- Home: `weft-simd` module (isolated because incubator modules need
  `--add-modules`; degrade gracefully when absent).
- Acceptance: JMH shows ≥3x on noise kernels vs vanilla scalar; end-to-end
  chunk-gen throughput ≥1.5x on the exploration benchmark. (WS-4.3 is excluded
  from this criterion while contested — see above.)
- Note on WS-4.1: **Noisium** optimizes the same worldgen math algorithmically
  on NeoForge 1.21.1. This is the one overlap where "both run" may genuinely
  be faster, since WS-4.1 is SIMD on the same kernels. It needs a profiler
  number before yield-vs-compose can be decided; deliberately left open rather
  than guessed, and no posture is seeded for it.

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
- **Scope settled 2026-08-18 (RESEARCH-0003 errata E3/§4.1): WS-7 stays as
  written.** RESEARCH-0001 §7 action 2 proposed rescoping this to "emit into
  existing tooling"; that recommendation is withdrawn, because `spark-api` is
  read-only (six accessors on `Spark`, no registration path) and no NeoForge
  1.21.1 exporter exists to emit into. Build the endpoint. Emit standard
  **OpenMetrics text** so the existing Prometheus/Grafana stack consumes it
  with no Weft-specific tooling — integration through the wire format, not
  through an API nobody offers. The one-way `spark-api` *read* (WS-6.2's GC
  data, and a `/weft report` MSPT cross-check) is a separate soft dependency;
  `tps()`/`mspt()` are `@Nullable`, so a null path is required.
- **Amended by [RFC-0009](RFC-0009-observability-exporter.md) — shipped, gates
  green, overhead measured (RFC-0009 §9.4: within ±0.5% median MSPT at the 10s
  cadence, from a harness that resolves a 200x cadence at +6-8%):** the
  exact metric names, event-stream envelope and schema, `[observability]`
  config block, and RFC-0003 compliance are decided there — the surface is API
  the day it ships, and seven of the series sketched above needed a rename, a
  narrowing or a drop because Weft does not measure what their names claim.
  Notably: no `weft-neighbors.toml` rows for the exporter mods (RFC-0003 §3.1
  forbids unverified modids; a **port collision is detected by binding**, not
  by modid), and `weft_jvm_gc_*` ships as a counter pair rather than a pause
  histogram because pause attribution is WS-6.2's territory.

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
  needs WS-1 to gate more of the mob tick, not tuning).
- WS-2 acceptance runs as `ws2PathStressReduction` (the 300-zombie stress
  world: a sealed-keep maze so every repath runs A* to its node budget) —
  cleared 2026-08-17 at ~50-59% entity-phase reduction, which flipped
  `asyncPathfinding` default-on — plus the flat-world watchdog
  (`ws2EntityPhaseReduction`, ~0-6%: async must cost nothing where paths
  are cheap). The P1 spawn-density graduation has its own hard gate
  (`spawnDensityAuthoritativeParity`: exact count parity on a converged
  static world + a live-spawning phase through the constructed state), and
  the RFC-0001 P1 exit criterion is tracked as `p1EndToEndMspt` (same-run
  full-tick MSPT A/B, services at shipping defaults vs all-off). WS-3..6
  criteria join the same harness as they land.

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
