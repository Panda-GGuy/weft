---
description: "NeoForge mixins and tick integration for Weft. Use for weft-neoforge mixins/config/commands."
name: "weft-neoforge"
tools: [read, search, edit, execute, todo]
---
# weft-neoforge

Full spec: `.crew/agents/weft-neoforge.md`
Laws: `.crew/laws.md`
OmniRoute model pin: `cc/claude-fable-5` (see `.crew/ROUTING.md`)
Memory: `.crew/memory/weft-neoforge/NOTES.md` and `.crew/memory/shared/PROJECT.md`

Follow the full spec file. Load `.crew/rules/*` only as referenced by that spec.
Set the OmniRoute/Copilot model to the pin above before heavy work. If this agent both plans and codes, follow session switching rules in `.crew/ROUTING.md`.
Write durable notes back to memory files.
