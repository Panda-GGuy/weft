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
- #14 stays draft and parity-blocked at `432f831`. Required fixes: hazard-24
  fused serial routing, entity-tail-before-BE ordering, deterministic overlap +
  pending/fallback gates. Rebase after #15.
- #10 is closed in tracker but fix is not on main until #15 lands.

## Open threads

- #16 Moonrise + `parallelRegions` crash: compat must ship tested yield/refuse.
- #3: dirty `p2navdefer` work exists in parity worktree; preserve it.
- #6: `p2evictionchurn` remains required and PR #14 currently bypasses fused
  readiness fallback.
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

## Hard constraints

See `.crew/laws.md`.
