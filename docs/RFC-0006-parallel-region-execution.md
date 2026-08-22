# RFC-0006: Parallel region execution — the shared-structure audit and the E1 increment

Status: ACCEPTED (increment 5 in progress) · 2026-08-17
Depends on: RFC-0001 §4 (regions), RFC-0005 (parity ladder), P2 increments 1–4

## 1. Problem

P2 increments 1–4 built the seams: tick ownership (1), the real chunk→region
topology (2), the legacy lane (3), and partitioned execution in canonical
region order with real region ids — still serial (4). What remains for class
E1 is running those buckets **concurrently on workers**. Vanilla's tick path
was written for one thread; this RFC is the evidence-based audit of every
shared mutable structure the entity and block-entity sections touch, and the
increment plan that makes concurrent buckets safe *without changing what any
single bucket computes*.

Method note: every hazard below was verified against the decompiled
1.21.1/NeoForge sources (not folklore, not Folia's changelog). Line numbers
are from the NeoForge-patched decompile.

## 2. The execution model (unchanged macro-order)

Fan-out happens **inside the vanilla section, with a barrier**: the server
thread reaches the entity (or BE) section, buckets are collected exactly as
in increment 4, buckets run on the engine pool under REGION contexts, and
the server thread waits at the barrier before vanilla continues. Vanilla's
macro-order — what happens before and after each section — is untouched.
Consequences:

- While a section runs, **only region workers touch simulation state**, and
  each bucket touches (by the §4.2 mergeDistance invariant) only its own
  region's world-state. Shared *engine-level* structures are the entire
  hazard surface — hence this audit.
- Outside sections, the server thread owns everything, exactly as today. So
  async-service mail (WS-2 paths, topology events) **keeps applying at
  INGEST on the server thread** — with barriered sections this *is* owner
  delivery. Free-running regions (no barrier) would need true mailbox
  rerouting; that is explicitly out of scope for this increment.
- Single-bucket sections (solo play: one region) take the increment-4
  serial path on the server thread — zero new overhead, zero new hazards.

## 3. The audit

| # | Structure (evidence) | Failure under concurrent buckets | Strategy (increment 5) |
|---|---|---|---|
| 1 | `ServerChunkCache.getChunk` — off-main calls `supplyAsync(mainThreadProcessor).join()` | **Deadlock**: main thread is parked at our barrier, the processor never runs | Worker read path: resolve via `getVisibleChunkIfPresent` → `ChunkHolder.getChunkIfPresent(FULL)`; the visible-chunk map is a volatile snapshot the parked main thread isn't mutating |
| 2 | `ServerChunkCache` 4-slot `lastChunk` cache — mutated on every main-thread lookup | Torn (pos, status, chunk) triples if workers shared it | Worker path bypasses the cache entirely (no read, no store) |
| 3 | `getChunkNow` off-main returns `null` | **Silent wrongness**: loaded chunks report as unloaded to workers | Same worker read path |
| 4 | Unloaded-chunk access from a worker (vanilla would sync-load) | Would need main-thread chunk system | **Fail loud** with position forensics + counter. ~~Ticket rings make this unreachable for in-region access; a trip is a real bug~~ — **the premise is false; see hazard 22.** The guard is correct and the strategy stands; what was wrong is the claim that it is unreachable |
| 5 | `Level.random` = `LegacyRandomSource` — `ThreadingDetector` throws (lines 34/45) | Hard crash on concurrent draw | Swap server levels to `ThreadSafeLegacyRandomSource` (same LCG, atomic seed — identical sequence single-threaded, worldgen-proven). Entity-path draws are 5 cosmetic sites (sound pitch ×4, fall-particle chance) → order-canonicalized under E1, digest-invisible |
| 6 | `EntityTickList.active` — plain `Int2ObjectLinkedOpenHashMap` | Concurrent add/remove (spawn/death) corrupts | Synchronize add/remove/contains (iteration stays main-thread) |
| 7 | `EntitySectionStorage.sections` + `sectionIds` (`Long2ObjectOpenHashMap` + `LongAVLTreeSet`) | Section-create on cross-section move races every entity query's read | Synchronize mutators **and** readers (coarse, correct; uncontended lock ≈ ns — shard later if the bench says so) |
| 8 | `EntityLookup.byId`/`byUuid` — plain maps | Spawn/death registration races id/uuid reads | Synchronize all accessors |
| 9 | `PersistentEntitySectionManager` — `knownUuids`, callbacks (`onMove`/`onRemove`) | Set/map mutation from workers | Synchronize the mutating methods; per-`EntitySection` multimaps are region-confined by distance (queries reach ≤ a few chunks; regions are ≥ mergeDistance apart) — no lock |
| 10 | `CollectingNeighborUpdater` — per-level `stack`/`addedThisLayer`/`count` | Parallel block writes interleave update chains | Thread-local updater per worker (chains run to completion within a call — semantics preserved per chain) |
| 11 | `LevelTicks.schedule` — plain map + queues | Scheduled ticks from block writes race | Synchronize `schedule` (drain is main-thread, unchanged) |
| 12 | `ServerLevel.blockEvents` — `ObjectLinkedOpenHashSet` | Block-event adds race | Synchronize the add |
| 13 | `Level.blockEntityTickers`/pending lists — plain `ArrayList`s; bucket-time adds land directly (`tickingBlockEntities` is false by then) | BE-placing-BE from parallel buckets corrupts | Synchronize `addBlockEntityTicker` (+ fresh-BE pending adds) |
| 14 | `Entity.baseTick` → `handlePortal()` → `changeDimension` **immediately mid-tick** (Entity.java 442 → 2188) | Cross-dimension mutation from a worker | Defer: worker-context `changeDimension` enqueues to a post-barrier main-thread queue, returns null (vanilla's established "no travel this tick" answer; the deferred travel runs the same tick, post-barrier) |
| 15 | `ProfilerFiller` captured by the entity consumer | Corrupt spans if `/debug` profiling is active | Documented unsupported with `parallelRegions` (normal runs use `InactiveProfiler` — no-op). Weft's own profiler is already server-thread-confined (P1 lesson) |
| 16 | `LegacyLane.submit` from workers | FIFO order becomes scheduling-dependent | Submissions carry (regionId, seq); the drain sorts — deterministic §7.2 order restored |

| 17 | `LevelChunk.getBlockEntity` — mutates `ChunkAccess.blockEntities` (fastutil `Object2ObjectOpenHashMap`) and `pendingBlockEntities` (`HashMap`) **on the read path**: drops removed entries, drains pending, and creates+registers on a miss under `IMMEDIATE` | Concurrent `get` against another thread's `put` on an open-addressing table returns `null` for a present key — a block entity vanishes mid-tick | Per-`LevelChunk` lock over the mutators **and** the reader (`LevelChunkBlockEntitySyncMixin`) |

**Hazard 17 was missed by this audit's first pass and is recorded here as a
correction** (added 2026-08-17, after increment 5 had merged). The audit
below dismissed "per-chunk state … BE maps" as region-confined, which is
true of *ownership* but not of *thread-safety*: the map is only ever touched
by its own region, yet `getBlockEntity` is a **mutating** call and vanilla's
own tick path invokes it constantly.

| 18 | `Level.getBlockEntity` — **returns `null` outright when called off the server thread** (`!isClientSide && Thread.currentThread() != this.thread ? null : …`) | **Silent wrongness**: every neighbour lookup from a worker sees an empty world. Vanilla hoppers hit this every tick via NeoForge's `VanillaInventoryCodeHooks`, which turns the null into `new InvWrapper(null)` and throws on `getSlots()` | Region/shard workers take the real lookup (`LevelWorkerBlockEntityMixin`); non-worker off-thread callers keep vanilla's null |

| 19..20 | *(candidates — see §3.1)* | | |

| 21 | `ServerLevel.sendBlockUpdated` iterates `navigatingMobs` (ServerLevel:1078), a level-wide plain `ObjectOpenHashSet<Mob>` (ServerLevel:193) holding **every mob in the level, across every region**; `onTrackingStart`/`onTrackingEnd` add and remove from it (ServerLevel:1757/1784) on spawn, death and chunk load. The loop then calls `recomputePath()` on the mobs it selected | **Two races.** (a) Iterate-versus-mutate: any worker's block change iterates the set while another region's bucket adds or removes a mob, and fastutil answers with `NullPointerException: … "this.wrapped" is null` in `ObjectOpenHashSet$SetIterator.next`. Vanilla's guard here, `isUpdatingNavigations`, is a recursion check that assumes one thread. (b) Cross-region write: the loop mutates the `PathNavigation` of a mob in region B while region B's bucket is ticking it — forbidden outright by §2 | **Defer** (hazard 14's idiom): on a region worker the whole call enqueues to the section-end queue and runs on the server thread after the barrier, same tick, where the set has no concurrent mutator and touching any region's mobs is legal. Costs nothing observable — `sendBlockUpdated`'s client-visible half is `chunkSource.blockChanged`, and `ServerChunkCache.tick` broadcasts at ServerLevel:379, *before* the entity section at ServerLevel:420, so block changes made during entity ticking already broadcast on the following tick in unmodified vanilla. `ServerLevelNavigationDeferMixin` |

| **22** | **Hazard 4's premise, and the worker read path's dependency on a parked thread.** `ChunkMap.prepareEntityTickingChunk` (ChunkMap:364) is `getChunkRangeFuture(holder, radius 2, ChunkStatus.FULL)`: vanilla guarantees that before a chunk ticks entities, every chunk within radius 2 is *generated* to `ChunkStatus.FULL`. But `ChunkStatus.FULL` (generation) and `FullChunkStatus.FULL` (promotion) are different things reached by different futures, and hazards 1–4's read path asked about **promotion** — whose continuation is scheduled onto `ChunkMap.mainThreadMailbox` (ChunkMap:704) | **Hard crash, deterministic.** During a fanned-out section the main thread is parked at our barrier and cannot drain that mailbox, so a border chunk's `fullChunkFuture` stays incomplete for exactly as long as the section runs. Every read into the border ring answered null and hazard 4's guard turned that into a crash: three consecutive boots, three different chunks, every one a short-reach read at a chunk boundary (`Entity.updateFluidHeightAndDoFluidPushing` → `getFluidState` ×2, `Mob.serverAiStep` → `getBlockState`). **This is hazard 1 wearing a different hat** — a dependency on a future only the parked main thread can complete — which is why it was deterministic rather than racy | **FIXED.** Answer the question vanilla's invariant actually guarantees: when no promoted view exists, fall back to the generated-`FULL` view if it is a real `LevelChunk`. That is the same object and the same block states the main thread would hand a vanilla caller reading the same border chunk — `replaceProtoChunk` (GenerationChunkHolder:90) rewrites every status future *except* the last, so the `FULL` future holds the `LevelChunk` and not an `ImposterProtoChunk`. Scoped to `getChunk(..., load=true)` **only**: `getChunkNow` is vanilla's "only if loaded" probe, whose null is a loadedness answer, and it never crashed so it has nothing to be rescued from. Beyond radius 2 a null still fails loud, and still should — that is a read vanilla would have had to sync-load, the real bug hazard 4 was written to catch. Border reads are counted and surfaced in `/weft status` |

| **23** | **Nested `runOwnedParallel` into the same fixed-size pool.** There are exactly two call sites: `RegionizedTicking.runBuckets` (region buckets) and `BlockEntityShards.runColoured` (colour-pass tasks). With `parallelRegions` **and** `blockEntitySharding` both on, and a level with >=2 region buckets and a region over the 64-unit gate, the outer fan-out puts a bucket body **on a pool worker**, and that body submits the inner colour passes back into the same pool and blocks on `Future.get()` | **Permanent hang.** Not a crash, so no crash report is produced and the client only reports a dead internal server. Server thread parked in `WeftScheduler.awaitAll` under `tickBlockEntitySectionOwned`; every engine worker idle in `ForkJoinPool.awaitWork`; the **same awaited task object** across two jstack dumps taken minutes apart. Found on a live single-player world (Create + AE2 + Chunky) and then reproduced deterministically in the suite | **FIXED** by removing the nesting rather than trying to survive it: sharding stands down whenever the section fans out (`sharded && !(parallel && buckets.size() >= 2)`). This costs nothing, because RFC-0008 §1 already scopes sharding as "the solo-play lever, where region-level parallelism is a no-op because the world is one region" — if two regions are already fanning out, the worker threads are in use and intra-region sharding has nothing left to win. Flattening the colour passes into the outer barrier would let both engage at once; that is a throughput feature this design does not claim, and a bigger change than a hang deserves |

### Hazard 23: what is proven, and what is not

**Proven.** The combination hangs; the trigger is `parallelRegions` +
`blockEntitySharding` with >=2 region buckets and a region over the sharding
gate; and removing the nesting fixes it. That last one is not an inference — the
new `p2combined` gate passes with the one-line fix and **hangs the entire
gametest server without it**, twice, with a server-thread stack identical to the
live report.

**Not proven.** *Why the JDK never runs the awaited task.* The obvious story is
pool starvation — workers blocked in nested joins with none left to run the inner
tasks — and it is wrong, or at least incomplete: in both the live hang and the
lab repro **every worker is idle in `awaitWork`, not blocked in `awaitDone`**.
Nothing is running, nothing is queued, and the coordinator waits on a task no
thread is contending for. A submission-signalling or task-loss interaction is the
remaining candidate and it is not yet demonstrated.

That distinction matters beyond bookkeeping. If the real mechanism is anything
other than "not enough workers", then **any** nested `runOwnedParallel` is
suspect, and the next increment that adds one — entity sharding is the obvious
candidate (RFC-0008 §5) — cannot assume a bigger pool makes it safe. The
standing rule until it is understood: **one level of submission per section.**

### Hazard 23 and the third repetition of the same test-design gap

`p2parallelbench` calls `setBlockEntitySharding(false)`. `p2shardbench` calls
`setParallel(false)`. Each isolates its own mechanism so its number means
something — correct benchmarking discipline, and the reason both stayed green
through a deadlock that any run with both flags on would have hit immediately.
Meanwhile `TESTBUILD-0001` shipped a config turning on both.

This is the third instance in one day of the same shape — hazard 21 (no rig had
`Brain` AI), hazard 22 (no rig changed chunk status mid-section), hazard 23 (no
rig set two flags at once). The pattern is not "our rigs are bad"; each was
built to prove one thing and did. The pattern is that **the untested case is the
interaction**, and the shipped configuration is itself a claim that needs a gate.
`p2combined` is that gate, and any future flag pairing the product ships
together needs one too.

| **24** | **Chunk residency is not guaranteed, only chunk promotion was.** `ChunkMap.prepareEntityTickingChunk` promises radius-2 at `ChunkStatus.FULL` *at the moment of promotion*; nothing keeps those neighbours resident afterwards. A ticket released by a teleport, or a pre-generator's sweep moving on, evicts them while the owning chunk keeps ticking | **Hard crash.** A ticking unit's short-reach read lands in an absent chunk, hazard 22's border fallback finds no generated view either, and the hazard 4 guard fires. Found by a player pressing teleport: a `minecraft:vault` at world x=2960 — the **westernmost block of chunk [185,188]** — ran `setChanged` → `updateNeighbourForOutputSignal` → `getBlockState` one block west into chunk [184,188], which had just been evicted. Vanilla survives this because `getChunk(load=true)` simply loads it again; a worker may not (hazard 1) | **FIXED** by the readiness gate hazard 22 deferred: a unit reaches a worker only when its **radius-1 read neighbourhood is presently live**, probed with `getChunkNow` (vanilla's own method on the server thread — a visible-map lookup, no load, no promotion) and cached per chunk per section, so the cost is nine lookups per *chunk* rather than per unit. Everything else goes to the existing serial tail, where a lazy load is legal. Radius 1 because that is what the failure reaches; beyond it the guard still fails loud, which is how the next gap announces itself |

### Hazard 24 retires a hedge, and the counter that caught a second one

Hazard 22's write-up named this fix and then declined it:

> The fix is a region-readiness/border guarantee (a bucket may fan out only when
> its region *and its read border* are live) … Rejected as unsound: … Needs its
> own RFC.

That was defensible when every observed failure was a chunk mid-promotion, which
the cheaper border fallback covers. It stopped being defensible the moment a
chunk turned out to be *absent* rather than *unpromoted*. The invariant is now
implemented, scoped to radius 1 rather than the full border guarantee — narrower
than the original proposal, and matched to the reach the crashes actually
demonstrate.

**And the gate's own first version broke a different signal.** Deferred units
were routed to the serial tail, and the tail's accounting billed everything in it
to `unmappedUnits` — an invariant that is supposed to stay 0, because a ticking
chunk with no topology region is a bug. An eviction soak promptly reported
**14,647 "unmapped" units**, which would have made the partition gate's
`unmapped != 0` check meaningless on any world with chunk churn. Both populations
are now counted at classification instead of by tail size: `unmappedUnits` stays
0, and `unreadyUnits` carries the deferrals, where non-zero is expected and
informative rather than alarming.

**Verification.** 40 rounds of deliberate eviction churn — forceload a 6×6 chunk
block, place hopper/chest/vault stacks on the westernmost block of its second
chunk column, then unload the first column underneath them while sections run —
across 8 regions with the fan-out engaged: **33,754 units deferred, 0 guard
trips, 0 exceptions, 0 unmapped units**, clean shutdown. Suite 23/23 twice back
to back, and the region-parallelism benchmark is unmoved (3.62x–3.87x section,
2.70x–2.76x MSPT), because on a world whose chunks are not churning the readiness
cache hits immediately.

### A WS-7 metric that measured region ids

Not a hazard, but found in the same session and worth recording because of *how*.
`weft_block_entities_ticking` was built by summing
`RegionizedTicking.lastBlockEntityPartition()` — an array of **region ids** — and
publishing the total as a block-entity count. On a three-region world it read
`6`, i.e. ids 1+2+3, while the level ticked thousands. The loop variable was
named `units`, which is how a type-correct `long[]` carried the wrong meaning
past review. The partitioner now reports its real captured unit count.

The general lesson matches hazard 23's: it was noticed only because someone
looked at the number on a real world and found it absurd. Both single-flag
benchmarks and 23 gametests never read it.

| **25** | **`Brain` AI reads remembered POSITIONS, at arbitrary range.** A `Brain` holds `HOME`, `JOB_SITE`, `MEETING_POINT`, `LIKED_NOTEBLOCK` and friends, and behaviours read the world at those positions directly rather than near the mob. `SleepInBed.checkExtraStartConditions` (SleepInBed:45) does a `getBlockState` at the villager's remembered bed — wherever it last slept | **Hard crash**, and one that hazard 24's fix cannot reach: that gate proves a *radius-1 neighbourhood* is live, the right bound for a block entity's neighbour-signal path and **no bound at all on a memory lookup**. The remembered chunk had been released by churn. Distinct class, not a tuning failure — *spatial gates cannot bound memory reads* | **FIXED** categorically rather than spatially: `MemoryReachEntities` lists the vanilla `Brain` users holding position memories, and the entity section routes them to the serial tail (server thread, where a lazy load is legal). Mirrors `WideReachBlockEntities`. Type-based rather than inspecting live memory sets, because those change at runtime — a mob safe to bucket on one tick would be unsafe the next — and the check would sit on the hot path. **Cost stated plainly:** listed types tick serially, so a village-heavy world loses the parallel win on villagers (2.7–4.7% of attributed cost on the motivating profile), visible in `unreadyUnits` rather than hidden |

### Hazard 25, and the first one the lab found by itself

Every hazard before this was found by a person playing. **This one was found by
the soak**, fifteen minutes into the first run that combined real mods, Brain
mobs and chunk churn in one world — which is the argument for
`TESTING-0001`'s lab profile, made by the lab rather than about it.

It also closes a loop opened deliberately. Hazard 24's note said the readiness
gate was scoped to radius 1 because that is what the observed failures reached,
and that **"beyond it the guard still fails loud, which is how the next gap will
announce itself rather than corrupt something quietly."** The next gap announced
itself the same evening, in exactly that way: fail-loud, with the offending call
path in the stack. Scoping a fix to the evidence and leaving the guard armed
beyond it worked as intended.

**Verification.** The soak that crashed now runs to completion clean — 150
cycles, 38,178 partitioned sections, 10 regions, 0 unmapped units, 0 domain
trips, 0 exceptions, 52,692 units deferred. The `p2memoryreach` gate asserts the
mechanism with two regions actually fanning out, and **fails without the fix**
(`Only 0 units deferred, expected at least 480`). Suite 24/24.

### Hazard 22's fix, and the counter that caught the first attempt

The first version of the fix put the fallback in the shared resolve path, so
`getChunkNow` started answering with generated-but-unloaded chunks — turning a
null that callers read as "not loaded" into a lie. It was visible immediately:
**8,260,234 border reads in thirty seconds** on a rig whose actual border ring
is a few hundred chunks. Scoping the fallback to the `load=true` path dropped
that to 21,712, a 380x reduction. The counter existed because hazard 4 used to
make this case a hard crash and the concession replacing it had to stay
visible; it paid for itself within one run.

The counter also carries the fix's own evidence. On a steady-state world it
stays flat — through 15 teleports and 106 rounds of forceload add/remove churn
it did not move at all, because once promotion completes the promoted view is
there and the fallback is never reached. It runs high only during **boot**
(~400k–670k across the first seconds on the repro world), which is exactly the
predicted shape: 560 zombies and 24 villagers all reading borders while a
promotion backlog sits behind a main thread that parks once per section. A
count that keeps climbing in steady state would mean a worker reaching
somewhere it should not, i.e. the bug hazard 4 was written to catch, and that
now shows up as a number instead of an outage.

**Verification.** The world that crashed 3-for-3 now boots clean 3-for-3
(`increment 5 parallel`, 5 regions, 0 unmapped units, 0 domain trips, clean
shutdown), with the hazard 21 trigger — villagers and doors — present in it.
A 106-round soak of teleports, forceload churn and mid-run zombie/villager
spawns holds 9,122 partitioned sections and 16.2M sharded block-entity units
with zero guard trips. Suite green 22/22.

### How hazards 21 and 22 were found: nobody had run a live soak

Both came out of the **first live-server soak with `parallelRegions` on** —
about five minutes of it, on 2026-08-18. Neither is exotic. Hazard 21 is a
villager opening a door; hazard 22 is a player walking away from spawn.

The 22-test gametest suite was green, twice back to back, through both of
them. That is the finding behind the finding. The rigs are synthetic in
precisely the ways that hid these: they spawn zombies and passive mobs, so no
`Brain`-based AI ever opened a door (hazard 21); and they forceload a fixed
grid up front and never move a player, so chunk status never changes under a
running section (hazard 22). Every rig was built to prove a specific mechanism
and each one succeeded — while sharing an assumption none of them stated, that
the world holds still.

This is hazard 18's shape for the third time (§3's note, then §3.1's), and it
should stop being a surprise: **a structure cleared by reasoning rather than by
a rig that exercises it is where the next hazard lives** — and so is a rig
whose world never changes shape. The live soak belongs in the exit criteria
alongside the parity suite, not after it.

### How hazard 18 was found, and why it hid for two increments

Hazard 18 is the twin of hazard 3 — the same "answers null off-main rather
than answering correctly" shape — and it crashed both `parallelRegions` and
WS-10 sharding. It is worth recording how it stayed invisible.

It is **not** a data race. It is deterministic: any block-entity tick on a
worker that asks the level about a *neighbour* gets `null`. Block entities
that hold their own reference never notice — a furnace ticker is handed its
`AbstractFurnaceBlockEntity` and never consults the level — and `p2parallel`'s
rig is furnaces and armour stands. So the gate was green while the code path
that breaks was never executed. Hoppers resolve an item-handler capability
every tick, and crashed on contact.

The diagnosis is worth keeping because three plausible theories were wrong,
each killed by direct experiment rather than argument:

- **Double chests** — the first reproducer placed chests side by side, which
  pairs them and routes through `DoubleBlockCombiner` where a null container
  is ordinary. Spacing them apart changed nothing.
- **`LevelChunk.blockEntities` corruption** (hazard 17) — a per-chunk lock
  changed nothing. Kept anyway: `getBlockEntity` really does mutate a
  fastutil open-addressing map, and hazard 18's fix is what finally lets
  several workers reach those maps at once, so 17 is now load-bearing.
- **Stale chunk instance** — switching worker reads from
  `getChunkIfPresent(FULL)` (the generation pipeline's view) to the live
  ticking chunk changed nothing. Also kept: a worker about to read
  simulation state should see the live chunk.

What settled it was instrumentation rather than reasoning: `Level.getBlockEntity`
returned null six times while `LevelChunk.getBlockEntity` returned null with the
entry present **zero** times, and a direct `chunk.getBlockEntity` retry on the
same thread succeeded — which is only possible if the null is produced *before*
the chunk is ever consulted.

The generalizable lesson: *a gate proves the code paths its rig actually
exercises, and vanilla content is not the same thing as vanilla coverage.*
`p2parallelcap` now holds this case, and it runs its rig serially first so a
future failure is attributable to concurrency rather than the rig.

Region-confined by construction (no treatment needed): per-section
`ClassInstanceMultiMap`s, per-chunk state (palettes, heightmaps, BE maps —
for *ownership*; see hazard 17 for why that did not imply thread-safety),
container contents, per-entity state, per-region interactions (damage,
pushing, targeting — nothing reaches across a ≥ mergeDistance gap in one
tick). Thread-safe by design (no treatment needed): Netty sends (per-channel
queues), the engine's own mailboxes and counters.

`ThreadedLevelLightEngine` **was** in that list, cleared on the one-line
grounds "mailbox enqueue." That clearance is withdrawn pending evidence — see
§3.1.

### 3.1 Open audit items (candidate hazards, 2026-08-18)

**Signed off 2026-08-18: hazards 19 and 20 are accepted as blocking exit
criteria for `parallelRegions` going default-ON.** What is provisional about
them is the *finding*, not the *gate* — the gate is a decision and it is made.
Neither may be closed by re-asserting the reasoning that opened it; each closes
only on the evidence named in its own row.

Every numbered row above cites decompiled 1.21.1/NeoForge evidence. The two
items below do not yet, so they are **candidates, not findings** — recorded
here rather than left implicit. Both are closed by the same decompile pass
increment 7 already needs (RFC-0007 §4's `ServerLevel.tick` go/no-go audit),
and neither blocks anything today because `parallelRegions` is default-OFF.

| # | Structure | Why it is open | What closes it |
|---|---|---|---|
| **19** (candidate) | **The light engine** — `ThreadedLevelLightEngine` and the path from a worker-side block write to a light-update enqueue | This RFC cleared it by assertion, not by the per-structure decompile every numbered row above rests on. The assertion covers the *enqueue* being a mailbox; it does not establish that the pre-enqueue bookkeeping between `LevelChunk.setBlockState` and that mailbox is free of shared mutable state, nor that any batching buffer on the way in is thread-safe. Worker-side block mutation is exactly what §2's model introduces | Decompile the write→enqueue path for 1.21.1: is every structure touched between a worker's `setBlockState` and the mailbox either per-call, per-thread, or synchronized? If not, it is a numbered hazard with a strategy. If yes, re-clear it *with* the evidence |
| **20** (~~candidate~~ **CONFIRMED 2026-08-20**, issue #16) | **A neighbor replacing the chunk system** under hazards 1–4 | Hazards 1–4's strategy is specifically "resolve via `getVisibleChunkIfPresent` → `ChunkHolder.getChunkIfPresent(FULL)`, because the visible-chunk map is a volatile snapshot the parked main thread isn't mutating." That argument is about **vanilla `ServerChunkCache` internals**. Moonrise ports a chunk-system rewrite and is co-installable on NeoForge 1.21.1. **This row stopped being hypothetical:** a field boot with `parallelRegions` active crashed the tick loop with `Cannot execute main thread task off-main` — Moonrise's `TickThread.ensureTickThread` firing from a Weft ForkJoin worker via `ServerChunkCache.pollTask`/`midTick` during an entity section. Note the failure mode was neither predicted branch: not hazard 1's deadlock nor hazard 3's silent wrongness, but the neighbor's *own* fail-loud assertion catching us first — which is the good outcome, and the reason it was findable in one boot | **Closed by the second route, not the first.** `moonrise` is now an R7 matrix cell (RFC-0003 §3.2) that boots the full parallel stack in config and asserts it is disarmed, with the registry posture yielding `regionized_ticking`/`entity_sharding`/`legacy_lane`. The *first* route — expressing Weft's worker read path against a surface Moonrise preserves — remains unbuilt, so co-enabling stays unsupported rather than merely untested |

Why both surfaced now, and why they are worth the row: this is the same shape
as hazard 18 (`Level.getBlockEntity` returning `null` off-thread), which hid
for two increments because the `p2parallel` rig held block entities that never
asked the level about a neighbour. A structure cleared by reasoning rather than
by a rig that exercises it is exactly where the next hazard 18 lives.

External corroboration that the light-engine item is not hypothetical, though
it is **not the reason** for filing it (the audit gap stands with zero mods
installed): ScalableLux — Starlight-derived, `1.21.1` on Fabric with a NeoForge
port branch — ships a `ThreadedLevelLightEngineMixin`, i.e. it rewrites this
exact class, and its `parallelism` setting defaults to auto
(`max(1, availableProcessors() / 3)`), so its parallel light updates are on by
default rather than opt-in. Moonrise carries Starlight too. Two co-installable
neighbors modify the class this RFC waved through.

Known gaps carried forward (documented, gated OFF-by-default):
teleports *within* a level to another region's chunks (ender pearls crossing
unloaded gaps stall at borders today — vanilla behavior — and land as mail
work in a later increment); a legacy passenger on a vanilla vehicle still
ticks via the vehicle (increment-3 gap); stats/advancements from exotic
cross-region interactions (unreachable by distance; the DEV-mode soak and
guard trips are the net).

## 4. Equivalence claim (RFC-0005 E1)

- Per-region digests are **bit-identical** to serial execution: each bucket
  runs the same units, in the same order, against state only it may touch.
  The p2parallel gate holds two independent islands to control-equal end
  states with parallelism actually engaged (off-thread probe).
- Cross-region interleaving and the 5 cosmetic `level.random` draws are the
  documented order-canonicalized set. Neither is digest-visible.
- The E0 anchor (single-region parity arena) is unchanged: one bucket takes
  the serial fast path. The anchor runs with `parallelRegions` active to
  prove the new code path has zero residue at bucket-count ≤ 1.

## 5. Rollout

`parallelRegions` (default OFF) is a sub-mode of `regionized_ticking`,
requiring `partitionedTicking`; resolved by the coexistence ladder like the
rest of the module. The safety mixins live in the fail-loud config
(`weft.mixins.json`, `defaultRequire=1`): if any seam misses, boot crashes —
never a silently-unsafe parallel mode. The RNG swap and the synchronization
mixins are active regardless of the flag (identical single-threaded
semantics, uncontended-lock cost only); the worker chunk path, deferral, and
fan-out engage only with the flag. **§3.1's candidate hazards 19 and 20 are
exit criteria for default-ON (signed off 2026-08-18)**: `parallelRegions`
does not flip default-ON with either still open. **Hazard 20 is now closed**
(CONFIRMED 2026-08-20 via issue #16, closed by the R7-matrix posture route —
Moonrise co-enabling stays unsupported rather than merely untested; see §3.1's
own row for the caveat that the *first* route, a worker read path proven safe
against Moonrise's chunk system, remains unbuilt). **Hazard 19 (the light
engine) is still open** — an audit gap, not a finding, per §3.1. Exit criteria
to default-ON remain: hazard 19 closed, the
full parity suite green at declared classes, chaos + R7 green, and the
Create/AE2 soak clean under the flag. Also currently open and unresolved:
RFC-0006 hazard 24 (issue #6, worker block-entity tick reading an absent
neighbour chunk) — the underlying fix has shipped, but its regression gate
(`p2evictionchurn`) is still blocked (PR #29, draft) and #6 is the repo's sole
open issue as of this writing.

**Amended 2026-08-18 — hazard 22 found, then fixed; opt-in restored.** For a
few hours this section read "experimental, throwaway worlds only": the first
live soak crashed `parallelRegions` deterministically and no configuration made
it safe. Root-causing it (see hazard 22 above) showed it was not the missing
region-readiness invariant it first looked like, but hazard 1's problem in
disguise — a worker read path waiting on a future only the parked main thread
could complete — and that has a bounded fix. **Opt-in is usable again**, on the
evidence in hazard 22's verification note. Default-ON still waits on 19 and 20.

**And an exit criterion added 2026-08-18 after hazard 23: every combination the
product ships must have a gate.** Isolating flags is right for benchmarks and
insufficient for release confidence. `TESTBUILD-0001` ships `parallelRegions` and
`blockEntitySharding` together, and that pairing deadlocked while both
single-flag benchmarks stayed green. No configuration may be shipped as
recommended unless some gate exercises it as shipped.

**And a fourth exit criterion, from the same soak: a live-server soak.** The
list above is entirely rig-based, and rigs are what missed hazards 21 and 22.
A soak must run with a player joining, moving, teleporting and leaving, on a
world with forceloaded chunks that contain entities, with `Brain`-based mobs
(villagers) present — the three properties every existing rig lacks. Cheap to
state, and it would have caught both findings in five minutes.
