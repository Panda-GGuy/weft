# RFC-0003: Coexistence Policy — Modularity & Graceful Yield

**Status:** Draft 1
**Depends on:** RFC-0001 (§7.1 Tier-3 detection is the mechanism; this RFC is
the policy), RFC-0002 (every workstream must comply)
**Scope:** Design barriers, not a feature. Weft must never be the mod that
breaks someone's pack because they also installed Krypton, Alternate Current,
Lithium-family mods, C2ME, ModernFix, or spark. This document is short on
purpose — these are rules to build *within*, not a system to build.

---

## 1. The ladder

For every Weft optimization module, when another mod occupies the same
territory, behavior degrades down this ladder — never off it:

1. **Cooperate** — both run because they genuinely compose (e.g. spark
   profiling alongside Weft's profiler; ModernFix startup fixes alongside
   WS-6 CDS).
2. **Yield** — Weft detects the overlap and disables *only its overlapping
   module*, logs one clear line, and everything else keeps working
   (e.g. Alternate Current present → WS-3 redstone compilation yields;
   Krypton-equivalent present → WS-9 egress batching yields).
3. **Self-disable subsystem** — if an overlap is detected only at runtime
   (a mixin fails to apply, a hook finds foreign patches), the module turns
   itself off mid-flight, degrades to vanilla behavior, and reports.
4. **Refuse loudly** — reserved for true tick-loop-ownership conflicts
   (another threading engine). Clear "choose one" report at load, à la
   RFC-0001 Tier 3. Never a mystery crash, never world corruption.

"Fail over and work as well as possible" is exactly rungs 2–3: the rest of
Weft keeps delivering value with the overlapped piece parked.

## 2. Hard rules for every module (the barriers)

- **R1 — One module, one switch.** Every optimization (each RFC-0002
  workstream, each engine subsystem) has an independent enable flag and no
  hard dependency on any sibling module being active. If module X is off,
  modules Y/Z run unchanged.
- **R2 — Fail-soft mixins for optimizations.** Optional-feature mixins use
  `require = 0` + a runtime "did my hook actually apply?" check; if not
  applied, the module self-disables (rung 3). Fail-loud (`require = 1`) is
  reserved for the P2+ core tick-ownership mixins only — where silent
  non-application would be dangerous.
- **R3 — Known-neighbor registry.** A small data file (`weft-neighbors.toml`)
  maps modids → default posture per Weft module
  (cooperate / yield / refuse), checked at load. Data, not code — updating
  compat for a new mod is a one-line PR. Unknown mods default to cooperate;
  Tier-3 mixin-overlap scanning remains the backstop.
- **R4 — User override, both directions.** Config can force-enable a yielded
  module ("I removed Alternate Current's config, let Weft handle redstone")
  or force-disable anything. Overrides log as user-chosen so bug reports are
  triageable.
- **R5 — One-glance compat report.** At startup, log a compact table: every
  Weft module → ACTIVE / YIELDED (to whom) / DISABLED (why). `/weft status`
  prints the same in-game. No user should ever need a debugger to learn what
  Weft is actually doing in their pack.
