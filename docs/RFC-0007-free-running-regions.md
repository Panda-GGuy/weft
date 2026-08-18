# RFC-0007: Free-running regions — owner-mail rerouting and the single-join tick

Status: DRAFT (increment 6 in progress) · 2026-08-17
Depends on: RFC-0001 §4 (threading model, §4.3 pipeline), §8.2 (mailboxes),
RFC-0005 (parity ladder), RFC-0006 (barriered parallel regions), P2 increments 1–5

## 1. What "free-running" means in v1 — and what it deliberately does not

Increment 5 runs region buckets concurrently, but **barriered inside each
vanilla tick section**: the server thread reaches the entity (or BE) section,
fans out, and waits. Two joins per level per tick, and every async-service
result still applies on the parked server thread's global inbox at INGEST —
RFC-0006 §2 named that model correct *because* of the barrier and scoped its
replacement out. This RFC is that replacement's design.

The scope boundary comes from RFC-0001 §4.3 and is worth restating because
"free-running" invites a Folia-shaped misreading: **the v1 pipeline is within
one 50 ms tick**. Per-region TPS isolation — regions crossing tick boundaries
independently, a lagging region not stalling others — is explicitly deferred
to v2 ("it changes user-visible semantics (redstone clocks desyncing across
regions) and belongs behind its own flag after the core is trusted"). So in
v1, free-running means:

1. **Owner-mail rerouting** (increment 6): results and cross-owner work are
   delivered to the *owning region's own mailbox* and drained by that
   region's worker at a defined point in its own execution — replacing the
   parked-main-thread global inbox as the delivery model. This is the item
   RFC-0001 §11 names as remaining, and it is the architectural prerequisite
   for everything after it (v2 TPS isolation included).
2. **The single-join tick** (increment 7, planned): a region runs its mail
   drain, entity bucket, and BE bucket as one uninterrupted unit, so regions
   run free of *each other* within the tick — one join per level per tick
   instead of a cross-region barrier at every section.

Neither increment lets a region observe another region mid-section any more
than increment 5 does, and neither changes what any single region computes —
the equivalence claims stay inside RFC-0005's existing E0/E1 classes (§3.5,
§4.3). No new equivalence class is needed until v2's temporal decoupling.

## 2. The mail model today (evidence)

Two facts, both verified in the code as of merge 8973061, define the problem:

- **There are two `RegionManager` universes.** The scheduler's own manager
  (`WeftMod.regions`) is deliberately empty — it exists to reserve owner ids
  (`RegionManager.reserveRegionId`); its REGION/MAIL phases tick no real
  work. The *real* chunk→region topology lives in `RegionTopology`'s
  per-level managers (increment 2), which decide vanilla-section bucket
  membership but carry **no mail**. `Region.mailbox()` exists on every
  region and is drained by the engine's MAIL phase — over the empty manager,
  so it has never carried production traffic.
- **All owner delivery is the global inbox.** `WeftMod.postToOwner(Runnable)`
  is target-less: WS-2 path results, off-thread topology events, and census
  events all land in `WeftScheduler.globalInbox`, drained at INGEST on the
  server thread at the head of `tickServer`. That is owner delivery *only*
  because barriered sections park every worker whenever the server thread
  runs: RFC-0006 §2's "with barriered sections this **is** owner delivery."
  `WeftScheduler.runOnOwner` — the positional routing entry point — already
  exists and is dead code, because the scheduler's manager it consults is
  empty.

Remove the barrier without rerouting mail and INGEST can mutate a region's
state while that region's worker is mid-tick. The rerouting must land first,
under the existing barrier, proven equivalent — then the barrier work can
begin. That ordering is this RFC's increment structure.

## 3. Increment 6: owner-mail rerouting (`ownerMailRouting`, class E0)

### 3.1 Routing authority and API

`RegionTopology`'s per-level managers become the routing authority — they
are the only map that knows real ownership. The loader API grows a
positional variant:

```
WeftMod.postToOwner(ServerLevel level, BlockPos pos, Runnable task)
```

