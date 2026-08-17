# RFC-0004: Intra-Region Entity Sharding (WS-10)

**Status:** Draft 1
**Depends on:** RFC-0001 (ownership model, guards, mailbox, graph layer's
snapshot→compute→commit pattern — this RFC applies the same discipline one
level down, to entities instead of chunks), RFC-0002 (workstream framework;
this is WS-10, and compounds with WS-1's distance-tier throttling rather than
replacing it), RFC-0003 (coexistence ladder — every rule below is written to
comply with it)
**Scope:** A second, independent axis of parallelism for entity/block-entity
ticking that pays off even when a world has collapsed to a single region —
which the P0 profiler shows is the normal case for single-player.

---

## 0. Why this exists

RFC-0001's region model parallelizes across *spatial separation*: different
players, different bases, different force-loaded chunks far enough apart that
they land in different regions. Real profiler data from a live single-player
world (attached separately) shows the failure mode of relying on that alone:

```
Hypothetical regions: 1 (hottest: 94.3% of tick, 790 chunks)
Estimated speedup if Weft owned this tick:
   2 workers -> 1.00x    4 workers -> 1.00x
   8 workers -> 1.00x   16 workers -> 1.00x
```

One player means one contiguous loaded area, which is — correctly — one
region under any merge distance. Region-level parallelism has ~0 headroom
here by construction, not by misconfiguration. But the same report shows
94.3% of tick cost concentrated in passive-mob AI and Create block entities,
the overwhelming majority of which are single-owner, independent work items.
That's a second parallelism axis Weft doesn't yet exploit: fanning tickables
out *within* one region, across worker threads, instead of only across
regions.

This is additive, not a replacement. Multiplayer/spread-base worlds still
get their win from regions; WS-10 is what gives solo play (and any
one-region world) a lever at all.

## 1. Why the naive version is unsafe

Fanning `Region`'s tickables loop out across a pool without further changes
breaks three things already load-bearing in the current engine:

1. **The ownership guard keys only on region id.** `WeftGuards.checkRegionMutation`
   grants a thread permission to mutate a region's state by checking
   `ctx.kind() == REGION && ctx.ownerId() == regionId`. If N threads all tick
   the same region concurrently, they'd all present that identical identity
   and the guard would authorize all of them simultaneously — silently
   deleting the single-owner invariant rather than parallelizing safely.
   This is the same failure class RFC-0001 was written to rule out (blind
   parallelism → dupes/corruption).
