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

Pre-alpha. Current phase: **P1** — engine core with a passing concurrency test
suite; the NeoForge module runs the pipeline in telemetry mode alongside the
vanilla tick. See RFC-0001 §11 for the roadmap and §12 for the honest risk
register (start with the Amdahl one).

## Design tenets

1. **Correctness is never opt-in.** Unknown mods are serialized, never guessed
   parallel. We accept "not faster yet"; we never accept "corrupts your world."
2. **Ownership, not locks.** Every piece of state has one owner; cross-owner
   work is mail or commit logs, applied at phase boundaries.
3. **The graph layer is the point.** Regionization alone breaks on exactly the
   mods people actually play. Networks get their own scheduler.
4. **Vanilla-compatible saves, always.**
