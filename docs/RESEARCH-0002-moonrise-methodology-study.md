# RESEARCH-0002: Methodology Study — Moonrise

**Status:** Living document, first pass
**Purpose:** [Moonrise](https://github.com/Tuinity/Moonrise) is a Fabric/NeoForge
mod, maintained by Paper/Folia's own lead author, that ports Paper's
non-gameplay-changing performance patches directly onto NeoForge. This is a
methodology study, not a code study — nothing here was copied; the findings
below come from reading the project's README, module layout, and commit
messages, then extracting the *design principle* behind each area. Where a
principle applies to Weft, it's noted as a recommendation, not an
implementation.
**Method:** Live GitHub research this session (README, source tree structure
under `src/main/java/ca/spottedleaf/moonrise/patches/`, recent commit
messages). No source files were read or reproduced.

---

## 1. What Moonrise actually is

Not a competitor on Weft's axis. Moonrise does zero tick-ownership or
threading work — its README is explicit: "Moonrise aims to optimise the game
without changing Vanilla behaviour," and its optimized areas are entity
movement/collision/physics/tracking, chunk ticking/loading/generation/saving,
and block/entity retrieval. All same-thread algorithmic work, the same bucket
as Lithium — and indeed its own compatibility table lists Lithium and
FerriteCore as ✅ compatible. It should be a **Cooperate**-tier neighbor in
RFC-0003, not a Yield or Refuse one (see §5).

The one incompatibility it lists is **C2ME: ❌ incompatible** — both projects
do their own async chunk-loading rewrite and collide on the same territory.
That's a directly useful data point for Weft: it's the same shape of conflict
as Weft's own Tier-3 tick-ownership rule, just one layer down, at chunk-IO
ownership instead of tick ownership. Worth remembering if Weft ever builds
its own chunk-loading path (see §3).

## 2. Methodology findings, mapped to what's actually load-bearing for Weft

### 2.1 "Don't compute what nobody will observe" — entity tracker

Moonrise's entity tracker rewrite (`patches/entity_tracker/`) includes a
commit titled *"Only tick entity tracker if players are tracking or if at
entity ticking."* The principle: before spending cost on a per-entity
subsystem every tick, ask whether anything actually consumes that subsystem's
output this tick — and skip entirely, not just less-frequently, when the
answer is no.

This directly extends last session's finding on WS-1. The AI-vs-movement
sub-attribution work showed AI logic is only ~19-20% of a mob's tick cost —
movement/physics is the rest, and WS-1's distance-based *throttling*
(reduce frequency) can't reach that floor because movement still runs every
tick regardless of AI rate. Moonrise's tracker principle suggests a
complementary, cheaper technique sitting *underneath* WS-1 rather than
replacing it: an unconditional skip for subsystems with zero live
consumers this tick (nobody tracking this entity, no cross-entity interaction
pending), rather than always computing at *some* reduced rate. Skip-when-
unobserved and throttle-by-distance are different tools — the first is free
when applicable, the second trades frequency for correctness within a
distance band. Worth adding as an explicit technique alongside WS-1's
existing tiers, not instead of them.

### 2.2 Chunk-system rewrite — a whole axis Weft doesn't currently model

`patches/chunk_system/` is the largest module by far (entity, io, level,
player, queue, scheduling, server, status, storage, ticket, ticks, util,
world submodules) — a full async chunk load/generate/save pipeline with its
own ticket-priority system, the same shape as Paper's own chunk system.

This is a genuinely different axis from anything in RFC-0002. Weft's
`RegionManager` governs **tick ownership** of chunks that are already loaded;
it says nothing about **how those chunks got loaded**, what order load
requests are prioritized in, or how IO is scheduled off-thread. WS-4 (SIMD)
touches worldgen math, not the scheduling/ticket wrapper around it. A
well-defined chunk-ticket/priority-queue layer — the *methodology*, not
Moonrise's code — is a legitimate gap: it's off-main-thread IO work,
orthogonal to tick-ownership threading, so it composes with regionization
rather than competing with it. If Weft ever wants to own chunk loading
itself (not currently scoped in any RFC), this is the model to study, with
the explicit expectation that doing so puts Weft in the same conflict
Moonrise already has with C2ME — plan that yield relationship from the start
rather than discover it after shipping.

**Recommendation: this is a candidate for a new, distinctly-scoped future
workstream (tentatively "chunk IO/ticket scheduling"), not a retrofit into
an existing one — flagging for RFC-0002 to consider adding, not adding it
unilaterally here.**

### 2.3 `getblock` / collision-shape caching — the other half of the movement floor

`patches/getblock/GetBlockChunk.java` and `patches/collisions/` (with
`CollisionUtil.java`, `ExplosionBlockCache.java`, plus `block`/`shape`/`util`
submodules) target exactly the territory the AI-vs-movement finding
identified as unreachable by WS-1: block-state retrieval and collision-shape
computation, which the README explicitly calls out as "used frequently" by
pathfinding and entity AI, and which underlie the movement/physics majority
of entity-tick cost generally.

The methodology: cache computed collision shapes per block state/position
instead of recomputing voxel-shape unions on every check, and fast-path
block-state lookups for the hot chunk-access pattern pathfinding/physics
actually uses. This is a **same-thread, synchronous** win — and it's the
direct answer to a question WS-2's async pathfinding work raised implicitly:
before assuming pathfinding cost needs offloading to another thread, check
whether some of it is just redundant synchronous lookups that a cache
removes for free. Profiler-led principle (RFC-0002 §0.1) says: measure
which one actually explains the cost before building the more complex fix.

**Recommendation: worth a profiler check — does the benchmark world's
movement/physics majority (the 80% WS-1 couldn't touch) show up as
block/collision-shape-lookup-bound or genuinely compute-bound? If the
former, a synchronous caching pass could be cheaper and more effective than
either WS-1 throttling or WS-10 sharding for that specific slice, and should
be scoped before assuming those two are the only levers.**

### 2.4 Tick-idle packet processing

`patches/tick_loop/TickLoopPacketProcessor.java` /
`TickLoopBlockableEventLoop.java`, matching the README's "handle packets sent
while the integrated/dedicated server is waiting for next tick." The
principle: the gap between "this tick's work is done" and "next tick starts"
is otherwise-idle time; do network I/O there instead of only at fixed
in-tick points, cutting perceived latency without touching simulation order
or determinism.

Weft's own Phase 6 (EGRESS) currently runs inside the tick pipeline proper.
Worth a light look at whether some egress work (the kind WS-9 already
targets — packet serialization, delta dedup) could similarly move into the
inter-tick idle gap rather than the EGRESS phase itself, for the same
latency win at zero threading-model cost. Small, low-risk, orthogonal to
everything else in flight.

### 2.5 Short-circuit cheap preconditions before expensive lookups

A representative commit: *"Avoid performing biome lookup for mob cost when
entity has no cost"* (`patches/mob_spawning/`). General pattern, not
specific to mob spawning: when a cheap precondition already determines the
outcome, skip the expensive lookup entirely rather than computing it and
discarding the result. Small individually, but the kind of thing worth
keeping as a standing review habit across WS-1/WS-2/WS-10's hot paths as
they mature, not a workstream on its own.

## 3. What this doesn't change

Nothing here touches Weft's actual differentiators (RESEARCH-0001 §3):
automatic Tier 0-3 mod-safety classification, the graph layer for cross-chunk
mod networks, and the pre-adoption profiler remain unclaimed by Moonrise the
same way they're unclaimed by the regionization forks — Moonrise isn't
attempting any of the three, by design. This study is about borrowing
proven *same-thread* technique to shore up exactly the part of the profile
(movement/physics) that Weft's own threading work can't reach, not about
competitive positioning.

## 4. Recommended actions

1. Add Moonrise to RFC-0003's neighbor table as **Cooperate** (same bucket
   as Lithium/ModernFix/spark) — done in this pass, see §5.
2. Flag §2.2 (chunk-IO/ticket scheduling) as a possible future RFC-0002
   workstream candidate for the next roadmap review — not scoped or numbered
   here, since it's a genuinely new axis deserving its own discussion rather
   than a quick addition.
3. Before the next round of WS-1/WS-2/WS-10 benchmark work, run a quick
   profiler check per §2.3: is the movement/physics majority of entity-tick
   cost block/collision-lookup-bound? That answer should inform whether a
   cheap synchronous caching pass belongs in the sequencing ahead of, or
   alongside, WS-10's sharding work.
4. Note §2.4 (tick-idle packet processing) as a small, low-risk idea worth a
   look whenever WS-9 (network egress batching) work resumes — not urgent,
   not blocking anything.

## 5. RFC-0003 neighbor table update

Moonrise added as a **Cooperate** entry (same-thread algorithmic work,
explicitly compatible with Lithium/FerriteCore per its own docs, touches no
tick-ownership territory) — see the updated table in
`docs/RFC-0003-coexistence-policy.md`.

## 6. Sources

- [Tuinity/Moonrise (GitHub)](https://github.com/Tuinity/Moonrise)
- Source tree examined (structure only, no code read/reproduced):
  `patches/entity_tracker/`, `patches/chunk_system/`, `patches/getblock/`,
  `patches/collisions/`, `patches/tick_loop/`, `patches/mob_spawning/`,
  `patches/chunk_tick_iteration/` at ref `mc/26.2`.

*End of RESEARCH-0002, first pass.*
