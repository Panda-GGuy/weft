# Shared project memory

Last updated: 2026-08-20 (commit-duty reconciliation against `origin/main`
`ac40243` and open PR heads)

## What Weft is

Multithreaded server engine for modded Minecraft (NeoForge 1.21.1). Regionized
ticking + graph layer + legacy lane for unknown mods.

## Phase status

- P0 complete (profiler).
- P1 complete (off-thread services; async pathfinding + spawn density defaults ON).
- P2 open. Regionized/partitioned/parallel/owner-mail/BE-shard remain behind
  default-OFF flags.
- Increment 6 owner-mail routing and increment 7 engine scaffold are on main.
- Increment 7 loader fuse wiring is draft PR #14. Head `97b9254` contains code
  head `1bf784c`, which routes
  failed fused readiness through serial owner order and preserves
  entity-before-BE ordering. CI is green, but parity re-review and deterministic
  `p2fuse` overlap/pending/fallback evidence remain required; its full GameTest
  runs still hit the known optional entity-tracker crash before suite completion.
- P3+ not started (graph adapters, race detector, production tag).

## Current field evidence

- 2026-08-20 SP Create/AE2 stress: opt-in run had better measured latency tails,
  but `owned parallel=0` was often observed. Do not attribute result solely to
  multi-region fan-out.
- Moonrise + `parallelRegions` crashed when Moonrise asserted a main-thread
  chunk task from a Weft worker. Shipping posture now implemented in PR #23
  (`crew/compat-moonrise-16`, `49297b9`): `moonrise` yields
  `regionized_ticking`/`entity_sharding`/`legacy_lane`, profiler cooperates,
  P1 services deliberately untouched. Booted both directions locally (R7 cell
  disarmed with the full parallel stack in config; negative control ACTIVE).
  Issue #16 stays open until #23 lands. Real coexistence is still unbuilt, so
  never co-enable Moonrise + parallel for soak.
- Durable source: `shared/FIELD-2026-08-20.md`.

## Pull request disposition

- #13 queue/project docs: superseded by this post-field reconciliation. Do not
  merge stale queue text; replace from lead docs or close as superseded.
- #14 fused loader path: keep draft and default OFF. Branch head is `97b9254`;
  re-review code head `1bf784c`
  ordering/readiness fix, add deterministic gates, rebase after #15, then rerun
  the full suite twice.
- #15 parity/harness cleanup: branch head `7423942` is green in PR build CI and
  records one complete 25/25 local GameTest suite with non-vacuous `p2navdefer`
  evidence. Issue #3 remains open until review/landing. Before merge, drop stale
  shared `PROJECT.md`/`BACKLOG.md` copies and review focused code against main.

## Open issues

- #16 Moonrise + parallel worker crash: open, but posture implemented and
  booted in PR #23. Close on landing. RFC-0006 hazard 20 promoted from
  candidate to confirmed; hazard 20's first route (worker read path expressed
  against a Moonrise-preserved surface) remains unbuilt.
- #3 hazard 21: open. PR #15 head `7423942` now carries non-vacuous
  `p2navdefer` evidence and one 25/25 full suite; review and land before tracker
  closure.
- #6 hazard 24: open pending non-vacuous `p2evictionchurn` evidence; PR #14's
  code fix does not close the issue without that gate.
- #10/#11 closed; PR #15 still carries related harness/accessor cleanup not yet
  on main.
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
