# Handoffs

## Standard increment loop

1. **weft-lead** - define goal, exit gate, flag name, equivalence class
2. **weft-architect** - confirm RFC coverage / write RFC slice if missing
3. **weft-api** - only if public surface changes
4. **weft-engine** - pure runtime
5. **weft-neoforge** - wire into MC tick / config / commands
6. **weft-compat** - neighbor/legacy interaction check
7. **weft-perf** - measure if a speed claim is expected (else explicit N/A)
8. **weft-parity** - tests/gates; approve or block
9. **weft-release** - CI/docs/status defaults; ship checklist

Optional:
- **weft-audit** before enabling a new parallel surface
- **weft-graph** when P3 adapter work starts

## Handoff packet (copy this)

```
### Handoff
From: <agent>
To: <agent>
Goal: <one sentence>
Flag/module: <name or n/a>
Equivalence class: E0 | E1 | E2 | n/a
RFC refs: <paths>
Code surfaces: <paths>
Model pin: <fullModel from .crew/ROUTING.md>
Done means: <testable exit>
Risks: <bullets>
Memory updates: <files touched under .crew/memory>
```

## Block rules

- **weft-parity** may block merge/default-ON if gates missing or red.
- **weft-release** may block release if CI/workflows/flags disagree with claims.
- **weft-architect** may block if module dependency rule would break.

## Lead-only powers

- Reorder backlog
- Split/cancel increments
- Assign model pin overrides for a session (record in `_session`)