2. **`Region` holds one shared `SplittableRandom`.** It is documented as
   deterministic per RFC-0001 §6.6, and `SplittableRandom` is explicitly not
   safe for concurrent use of the same instance — its internal state mutates
   non-atomically. Parallel tickables drawing from it would tear the
   sequence and break reproducibility (which the technical/RNG-manipulation
   community and WS-8's benchmark-as-CI both depend on).
3. **Entity-to-entity effects are direct field writes today.** Combat
   (damage), breeding (love mode, partner claim), item pickup (claiming a
   dropped stack), and entity list mutation (spawn/despawn during iteration)
   all currently assume one thread, one ordered pass. Concurrently ticking
   two entities that interact this tick is a real race, not a theoretical
   one: two mobs damaging the same target, two chickens claiming the same
   wheat, or a spawn/removal racing a live iteration over the entity list.

None of this is a reason not to build it — it's the checklist for building
it the same way the graph layer already solves the analogous problem for
Create/AE2 networks.

## 2. Design

### 2.1 Shard identity

Add `ThreadContext.Kind.SHARD` alongside the existing REGION/GRAPH/GLOBAL/
LEGACY/NONE. A shard's owner key is a packed `(regionId, shardIndex)` pair —
same bit-packing idiom `ChunkKey` already uses for `(x, z)`:

```java
public final class ShardKey {
    public static long pack(long regionId, int shardIndex) { ... }
    public static long regionId(long key) { ... }
    public static int shardIndex(long key) { ... }
}
```

At the start of Phase 1 (REGION), a region whose tickable count is at or
above `WeftConfig.ENTITY_SHARD_MIN_BATCH` is partitioned into shards
(round-robin over the tickables list is sufficient — no spatial meaning is
needed at this grain). Each shard runs its own serial loop, same as today's
per-region loop, just narrower; entities *within* one shard are still ticked
one at a time, so nothing changes for same-shard neighbors. Small regions
stay single-shard, which is exactly today's behavior — this is a superset,
not a fork.

### 2.2 Guard rule

`WeftGuards` gains an entity-mutation check with one strict rule: **an
entity tick may mutate only its own component state directly.** Any effect
that touches another entity, or the region's shared entity list, must go
through the effect log below — never a direct cross-thread field write,
regardless of whether the target happens to be in the same shard or not.
Strict-by-default is deliberately simpler than trying to prove
same-shard-is-safe per call site; it can be relaxed later with evidence, not
assumed safe now.

### 2.3 Entity effect log (the entity-layer CommitLog)

This mirrors `dev.weft.api.graph.CommitLog` exactly, one grain down:

```java
public interface EntityEffectLog {
    void damage(long targetHandle, float amount, long sourceHandle);
    long claimItem(long itemHandle, long claimantHandle);      // conditional, anti-dupe
    void setLoveMode(long targetHandle, boolean active);
    void removeEntity(long handle);
    void spawnEntity(EntitySpec spec);
}
```

Every shard accumulates effects locally during its parallel pass (pure
recording, same as `GraphScheduler.RecordingCommitLog`). After all shards in
a region finish, effects are merged and applied in one deterministic order —
sorted by `(sourceHandle, emission sequence)`, never thread-finish order —
in a step that sits alongside the existing MAIL phase. Contested effects
(two claimants for one item, two attackers landing lethal damage on the same
tick) use the same conditional-write trick `CommitLog.insertItemConditional`
already established: first claim in the deterministic order wins, the loser
is rejected and can react next tick. This is the direct anti-race analog of
the graph layer's anti-dupe mechanism — same idea, applied to entities.

Entity list mutation (spawns/removals) is likewise collected as ops and
applied only by the single coordinator thread between phases — the live
list itself is never touched from inside a parallel shard pass, same
snapshot→compute→commit shape as the graph layer.

### 2.4 RNG

Before fan-out, the coordinator thread pre-splits the region's
`SplittableRandom` into one child stream per shard via `.split()`, in shard
index order — a single-threaded, deterministic step. Each shard then owns
its child stream exclusively for that tick. Same seed + same shard count +
same tickable set reproduces byte-identical draws. Changing shard count
changes the substreams entities see, which is consistent with an already-
accepted tradeoff (RFC-0001 already documents that region merge/split
reshapes RNG continuity) rather than a new one.

### 2.5 Determinism vs. vanilla ordering — the one honest tradeoff

Within-tick *interleaving* across entities is no longer vanilla's single
ordered list — it's N interleaved batches. Contested-effect *resolution* is
made deterministic and reproducible (§2.3), so re-running the same tick,
seed, and shard configuration reproduces the same outcome. But that outcome
can differ from unmodified vanilla's exact tick-order result in edge cases
that intentionally depend on list order (some technical builds exploit this
on purpose). This must be documented plainly, not discovered by a bug
report, and the feature ships opt-in/off-by-default until the parity suite
(§4) proves it out.

## 3. Coexistence (RFC-0003 compliance)

- **R1 (one switch):** `weft.entitySharding.enabled`, independent of every
  other module.
- **R2 (fail-soft):** if the entity-tick mixin hooks this depends on don't
  apply, shard count silently floors to 1 — today's exact serial behavior,
  zero residue, per R6.
- **R5 (status line):** active shard count per region surfaces in
  `/weft status` and the startup compat table, so nobody has to guess
  whether they're getting the sharded or serial path.
- **Tier-3 interaction:** any mod that patches entity-tick internals directly
  (custom AI threading, e.g. a Pufferfish-style fork) is exactly the
  tick-ownership overlap RFC-0001 §7.1 already detects — refuse loudly,
  don't silently double-parallelize the same entities.

## 4. Acceptance criteria

- **Guard trips:** zero, in DEV mode, across the benchmark world with
  sharding enabled at every tested shard count.
- **Conservation parity:** aggregate outcomes (total damage dealt, item
  counts created/destroyed, breeding events) identical between shard-count=1
  and shard-count=N runs of the same seed — same *totals*, order allowed to
  differ per §2.5.
- **RNG reproducibility:** same seed + same shard count + same entity set →
  byte-identical draw sequence across repeated runs.
- **Throughput:** on a synthetic equivalent of the profiled world (~500
  passive mobs collapsed into one region), entity-phase wall time drops
  measurably with shard count on an 8+ core machine — closing a real chunk
  of the gap the current report shows flatlined at 1.00x.

## 5. Explicitly not building (v1)

- **No attempt to preserve exact vanilla iteration order under sharding.**
  Documented behavior change, opt-in, per §2.5.
- **No cross-shard locking.** A locked version is just a slower serial loop
  with extra steps — it defeats the point.
- **No speculative/optimistic execution with rollback.** Too much complexity
  for unproven benefit; revisit only with benchmark evidence (WS-8) that the
  conditional-effect-log approach isn't enough.

## 6. Sequencing

Lands after WS-8 (benchmark-as-CI) exists — this is exactly the kind of
change that needs a regression gate before it ships, not after. Natural
companion to WS-1: distance-tier throttling shrinks how many entities need
full-rate AI at all; sharding parallelizes whatever's left. The two compound
rather than compete.

*End of RFC-0004 draft 1.*
