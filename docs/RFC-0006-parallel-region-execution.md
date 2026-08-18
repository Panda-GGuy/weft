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
| 4 | Unloaded-chunk access from a worker (vanilla would sync-load) | Would need main-thread chunk system | **Fail loud** with position forensics + counter. Ticket rings make this unreachable for in-region access; a trip is a real bug |
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

Every numbered row above cites decompiled 1.21.1/NeoForge evidence. The two
items below do not yet, so they are **candidates, not findings** — recorded
here rather than left implicit. Both are closed by the same decompile pass
increment 7 already needs (RFC-0007 §4's `ServerLevel.tick` go/no-go audit),
and neither blocks anything today because `parallelRegions` is default-OFF.

| # | Structure | Why it is open | What closes it |
|---|---|---|---|
| **19** (candidate) | **The light engine** — `ThreadedLevelLightEngine` and the path from a worker-side block write to a light-update enqueue | This RFC cleared it by assertion, not by the per-structure decompile every numbered row above rests on. The assertion covers the *enqueue* being a mailbox; it does not establish that the pre-enqueue bookkeeping between `LevelChunk.setBlockState` and that mailbox is free of shared mutable state, nor that any batching buffer on the way in is thread-safe. Worker-side block mutation is exactly what §2's model introduces | Decompile the write→enqueue path for 1.21.1: is every structure touched between a worker's `setBlockState` and the mailbox either per-call, per-thread, or synchronized? If not, it is a numbered hazard with a strategy. If yes, re-clear it *with* the evidence |
| **20** (candidate) | **A neighbor replacing the chunk system** under hazards 1–4 | Hazards 1–4's strategy is specifically "resolve via `getVisibleChunkIfPresent` → `ChunkHolder.getChunkIfPresent(FULL)`, because the visible-chunk map is a volatile snapshot the parked main thread isn't mutating." That argument is about **vanilla `ServerChunkCache` internals**. Moonrise ports a chunk-system rewrite (its README lists "Chunk system rewrite" among the Paper patches it carries) and is co-installable on NeoForge 1.21.1. If it replaces those internals, the safety argument does not transfer, and the failure modes are hazard 1's deadlock or hazard 3's silent wrongness | Either establish that Weft's worker read path is expressed against a surface Moonrise preserves, or make `moonrise` + `parallelRegions` an R7 matrix cell with a posture. Until then no P2 posture is seeded for `moonrise` (RFC-0003 §3) |

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
added to the default-ON exit criteria**: `parallelRegions` does not flip
default-ON with either still open. Exit criteria to default-ON remain: the
full parity suite green at declared classes, chaos + R7 green, and the
Create/AE2 soak clean under the flag.
