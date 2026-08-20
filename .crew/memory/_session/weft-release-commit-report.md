# weft-release commit report

Date: 2026-08-20
Branch: `crew/release-hygiene`
Commit: `70bc5ee` (`docs(release): pin stressmark evidence protocol`)
Push: `origin/crew/release-hygiene` updated successfully

## Landed increment

- Preserved and committed all seven dirty release-doc/shared-memory paths.
- Added pinned fresh-world Stressmark OFF/ON protocol with destructive-world
  safeguards and explicit non-soak/non-default-ON scope.
- Refreshed shared project queue, release evidence, live PR checkpoint, and
  current failed neighbors/bench gate status.
- Kept every ownership/parallel flag default OFF; no config or code changed.

## Commands run

- `git status --short --branch`
- `git log -5 --oneline`
- `git diff --check`
- PowerShell checks for balanced Markdown fences, local relative links, and
  Stressmark default-OFF flag names against `WeftConfig.java`
- `gh pr view 13`, `gh pr view 14`, `gh pr view 15` with JSON check metadata
- `gh run view 32333623722`, `gh run view 32330406425`,
  `gh run view 32331615939` with JSON job metadata
- `git commit -m "docs(release): pin stressmark evidence protocol"`
- `git push origin crew/release-hygiene`

## Leftover risks

- P2 remains NOT READY. PR #14 is draft and needs current-head parity rereview
  plus non-vacuous fused/fallback gates.
- Scheduled neighbors run `32333623722` failed `metrics-port` before boot due
  invalid stub modid `metrics-port`; fix harness and rerun all seven cells.
- Scheduled bench run `32330406425` failed world-benchmark baseline comparison;
  cause remains unknown.
- Issues #3/#6/#10 and qualifying Create/AE2 soak remain open.
- RFC increment numbering/status drift remains queued.

## Next owner

- `weft-release`: fix `metrics-port` harness and RFC/README status drift.
- `weft-parity`: current-head PR #14 rereview and named gates.
- `weft-lead`: merge-order/rebase coordination for PRs #13, #15, then #14.
