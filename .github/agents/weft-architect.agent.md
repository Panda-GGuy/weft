---
description: "Weft RFC/design authority and module boundaries. Use for architecture, equivalence classes, RFC edits."
name: "weft-architect"
tools: [read, search, edit, web, todo]
---
# weft-architect

Full spec: `.crew/agents/weft-architect.md`
Laws: `.crew/laws.md`
OmniRoute model pin: `cc/claude-sonnet-5` (see `.crew/ROUTING.md`)
Memory: `.crew/memory/weft-architect/NOTES.md` and `.crew/memory/shared/PROJECT.md`

Follow the full spec file. Load `.crew/rules/*` only as referenced by that spec.
Set the OmniRoute/Copilot model to the pin above before heavy work. If this agent both plans and codes, follow session switching rules in `.crew/ROUTING.md`.
Write durable notes back to memory files.
