# NOTE-0001: P1 async services — design decisions and findings

**Status:** Implemented (shadow mode) as of 2026-08-16
**Supplements:** RFC-0001 §11 (P1), §2 (Pufferfish/Petal row)
**Rule:** RFC-0001 stays authoritative; this note records the concrete design
chosen for P1 and the evidence gathered, for eventual folding into RFC draft 2.

## The service execution model

`AsyncService<I, R>` (weft-api) + `AsyncServiceRunner` (weft-engine) implement
the service-side mirror of the graph layer's snapshot → compute → commit:

- **Snapshot:** the owner thread captures an immutable input at a safe point
  (tick end). Compute never touches live world state.
- **Coalescing single-flight:** `refresh()` never blocks beyond a CAS; at most
  one compute runs; mid-compute inputs replace the pending slot (latest wins).
  A slow service can never queue a backlog behind the tick.
- **Atomic publish:** consumers read the last completed result and accept one
  tick (or more, under load) of staleness — the same latency vanilla exhibits
  for most cross-chunk observation (RFC §5.2 argument, reused).
- **Failure isolation:** a throwing compute keeps the previous published
  result; failures are counted and surfaced, never propagated into the tick.

## First consumer: spawn-density scanning (shadow mode)

Vanilla's `NaturalSpawner.createState` walks every entity every tick on the
server thread. The service recomputes the same category counts off-thread and
diffs against vanilla's authoritative result every tick (`/weft services`).

Two rules that MUST be mirrored exactly (cross-checked against the decompiled
1.21.1 source after live parity data exposed a systematic undercount):

1. Classification is NeoForge's `entity.getClassification(true)` — mods can
   override how entities count toward caps. Not `getType().getCategory()`.
2. An entity only counts if its chunk resolves as a full chunk (vanilla's
   `ChunkGetter.query` gate).

### Evidence collected (single-player + a Create/AE2 instance)

- Parity: 100% clean ticks in steady state; every mismatch traced to the
  designed one-tick staleness (chunk-load churn; a 65k mass-spawn burst
  converged the next tick). Modded dimensions (ae2:spatial_storage) are
  covered automatically and hold 100%.
- Cost: capture ~30–200 µs/tick normally; compute ~20–90 µs off-thread;
  vanilla's own scan (timed via `NaturalSpawnerMixin` into the P0 report as
  `minecraft:natural_spawner/create_state`) measured at ~4.5% of an
  animal-farm tick — the prize for going authoritative.
- Scaling limit found: the tick-end capture is O(all entities including
  MISC); 65k dropped items pushed it to 16 ms. Hence the census below.

## Incremental census (authoritative-flip prerequisite)

`EntityCensus` (weft-engine) maintains the counts from entity add/remove/
chunk-crossing events — O(changes) per tick. Known hole: **persistence flips
(taming, name-tagging) fire no event**, so the census drifts. Mitigation:
`reconcile()` against a periodic full scan (default every 200 ticks) repairs
and *reports* drift (missing/stale/moved). The flip gate is sustained
near-zero drift in real play, surfaced live in `/weft services`.

## Hard-won correctness lessons (general Weft rules)

1. **Client thread intrusion:** in single player the client level ticks its
   block entities through the same `LevelChunk$BoundTickingBlockEntity` our
   mixin hooks. Any shared mutable hook state must be owner-thread-confined
   (the profiler crash: corrupted `ArrayDeque` after client-thread pushes).
   This generalizes: every future hook must assume it can be reached from
   non-server threads.
2. **Report text is a protocol:** chat consumers split on `\n` (`%n` leaks
   `\r` on Windows) and `§` is a formatting prefix — engine-produced text is
   bare-newline ASCII by contract, with a regression test.

## Open items toward the authoritative flip

- `LocalMobCapCalculator` construction touches `ChunkMap` (player-range
  queries): rebuild it on-thread from the census per-chunk counts (cheap:
  chunks-with-mobs, not entities), or snapshot player positions into the
  service input. Decide when flipping.
- `PotentialCalculator` (biome mob-spawn costs) is position-sensitive and only
  applies to rare entity types (e.g. soul-sand-valley skeletons); plan is a
  registry scan at startup for affected types, computed on-thread over that
  (usually tiny) subset.
- Second service target: async mob pathfinding — per-request rather than
  per-tick-refresh; validates the API shape against a harder workload.
