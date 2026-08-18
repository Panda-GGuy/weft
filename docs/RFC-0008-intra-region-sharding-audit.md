# RFC-0008: Intra-region sharding against real ticking — the audit that reshapes WS-10

Status: ACCEPTED — implemented and green at class E2 · 2026-08-17
Depends on: RFC-0004 (WS-10 design, engine-level), RFC-0005 (parity ladder,
class E2), RFC-0006 (parallel-region audit — this RFC is its sub-region twin),
P2 increments 1–6
Supersedes: RFC-0004 §2.1's round-robin partition **for the vanilla tick path**
(the engine-native path is unchanged)

## 1. Why RFC-0004 cannot be activated as written

RFC-0004 landed the engine machinery (`ShardKey`, `ShardContext`,
`EntityEffects`, `WeftGuards.checkShardMutation`) and it works — over
`WeftScheduler`'s own `RegionManager`, which carries no chunks in
production. So sharding has never met real ticking. Activating it means
hooking the loader path where entities and block entities actually tick
(`RegionizedTicking`'s per-region buckets), and that exposes two assumptions
in RFC-0004 that do not survive contact with vanilla:

**Assumption 1 — "tickables record cross-entity effects into a log"
(§2.2/§2.3).** Weft-native tickables can. Vanilla entities cannot: they
write to each other directly. `EntityEffects` covers damage, item claim,
love mode, removal, and spawn — a set chosen for the graph-layer analogy,
not derived from vanilla's actual cross-entity surface. Routing vanilla
through it would require intercepting every such path by mixin and changing
when each effect becomes visible.

**Assumption 2 — "round-robin over the tickables list is sufficient — no
spatial meaning is needed at this grain" (§2.1).** This is the load-bearing
claim, and it is **false for vanilla entities**. Verified in the
1.21.1/NeoForge decompile (`LivingEntity.java`): `aiStep()` calls
`pushEntities()` **every tick, for every living entity** (line 2787).
`pushEntities` (line 2830) does a live `level.getEntities(...)` spatial
query and then `doPush(entity)` on **every colliding neighbor** — a direct
`setDeltaMovement` write to another entity — plus `hurt(...cramming...)`
when the crowding rule trips. Two mobs standing next to each other, placed
in different shards by a round-robin split, race on each other's motion on
the mainline path. This is not an edge case to be handled later; it is what
mob ticking *does*.

The consequence for scope: **entity sharding is not the next increment.**
Making it safe means either deferring all collision pushes to a
post-barrier merge (a physics semantics change, and a very high op volume),
or spatially separating entities that can collide — which is hard when
entities move every tick and their AI queries reach tens of blocks. Neither
is a single increment, and neither should be attempted before the cheaper
half proves the machinery against real ticking.

## 2. Why block entities are the tractable half

Block entities differ in exactly the way that matters: **they do not move,
and their per-tick reach is short and spatially bounded.** The worst
ordinary case is the hopper, verified in `HopperBlockEntity.java`: its
target is `pos.relative(facing)` (line 361) and its source is the container
one block above (line 366) — a one-block reach, both directions. Create's
dominant tickers (fluid pipes, tanks) are adjacency-coupled the same way.
No collision, no AI queries, no entity-list mutation, no breeding.

That restores RFC-0006's central safety argument at sub-region grain. RFC-0006
could dismiss per-chunk state, container contents, and per-section multimaps
as "region-confined by construction" *because regions are ≥ mergeDistance
apart*. Intra-region sharding destroys that argument in general — two shards
in one region share chunks — but it survives if **shards are spatially
separated by more than the maximum per-tick reach.**

## 3. Design: chunk-colored spatial shards, wide-reach types on a serial tail

Within one region's block-entity bucket:

1. **Color chunks** by `(cx mod 2, cz mod 2)` — four classes. Two distinct
   chunks of the same color differ by an even, non-zero amount in at least
   one axis, so their block ranges are ≥ 17 blocks apart. Concurrently
   running same-color chunks therefore cannot reach each other with a
   one-block interaction.
2. **Four passes, barriered.** Each pass fans its color's chunks across
   workers under `ThreadContext.Kind.SHARD` with a `ShardKey.pack(regionId,
   colorIndex)` owner key; the next pass starts after the previous joins.
   A 778-chunk region has ample chunks per color, so parallelism is bounded
   by the pool, not the coloring.
3. **Order within a chunk is vanilla's order**, unchanged — the collection
   pass preserves it exactly as increment 4 does.
4. **Wide-reach types run in a serial tail**, not in the coloring: a
   17-block gap is *not* provably enough for every vanilla block entity.
   Known offenders — `PistonMovingBlockEntity` (moves a push line up to 12
   blocks and writes blocks along it), sculk catalyst/spreader (charge
   cursors walk outward), beacon and conduit (apply effects to players over
   a large radius, and player state belongs to the GLOBAL lane) — are
   type-listed and ticked serially on the region's own thread after the four
   passes, exactly like increment 4's unmapped tail. The list is a
   conservative allowlist-of-exclusions: unknown modded types shard, and
   §3.1's guard is what catches a wrong guess.
5. **Fail-loud out-of-domain detection (the honest part).** A fixed gap is a
   claim, not a proof, so the claim is *enforced* rather than trusted:
   while a shard pass runs, block-entity access is checked against the
   accessing shard's chunk, and a violation is counted with position
   forensics (thrown in DEV) — RFC-0006 hazard #4's idiom.

   The check condition is precisely "a **different chunk of the same
   color**", which is narrower than "not my chunk" for a load-bearing
   reason: a hopper at a chunk edge legitimately reaches one block into the
   *adjacent* chunk, which is extremely common, and adjacent chunks always
   have different colors — so they run in different passes and provably
   cannot be concurrent. Only same-color chunks execute simultaneously, so
   only those constitute a race. Flagging cross-chunk access generally would
   drown the gate in false positives and teach everyone to ignore it; this
   condition trips only when a ticker's reach actually exceeded
   `MIN_BLOCK_GAP`, which is exactly the classification bug the list in
   point 4 exists to prevent.

   The seam sits on block-entity lookup rather than block-state reads:
   cross-chunk *container* access is the hopper-shaped hazard, while
   `getBlockState` is far hotter and is what the separation argument itself
   covers.

### 3.1 What this reuses unchanged

`ShardKey`, `ShardContext`, `ThreadContext.Kind.SHARD`, and
`checkShardMutation` all apply as-is. `EntityEffects` is **not used** by
this increment: with spatial separation plus the guard, a sharded BE mutates
only its own chunk's state, so there is no cross-shard effect to log. The
effect log stays reserved for the entity work of §5, where it is genuinely
needed.

One dependency that was not visible when this RFC was drafted: sharded
block entities look their neighbours up through `Level.getBlockEntity`,
which vanilla answers `null` for off-thread callers. That is RFC-0006
hazard 18, found while first running this increment's gate, and this design
does not work without its fix — the colouring is what makes concurrent
lookups *safe*, but the lookups have to actually work.

On `WeftGuards`: `checkRegionMutation` returns `false` for `SHARD` contexts
(RFC-0004 §2.2's strict rule), which would forbid the chunk mutation a
sharded BE tick legitimately performs — but this is currently moot and worth
stating rather than quietly relying on: **the guards are not wired into any
vanilla mutation path.** Grepping the loader finds no `checkRegionMutation`
or `checkShardMutation` call outside the engine's own code, so RFC-0004 §4's
"zero guard trips" criterion presently describes the engine-native path only.
§3 point 5's domain check is therefore the *real* enforcement for this
increment, and it is counted and gated accordingly. Wiring `WeftGuards` into
vanilla's mutation paths remains open work (RFC-0001 §4.4) and would
subsume it; the strict `SHARD` rule should be relaxed to "own chunk" at that
point, not before.

Reused unchanged from increment 5, and load-bearing here: six of the eight
`mixin.parallel` safety mixins synchronize their structures
unconditionally (entity tick list, entity lookup, section storage, chunk-map
tracker, `Level.addBlockEntityTicker`/`nextSubTickCount`, known UUIDs), so
they already cover shard concurrency. The two that gate on
`ParallelAccess.isRegionWorker()` matter: shard tasks **must** set that flag,
because the worker chunk-read path is what avoids `getChunk`'s
`supplyAsync(mainThreadProcessor).join()` deadlock while the server thread is
parked at a pass barrier (RFC-0006 hazard 1).

## 4. Equivalence class and gates

**Class E2** (RFC-0005 §4), and E2's conservation capture had to be built
for it — RFC-0005 §5 recorded it as not-yet-implemented, so it lands *with*
this increment's gate, before the change it judges (the P1 lesson).

Why not E1: block entities in *adjacent* chunks get different colors and so
tick in different passes, which can reorder an interacting pair relative to
vanilla — a hopper chain crossing a chunk boundary may move an item on a
different tick than vanilla would. Deterministic and reproducible at a fixed
coloring, but not vanilla's order. Items are conserved; *when* they move can
differ. That is exactly the RFC-0004 §2.5 tradeoff, and E2 is its class.

Gates required before this ships anywhere:

- **Conservation control discipline** (RFC-0005 §3 applied to E2): two
  identical vanilla runs must produce identical conservation captures before
  a sharded run may be judged, over a scenario with real conservation *flow*
  (item transport, damage, births) — a conservation gate over a scenario
  with no flow certifies nothing.
- **Conservation equality**: shard-count-1 vs shard-count-N over the same
  seed — identical entity populations by type, identical item totals by
  type, identical damage total (fixed-point), births, deaths, spawns and
  removals by type.
- **State parity where it must still hold**: the single-chunk arena keeps
  its E0 anchor (one color, one shard: the serial fast path must have zero
  residue).
- **Zero guard trips** in DEV mode across the benchmark world at every
  tested shard count (RFC-0004 §4).
- **Engagement + containment probes**: passes actually fanned out (thread
  probe, as `p2parallel` does), wide-reach tail counted, out-of-domain
  trips zero.
- **Throughput** (RFC-0004 §4, and the reason this increment was chosen):
  BE-section wall time and full-tick MSPT on a one-region BE-dense world,
  same-run A/B — the first P2 performance number of any kind.

## 5. Deferred, with reasons

- **Entity sharding** — blocked on §1's `pushEntities` finding. The
  plausible path is spatial separation with a much larger gap plus deferred
  collision resolution through `EntityEffects`; it needs its own RFC and its
  own evidence, and it should follow this increment so the shard machinery
  has already been proven against real ticking.
- **Cross-shard locking** and **speculative execution** — still excluded per
  RFC-0004 §5.

## 6. Honest applicability — what this does and does not buy

For a **vanilla-BE-dense world** (hopper arrays, furnace banks, farms) this
parallelizes the block-entity phase directly.

For **the user's real Create+AE2 pack**, the profiled instance puts Create
block-entity ticking at ~11.4% of attributed cost (fluid_pipe 6.3%,
fluid_tank 2.6%, steam_engine 2.5%) inside a single 778-chunk region — so
this increment is aimed at the right slice, but the flag interaction must be
stated plainly: **with `legacyLane` active, Create's block entities are
extracted to the serialized LEGACY phase and this increment never sees
them.** Sharding covers them only with the legacy lane off, which means
Tier-2 code running on shard workers — a deliberate user choice, not a
default, and the coexistence ladder still refuses tick-engine neighbours.
The structural fix for Create specifically remains the P3 adapter
(RFC-0001 §11), and the remaining ~85% of that pack's cost is entity work,
which §5 defers. Nobody should read this increment as "the Create pack gets
faster"; it is the machinery proof plus a real win on BE-heavy worlds, and
the first honest P2 throughput number either way.
