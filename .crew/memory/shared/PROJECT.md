# Shared project memory

Last updated: 2026-08-20

## What Weft is
Multithreaded server engine for modded Minecraft (NeoForge 1.21.1). Regionized ticking + graph layer + legacy lane for unknown mods.

## Phase status
- P0 complete (profiler)
- P1 complete (off-thread services; async pathfinding + spawn density defaults ON)
- P2 open: regionized/partitioned/parallel/owner-mail/BE-shard behind default-OFF flags
- Inc6 ownerMailRouting SHIPPED (default OFF)
- Inc7 scaffold SHIPPED on main (PendingUnits, runOwnedFused, singleJoinTick OFF, NOTE-0001 GO) via PR #12
- Inc7 loader fuse wiring (7c) IN FLIGHT on crew/neoforge-inc7
- P3+ not started (graph adapters, race detector, production tag)

## Default posture
Parallel/ownership features default OFF until soak + parity say otherwise.

## Next work (see also shared/NEXT-QUEUE.md)
1. NOW: neoforge 7c fuse wiring; parity issues #10 + reopened #3/#6
2. READY: p2fuse gate; dedicated gates for #3/#6; docs hygiene; audit re-anchor review
3. THEN: soak (Create/AE2, chaos, neighbors) under opt-in flags
4. THEN: remaining gaps; only then default-ON discussion
5. LATER: RFC-0008 entity sharding research; P3 weft-graph adapter spike

## Open issues (tracker)
- #10 intermittent Entity already tracked under p2parallelbench (new)
- #3 hazard 21 REOPENED pending dedicated gate
- #6 hazard 24 REOPENED pending dedicated gate
- #4/#5/#7 closed

## OmniRoute
Pins in .crew/ROUTING.md. Combos weft/plan|code|correctness|perf|audit|cheap.

## Hard constraints
See .crew/laws.md
