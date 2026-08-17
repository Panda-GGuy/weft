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

| Neighbor | Overlapping Weft module | Default posture |
|---|---|---|
| Alternate Current | WS-3 redstone compilation | Yield |
| Krypton / equivalents | WS-9 network egress | Yield |
| C2ME | Worldgen scheduling (WS-4 noise stays — different layer) | Yield scheduler, keep SIMD kernels if hookable, else yield both |
| Lithium-family / ServerCore | Per-target: yield only colliding mixin territories | Cooperate, targeted yields |
| ModernFix | None material | Cooperate |
| spark | P0 profiler (both observe fine) | Cooperate |
| Other threading engines | Tick ownership | Refuse (Tier 3) |

## 4. What we deliberately do NOT build

- No runtime negotiation protocol between mods, no shared-optimization API
  handshakes, no reflection into neighbors' internals. Detection is modid +
  mixin-overlap only. (If a neighbor project ever wants deeper integration,
  that's a conversation, not speculative code.)
- No per-feature shims replicating a neighbor's behavior when yielding —
  yield means *their* implementation runs, full stop.

*End of RFC-0003 draft 1.*
