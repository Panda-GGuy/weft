# Shared project memory

Last updated: 2026-08-20

## What Weft is
Multithreaded server engine for modded Minecraft (NeoForge 1.21.1). Regionized ticking + graph layer + legacy lane for unknown mods.

## Phase status
- P0 complete (profiler)
- P1 complete (off-thread services; async pathfinding + spawn density defaults ON)
- P2 open: many increments exist behind flags (regionized, legacy, partitioned, parallel, owner mail, BE sharding)
- P3+ not started (graph adapters, race detector, production tag)

## Default posture
Parallel/ownership features default OFF until soak + parity say otherwise.

## Next likely work (lead may reorder)
1. Finish free-running / single-join path (RFC-0007 inc 7) if still open in tree
2. Soak matrix under parallel flags (Create/AE2, chaos, neighbors)
3. Close known gaps (e.g. legacy passenger on vanilla vehicle)
4. Only then discuss default-ON
5. P3 graph adapter spike after P2 confidence (activate weft-graph)

## OmniRoute (verified 2026-08-20)
- Active: claude, codex, xai-oauth, grok-cli, opencode, deepseek
- Model prefixes: cc/, cx/, xao/, gc/, oc/, ds/
- OpenCode free: connected (`oc/big-pickle`, etc.)
- Combos: none created yet (see .crew/ROUTING.md for weft/* combo recipes)
- Pin philosophy: Sonnet=plan, Fable/Opus/Codex=code, Grok4.5=fast code, Grok-reason/DS-Pro=audit, DS-Flash/OC=cheap
- Pins live in .crew/ROUTING.md

## Hard constraints
See .crew/laws.md
