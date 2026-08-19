# TESTBUILD-0001 — Multithreading preview

**Status:** preview build, not a release. P2 is still open.
**Date:** 2026-08-18
**Build:** `feat/ws7-observability-exporter` + the P2 throughput gates
**Target:** NeoForge 21.1.248 / Minecraft 1.21.1

Three things happened to get this build.

**Region parallelism was measured for the first time, and it is fast:
2.43–2.76x full-tick MSPT, 3.33–3.90x on the entity section**, across seven runs
with a negative control that reads ~1.0x.

**Then it was run on a live server for the first time, and it crashed twice in
five minutes** — a villager opening a door, and a player walking away from
spawn. Neither was exotic, and the 22-test suite was green through both.

**Then both were root-caused and fixed.** The interesting one turned out not to
be the architectural gap it first looked like: worker chunk reads were waiting
on a chunk-promotion future that only the *parked main thread* could complete,
which is hazard 1's problem in a different disguise and has a bounded fix. So
`parallelRegions` is **on** in the shipped config, where an earlier cut of this
document had it off.

It is still a preview of an open workstream — P2's exit criteria are not all
green, which is why the *shipping default* stays off and you are opting in. Use
a world you have backed up.

---

## 1. The number

Region parallelism (`parallelRegions`, RFC-0006, class E1) is the headline
mechanism of the whole project, and until this build **its speed had never been
measured**. Increments 4 and 5 proved it was *correct* — bit-identical end
states, buckets provably off the server thread — and stopped there. RFC-0008's
bench asked about throughput, but asked it of intra-region block-entity
sharding on a one-region world, where region parallelism is arithmetically a
no-op.

Measured by `p2parallelbench` on a 16-thread machine, 14 worker threads,
8 regions × 220 mobs = 1760 ticking mobs:

| | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| Entity section, serial (ms) | 23.16 | 22.32 | 22.41 | 22.10 | 17.50 | 17.65 | 17.23 |
| Entity section, parallel (ms) | 6.81 | 6.66 | 6.43 | 6.63 | 4.69 | 4.70 | 4.42 |
| **Entity section** | **3.40x** | **3.35x** | **3.48x** | **3.33x** | **3.73x** | **3.76x** | **3.90x** |
| Section p95 | — | — | 3.31x | 3.29x | 3.25x | 3.07x | 3.60x |
| **Full-tick MSPT** | **2.43x** | **2.47x** | **2.63x** | **2.44x** | **2.75x** | **2.49x** | **2.76x** |

Runs 1–4 and 5–7 differ in absolute times because the later ones had the machine
to themselves; the *ratio* is what carries across, and it does. Quote the range,
not the best number in it.

And the reading that makes those admissible — the same harness, the same 1760
mobs, packed into **one** region, where the fan-out cannot engage because
`runBuckets` needs two buckets:

| Negative control (1 region) | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| **Ratio** | **1.03x** | **1.04x** | **1.02x** | **0.98x** | **1.04x** |

That ~1.0x is the point. It says the harness does not manufacture a win from a
flag flip — which is exactly what P2's retracted 1.59x did.

**Two numbers, two audiences.** ~3.5x is what region parallelism does to the
work it owns. ~2.5x is what the tick as a whole did on this rig, Amdahl-bounded
by everything that is *not* the entity section. On your pack the second number
depends on your own profile; `/weft report` shows it.

### Block-entity sharding: a tail win, not a throughput win

`blockEntitySharding` (RFC-0008, class E2) is the intra-region lever — the one
that does something when you have only one region. Its bench was rewritten
against the same ruler, and it is now quotable for the first time, but only for
the claim the data supports:

| 1600 hoppers, one region | run A | run B | control A | control B |
|---|---|---|---|---|
| BE section median | 1.34x | 1.01x | 0.87x | 0.86x |
| **BE section p95** | **1.72x** | **1.57x** | 1.02x | 0.90x |

The median does not reproduce; the **p95 does**. So the honest claim is *fewer
and smaller spikes at roughly the same average* — precisely what 2×2 chunk
colouring is designed to do, and what RFC-0008 predicted before it could be
measured. Either way it is bounded by your block-entity share of the tick,
about 10% on a Create-heavy pack.

### Method, and why it is believable this time

