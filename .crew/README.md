# Weft Crew

Portable multi-agent setup for finishing Weft under Copilot + OmniRoute.

Pull this repo on any machine, point the IDE at OmniRoute, open chat. The thin
entry in `.github/copilot-instructions.md` loads project laws and points here.

## Layout

```
.crew/
  README.md                 <- this file
  ROSTER.md                 <- agents, owners, preferred models
  ROUTING.md                <- OmniRoute connector -> model map (LIVE PINS)
  HANDOFF.md                <- how agents pass work
  laws.md                   <- non-negotiable project laws (short)
  agents/*.md               <- full agent specs (source of truth)
  rules/*.md                <- separate file/domain rules (not always-on)
  memory/
    shared/                 <- cross-agent facts (tracked)
    <agent-id>/             <- per-agent durable notes (tracked)
    _session/               <- scratch only (gitignored)
.github/
  copilot-instructions.md   <- thin always-on entry for Copilot/OmniRoute
  agents/*.agent.md         <- picker-facing stubs that point at .crew agents
```

## Human vs machine discoverability

- Casual readers see normal `docs/RFC-*.md` and `README.md`.
- Agents (and curious humans) find the crew under `.crew/` and thin stubs under `.github/agents/`.
- Domain rules live in `.crew/rules/` and are **referenced** from agents. They are not dumped into always-on Copilot context.
- Keep `.github/instructions/` empty unless you deliberately want auto-attach noise.

## Start a session (any machine)

1. `git pull`
2. Start OmniRoute with connectors (Claude, Codex, xAI, Grok CLI, OpenCode - see ROUTING.md).
3. Open the repo in the IDE with Copilot routed through OmniRoute.
4. Pick an agent from the picker (`weft-lead` default) **or** say:
   - `Act as weft-lead. Read .crew/laws.md and .crew/ROSTER.md.`
5. Set the model pin from `.crew/ROUTING.md` for that agent (or use a `weft/*` combo once created).
6. Before non-trivial work, the active agent reads:
   - `.crew/memory/shared/PROJECT.md`
   - `.crew/memory/<agent-id>/NOTES.md`
7. After durable lessons, append to those memory files (not chat-only).

## Model policy (summary)

Full live pins: `.crew/ROUTING.md`.

| Need | Prefer | Example pin |
|---|---|---|
| Architecture / correctness | Claude Opus | `cc/claude-opus-5` |
| Broad impl / mixins | Codex Sol high | `cx/gpt-5.6-sol-high` |
| Fast iteration | Grok 4.5 | `xao/grok-4.5` |
| Cheap bulk | OpenCode free | `oc/big-pickle` |

OpenCode free **is** connected (`oc/*`). Grok CLI remains a secondary cheap path.

## Memory rules

- **Tracked:** `memory/shared/**`, `memory/<agent-id>/**` (except `_session`)
- **Gitignored:** `memory/_session/`
- Write short bullets, date-stamp (`YYYY-MM-DD`), link RFCs/paths.
- No secrets, tokens, or machine-local absolute paths.

## Do not

- Put Minecraft imports into `weft-engine` / `weft-api` / `weft-services`
- Silently parallelize unknown mods
- Flip parallel flags default-ON without parity + soak evidence
- Duplicate long RFCs into agent files - link them
