# Next queue (lead-owned)

Last updated: 2026-08-20 (commit-duty reconciliation at `origin/main`
`ac40243`)

Pick highest READY packet for role. Preserve dirty worktrees. Do not default
enable any ownership/parallel flag.

## Merge order (updated 2026-08-20 late)

1. DONE: PR #17 merged to main (`1102584`).
2. DONE: PR #13 closed as superseded by #17.
3. PR #15 remains draft at `7423942`. Its report records one 25/25 full suite
   and non-vacuous `p2navdefer`; review focused code, remove stale shared
   `PROJECT.md`/`BACKLOG.md` copies, then decide issue #3 and merge readiness.
4. Keep #14 draft at `97b9254` (code head `1bf784c`). Re-review after #15
   lands; require non-vacuous
   fused/fallback gates and full GameTest isolation of tracker crash.
5. Cleanup DONE for merged remotes: deleted `crew/lead-plan`, `crew/engine-inc7`,
   `feat/p2-parallel-regions-throughput`, `feat/ws7-observability-exporter`,
   `crew/sync-queue-main`. Keep only open-PR branches + active release hygiene.

## Parity handoff — READY

Branch/worktree: `crew/parity-close` / `C:\Users\Panda\weft-wt-parity`.
Worktree is clean and pushed at `7423942`; preserve it.

- Review pushed `p2navdefer` evidence: one full suite passed 25/25 and required
  batch engaged. Confirm probe contract is sufficient, then close #3 only after
  focused code lands; otherwise record exact missing proof.
- Build `p2evictionchurn`: boundary BE, live then evicted neighbor, unrelated
  fan-out engaged, `unreadyUnits > 0`, `unmappedUnits == 0`, server-thread serial
  tail, zero guard trip.
- Re-review PR #14 head `1bf784c`: confirm failed fused readiness routes work in
  serial owner order and every entity serial tail completes before any BE stage.
  Extend `p2fuse` to deterministically prove cross-region stage overlap,
  pending-unit behavior, and entity/BE fallback paths.
- PR #14 full GameTest attempts still stop at the known optional
  `parallelregionsentitysection` tracker crash. After #15 rebase/order cleanup,
  rerun the full suite twice and record exact complete-suite evidence.
- Preserve #15 fixed-ID harness cleanup. Current report records one 25/25 full
  suite; add another clean full-suite run if reviewer keeps two-run gate. Green
  CI compile/build is not soak.
- Exit: #3/#6 close only with named non-vacuous gates; otherwise remain open.

## Compatibility handoff — DONE, awaiting review

Branch `crew/compat-moonrise-16` @ `49297b9`, PR #23. Field worktree
`C:\Users\Panda\weft` is on that branch and still holds unrelated dirty work
from other packets (fan-out status line, rcon.py, lab scripts) — preserve it.

Delivered:
- `moonrise` registry row: profiler cooperate; `regionized_ticking`,
  `entity_sharding`, `legacy_lane` yield. Yield not refuse (no tick-ownership
  claim, RESEARCH-0002 §1).
- P1 services deliberately NOT yielded, pinned by
  `moonriseLeavesP1ServicesAlone`.
- `WeftModules` disarmed-sub-flags line: a yielded module prints no
  extraDetail, so the "my toml says parallelRegions = true" contradiction is
  now visible instead of silent.
- R7 `moonrise` cell boots the FULL parallel stack in config and greps the
  disarmed line — a YIELDED line alone cannot distinguish parked from
  relabelled.
- Booted locally both directions: cell shows YIELDED + DISARMED +
  `owned parallel=0` with pathfinding still ACTIVE; negative control (same
  config, no stub) shows `regionized_ticking ACTIVE (increment 5 parallel)`.
- RFC-0006 hazard 20 candidate -> confirmed; RFC-0003 §3.2 added; issue #16
  commented with the evidence.

Remaining for compat:
- Land #23, then close #16.
- Hazard 20's first route is still unbuilt: express the worker chunk-read path
  against a surface Moonrise preserves. Until then co-enabling is unsupported,
  not merely untested. Lab rule stands: no Moonrise + parallel soak.
- Hazard 19 (ScalableLux / light engine) posture still deliberately unset —
  same shape, no evidence yet, do not seed one.

## Release handoff — READY

PR #19 is merged at `ac40243`. Follow-up PR #20 is clean and green at `8b01f7a`
on branch/worktree `crew/release-commit-duty` /
`C:\Users\Panda\weft-wt-release2`.

- Reconcile PRs in merge order above. PR #20 is reviewable lead/release memory;
  remove duplicated stale shared docs from #15 rather than taking old branch
  versions.
- Refresh README/RFC status: #10 fixed on #15 but not main; #3/#6/#16 open; #14
  draft and parity-blocked; all P2 ownership flags default OFF.
- Document reproducible ON/OFF field protocol: same world recreation, mod list,
  flags, warm-up/window, Spark URLs, Weft counters, and fan-out evidence. State
  that current 8.99 vs 10.1 median and 14.3 vs 21.4 p95 sample is promising but
  not proof of parallel fan-out because `owned parallel=0` was often observed.
- Confirm current seven-cell neighbor workflow with dated CI evidence. Add
  Moonrise cell only after compat posture is merged and tested.
- Run static workflow parse and `git diff --check`; no release/default-ON claim.
- Exit: release report says NOT READY and lists #3/#6/#16, PR #14 blockers,
  chaos/R7, real-pack soak, and hazards 19/20 as gates.

## Blocked follow-ons

- #14 merge: blocked on parity re-review, deterministic gates, complete full
  suites, and #15 rebase order.
- Real-pack soak: blocked on #16 shipping posture. Run without Moonrise only if
  evidence is explicitly scoped to that pack.
- Default-ON discussion: blocked on all parity/compat/release exits, soak,
  chaos/R7, and hazards 19/20 review.
- P3 graph work: wait until P2 confidence track clears.

## Hard rules

1. Correctness over speed; unknown mods stay serialized.
2. Flags stay default OFF.
3. Green builds/GameTests are regression evidence, not soak.
4. On provider 429/quota/outage, checkpoint branch, commit, dirty files, checks,
   next command, and blocker; then follow `.crew/ROUTING.md` fallbacks.
