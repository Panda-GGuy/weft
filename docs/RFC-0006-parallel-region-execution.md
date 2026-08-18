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

### OPEN BUG — concurrent capability lookups (unresolved, 2026-08-17)

Hazard 17 was found while chasing a crash that it does **not** explain, and
which is still open. Stated plainly so nobody mistakes the audit for
complete:

Two regions ticking **hoppers** concurrently crash with a
`NullPointerException` inside NeoForge's
`VanillaInventoryCodeHooks.extractHook`: `ChestBlock.getContainer` is handed
`null` by `level.getBlockEntity` for a chest that demonstrably exists, and
wraps it in `new InvWrapper(null)`. Reproducer: the `p2parallelcap` gate,
which runs the identical rig **serially first** and only then enables
`parallelRegions` — the serial control passes and delivers items, so this is
concurrency, not the rig.

Eliminated so far, each by direct experiment:

- **Double chests** — stacks are spaced three blocks apart, so no pairing;
  crash unchanged.
- **`LevelChunk.blockEntities` map corruption** — per-chunk lock over the
  reader and both mutators (hazard 17's fix); crash unchanged.
- **Stale chunk instance** — worker reads switched from
  `getChunkIfPresent(FULL)` (the generation pipeline's view) to
  `getTickingChunk()` (the live chunk); crash unchanged.

Still open: NeoForge's capability resolution itself (provider caches,
`BlockCapabilityCache` invalidation), and the block-state read path
(`PalettedContainer`) that `createBlockEntity` consults. Note the outer
`getItemHandlerAt` reads the same position as a chest microseconds earlier on
the same thread, so whatever fails is not a stable property of the position.

The generalizable lesson is already earned: *a gate proves the code paths its
rig actually exercises, and vanilla content is not the same thing as vanilla
coverage.* `p2parallel`'s furnaces and armour stands hold their own block-entity
references and never ask the level for a neighbour, so nothing in the original
rig could have caught this.

Region-confined by construction (no treatment needed): per-section
`ClassInstanceMultiMap`s, per-chunk state (palettes, heightmaps, BE maps —
for *ownership*; see hazard 17 for why that did not imply thread-safety),
container contents, per-entity state, per-region interactions (damage,
pushing, targeting — nothing reaches across a ≥ mergeDistance gap in one
tick). Thread-safe by design (no treatment needed): `ThreadedLevelLightEngine`
(mailbox enqueue), Netty sends (per-channel queues), the engine's own
mailboxes and counters.

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
fan-out engage only with the flag. Exit criteria to default-ON remain: the
full parity suite green at declared classes, chaos + R7 green, and the
Create/AE2 soak clean under the flag.
