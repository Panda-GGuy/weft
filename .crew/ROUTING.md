# OmniRoute routing for the Weft crew

Verified against live OmniRoute **v3.8.49** at `http://localhost:20128`
on **2026-08-20** (second pass: capability-based pins, not brand habit).

## Active connectors (live)

| Connection id | Prefix | Notes |
|---|---|---|
| `claude` | `cc/` | Claude Code OAuth — Fable/Opus/Sonnet/Haiku |
| `codex` | `cx/` | OpenAI Codex OAuth — GPT 5.6 Sol/Terra/Luna + Spark |
| `xai-oauth` | `xao/` | xAI Grok OAuth — 4.5 coding, 4.20 reasoning, build |
| `grok-cli` | `gc/` | Grok CLI — 4.5 + Composer fast |
| `opencode` | `oc/` | OpenCode free pool |
| `deepseek` | `ds/` | DeepSeek V4 Pro + Flash (active) |

## Capability matrix (how we choose)

| Job | Best available pin | Why |
|---|---|---|
| **Planning / orchestration / RFC structure** | `cc/claude-sonnet-5` | Sonnet is the planner: instruction-following, tool choice, long coherent plans without over-coding |
| **Hard implementation (Java engine, mixins, APIs)** | `cc/claude-fable-5` | Fable-5 is the coding specialist on Claude Code; primary hands-on coder |
| **Hardest coding / subtle concurrency edits** | `cc/claude-opus-5` then `cx/gpt-5.6-sol-max` | Opus-5 for deepest Claude coding; Codex Sol max when agentic multi-file heat is needed |
| **Agentic multi-file coding (alt stack)** | `cx/gpt-5.6-sol-high` | GPT-5.6 Sol high = strong Codex coding default without max spend |
| **Fast coding iteration / perf loops** | `xao/grok-4.5` | xAI positions Grok 4.5 as their smartest **coding** model (incl. Copilot) |
| **Deep hazard reasoning / audit think** | `xao/grok-4.20-0309-reasoning` or `ds/deepseek-v4-pro` | Explicit reasoning SKUs; better for "what races exist" than flash coders |
| **Policy / coexistence judgment** | `cc/claude-sonnet-5` | Structured judgment, not bulk code |
| **Cheap bulk (CI text, scaffolding, summaries)** | `ds/deepseek-v4-flash` or `oc/big-pickle` | Flash/free pools; not for default-ON decisions |
| **Free code-specialized cheap** | `oc/north-mini-code-free` | OpenCode north-mini-code-free |
| **Quick triage** | `cc/claude-haiku-4-5-20251001` | Tiny checks only |

### Family notes (research + catalog)

- **Claude Sonnet 5** — plan, coordinate, write RFCs, decide gates. Not first pick for dense mixin surgery.
- **Claude Fable 5** — first pick for Weft code edits (engine/neoforge/api/graph).
- **Claude Opus 5** — escalate when Fable is not enough (gnarly concurrency, correctness-critical patches).
- **Codex GPT-5.6 Sol \*** — peer coding stack; use high by default, max/ultra only when stuck. Spark = lighter/faster Codex.
- **Grok 4.5 (`xao`)** — fast high-quality coding loops, perf hypothesize/implement. Prefer over Composer for real code.
- **Grok 4.20 reasoning** — slower/heavier think; audits and race analysis.
- **Grok Composer (`gc`)** — draft/compose speed, not primary for ownership code.
- **DeepSeek V4 Pro** — strong low-cost brain for analysis + solid secondary coding; good audit alternate.
- **DeepSeek V4 Flash** — cheap/fast bulk and first-pass drafts.
- **OpenCode free (`oc/*`)** — budget overflow only; never sole reviewer for ship/default-ON.

## Alias table

| Alias | fullModel |
|---|---|
| `PLAN` | `cc/claude-sonnet-5` |
| `CODE` | `cc/claude-fable-5` |
| `CODE_HEAVY` | `cc/claude-opus-5` |
| `CODEX` | `cx/gpt-5.6-sol-high` |
| `CODEX_MAX` | `cx/gpt-5.6-sol-max` |
| `CODEX_FAST` | `cx/gpt-5.3-codex-spark` |
| `GROK_CODE` | `xao/grok-4.5` |
| `GROK_REASON` | `xao/grok-4.20-0309-reasoning` |
| `GROK_COMPOSE` | `gc/grok-composer-2.5-fast` |
| `DS_PRO` | `ds/deepseek-v4-pro` |
| `DS_FLASH` | `ds/deepseek-v4-flash` |
| `OC_FREE` | `oc/big-pickle` |
| `OC_CODE` | `oc/north-mini-code-free` |
| `HAIKU` | `cc/claude-haiku-4-5-20251001` |

