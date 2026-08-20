# OmniRoute snapshot

Captured: 2026-08-20 (capability repin pass)

## Connections
- claude (cc), codex (cx), xai-oauth (xao), grok-cli (gc), opencode (oc), deepseek (ds)

## Pin philosophy
- Plan -> Sonnet (`cc/claude-sonnet-5`)
- Code -> Fable first (`cc/claude-fable-5`), Opus escalate (`cc/claude-opus-5`), Codex peer (`cx/gpt-5.6-sol-high`)
- Fast code loops -> Grok 4.5 (`xao/grok-4.5`)
- Deep think/audit -> Grok 4.20 reasoning / DeepSeek V4 Pro
- Cheap bulk -> DeepSeek Flash / OpenCode free

## Combos
Still need creating in dashboard (see ROUTING.md recipes).

## Combos created (2026-08-20)
Strategy: priority failover. Names selectable as model ids.

- weft/plan: cc/claude-sonnet-5 -> ds/deepseek-v4-pro -> xao/grok-4.20-0309-reasoning
- weft/code: cc/claude-fable-5 -> cc/claude-opus-5 -> cx/gpt-5.6-sol-high -> xao/grok-4.5
- weft/correctness: cc/claude-sonnet-5 -> cc/claude-fable-5 -> xao/grok-4.20-0309-reasoning -> cx/gpt-5.6-sol-max
- weft/perf: xao/grok-4.5 -> cc/claude-fable-5 -> cx/gpt-5.3-codex-spark -> ds/deepseek-v4-flash
- weft/audit: xao/grok-4.20-0309-reasoning -> ds/deepseek-v4-pro -> cc/claude-sonnet-5
- weft/cheap: ds/deepseek-v4-flash -> oc/big-pickle -> gc/grok-composer-2.5-fast
