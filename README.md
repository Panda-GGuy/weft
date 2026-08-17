# Weft

**A multithreaded server engine for modded Minecraft.**

In weaving, warp threads run in parallel and the weft binds them into one
fabric. Same idea here: regions of the world tick in parallel; Weft is what
makes them one consistent simulation.

Weft replaces Minecraft's single-threaded server tick with a phased parallel
pipeline — regionized world ticking (the Folia insight) plus a first-class
**graph layer** that gives cross-chunk mod systems (energy nets, logistics,
rotational networks) a correct parallel home — while a **compat sandbox** runs
unverified mods with exact single-threaded semantics so existing mods keep
working, unmodified.

Read [RFC-0001](docs/RFC-0001-weft-architecture.md) first. It is the design
authority for everything in this repo. Design notes, decisions, findings, and
the testing playbook live on the
[project wiki](https://github.com/Panda-GGuy/weft/wiki).

## Modules

| Module | What | Minecraft deps |
|---|---|---|
| `weft-engine` | Scheduler, regions, mailboxes, guards, graph scheduler | **None** (enforced at build) |
| `weft-api` | Annotations + interfaces mods target (`@WeftSafe`, `RegionScheduler`, graph API) | **None** (enforced) |
| `weft-sandbox` | Mod tier classification, legacy lane, coexistence policy (RFC-0003) | None (pure parts) |
| `weft-services` | Engine-side services for the RFC-0002 workstreams (WS-1 activation, WS-2 pathfinding) | **None** (enforced) |
| `weft-neoforge` | The actual mod: mixins, event bus adaptation, config | NeoForge 1.21.1 |
| `weft-adapters` | Per-mod graph adapters (Create, AE2, …) | Planned (P3) |

## Building

Core (engine + api + sandbox — pure Java 21, no Minecraft):

```
./gradlew build
```

Full build including the NeoForge mod (needs maven.neoforged.net):

```
./gradlew build -PwithNeoForge
```

## Status

Pre-alpha. Current phase: **P0/P1** — engine core with a passing concurrency
test suite, plus the **P0 profiler**: install the mod on any stock server *or
single-player world* (it hooks the integrated server too) and it measures how
much of your pack's tick Weft could parallelize. See RFC-0001 §11 for the
roadmap and §12 for the honest risk register (start with the Amdahl one).

**P0 verified in-game** (2026-08-16, NeoForge 21.1.248 / MC 1.21.1): mod loads,
all mixins apply cleanly, `/weft report` prints the regionizability report and
writes `weft-report.txt`, the 60-second console summary fires, and profiling
overhead is negligible (~two `System.nanoTime()` calls per entity/BE tick).
Profiling is toggleable at runtime with `/weft profile on|off`; tunables live
in `config/weft-common.toml`.

**P1 started — first off-thread service** (same day): the spawn-density scan
runs as an `AsyncService` in **shadow mode** (vanilla stays authoritative;
we compute the same state off-thread and diff against it every tick — see
`/weft services` for live parity numbers). Stress-tested to 65k entities:
zero service failures, all parity deltas explained by the by-design one-tick
staleness, and vanilla's own scan cost is now itemized in `/weft report`
(`natural_spawner/create_state`) as the measured prize for going
authoritative later.

**RFC-0002/0003 workstreams started** (2026-08-16): every Weft optimization
module now walks the [RFC-0003](docs/RFC-0003-coexistence-policy.md)
coexistence ladder at startup — independent kill switch, known-neighbor
registry (`weft-neighbors.toml`), user force-enable/disable overrides, and a
one-glance posture table in the log and `/weft status`. First entries from
[RFC-0002](docs/RFC-0002-modernization-workstreams.md):

- **WS-8 benchmark-as-CI**: JMH suites over the engine hot paths (mailboxes,
  region merge/split, pipeline scheduling, graph commit routing, the WS-1
  decision) run nightly; the `bench` workflow records results on the
  `bench-data` branch and fails on regression beyond the noise band.
  Run locally: `./gradlew :weft-engine:jmh :weft-services:jmh`.
- **WS-1 entity activation scheduling**: mobs far from every player tick
  their sensing and goal/target selectors at reduced frequency (32/64-block
  tiers, 1/4 and 1/20 rates by default) while movement, navigation, brains,
  and despawn accounting stay per-tick. Fail-soft mixin (self-disables if it
  cannot apply), per-type overrides and exemptions in config. The engine-side
  acceptance A/B (2k passive + 500 hostile on the profiled world shape,
  `ActivationPhaseBench`) shows **72% entity-phase reduction** (634 us ->
  177 us per tick, decision cost ~12 ns/mob) — well past the >=30% bar; the
  in-game benchmark-world run (WS-8 remainder) is what's left before the
  default flips. `/weft report` now prints a **projected WS-1 savings** line
  for *your* pack: every entity sample records the interval the configured
  tiers would assign it, so the report answers "what would enabling this buy
  me" before you flip `activationScheduling = true`.
- **WS-2 async pathfinding** (the RFC-0001 P1 off-thread service): the A*
  inside `PathNavigation.createPath` runs on Weft path workers instead of
  the server thread. Node evaluation stays vanilla's own per-mob
  `PathFinder` (modded NodeEvaluators respected) over the region snapshot
  vanilla already captures on-thread; results return through the engine
  scheduler's mailbox and apply at the next tick boundary while the mob
  keeps following its previous path (no stutter, exactly-once apply —
  smoke-checked). Single-flight per mob: rapid repaths coalesce, latest
  wins. The engine-native pathfinder that takes over at P2 landed alongside
  it in `weft-services` with the WS-8 numbers to justify it: hierarchical
  chunk-level A* **30x** over flat A* on a 430-block obstructed path
  (480 us vs 14.6 ms), and a shared flow field serving a 300-mob horde
  **4.1x** cheaper than per-mob A* (10 ms vs 41 ms) even recomputing the
  flood every call. **Ships off** (`asyncPathfinding = false`) pending
  in-game acceptance on the 300-zombie stress world.

**WS-10 intra-region entity sharding started**
([RFC-0004](docs/RFC-0004-entity-sharding.md), engine side, same day): the
second parallelism axis, for the worlds where region-level parallelism
flatlines at 1.00x by construction (one player = one region). Big regions fan
their tickables out over shards — each shard a serial loop with its own
`SHARD` ownership context, pre-split deterministic RNG substream, and an
**entity effect log** (the entity-layer `CommitLog`: damage, item claims,
love mode, spawn/remove are recorded during the parallel pass and applied in
one deterministic (source, seq) order, so contested claims resolve
identically at any shard count). Engine benchmark on the profiled solo-play
shape (2000 tickables, one region): **1637 us serial vs 254 us sharded
(6.5x)**, tracked nightly by WS-8. **Ships off** (`entitySharding = false`)
per RFC-0004 §2.5 — within-tick interleaving is not vanilla's exact list
order — and the engine does not own real entity ticking until P2 anyway.

## Trying the P0 profiler locally

1. Build the jar: \`./gradlew :weft-neoforge:build -PwithNeoForge\`
   (or grab \`weft-neoforge-jar\` from the latest green Actions run).
2. Drop \`weft-neoforge/build/libs/weft-neoforge-*.jar\` into your NeoForge
   1.21.1 \`mods/\` folder — works in single player (integrated server) and on
   dedicated servers.
3. Play a minute, then run \`/weft report\` (needs op / cheats). You get the
   regionizability report in chat and \`weft-report.txt\` in the game dir:
   parallelizable fraction, hypothetical region count, estimated speedup at
   2/4/8/16 workers, and the top cost sources in your pack.

Dev workflow: \`./gradlew :weft-neoforge:runClient -PwithNeoForge\` launches a
dev client with the mod loaded.

## Design tenets

1. **Correctness is never opt-in.** Unknown mods are serialized, never guessed
   parallel. We accept "not faster yet"; we never accept "corrupts your world."
2. **Ownership, not locks.** Every piece of state has one owner; cross-owner
   work is mail or commit logs, applied at phase boundaries.
3. **The graph layer is the point.** Regionization alone breaks on exactly the
   mods people actually play. Networks get their own scheduler.
4. **Vanilla-compatible saves, always.**
