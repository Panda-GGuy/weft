# Weft Crew Roster

Full specs: `.crew/agents/<id>.md`
Live pins: `.crew/ROUTING.md` (capability matrix)

## Model quick legend

| Pin | fullModel | Best at |
|---|---|---|
| PLAN | `cc/claude-sonnet-5` | Planning, RFCs, gates, policy |
| CODE | `cc/claude-fable-5` | Primary coding (Fable) |
| CODE_HEAVY | `cc/claude-opus-5` | Hardest Claude coding (Opus) |
| CODEX | `cx/gpt-5.6-sol-high` | Agentic multi-file coding |
| GROK_CODE | `xao/grok-4.5` | Fast coding iteration |
| GROK_REASON | `xao/grok-4.20-0309-reasoning` | Deep hazard reasoning |
| DS_PRO | `ds/deepseek-v4-pro` | Strong cheap analysis |
| DS_FLASH | `ds/deepseek-v4-flash` | Cheap bulk |

## Core team

| ID | Responsibility | Primary pin |
|---|---|---|
| `weft-lead` | Orchestrate increments, handoffs | PLAN `cc/claude-sonnet-5` |
| `weft-architect` | RFC/design authority | PLAN `cc/claude-sonnet-5` |
| `weft-engine` | Pure Java runtime | CODE `cc/claude-fable-5` |
| `weft-neoforge` | Mixins / MC tick integration | CODE `cc/claude-fable-5` |
| `weft-parity` | Correctness brake + GameTests | PLAN `cc/claude-sonnet-5` (code with CODE) |
| `weft-perf` | Profiler, services, benches | GROK_CODE `xao/grok-4.5` |
| `weft-compat` | Tiers, legacy, neighbors | PLAN `cc/claude-sonnet-5` |
| `weft-api` | Public API | CODE `cc/claude-fable-5` |
| `weft-release` | Gradle/CI/ship checklist | DS_FLASH `ds/deepseek-v4-flash` |
| `weft-watchdog` | Stall/crash monitor + bounded restart | CODEX `cx/gpt-5.6-sol-high` |

## On-demand

| ID | When | Primary pin |
|---|---|---|
| `weft-audit` | Hazard audits before new parallel surfaces | GROK_REASON `xao/grok-4.20-0309-reasoning` |
| `weft-graph` | P3 graph + adapters | CODE `cc/claude-fable-5` |

## Why this split still holds

| Concern | Owner |
|---|---|
| Roadmap | lead (PLAN) |
| Design law | architect (PLAN) |
| Engine vs mixins boundary | engine + neoforge (CODE) |
| Ship/default-ON | parity (PLAN, CODE for tests) |
| Speed claims | perf (GROK_CODE) |
| Pack safety | compat (PLAN) |
| Public surface | api (CODE) |
| CI truth | release (DS_FLASH) |
| Hazard archaeology | audit (GROK_REASON/DS_PRO) |
| Tech-mod networks | graph (CODE) |

## Invocation

- Default entry: **weft-lead**
- Hard stops: **weft-parity**, **weft-release**
