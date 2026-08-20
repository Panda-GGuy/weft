# weft-lead memory

## Standing notes

- Queue of record: `.crew/memory/shared/NEXT-QUEUE.md`.
- Current main: `origin/main` `d1095cc` (2026-08-20 reconciliation).
- Flags stay default OFF. Do not rewrite feature code from lead worktree.
- Green CI/GameTests are regression evidence, never soak.
- Field benchmark result cannot be credited to parallel fan-out while
  `owned parallel=0` is observed.

## PR reconciliation

- #13 is stale docs based before field reorder; supersede/close after replacement
  docs land.
- #15 is first merge candidate after rebase onto `d1095cc`; preserve harness fix
  and parity report, drop stale shared queue copies.
- #14 stays draft and parity-blocked at `1bf784c`. Its latest patch addresses
  fused serial routing and entity-tail-before-BE ordering; parity must re-review
  those fixes and still require deterministic overlap/pending/fallback gates plus
  two complete full-suite runs. Rebase after #15.
- #10 is closed in tracker but fix is not on main until #15 lands.

## Open threads

- #16 Moonrise + `parallelRegions` crash: compat must ship tested yield/refuse.
- #3: dirty `p2navdefer` work exists in parity worktree; preserve it.
- #6: `p2evictionchurn` remains required; PR #14's readiness fallback patch is
  code progress, not issue-closing evidence.
- Release stays NOT READY until #3/#6/#16, corrected fusion contract,
  neighbor/chaos/R7, real-pack soak, and hazards 19/20 review clear.

## Worktree safety snapshot

- Lead worktree began clean on `crew/lead-plan` `fe9a548`.
- Field worktree `C:\Users\Panda\weft` is mixed dirty work: routing/watchdog,
  compat posture, fan-out status, lab scripts/data, and feature edits. Never
  reset or bundle it wholesale.
- Parity worktree has focused dirty #3 work.
- Release and engine worktrees carry stale shared-memory dirt.
- NeoForge and PR14-review worktrees were clean at inspection.

## Lessons

- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-20: provider limits are route failures. Checkpoint dirty state and
  checks, then walk `.crew/ROUTING.md`; never wait by abandoning work.
- 2026-08-20: three PRs copied same shared queue files. Merge feature/parity
  commits separately from lead-owned queue to avoid reviving stale priorities.
- 2026-08-20: field compatibility evidence outranks planned optimization order;
  Moonrise shipping posture and fan-out honesty now precede soak claims.
- 2026-08-20: when a blocked PR advances, queue memory must cite the reviewed
  head and separate fixed code contracts from still-missing test evidence.
- 2026-08-20: if an assigned worktree path loses its Git metadata, preserve its
  scratch files, recover on a fresh linked worktree, and copy only reviewed
  lead-owned docs; never use the mixed main worktree as a substitute branch.

## Hard constraints

See `.crew/laws.md`.
