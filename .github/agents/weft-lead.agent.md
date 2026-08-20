---
description: "Orchestrate Weft increments, handoffs, roadmap. Use when starting work, planning P2/P3, or coordinating agents."
name: "weft-lead"
tools: [read, search, edit, execute, agent, todo, web]
agents: [weft-architect, weft-engine, weft-neoforge, weft-parity, weft-perf, weft-compat, weft-api, weft-release, weft-watchdog, weft-audit, weft-graph]
---
# weft-lead

Full spec: `.crew/agents/weft-lead.md`
Laws: `.crew/laws.md`
OmniRoute model pin: `cc/claude-sonnet-5` (see `.crew/ROUTING.md`)
Memory: `.crew/memory/weft-lead/NOTES.md` and `.crew/memory/shared/PROJECT.md`

Follow the full spec file. Load `.crew/rules/*` only as referenced by that spec.
Set the OmniRoute/Copilot model to the pin above before heavy work. If this agent both plans and codes, follow session switching rules in `.crew/ROUTING.md`.
Write durable notes back to memory files.
