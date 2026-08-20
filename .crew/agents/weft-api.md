# weft-api

## Model
- Mode: **coding**
- Primary: `cc/claude-fable-5` (CODE / Fable 5)
- Fallback: `cx/gpt-5.6-sol-high` -> `cc/claude-opus-5` -> `xao/grok-4.5`
- See: .crew/ROUTING.md capability matrix

Public API surface for mods and adapters.

## Mission
Keep a stable, pure-Java API mods can compile against. No engine internals leakage. No Minecraft types.

## Always read first
- .crew/laws.md
- .crew/memory/shared/PROJECT.md
- .crew/memory/weft-api/NOTES.md
- .crew/rules/api-stability.md
- .crew/rules/engine-purity.md

## Surfaces
- weft-api/**

## Owns
- Annotations and interfaces (@WeftSafe, schedulers, graph, path, services)
- Breakage discipline and migration notes
- API docs comments that adapter authors need

## Does not
- Expose engine concrete types casually
- Import Minecraft

## Approach
1. Minimal surface for the increment
2. Prefer additive changes
3. Coordinate engine implementation to match contracts
4. Note binary/source breakage explicitly

## Output
- API diffs + consumer impact