P2 has produced one throughput number it had to retract, and RFC-0008's bench
produced six readings spanning 0.85x–1.31x — an instrument that could not tell
a win from a loss. The shared harness (`SectionAb`) is built out of those
failures:

1. **Time the section, not the tick.** Full-tick MSPT cannot resolve a change
   confined to one section of a tick that is mostly other things.
2. **Interleave A/B/A/B/A/B, don't just pair A/B.** Same-run A/B removes
   cross-run variance but not warmup *order* bias — whichever phase runs second
   inherits a warmer JIT. That is what the 1.59x was. Six alternating phases,
   three per condition pooled, ticks around each flip discarded.
3. **Measure with the profiler off.** This one was invisible until now.
   `WeftProfiler` is server-thread-confined, so its clock reads happen on the
   serial path and silently *stop* on any path that moves work to workers.
   Every RFC-0008 reading was taken with profiling on, so its baseline carried
   overhead the sharded phase did not pay — and it *still* could not show a
   consistent win, which strengthens that verdict rather than weakening it.
4. **Carry a negative control.** A result is only evidence if the harness can
   be shown to resolve what it claims to.

Guards that fail the run rather than reporting a pretty number: every measured
parallel section must have fanned out across all 8 buckets on ≥2 worker
threads, no baseline section may have fanned out, the islands must have
resolved to 8 distinct regions, and no benchmark mob may have died mid-run. Two
of these caught real bugs in the harness itself before it produced a number.

---

## 2. The two crashes, and what they turned out to be

The 22-test suite was green, twice back to back, with `parallelRegions` on. Then
it ran on an actual server -- a forceloaded 4-island world, 560 mobs, a player
joining and moving -- and crashed twice in about five minutes. Both are fixed;
both are worth understanding, because the second was not what it looked like.

### Hazard 21 -- a villager opened a door ([#3](https://github.com/Panda-GGuy/weft/issues/3))

`ServerLevel.sendBlockUpdated` iterates `navigatingMobs`, a *level-wide* set of
every mob in every region, while other regions' buckets add to and remove from
it on spawn, death and chunk load. fastutil answers with an NPE.

The half that does not show in the stack trace matters more: the loop then calls
`recomputePath()` on mobs belonging to **other regions, while those regions are
ticking them**. A lock around the iteration would have silenced the crash and
left a cross-region write in place. So the fix is deferral to the section
barrier -- hazard 14's existing idiom -- where the set has no concurrent mutator
and touching any region's mobs is legal again.

It delays no packet, which is checkable rather than hopeful:
`sendBlockUpdated`'s client-visible half is `chunkSource.blockChanged`, and
`ServerChunkCache.tick` broadcasts at `ServerLevel:379` -- *before* the entity
section at `:420`. Vanilla already defers these to the next tick.

### Hazard 22 -- a chunk read waiting on a parked thread ([#4](https://github.com/Panda-GGuy/weft/issues/4))

This one crashed the server **on boot, every time**, on any world with
forceloaded chunks containing entities -- three consecutive boots, three
different chunks:

```
Weft region worker requires chunk [245, 125] at status minecraft:full
but it is not loaded/complete (RFC-0006 hazard 4: ...)
```

The first read of this was "chunk status doesn't hold still, so the region needs
a readiness/border invariant it doesn't have" -- an architectural gap needing its
own RFC. That was wrong, and the crash reports said so: all three were
**short-reach reads at a chunk boundary** (`updateFluidHeightAndDoFluidPushing`
to `getFluidState` twice, and `Mob.serverAiStep` to `getBlockState`). Nothing was
reaching far.

The border was never missing. `ChunkMap.prepareEntityTickingChunk` is
`getChunkRangeFuture(holder, radius 2, ChunkStatus.FULL)` -- vanilla guarantees
that before a chunk ticks entities, everything within radius 2 is generated to
`ChunkStatus.FULL`. That is exactly the border an entity tick reads into.

The trap is that **`ChunkStatus.FULL` (generation) and `FullChunkStatus.FULL`
(promotion) are different things reached by different futures.** Vanilla's
guarantee is about generation; Weft's worker read path was asking about
promotion. And promotion completes through `prepareAccessibleChunk`, whose
continuation is scheduled onto `ChunkMap.mainThreadMailbox` -- which, during a
fanned-out section, **the parked main thread cannot drain**. So a border chunk's
`fullChunkFuture` stays incomplete for precisely as long as the parallel section
runs, every read into it answers null, and hazard 4's guard turns that into a
crash.

