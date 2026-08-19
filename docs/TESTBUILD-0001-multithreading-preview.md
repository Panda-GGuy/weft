# TESTBUILD-0001 — Multithreading preview

**Status:** preview build, not a release. P2 is still open.
**Date:** 2026-08-18
**Build:** `feat/ws7-observability-exporter` + the P2 throughput gates
**Target:** NeoForge 21.1.248 / Minecraft 1.21.1

Two things happened to get this build, and they point in opposite directions.

**Region parallelism was measured for the first time, and it is fast:
2.43–2.63x full-tick MSPT, 3.33–3.48x on the entity section**, reproduced
across four runs with a negative control that reads ~1.0x.

**Then it was run on a live server for the first time, and it crashed twice in
five minutes.** One of those is fixed here. The other is open, and it means
`parallelRegions` cannot be recommended for a world you care about — not even
as an opt-in.

So this document has to do two jobs: report a real number, and be straight that
you cannot yet have it. The shipped config
(`testbuild/weft-common.toml`) reflects the second half.

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

| | run 1 | run 2 | run 3 | run 4 |
|---|---|---|---|---|
| Entity section, serial | 23.16 ms | 22.32 ms | 22.41 ms | 22.10 ms |
| Entity section, parallel | 6.81 ms | 6.66 ms | 6.43 ms | 6.63 ms |
| **Entity section** | **3.40x** | **3.35x** | **3.48x** | **3.33x** |
| Section p95 | — | — | 3.31x | 3.29x |
| **Full-tick MSPT** | **2.43x** | **2.47x** | **2.63x** | **2.44x** |

And the reading that makes those admissible — the same harness, the same 1760
mobs, packed into **one** region, where the fan-out cannot engage because
`runBuckets` needs two buckets:

| Negative control (1 region) | run 1 | run 2 | run 3 | run 4 |
|---|---|---|---|---|
| **Ratio** | **1.03x** | **1.04x** | **1.02x** | **0.98x** |

That ~1.0x is the point. It says the harness does not manufacture a win from a
flag flip — which is exactly what P2's retracted 1.59x did.

**Two numbers, two audiences.** 3.4x is what region parallelism does to the
work it owns. 2.4x is what the tick as a whole did on this rig, Amdahl-bounded
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

## 2. Why you cannot turn the fast one on yet

The 22-test gametest suite was green, twice back to back, with
`parallelRegions` on. Then it was run on an actual server — a forceloaded
4-island world, 560 mobs, a player joining and moving — and it crashed twice in
about five minutes.

**RFC-0006 hazard 21 — villager opens a door. FIXED in this build.**
`ServerLevel.sendBlockUpdated` iterates `navigatingMobs`, a *level-wide* set of
every mob in every region, while other regions' buckets add to and remove from
it. fastutil answers with an NPE. Worse, the loop then calls `recomputePath()`
on mobs belonging to other regions while those regions are ticking them — a
cross-region write, which a lock would have hidden rather than fixed. Fixed by
deferring the call to the section barrier, hazard 14's existing idiom.

**RFC-0006 hazard 22 — a player walks away from spawn. OPEN.**
A region worker that reads a chunk whose status is changing hits Weft's
fail-loud guard and takes the server down:

```
Weft region worker requires chunk [-3, 5] at status minecraft:full
but it is not loaded/complete (RFC-0006 hazard 4: ...)
```

The guard is right. What was wrong is hazard 4's premise — "ticket rings make
this unreachable" — which assumed chunk status holds still. It does not: it
changes on world load-in, player movement, teleports and forceload edits, while
sections are mid-flight. This reproduced deterministically on three consecutive
boots (a different chunk each time) and again from nothing more exotic than a
player teleporting.

Fixing it needs a region-readiness invariant that does not exist yet — a bucket
may fan out only when its region *and its read border* are live, which is what
Folia's region model provides and `RegionTopology` does not. Improvised
alternatives were considered and rejected as unsound: a lock (doesn't address
the status transition), returning `null` from `getChunk(load=true)` (breaks
vanilla's non-null contract, moving the crash somewhere unpredictable),
re-running the entity (its tick has already half-applied), and a "quiesce" gate
(`ChunkMap.hasWork()` includes `distanceManager.hasTickets()`, true on every
live server). It gets its own RFC.

### The part worth keeping

Both bugs were found by five minutes of live play, after two green suite runs.
The rigs are synthetic in exactly the ways that hid them: they use zombies and
passive mobs, so no `Brain`-based AI ever opened a door; and they forceload a
fixed grid and never move a player, so chunk status never changes under a
running section. Every rig proved its own mechanism and succeeded — while
sharing an unstated assumption that the world holds still.

A live-server soak is now an exit criterion in RFC-0006 §5 alongside the parity
suite, not after it.

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
| `blockEntitySharding` | **on** | The one real multithreaded lever that is stable today — see the soak below. |
| `parallelRegions` | **off** | The 2.4x. Blocked by hazard 22. |
| `entitySharding` | off | RFC-0008 §1: `aiStep` calls `pushEntities` every tick, writing motion directly to colliding neighbours. Round-robin sharding races adjacent mobs. Do not enable. |
| `legacyLane` | off | Changes *when* unverified mods tick — a second variable. |
| `activationScheduling` | off | A real win on mob-heavy packs, but it is not multithreading: it makes distant mobs think less often, a visible behaviour change. Enable separately if you want it. |

**The shipped config was soaked, not assumed.** Same live server, same
scenario that killed `parallelRegions`: 5 regions, 4096 hoppers, a joining and
moving player, 12 teleports across four islands and spawn. Result: **9,208,206
block-entity units sharded across 2247 sections, 8988 colour passes, 0 domain
guard trips, 0 hazard-4 trips, 0 exceptions, clean shutdown and save.** That it
survives where region parallelism does not is consistent with RFC-0008 §2's
central argument — block entities do not move, so their per-tick reach stays
inside their own chunk domain, which the shard guard enforces and reports on.

So, plainly: this build gives you a verified-stable tail-latency improvement on
block-entity-heavy worlds, the profiler and `/weft status` to see your own
tick's shape, and a measured 2.4x that is sitting behind one open hazard.

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

## 5. If you do test `parallelRegions` anyway

In a throwaway world, knowing it will fall over:

```toml
parallelRegions = true
```

Reportable, with `/weft status` attached:

- Any crash naming a worker thread, `ParallelAccess`, or a shard guard **other
  than** the hazard-4 message above — that one is known.
- Items appearing or disappearing in a hopper or pipe chain. Conservation
  failures are the specific thing sharding could plausibly break, and none has
  been observed.
- Mobs behaving differently near region boundaries.
- Nonzero `domain trips` in `/weft status`. Every run so far reports 0.
