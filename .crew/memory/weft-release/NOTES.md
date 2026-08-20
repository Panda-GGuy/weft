# weft-release memory

## Standing notes
- Root `.gitignore` intentionally covers local `gametest-*.log` and `labserver-*.log` harness artifacts; do not broaden this to source-like names or generic `*.log` without review.
- README workflow status must track `chaos.yml`, `neighbors.yml`, and `bench.yml`; neighbor matrix currently has seven cells and covers cooperate, yield, self-disable, and refuse.
- Shipping defaults verified 2026-08-19: async pathfinding and authoritative spawn density ON; activation, region/partition/parallel ticking, owner mail, entity/block-entity sharding, legacy lane, and metrics OFF.

## Open threads
- Current seven-cell neighbor matrix is not green: scheduled main run `32333623722` (2026-08-20) passed six cells but `metrics-port` failed before server boot because generated stub modid `metrics-port` violates NeoForge's modid regex. Fix harness and rerun before claiming current matrix proven.
- Scheduled bench run `32330406425` (2026-08-20, main `d269116`) passed JMH and ran the world bench/promtool checks, but failed world-bench `Compare against recorded baseline`; cause is not established. Do not describe nightly bench gate as green.
- P2 remains NOT READY until parity, chaos/R7 under opt-in flags, and Create/AE2 soak exit criteria are green.
- Reconcile P2 numbering: RFC-0007 reserves increment 7 for the planned single-join tick, while `WeftConfig` and RFC-0008 label block-entity sharding increment 7. RFC-0007 also still says owner-mail increment 6 is in progress despite implemented/green gate text elsewhere.
- Merge order checkpoint: #13 first; #15 only after draft removal/review; #14 last and only after current-head parity rereview plus non-vacuous fused/fallback gates. #14 head advanced from reviewed `432f831` to `1bf784c` with fallback-order fixes, so old review findings cannot be marked resolved without rereview. Current merge simulation found no textual conflicts, and all three carry identical shared PROJECT/BACKLOG/NEXT-QUEUE blobs.

## Lessons
- 2026-08-20: fair equal-heap ON/OFF Stressmark is tied and must be archived as
  field evidence, not as default-ON clearance. Keep ship posture NOT READY.
  Prefer merge/close of docs PRs over leaving superseded open PRs around.
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: dated evidence must stay scoped to matrix that ran; distinguish original four-cell neighbor proof from current seven-cell workflow.
- 2026-08-19: release workflow body had lagged from P1 shadow services to authoritative P1; audit tag-release copy alongside README status.
- 2026-08-19: reverified all five workflow files parse; README claims match chaos (four torn saves), seven-cell R7 neighbors, and nightly/manual bench gates. Ignore probes cover nested harness logs without swallowing unrelated `.log` files.
- 2026-08-20: PR #13/#14/#15 each had green PR build jobs (core, NeoForge build, informational engine benchmark), but build workflow does not run full GameTests, R7, chaos, or soak. PR #14 remains draft: latest head `1bf784c` attempts prior review blockers, but has no review on that head and its final full-suite rerun still failed before completion. PR #15 remains draft despite its branch report recording two local 24/24 GameTest passes.
- 2026-08-20: added `scripts/lab/STRESSMARK.md`: exact pinned-seed, fresh-world OFF/ON worldgen protocol. `singleJoinTick`, entity sharding, and BE sharding remain OFF; ON engagement requires at least two live regions and nonzero worker/task engagement. This protocol cannot be cited as soak or default-ON clearance.
- Commit-duty lesson: treat dirty shared memory and release evidence as one
  bounded docs increment when they describe the same checkpoint; verify flag
  names against `WeftConfig`, commit/push evidence first, then land a separate
  ignored `_session` commit report so reported SHA and push result are exact.
