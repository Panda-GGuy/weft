# Shared project memory

Last updated: 2026-08-20 (lead reconciliation against `origin/main` `d1095cc`)

## What Weft is

Multithreaded server engine for modded Minecraft (NeoForge 1.21.1). Regionized
ticking + graph layer + legacy lane for unknown mods.

## Phase status

- P0 complete (profiler).
- P1 complete (off-thread services; async pathfinding + spawn density defaults ON).
- P2 open. Regionized/partitioned/parallel/owner-mail/BE-shard remain behind
  default-OFF flags.
- Increment 6 owner-mail routing and increment 7 engine scaffold are on main.
- Increment 7 loader fuse wiring is draft PR #14. CI is green, but parity review
  blocks merge at `432f831`: fused hazard-24 work can run after failed readiness,
  entity serial-tail ordering violates entity-before-BE semantics, and `p2fuse`
  does not prove stage overlap or fallback paths.
- P3+ not started (graph adapters, race detector, production tag).

## Current field evidence

- 2026-08-20 SP Create/AE2 stress: opt-in run had better measured latency tails,
  but `owned parallel=0` was often observed. Do not attribute result solely to
  multi-region fan-out.
- Moonrise + `parallelRegions` crashed when Moonrise asserted a main-thread
  chunk task from a Weft worker. Issue #16 is open. Shipping coexistence posture
  must yield/refuse tick ownership until tested; never co-enable for soak.
- Durable source: `shared/FIELD-2026-08-20.md`.

## Pull request disposition

- #13 queue/project docs: superseded by this post-field reconciliation. Do not
  merge stale queue text; replace from lead docs or close as superseded.
- #14 fused loader path: keep draft and default OFF; address review blockers,
  rebase after #15, add deterministic gates, then rerun full suite twice.
- #15 parity harness/accessor cleanup: first merge candidate. CI is green and
  report records two 24/24 suites. Rebase onto `d1095cc`, preserve focused code
  and parity notes, and drop stale shared queue copies superseded here.

## Open issues

- #16 Moonrise + parallel worker crash: open; compat shipping gate.
- #3 hazard 21: open pending non-vacuous `p2navdefer` evidence. Focused dirty
  work exists in parity worktree; do not overwrite it.
- #6 hazard 24: open pending `p2evictionchurn`; also blocks PR #14 fused route.
- #10 closed by PR #15 harness fix, but change is not on main yet.
- #4/#5/#7 closed.

## Release posture

NOT READY. No P2 ownership/parallel flag may default ON. Required before any
default discussion: #16 shipping posture, #3/#6 gates, corrected PR #14 fusion
contract, current neighbor/chaos/R7 evidence, real-pack soak, and hazards 19/20
re-audit.

## Next work

See `shared/NEXT-QUEUE.md` for actionable parity/release/compat handoffs.

## OmniRoute

Pins and provider fallbacks live in `.crew/ROUTING.md`. On provider limits,
checkpoint branch, dirty files, checks, next command, and blocker before walking
fallbacks; never reset a dirty worktree.

## Hard constraints

See `.crew/laws.md`.
