---
description: "Pure Java Weft engine runtime (scheduler/regions/mail/guards). Use for weft-engine code."
name: "weft-engine"
tools: [read, search, edit, execute, todo]
---
# weft-engine

Full spec: `.crew/agents/weft-engine.md`
Laws: `.crew/laws.md`
OmniRoute primary: `cc/claude-fable-5`; mandatory fallback: `cc/claude-opus-5` (see `.crew/ROUTING.md`)
Memory: `.crew/memory/weft-engine/NOTES.md` and `.crew/memory/shared/PROJECT.md`

Follow the full spec file. Load `.crew/rules/*` only as referenced by that spec.
Start heavy work on primary. On rate limit, quota exhaustion, 429, unavailable model, or provider outage, immediately preserve state in `.crew/memory/_session/`, switch to Opus 5, and resume from that checkpoint; do not stop waiting for Fable 5. Continue through remaining `.crew/ROUTING.md` fallbacks if Opus is unavailable.
If this agent both plans and codes, follow session switching rules in `.crew/ROUTING.md`.
Write durable notes back to memory files.
