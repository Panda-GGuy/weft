# NOTE-0001: The `ServerLevel.tick` between-sections window — increment 7 go/no-go evidence

Status: EVIDENCE NOTE · 2026-08-19
Supports: RFC-0007 §4 (single-join tick), item 2 of the go/no-go audit

## Method

Read the decompiled NeoForge 1.21.1 sources this workspace actually builds
against — the NFRT artifact
`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_e29e8b54cc357b03faf540a7aaa838f8976d7f14_output.jar`
(the same decompile RFC-0006's hazard audit cites). Files inspected:
`net/minecraft/server/level/ServerLevel.java`,
`net/minecraft/world/level/Level.java`. No folklore; line numbers below are
from that artifact.

## Finding 1: the window between the entity section and `tickBlockEntities` is empty

`ServerLevel.tick` (ServerLevel:333) runs, in order: world border, weather,
sleep, time, `tickPending` (block+fluid scheduled ticks), raids,
`chunkSource.tick`, block events — all **before** the `entities` push. Then,
inside the `if (flag1 || this.emptyTime++ < 300)` block:

```java
this.entityTickList.forEach(p_308566_ -> { ... });   // ServerLevel:400
profilerfiller.pop();                                 // ServerLevel:427
this.tickBlockEntities();                             // ServerLevel:428
```

Between the entity `forEach` call site and `tickBlockEntities()` there is
exactly one statement: `profilerfiller.pop()` — a profiler frame pop, no
simulation, no per-level work, nothing observable. RFC-0007 §4's second hard
requirement ("the between-sections code must not reorder observably") is
therefore satisfied vacuously in vanilla+NeoForge 1.21.1: **there is no
between-sections code.** The fusion seam does not need to move and the
increment does not need to narrow on this account.

Two adjacencies worth recording for the fused design, both outside the
window but touching its edges:

- `dragonFight.tick()` (ServerLevel:395) runs immediately **before** the
  entity forEach, inside the same `entities` profiler frame. Under fusion it
  must stay on the server thread before fan-out — same place the current
  partitioned collection pass already leaves it.
- `entityManager.tick()` (ServerLevel:432) runs **after** `tickBlockEntities`
  and is level-global (entity section storage maintenance). It runs after
  the single join, exactly where it runs today after the BE section.

## Finding 2: vanilla's pending-BE semantics (what per-region containers must replicate)

`Level.tickBlockEntities` (Level:534):

```java
this.tickingBlockEntities = true;                       // Level:541
// NeoForge addition: freshBlockEntities onLoad() flush precedes ticker merge
if (!this.pendingBlockEntityTickers.isEmpty()) {
    this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);  // Level:553
    this.pendingBlockEntityTickers.clear();
}
Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();  // Level:557
while (iterator.hasNext()) {
    ... isRemoved() -> iterator.remove(); else shouldTickBlocksAt -> tick();
}
this.tickingBlockEntities = false;                      // Level:569
```

and `addBlockEntityTicker` (Level:523):

```java
(this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(p_151526_);
```

So the per-tick iteration set is fixed at `tickBlockEntities` **entry** —
after the entity section. A ticker added during the entity section (sculk
growth from a catalyst charge, a falling block landing and placing a ticking
BE) goes straight into `blockEntityTickers` (`tickingBlockEntities` is false
then) and ticks the **same** tick. A ticker added by a ticking BE goes to
`pendingBlockEntityTickers` and ticks the **next** tick. This is exactly the
semantics `dev.weft.engine.region.PendingUnits` replicates per region, and
the fused stage order `[mail → entity → BE]` preserves the same-tick
property because the region's entity stage completes before its BE stage
snapshots.

NeoForge's patch adds `freshBlockEntities`/`pendingFreshBlockEntities`
(`onLoad()` deferral) at the head of `tickBlockEntities` with the same
ticking-flag pattern (Level:527, Level:536-549). A fused BE stage must give
these the same per-region treatment when the loader wiring lands — recorded
here so the wiring increment does not miss it.

## Finding 3: what the loader already intercepts

Weft's seams sit exactly at the two call sites above:
`ServerLevelRegionTickMixin` wraps the `entityTickList.forEach` INVOKE inside
`ServerLevel.tick`; `LevelRegionTickMixin` wraps the whole
`Level.tickBlockEntities` method. Fusing means the second wrap's collection
pass must run **before** the entity fan-out rather than after it returns —
which is a loader-side restructuring (weft-neoforge), not an engine change,
and it collides with vanilla's own add-ticker timing unless the per-region
pending containers replace the global list first. Hence the engine-first
scaffolding order.

## Go/no-go

**Go**, with the increment shaped as RFC-0007 §4 wrote it: the audit item
that could have narrowed it (observable between-sections work) is empty in
evidence. The remaining work is the loader wiring (per-region ticker capture
feeding `PendingUnits`, fused stage assembly, `p2fuse` gametest), which is
weft-neoforge surface.