So hazard 22 is **hazard 1 wearing a different hat**: a dependency on a future
only the parked main thread can complete. That is why it reproduced 3-for-3
instead of behaving like a race -- and why it has a bounded fix rather than
needing a new region model.

**The fix** answers the question vanilla's invariant actually guarantees: when no
promoted view exists, use the generated-`FULL` view, provided it is a real
`LevelChunk`. That is the same object and the same block states the main thread
would hand a vanilla caller reading the same border chunk. Three scoping
decisions carry it:

- **`getChunk(..., load=true)` only.** `getChunkNow` is vanilla's "only if
  loaded" probe; its null *is* the answer, and it never crashed.
- **Beyond radius 2 still fails loud** -- that is a read vanilla would have had
  to sync-load, the real bug hazard 4 exists to catch.
- **Not a re-opening of the p2parallelcap bug**, which was a worker resolving
  *its own ticking chunk* to a non-post-processed view. This path is reached only
  when no promoted view exists at all, i.e. a border chunk being read and not
  ticked. `p2parallelcap` is the standing gate and stays green.

### The counter that caught the first attempt at the fix

Border reads are counted and shown in `/weft status`, because hazard 4 used to
make this case a hard crash and the concession replacing it had to stay visible.
It paid for itself immediately. Version one of the fix put the fallback in the
shared resolve path, so `getChunkNow` began answering with generated-but-unloaded
chunks -- turning a null callers read as "not loaded" into a lie. That surfaced as
**8,260,234 border reads in thirty seconds** on a rig whose real border ring is a
few hundred chunks. Scoping to the `load=true` path dropped it to 21,712, a 380x
reduction.

The counter also carries the fix's evidence. It is **flat in steady state** --
unmoved through 15 teleports and 106 rounds of forceload churn -- and runs high
only during boot (~400k-670k), exactly the predicted shape: a promotion backlog
behind a main thread that parks once per section. A count that keeps climbing
during normal play would mean a worker reaching somewhere it should not, and now
shows up as a number instead of an outage.

### Hazard 23 - the two flags together ([#5](https://github.com/Panda-GGuy/weft/issues/5))

Found after this document first shipped, by the config this document recommends.

`parallelRegions` and `blockEntitySharding` were each tested alone and never
together. With both on, two regions, and a region over the 64-block-entity gate,
the server **hangs outright** - not crashes, so there is no crash report and the
client just reports a dead internal server. A region bucket runs on a pool
worker, calls the sharding pass, which submits back into the same pool and blocks
on it.

Fixed by standing sharding down whenever the section fans out, which costs
nothing: RFC-0008 scopes sharding to the single-region case in the first place.
**Consequence for you: on a multi-region world `blockEntitySharding` is now inert
by design.** Leave it on - it is your only lever if you are ever down to one
region, and it does nothing when you are not.

What makes this one uncomfortable is that both single-flag benchmarks stayed
green through it, and the shipped config turned on both. The new `p2combined`
gate hangs the whole test server without the fix and passes with it, and "every
combination the product ships must have a gate" is now an exit criterion in
RFC-0006 section 5.

### What to keep from this

Both bugs were found by five minutes of live play, after two green suite runs.
The rigs are synthetic in exactly the ways that hid them: they use zombies and
passive mobs, so no `Brain`-based AI ever opened a door; and they forceload a
fixed grid and never move a player, so no section ever ran while a promotion was
pending. Every rig proved its own mechanism and succeeded -- while sharing an
unstated assumption that the world holds still.

A live-server soak is now an exit criterion in RFC-0006 section 5, alongside the
parity suite rather than after it.

---

## 3. What this build ships on, and what it is worth

`testbuild/weft-common.toml` is the drop-in. Copy it over
`config/weft-common.toml`; Weft fills in any key it omits with the shipping
default.