- **R6 — Yield must be total.** A yielded module leaves zero residue: no
  half-applied mixins, no background threads, no config side effects. Vanilla
  (or the other mod's) behavior is bit-identical to Weft-absent for that
  subsystem.
- **R7 — CI boots the neighbors.** The compat gauntlet (RFC-0001 §10) gains a
  small matrix: boot with each known-neighbor mod present, assert the
  expected posture from R3 and a clean startup. Cheap, catches regressions.

## 3. Current known-neighbor postures (seed data for R3)

**This table is prose; `weft-neighbors.toml` is the data R3 actually resolves
against (R3: "data, not code").** Where the two disagree, the registry wins at
runtime and this table is the bug. The `modid` column exists so that
disagreement is visible instead of silent — a row with an unconfirmed modid is
a posture *decision* that is not yet *enforced*, because a wrong or missing
modid never matches and the registry looks like it works.

For the consolidated view — what each neighbor is, why the posture is what
it is, and which mods deliberately have *no* row — see
[RESEARCH-0004](RESEARCH-0004-neighbor-landscape.md).

| Neighbor | modid (in registry?) | Overlapping Weft module | Default posture |
|---|---|---|---|
| Alternate Current | `alternate_current` ✅ | WS-3 redstone compilation | Yield |
| Dedicated async-pathfinding mods | `asyncpathfinding`, `async_pathfinding` ✅ (best-effort seeds) | WS-2 async pathfinding — one subsystem, one owner | Yield |
| spark | `spark` ✅ | P0 profiler (both observe fine); Weft additionally *reads* `spark-api` one-way where present | Cooperate |
| Lithium-family | `lithium` ✅ | Per-target: yield only colliding mixin territories | Cooperate, targeted yields |
| **ServerCore** | `servercore` ✅ | **Entity Activation Range → WS-1** (strictly wider: whole-tick gating vs AI-frequency throttling); **`mob-spawning` + Dynamic Performance Checks → P1 spawn-density** (both construct/mediate the mobcap inputs) | **Yield on both.** Deliberately conservative — their activation range ships disabled and §4 forbids reading a neighbor's config, so modid presence is all Weft can see. R4 force-enable is the escape hatch |
| **ScalableLux** | `scalablelux` ✅ | **WS-4.3 light propagation batching** (their lane). Nothing else Weft registers today | Yield `ws4_light`; Cooperate on profiler + spawn_density. **`regionized_ticking` posture deliberately UNSET** pending RFC-0006 hazard 19 — its parallel light updates are *on by default* (`parallelism` defaults to auto = `max(1, cores/3)`) and the interaction with worker-side block mutation is untested by either project |
| **Moonrise** | `moonrise` ✅ (added 2026-08-20) | P0/P1: none material (same-thread entity/collision work, no tick-ownership claim). **P2: confirmed conflict** — its chunk-system rewrite ran `TickThread.ensureTickThread` from a Weft ForkJoin worker during a parallel entity section and crashed the tick loop (`Cannot execute main thread task off-main`). Hazard 20 is no longer a candidate; see issue #16 | **Cooperate on the profiler; yield `regionized_ticking`, `entity_sharding`, `legacy_lane`.** Yield rather than refuse — Moonrise claims no tick ownership, so the operator should not be forced to choose. P1 services keep running (they are not on the crashing path). R7 `moonrise` cell boots the full parallel stack in config and asserts it is disarmed |
| Krypton / equivalents | ❌ modid unconfirmed | WS-9 network egress | Yield (decided, **not enforced**) |
| C2ME | ❌ modid unconfirmed (Fabric-only at the mod level; NeoForge only via Connector) | Worldgen scheduling (WS-4 noise stays — different layer) | Yield scheduler, keep SIMD kernels if hookable, else yield both (decided, **not enforced**) |
| ModernFix | ❌ modid unconfirmed | None material | Cooperate (= registry default for unknown mods, so absence is harmless here) |
| Forgia | `forgia` ✅ | Tick ownership (NeoForge-native Folia port) | Refuse (Tier 3) |
| NeoFolia | `neofolia` ✅ | Tick ownership (NeoForge-native Folia port) | Refuse (Tier 3) |
| Foliage | `foliage` ✅ | Tick ownership (NeoForge Folia hybrid) | Refuse (Tier 3) |
| Eturlia | `eturlia` ✅ | Tick ownership (Folia/Paper core + NeoForge loader hybrid — different runtime shape, but same region-threading claim on the tick) | Refuse (Tier 3) |
| Other threading engines | — (Tier-3 mixin-overlap scan is the backstop) | Tick ownership | Refuse (Tier 3) |

### 3.1 Registry drift, and how it is closed (errata E7, 2026-08-18)

The drift RESEARCH-0003 E7 flagged is real and was **bidirectional**. Measured
delta at `cd39aed`:

- **In the table, absent from the registry (5):** Krypton, C2ME, ModernFix,
  ServerCore, Moonrise.
- **In the registry, absent from the table (1 concept, 2 modids):** the
  dedicated async-pathfinding yield (`asyncpathfinding`, `async_pathfinding`).

Closed in the truthful direction, which is not "seed everything":

1. **Added to the registry**, modids read out of actual jar metadata on the
   branch targeting MC 1.21.1: `servercore`, `scalablelux`. Both are covered by
   the R7 boot matrix and pinned by `ShippedNeighborRegistryTest`.
2. **Added to the table:** the async-pathfinding row above.
3. **Marked, not invented:** Krypton, C2ME and ModernFix keep their decided
   postures and are labelled modid-unconfirmed. Two of the three are
   additionally inert today — Krypton's overlap is WS-9, which does not exist,
   and ModernFix's posture (`cooperate`) is already the registry default for
   unknown mods. Seeding a guessed modid would look like coverage while
   matching nothing; that is the failure mode this column exists to prevent.
4. **Narrowed, not re-postured:** Moonrise. Its modid *is* verified
   (`moonrise`, `Tuinity/Moonrise` branch `mc/1.21.1`, real `neoforge` module,
   GPLv3), but the audit finding above means no P2 posture is seeded for it.

Standing rule this establishes: **a row may not enter `weft-neighbors.toml`
without (a) a modid read from `neoforge.mods.toml` / `fabric.mod.json`, and
(b) an R7 matrix cell that boots it.** Postures nobody has booted are prose.

### 3.2 Moonrise, re-postured by a crash (issue #16, 2026-08-20)

§3.1 item 4 held that no P2 posture would be seeded for Moonrise until the
hazard-20 audit closed. It closed the expensive way: a field boot with
`parallelRegions` active crashed the tick loop, Moonrise's chunk system
asserting main-thread execution from a Weft worker. That is the interaction
the audit item existed to predict, so the posture is now seeded — the reason
§3.1 withheld it was absence of evidence, and the evidence arrived.

Two choices this pass deliberately makes:

1. **Yield, not refuse.** Rung 4 is for mods that claim tick ownership, and
   Moonrise claims none (RESEARCH-0002 §1) — refusing would make operators
   choose between two mods that have no *architectural* conflict, only an
   unbuilt integration. Yield parks Weft's module and Moonrise runs.
   §4 still applies: no shim replicates region ticking while yielded.
2. **Only the tick-ownership modules yield.** P1's off-thread services were
   not on the crashing stack, and yielding them would be over-yielding by
   association — the exact failure `ShippedNeighborRegistryTest` pins against.

What makes the posture *enforced* rather than declared: yielding
`regionized_ticking` disarms the crash path transitively.
`RegionizedTicking.applyActive(false)` clears `partitioned`, and `parallel`
is only ever assigned `partitioned && parallelRegions`, so a config file
still reading `parallelRegions = true` cannot produce a worker section. The
R7 `moonrise` cell boots exactly that configuration and greps for the
disarmed-sub-flags line, because a posture-table line alone cannot
distinguish a parked module from a relabelled one.

**Not closed by this:** real coexistence. Weft's worker chunk reads
(RFC-0006 hazards 1–4) are still reasoned against *vanilla*
`ServerChunkCache` internals, and Moonrise replaces them. Co-enabling
remains unsupported, and the lab rule stands: no Moonrise + parallel-region
soak.

## 4. What we deliberately do NOT build

- No runtime negotiation protocol between mods, no shared-optimization API
  handshakes, no reflection into neighbors' internals. Detection is modid +
  mixin-overlap only. (If a neighbor project ever wants deeper integration,
  that's a conversation, not speculative code.)
- No per-feature shims replicating a neighbor's behavior when yielding —
  yield means *their* implementation runs, full stop.

*End of RFC-0003 draft 1.*
