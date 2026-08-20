# Next queue (lead-owned)

Last updated: 2026-08-20 (catch-up relaunch)

How to use: when an agent finishes its current packet, pick the **highest
priority READY** item for its role. Do not start BLOCKED items. Do not flip
any ownership/parallel flag default-ON.

## NOW (in flight)

| ID | Owner | Work | Exit |
|---|---|---|---|
| NOW-7c | weft-neoforge | Wire fused single-join path behind `singleJoinTick` OFF (PendingUnits + runOwnedFused already on main) | Flag OFF bit-identical; ON serial-equivalent path; counters/status; report; push `crew/neoforge-inc7` |
| NOW-ISSUES | weft-parity | Open issues #10 (new flake), #3/#6 reopened | Honest GH comments; dedicated gates or explicit leave-open; `parity-issues-report.md` |
| NOW-LEAD | weft-lead | Keep this queue + lead-plan current as NOW items finish | Handoffs match tip; PROJECT/BACKLOG not stale |

## READY immediately after NOW (no waiting on soak)

Priority order:

### Q1 — Land 7c + gate it (depends on NOW-7c green compile/tests)
- **Owner:** weft-parity (gate) + weft-neoforge (fixups)
- **Work:** `p2fuse` two-island GameTest (A's BE completes while B's entities still run); `p2parity` still green with flag OFF and ON-vanilla
- **Pin:** CODE `cc/claude-fable-5` or CODEX `cx/gpt-5.6-sol-high` for test code; PLAN sonnet for pass/fail judgment
- **Done means:** PR merged or ready; suite green twice; flag still default OFF

### Q2 — Hazard gate honesty (#3 / #6 / #10)
- **Owner:** weft-parity
- **Work:**
  - #3: dedicated `p2navdefer`-style gate (villager/door under fan-out) or documented why impossible + leave open
  - #6: dedicated `p2evictionchurn` (forced load/unload under section) or leave open with that requirement
  - #10: root-cause or minimal standing repro for `Entity is already tracked!`; do not close on vibes
- **Pin:** PLAN then CODE for gametests
- **Done means:** each issue closed **only** with non-vacuous gate, else stays open with named gap

### Q3 — Docs / status hygiene (parallel with Q1–Q2)
- **Owner:** weft-release
- **Work:** RFC-0006/0007 headers no longer say shipped increments are "in progress"; README/status mention open #3/#6/#10 honestly; no default-ON implication
- **Pin:** DS_FLASH / cheap
- **Done means:** status language matches flags + open issues (law 9)

### Q4 — weft-audit go/no-go on fuse window mitigations (before any 7c default talk)
- **Owner:** weft-audit
- **Work:** Independent review of NOTE-0001 GO + list RFC-0006 mitigations that assumed "server parked between sections" and must re-anchor to single join; hazard-23 interaction under fused fan-out
- **Pin:** GROK_REASON / DS_PRO
- **Done means:** written go/no-go in `.crew/memory/_session/` + audit NOTES; blocks default-ON discussion only (not scaffolding)

## READY after Q1–Q2 (P2 confidence track)

### Q5 — Soak matrix under opt-in flags
- **Owner:** weft-parity + weft-perf (measure) + human/lab scripts
- **Work:** Create/AE2 pack, chaos kill-save, neighbors ladder, R7 under `parallelRegions` / partitioned / mail as appropriate
- **Done means:** dated evidence notes; failures filed as issues; still no default-ON without lead+parity

### Q6 — Close remaining known gaps
- **Owner:** weft-compat / weft-neoforge as assigned
- **Examples:** legacy passenger on vanilla vehicle; modded Brain entities outside MemoryReachEntities.SERIAL fail-loud policy docs
- **Done means:** issue or NOTES entry with fix or accepted limitation

### Q7 — Default-ON policy discussion only
- **Owner:** weft-lead + weft-parity + weft-release
- **Blocked on:** Q1–Q6 evidence, hazards 19/20 exit criteria, soak green
- **Done means:** written decision; flags flipped only with parity signoff

## LATER (do not start until P2 confidence)

### Q8 — Entity sharding research path (RFC-0008) — deferred
- pushEntities hazard; combination gates before any nested submission

### Q9 — P3 graph adapter spike
- Activate **weft-graph**; first consumer + one adapter (Create/AE2/energy)

### Q10 — Production default policy
- Dedicated vs integrated server; ship checklist with weft-release

## Idle agent cheat-sheet

| If you are… | and NOW is done | start |
|---|---|---|
| weft-neoforge | 7c landed | Q1 followups / help parity gates needing mixins |
| weft-parity | issues packet done | Q1 p2fuse review + Q2 remaining gates |
| weft-engine | idle | Support Q1 pure-side test gaps; no MC imports |
| weft-audit | called | Q4 |
| weft-release | idle | Q3 |
| weft-compat | idle | Q6 gap list |
| weft-perf | idle | Q5 measurement plan only (no default-ON claims) |
| weft-graph | idle | wait for Q9 |
| weft-lead | always | refresh this file + handoffs when NOW completes |

## Hard rules
1. Correctness over speed; unknown mods legacy.
2. No default-ON without parity + soak evidence.
3. Green unit/gametest ≠ soak.
4. Portable notes under `.crew/memory/**`; session scratch stays gitignored.
