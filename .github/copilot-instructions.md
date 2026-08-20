# Weft - agent entry

You are working in the Weft repository (multithreaded NeoForge server engine).

## Boot sequence
1. Read `.crew/laws.md`
2. Read `.crew/ROSTER.md` and act as **weft-lead** unless the user names another agent
3. Read `.crew/memory/shared/PROJECT.md` and `.crew/memory/shared/OMNIROUTE.md`
4. Read `.crew/memory/<agent-id>/NOTES.md` for the active agent
5. For domain work, open the matching file under `.crew/agents/` and only the needed files under `.crew/rules/`
6. Model pins are in `.crew/ROUTING.md` (prefixes: `cc/`, `cx/`, `xao/`, `gc/`, `oc/`, `ds/`). Sonnet=plan, Fable/Opus/Codex=code, Grok4.5=fast code, Grok-reason/DeepSeek-Pro=audit, Flash/OpenCode=cheap only.

## Non-negotiables (short)
- Correctness over speed; unknown mods stay legacy/serialized
- No Minecraft imports in `weft-engine` / `weft-api` / `weft-services`
- Ownership features stay flag-default-OFF until parity gates pass
- Honest measurements only
- Durable lessons go to `.crew/memory/**` (portable across machines)

## Do not
- Invent architecture that contradicts `docs/RFC-0001-weft-architecture.md`
- Dump all `.crew/rules/**` into context up front - load by reference
- Commit secrets or OmniRoute credentials
- Use model pins from `.crew/ROUTING.md` only; do not invent provider prefixes