| Flag | Ships | Why |
|---|---|---|
| `regionizedTicking` | **on** | Prerequisite. Sections through the engine, still serial. |
| `partitionedTicking` | **on** | Prerequisite. Grouped by region, still serial, still the server thread. |
| `ownerMailRouting` | **on** | Verified in the live soak: 234 routed, 234 drained, 0 inline fallback. |
| `blockEntitySharding` | **on** | The intra-region lever, and the only one that does anything on a one-region world. A tail win. |
| `parallelRegions` | **on** | The 2.4–2.8x. Was off until hazards 21 and 22 were fixed. |
| `entitySharding` | off | RFC-0008 §1: `aiStep` calls `pushEntities` every tick, writing motion directly to colliding neighbours. Round-robin sharding races adjacent mobs. Do not enable. |
| `legacyLane` | off | Changes *when* unverified mods tick — a second variable. |
| `activationScheduling` | off | A real win on mob-heavy packs, but it is not multithreading: it makes distant mobs think less often, a visible behaviour change. Enable separately if you want it. |

**The shipped config was soaked, not assumed.** With `parallelRegions` on:

- **The world that crashed 3-for-3 now boots clean 3-for-3** — `increment 5
  parallel`, 5 regions, 0 unmapped units, 0 domain trips, clean shutdown — with
  hazard 21's trigger (villagers and doors) present in it.
- **A 106-round soak**: teleports across five locations, forceload add/remove
  churn, mid-run zombie and villager spawns. 9,122 partitioned sections, 16.2M
  sharded block-entity units, 22,000 mail messages routed and drained with 0
  inline fallback, **0 unmapped units, 0 domain trips, 0 exceptions**, clean
  shutdown.
- **Suite green 22/22**, `p2parallelcap` included.

So, plainly: this build gives you a measured 2.4–2.8x on multi-region worlds, a
tail-latency improvement on block-entity-heavy ones, and the profiler plus
`/weft status` to see which case you are in.

One documented behaviour change from `blockEntitySharding`: block entities in
**adjacent chunks tick in different colour passes**, so a hopper chain crossing
a chunk border can move an item on a different tick than vanilla. Totals are
conserved and results are reproducible; vanilla's exact ordering is not
(RFC-0004 §2.5). Set it to `false` if you run tick-perfect contraptions.

Back out of everything with `regionizedTicking = false` — that one flag
disables the whole stack with no behavioural residue (RFC-0003 R6). You do not
need to remove the mod.

---

## 4. Seeing your own numbers

**Your region count decides what region parallelism could ever buy you**, and
it is a property of your world, not your CPU. A region is a cluster of ticking
chunks; clusters within `mergeDistance` (8) chunks merge. One player with no
chunk loaders is **one region** — the 1.0x control above is the measurement of
exactly that, and no amount of CPU changes it. Several players spread out, or
spread-out chunk-loaded farms, is where the 2.4x lives.

```bash
/weft status
```

Live region topology, which module postures are active, shard-pass and guard
counters.

```bash
/weft report
```

The P0 profiler's breakdown — entity work, block-entity work, unparallelisable
global work — plus its estimated speedup at 2/4/8/16 workers. If it says your
tick is mostly global work, no scheduler helps, and it will tell you so.

To reproduce the benchmarks:

```bash
./gradlew :weft-neoforge:runGameTestServer -PwithNeoForge
```

Writes `weft-neoforge/run/gametest/weft-bench.json` and prints a one-line
`[weft-bench]` summary per benchmark. **Read the controls first**
(`p2_parallel_regions_control`, `p2_be_sharding_control`): if either reports
much above 1.0x, the harness is measuring phase order on your machine and the
real readings are worthless on that run — the benchmark fails itself in that
case rather than recording the number.

---

## 5. Reporting problems

This is a preview of an open workstream. Worth reporting, with `/weft status`
output attached:

- Any crash naming a worker thread, `ParallelAccess`, or a shard guard.
- A **`border chunk reads` count that keeps climbing during normal play.** It
  should be flat once a world has finished loading in; a rising count means a
  worker is reaching for chunks it should not, which is the condition hazard 4
  was written to catch.
- Nonzero `domain trips`, or nonzero `unmapped units`. Every run so far reports
  0 for both.
- Items appearing or disappearing in a hopper or pipe chain. Conservation
  failures are the specific thing sharding could plausibly break, and none has
  been observed.
- Mobs behaving differently near region boundaries.
- MSPT going *up*. On a one-region world a small regression is expected and
  bounded; a large one is a finding.

Back out with `regionizedTicking = false`, which disables the whole stack with no
behavioural residue (RFC-0003 R6). You do not need to remove the mod.