## Per-agent route

| Agent | Role fit | Primary | Fallback order |
|---|---|---|---|
| **weft-lead** | Plan / sequence / handoffs | `PLAN` `cc/claude-sonnet-5` | `DS_PRO` -> `GROK_REASON` -> `CODEX` |
| **weft-architect** | RFC design / boundaries | `PLAN` `cc/claude-sonnet-5` | `GROK_REASON` -> `DS_PRO` -> `CODE_HEAVY` |
| **weft-engine** | Pure Java implementation | `CODE` `cc/claude-fable-5` | `CODE_HEAVY` -> `CODEX` -> `CODEX_MAX` -> `GROK_CODE` |
| **weft-neoforge** | Mixins / tick wiring | `CODE` `cc/claude-fable-5` | `CODE_HEAVY` -> `CODEX` -> `CODEX_MAX` -> `GROK_CODE` |
| **weft-parity** | Gate judgment + test design | `PLAN` `cc/claude-sonnet-5` | `CODE` (when writing tests) -> `DS_PRO` -> `GROK_REASON` -> `CODE_HEAVY` |
| **weft-perf** | Fast perf code + measure loops | `GROK_CODE` `xao/grok-4.5` | `CODE` -> `CODEX_FAST` -> `DS_FLASH` |
| **weft-compat** | Policy / tiers / neighbors | `PLAN` `cc/claude-sonnet-5` | `DS_PRO` -> `CODE` (when editing sandbox code) |
| **weft-api** | Public API coding | `CODE` `cc/claude-fable-5` | `CODEX` -> `CODE_HEAVY` -> `GROK_CODE` |
| **weft-release** | CI/YAML/bulk ship mechanics | `DS_FLASH` `ds/deepseek-v4-flash` | `OC_FREE` -> `GROK_COMPOSE` -> `CODEX_FAST` |
| **weft-audit** | Hazard reasoning (on-demand) | `GROK_REASON` `xao/grok-4.20-0309-reasoning` | `DS_PRO` -> `PLAN` -> `CODE_HEAVY` |
| **weft-graph** | P3 graph/adapters coding | `CODE` `cc/claude-fable-5` | `CODEX` -> `CODE_HEAVY` -> `PLAN` (API shape) |

### Session switching rule

If one agent both **plans** and **codes** in the same session:
1. Start on the **Primary** pin for the dominant mode of the current step.
2. For parity/architect/lead: switch to `CODE` / `CODE_HEAVY` only when producing patches.
3. Never keep ship/default-ON approval on `DS_FLASH` / `OC_*` / `HAIKU` alone.

## Combos (created in OmniRoute 2026-08-20)

| Combo | Steps | Intent |
|---|---|---|
| `weft/plan` | `cc/claude-sonnet-5` -> `ds/deepseek-v4-pro` -> `xao/grok-4.20-0309-reasoning` | Lead/architect/compat |
| `weft/code` | `cc/claude-fable-5` -> `cc/claude-opus-5` -> `cx/gpt-5.6-sol-high` -> `xao/grok-4.5` | Engine/neoforge/api/graph |
| `weft/correctness` | `cc/claude-sonnet-5` -> `cc/claude-fable-5` -> `xao/grok-4.20-0309-reasoning` -> `cx/gpt-5.6-sol-max` | Parity judgment then test code |
| `weft/perf` | `xao/grok-4.5` -> `cc/claude-fable-5` -> `cx/gpt-5.3-codex-spark` -> `ds/deepseek-v4-flash` | Perf loops |
| `weft/audit` | `xao/grok-4.20-0309-reasoning` -> `ds/deepseek-v4-pro` -> `cc/claude-sonnet-5` | Hazard audits |
| `weft/cheap` | `ds/deepseek-v4-flash` -> `oc/big-pickle` -> `gc/grok-composer-2.5-fast` | Bulk only |

## Rules

- Prefer catalog `fullModel` ids (`cc/...`, not guessed `claude-code/...`).
- On rate-limit/outage, walk the agent fallback list; note switch in `.crew/memory/_session/`.
- Re-pull `/api/models` after OmniRoute upgrades; pins can rename.
- No API keys in-repo.
