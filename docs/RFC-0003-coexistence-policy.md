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
| Moonrise | `moonrise` ❌ **not in registry** | Was recorded "None material." **Narrowed 2026-08-18:** true for P0/P1 (same-thread entity/collision work, no tick-ownership claim, self-reports compatible with Lithium/FerriteCore). **Not established for P2** — Moonrise ports a *chunk system rewrite* and *Starlight*, and RFC-0006 hazards 1–4 build Weft's worker chunk read path directly on vanilla `ServerChunkCache` internals. See RFC-0006 hazard 20 (candidate) | Cooperate for P0/P1. **P2 posture unset** — do not seed one until hazard 20 closes |
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
delta at `1f217cd`:

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

## 4. What we deliberately do NOT build

- No runtime negotiation protocol between mods, no shared-optimization API
  handshakes, no reflection into neighbors' internals. Detection is modid +
  mixin-overlap only. (If a neighbor project ever wants deeper integration,
  that's a conversation, not speculative code.)
- No per-feature shims replicating a neighbor's behavior when yielding —
  yield means *their* implementation runs, full stop.

*End of RFC-0003 draft 1.*
