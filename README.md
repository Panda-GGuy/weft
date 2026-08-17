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
authority for everything in this repo.

## Modules

| Module | What | Minecraft deps |
|---|---|---|
| `weft-engine` | Scheduler, regions, mailboxes, guards, graph scheduler | **None** (enforced at build) |
| `weft-api` | Annotations + interfaces mods target (`@WeftSafe`, `RegionScheduler`, graph API) | **None** (enforced) |
| `weft-sandbox` | Mod tier classification, legacy lane | None (pure parts) |
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