which resolves `RegionTopology.managerFor(level).regionAtBlock(...)` and
posts a `Message.Task` to that region's mailbox. Resolution happens **at
drain-eligibility time, not post time, on the poster's thread** — see §3.3
for why the routed target can go stale and how that is handled. The
target-less `postToOwner(Runnable)` keeps its exact semantics (global inbox,
INGEST, server thread): topology mutations and census events are not owned
by any region — they mutate the managers and services themselves — and must
stay global. WS-2 is the first positional client: `findPathMaybeAsync` has
the mob at submit time, so the submit site captures `(level, blockPosition)`
as the routing target for that request's result delivery.

The scheduler's vestigial manager stays, for id reservation only, and the
engine pipeline's own MAIL phase stays as-is (still empty in production):
full unification of the engine pipeline with vanilla-section execution is
the v2 arc, not this increment's. What changes is that `Region.mailbox()`
finally carries real traffic, delivered and drained under the real topology.

### 3.2 The drain point

A routed region's mailbox is drained **at the head of that region's bucket,
under its REGION thread context**, in the same fan-out (parallel) or
canonical serial order (partitioned) the bucket itself runs in. Contract:
mail posted before the section began is applied before any of the owner's
simulation that tick; mail posted later waits exactly one section — the same
"next boundary" promise the global inbox gives today (RFC-0001 §8.2).

Delivery timing does move: global INGEST runs at the head of `tickServer`,
before weather, scheduled ticks, raids, and block events; a bucket-head
drain runs after those, immediately before the region's entity ticking.
For every current positional client this is unobservable — a WS-2 result is
a write to an `AsyncPath` read only by its own mob's navigation, which runs
in the entity section of the same region the result was routed to — and the
digest suite holds the claim at **E0**. The contract note (delivery is
"before the owner's simulation," not "at tick head") is documented here so a
future client that *is* sensitive to pre-entity vanilla steps gets designed
against the real contract, not the accidental one.

### 3.3 Topology-change hazards (the audit)

Mail can sit in a region's mailbox across a topology mutation. Every case:

