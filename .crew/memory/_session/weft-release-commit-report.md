# weft-release commit report

Date: 2026-08-20
Branch: `crew/release-hygiene`
Commit: `e7f834f` (`docs(release): record equal-heap Stressmark tie`)
Push: `origin/crew/release-hygiene` updated successfully

## Landed increment

- Preserved and committed all seven dirty release-doc/shared-memory paths.
- Added pinned fresh-world Stressmark OFF/ON protocol with destructive-world
  safeguards and explicit non-soak/non-default-ON scope.
- Refreshed shared project queue, release evidence, live PR checkpoint, and
  current failed neighbors/bench gate status.
- Kept every ownership/parallel flag default OFF; no config or code changed.
- Recorded durable release lesson that equal-heap Stressmark is tied field
  evidence (MSPT about 39 vs 40), not default-ON clearance; PR #18 already
  archived comparison on `main`.

## Commands run

- `git status --short --branch`
- `git log -5 --oneline`
- `git diff --check`
- PowerShell checks for balanced Markdown fences, local relative links, and
  Stressmark default-OFF flag names against `WeftConfig.java`
- `gh pr view 13`, `gh pr view 14`, `gh pr view 15` with JSON check metadata
- `gh run view 32333623722`, `gh run view 32330406425`,
  `gh run view 32331615939` with JSON job metadata
- `git fetch origin --prune`
- `gh pr view 18 --json ...` (confirmed merged as `15ee063`)
- `gh pr list --state open ...` (only draft #14 and #15 remain)
- `git merge-tree $(git merge-base origin/main HEAD) origin/main HEAD`
- `git commit -m "docs(release): pin stressmark evidence protocol"`
- `git commit -m "docs(release): record equal-heap Stressmark tie"`
- `git push origin crew/release-hygiene`

## Leftover risks

- P2 remains NOT READY. Equal-heap Stressmark is tied and grants no default-ON
  or multi-region-win claim. PR #14 is draft and needs current-head parity
  rereview plus non-vacuous fused/fallback gates.
- Scheduled neighbors run `32333623722` failed `metrics-port` before boot due
  invalid stub modid `metrics-port`; fix harness and rerun all seven cells.
- Scheduled bench run `32330406425` failed world-benchmark baseline comparison;
  cause remains unknown.
- Issues #3/#6/#10 and qualifying Create/AE2 soak remain open.
- RFC increment numbering/status drift remains queued.

## Next owner

- `weft-lead`: open/reconcile release-hygiene PR; branch is 16 commits ahead
  and 3 behind `origin/main`, with conflicts in shared memory/report files.
- `weft-release`: fix `metrics-port` harness and RFC/README status drift on a
  current-main branch; avoid folding that code change into this docs branch.
- `weft-parity`: current-head PR #14 rereview and named gates.
- `weft-lead`: prepare #15 for review only after suite evidence; #14 stays last.
