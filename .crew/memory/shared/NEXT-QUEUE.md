# Next queue (lead-owned)

Last updated: 2026-08-20 (PR #13/#14/#15 + field reconciliation)

Pick highest READY packet for role. Preserve dirty worktrees. Do not default
enable any ownership/parallel flag.

## Merge order

1. Publish this focused lead-doc reconciliation; use it instead of stale shared
   copies carried by #13/#14/#15.
2. Rebase #15 onto `origin/main` `d1095cc`; keep parity code/report/NOTES, drop
   superseded shared `BACKLOG`/`PROJECT`/`NEXT-QUEUE`; make ready after diff and
   full-suite evidence review.
3. Close #13 as superseded once replacement lead docs are reachable from main,
   or retarget #13 to contain only replacement docs. Do not merge old queue.
4. Keep #14 draft. Rebase it after #15 only after three parity-review blockers
   are fixed; drop its stale shared queue copies.

## Parity handoff — READY

Branch/worktree: `crew/parity-close` / `C:\Users\Panda\weft-wt-parity`.
Worktree is dirty with focused #3 work; never reset or overwrite it.

- Finish `p2navdefer`: real fan-out, villager/door navigation trigger, deferred
  call observed after fused/section join on server thread. Current dirty patch
  adds counters/probe but needs build and non-vacuous GameTest proof.
- Build `p2evictionchurn`: boundary BE, live then evicted neighbor, unrelated
  fan-out engaged, `unreadyUnits > 0`, `unmappedUnits == 0`, server-thread serial
  tail, zero guard trip.
- Re-review #14 only after fixups. Required contract:
  1. failed fused readiness routes BE/fresh work to actual serial tail;
  2. every entity serial tail completes before any BE stage;
  3. `p2fuse` deterministically proves cross-region stage overlap, pending-unit
     behavior, and entity/BE fallback paths.
- Preserve #15 fixed-ID harness cleanup. Rebase, run full GameTest suite twice,
  and record exact 24/24 evidence. Green CI compile/build is not soak.
- Exit: #3/#6 close only with named non-vacuous gates; otherwise remain open.

## Compatibility handoff — READY

Branch/worktree: field worktree `C:\Users\Panda\weft` currently contains mixed
dirty work from several packets. Touch only compat files or move work to a clean
branch without resetting existing changes.

- Issue #16 is source of truth: Moonrise mid-tick task asserted main-thread
  execution from Weft worker under `parallelRegions`.
- Validate neighbor detection uses actual Moonrise mod id(s), then ship tested
  coexistence posture: profiler cooperate; tick-ownership modules yield or
  refuse. Ensure posture transitively prevents `parallelRegions` worker path,
  not only top-level `regionized_ticking` UI state.
- Add sandbox registry test and a coexistence/launch assertion proving worker
  fan-out cannot engage when Moonrise is present.
- Update RFC-0003/neighbor docs and issue #16 with exact evidence. Until merged,
  lab rule is: no Moonrise + parallel/region ownership soak.
- Do not absorb unrelated fan-out-status, watchdog, routing, or lab edits from
  field worktree into compat commit.

## Release handoff — READY

Branch/worktree: `crew/release-hygiene` / `C:\Users\Panda\weft-wt-release`.
It has old shared-memory dirt copied from pre-field queue; preserve then drop or
supersede those copies during rebase—never reset blindly.

- Reconcile PRs in merge order above. Remove duplicated stale shared docs from
  #15/#14 rather than resolving them by taking old branch versions.
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

- #14 merge: blocked on parity review items above and #15 rebase order.
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