| # | Event | Hazard | Treatment |
|---|---|---|---|
| 1 | Merge (`absorb`) | Victim's queued mail must follow its chunks | Already handled: `absorb` drains the victim's mailbox into the absorber, in order |
| 2 | Split (`recomputeSplits`) | A task routed for position P sits in the parent's mailbox while P moves to the split region; parent and split buckets may run concurrently next section | `RegionManager` gains a **stranded-mail sink**: on split, the parent's queued mail is drained to the sink (loader wires it to the global inbox). Global-inbox tasks run at INGEST on the server thread — always safe, one tick late, and splits are rare (churn fix c2fd0df made them provably rare) |
| 3 | Empty-region removal (`removeChunk` → region dropped) | Queued mail dropped silently | Same sink: drain to global on removal |
| 4 | Region unmapped at post time (unloaded chunk, despawned mob) | No target | Global fallback at post time, counted (`routedGlobalFallback`) |
| 5 | Flag layering: routing on but no bucket ever drains (partitioning off) | Stranded mail forever | `ownerMailRouting` **requires** `partitionedTicking` (resolved by `applyActive` like `parallelRegions`); with partitioning inactive, positional posts fall back to global. Deactivation mid-run drains every region mailbox to global once (same reasoning as the legacy lane's unconditional drain) |
| 6 | Per-pair FIFO across a merge | Sender posts m1→R; absorb begins reposting R's queue to R'; sender posts m2→R' directly; m2 can land before m1 | Documented, not fixed: the only positional client (WS-2) is single-flight per mob — two in-flight results for one key cannot coexist (`PathService` coalescing), so no current traffic can express the inversion. The invariant for future clients (per-sender sequencing or repost-before-accept) is recorded here as the fix to build *with* the client that needs it |

### 3.4 What increment 6 does *not* change

The barrier stays. LEGACY/GLOBAL phases stay where they are (engine tick at
the head of `tickServer`), the legacy lane keeps its single global drain per
tick and its `(regionOrder, seq)` sort — nothing about this increment
invalidates RFC-0006's audit, because workers still only run inside
barriered sections; the drain point added at bucket head runs strictly
inside the section under the same REGION context the bucket already holds.

### 3.5 Equivalence claim and gates

Claim: **E0**. Same units, same order, same thread discipline; the only new
execution is mail tasks running at bucket head on a worker instead of at
INGEST on the server thread, and for the digestable scenarios (no positional
mail traffic in the parity arena) the digest is bit-identical, enforced by:

- **Parity anchor extended**: the `p2parity` Weft-owned run activates
  `ownerMailRouting` alongside all prior increments — still bit-identical,
  zero unmapped residue, zero global-fallback surprises on an all-vanilla
  arena (the routing engages vacuously there; engagement is the next gate's
  job — a gate that can pass on an inert flag is not the engagement gate).
- **`p2mail` gametest** (new, hard): two-island topology (the
  `p2partition` pattern). A synthetic positional task targeting island A
  must run (a) under island A's region id (`ThreadContext` probe), (b)
  before island A's entities tick that section (order probe), (c) never on
  island B's bucket or the server thread when parallel fan-out is engaged.
  A task for an unmapped position must fall back to the global inbox and run
  at INGEST. Counters (`routedToRegion`, `routedGlobalFallback`) gated
  nonzero/exact — the engagement guard.
- **Engine unit tests**: stranded-mail sink on split and on empty-removal;
  absorb ordering preserved; sink never invoked while mapped.
- Full suite green **twice back-to-back**, as every increment before it.

## 4. Increment 7 (planned): the single-join tick (class E1)

With mail rerouted, the remaining v1 barrier work is collapsing the
per-section joins into one per level per tick: each region runs
`[mail drain → entity bucket → BE bucket]` as one worker task. Two hard
requirements fall out of vanilla's structure, both to be verified against
the decompile at implementation time (RFC-0006 method note applies —
evidence, not folklore):

1. **Per-region BE pending containers.** Vanilla's `blockEntityTickers` is
   one list whose per-tick iteration set is fixed at `tickBlockEntities`
   entry — *after* the entity section, so entity-added BEs (sculk growth
   from a catalyst-triggered charge, falling-block placements) tick the same
   tick. A fused region task must therefore collect its own region's
   entity-added tickers after its entity bucket — which means regionizing
   the pending lists (RFC-0001 §4.2's "each region carries its own …
   block-entity tick list" made literal), not just partitioning iteration of
   the global list.
2. **The between-sections code must not reorder observably.** Whatever
   vanilla runs between the entity call site and `tickBlockEntities` in
   `ServerLevel.tick` currently runs after *all* entities and before *all*
   BEs; under fusion it runs after the join, i.e. after every region's BEs.
   If the decompile shows that window contains observable per-level work,
   the fusion seam moves or the increment narrows — this is the increment's
   go/no-go audit item, and it is deliberately not asserted here without
   evidence.

Claim: E1, unchanged in kind from increment 5 — per-region digests stay
bit-identical (within a region: vanilla entity order, then vanilla BE
order); only cross-region *section* interleaving is added to the documented
order-canonicalized set. Gate: the two-island pattern again, plus a probe
that region A's BE bucket can complete while region B's entity bucket is
still running (the "free of each other" claim made concrete).

## 5. Explicitly deferred to v2

Per-region TPS isolation (regions at independent tick rates) remains out of
scope per RFC-0001 §4.3, and this RFC records what it would newly require so
nobody mistakes increment 7 for it: per-region game time and its save/replay
story, a legacy-lane drain redefined per region-epoch instead of per server
tick (the §7.2 "settled world" guarantee re-derived), a new RFC-0005
equivalence class for temporally-decoupled comparison (digests at "tick T"
stop being well-defined across regions), and a user-facing semantics flag
for cross-region redstone desync. None of that is started by this RFC; all
of it is unblocked by increment 6's delivery model.

## 6. Rollout

`ownerMailRouting` (default OFF) joins the sub-mode chain under
`regionized_ticking`, requiring `partitionedTicking` (not `parallelRegions`
— serial buckets drain mail at the same point). Fail-loud where a seam
misses, counted everywhere mail changes hands, surfaced in `/weft status`
(routing counters join the R5 detail line). Exit criteria to default-ON for
the whole P2 stack are unchanged from RFC-0006 §5 — full parity suite green
at declared classes, chaos + R7 green, Create/AE2 soak clean under the
flags — with the soak deliberately scheduled *after* this increment lands,
so it soaks the delivery model P2 will actually ship, not the one this
increment replaces.
