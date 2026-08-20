# Release hygiene report

Date: 2026-08-19 (reverified 2026-08-20)
Branch: `crew/release-hygiene`
Verdict: NOT READY

## Changes

- Broadened root `.gitignore` from `gametest-run*.log` to `gametest-*.log` and added `labserver-*.log`. These patterns target root/local harness log names only; source files and nested build output remain unaffected.
- Corrected README neighbor-matrix status: current workflow has seven cells and covers `SELF-DISABLED` as well as cooperate/yield/refuse. Kept the dated first-run claim explicit as the then-current four cells.
- Corrected README and tag-release copy to match shipping flags: P1 services are authoritative/on by default, while implemented P2 ownership features remain default-off.
- Added `scripts/lab/STRESSMARK.md`, an exact OFF/ON fresh-world protocol pinned to seed `8300982059889285399`, 20 Stressmark bots, speed 8.0, and 120 seconds. It preserves existing worlds, keeps all shipping defaults unchanged, and explicitly excludes soak/default-ON claims.

## Live PR checkpoint (2026-08-20)

- **#13** (`8f235ae`, docs queue): open, non-draft, GitHub `MERGEABLE/CLEAN`; core, NeoForge build, and informational engine benchmark pass. Mechanically ready to merge first. It is memory/docs only and supplies the shared queue blobs used by #14/#15.
- **#15** (`b97bd28`, parity cleanup): open draft, GitHub `MERGEABLE/CLEAN`; same three PR jobs pass. Branch report records compile plus two local full-suite passes (24/24) and fixes the test-only entity-id collision, but GitHub CI does not run that suite. Keep after #13 and draft-blocked pending owner/review disposition.
- **#14** (`1bf784c`, fused wiring): open draft, GitHub `MERGEABLE/CLEAN`; same three PR jobs pass. Head advanced after parity reviewed `432f831`; new commit attempts hazard-24 and entity-tail ordering fixes by conservatively standing down fan-out. It has no parity review on current head, adds no non-vacuous fallback/fusion coverage, and its final full-suite rerun again stopped at `Entity is already tracked!`. Keep merge blocked pending current-head rereview and green named gates. No default-ON clearance.

All three PR heads were one commit behind current main `d1095cc` when checked.
Their PROJECT/BACKLOG/NEXT-QUEUE blobs are byte-identical. `git merge-tree`
checks found no present textual conflicts for #13+#14, #13+#15, or #14+#15.
Recommended order is #13, then #15 after it is ready, then #14 only after current-head
rereview accepts its fixes and named gates rerun. Rebase/update each head on current main before
final merge and rerun required checks; clean mergeability today is not future
conflict proof.

## Workflow/doc audit

- `chaos.yml`: matches README claim. Nightly and manual workflow runs `scripts/chaos/kill-save-test.sh`; harness defaults to four `save-all`/`kill -9` iterations and requires a clean final recovery boot.
- `neighbors.yml`: nightly and manual seven-cell matrix currently covers `spark`, `lithium`, `asyncpathfinding`, `servercore`, `scalablelux`, `metrics-port`, and `forgia`. README was stale at four cells and omitted the self-disable rung; fixed locally.
- `bench.yml`: matches README claim. Nightly and manual jobs run engine/services JMH plus the NeoForge GameTest benchmark world, publish both series to `bench-data`, and set `fail-on-alert: true` at a `150%` alert threshold. World-bench also checks captured Prometheus exposition with `promtool`.

All five workflow YAML files parse successfully in a static `PyYAML` check.

Current scheduled evidence is not all green:

- Main build at `d1095cc` passed core, NeoForge, and informational engine benchmark jobs.
- Chaos run `32331615939` passed its four torn-save iterations and recovery check.
- Neighbors run `32333623722` passed six of seven cells. `metrics-port` failed before server boot because `stub-metrics-port.jar` declares invalid modid `metrics-port` (hyphen violates `^[a-z][a-z0-9_]{1,63}$`). This is a harness failure, not evidence that the self-disable behavior passed.
- Bench run `32330406425` passed JMH; world benchmark execution, result display, and `promtool` passed, but `Compare against recorded baseline` failed. No cause or performance regression is asserted from the available output.

## Honest-status/flag audit

README flag claims match `WeftConfig` defaults checked here:

- ON: `asyncPathfinding`; `spawnDensityMode = AUTHORITATIVE`.
- OFF: `activationScheduling`, `entitySharding`, `regionizedTicking`, `partitionedTicking`, `parallelRegions`, `blockEntitySharding`, `ownerMailRouting`, `legacyLane`, and `metricsEnabled`.

No defaults changed.

README's P2 headline and increment-5 remaining-work text omitted implemented
owner-mail routing and block-entity sharding. Both now appear as default-OFF,
contract-gated increments; single-join and soak work remain open. Release
workflow copy also drifted: it still called P1 services shadow-only and claimed
no simulation rerouting. It now says P1 is authoritative and P2 ownership stays
default-off.

## Verification

- `git check-ignore -v --no-index gametest-foo.log gametest-run123.log labserver-foo.log` resolves all three through root `.gitignore`.
- Nested probes (`src/gametest-example.log`, `src/labserver-example.log`) are also ignored; unrelated `real-source.log` is not, so the rule does not become a generic `*.log` ignore.
- The previously committed hygiene increment was clean before this follow-up.
  This follow-up inventories and preserves all seven dirty release-doc/shared-memory
  paths; no user logs or existing work were deleted.
- `git diff --check` passes.
- Workflows were inspected statically; no nightly/manual workflow was dispatched and no full build was run because changes are ignore/docs/memory only.
- Live PR/check/workflow state was read through GitHub on 2026-08-20. No workflow was dispatched, no PR was merged, and no default was changed.
- Existing dirty shared PROJECT/BACKLOG/NEXT-QUEUE work was preserved; no reset, cleanup, world deletion, or user-data deletion occurred.
- `scripts/lab/STRESSMARK.md` requires unique new level names and forbids deleting, copying, or reusing existing worlds.

## Release readiness

NOT READY. P2 parallel/ownership features still ship OFF. PR #14 lacks current-head parity acceptance and green named gates, current seven-cell R7 and bench schedules are not green, #3/#6 gates remain open, and no qualifying Create/AE2 soak was run. Stressmark field/worldgen evidence is not soak and provides no reason to weaken those gates.

## Follow-ups

- Keep README neighbor count synchronized when cells change, or replace exact count with generated/current evidence.
- Reconcile P2 increment numbering: RFC-0007 calls single-join increment 7,
  while `WeftConfig` and RFC-0008 call block-entity sharding increment 7.
- Refresh RFC-0007's DRAFT/in-progress owner-mail status after numbering is
  settled; code and gate text describe increment 6 as implemented and green.
- Fix the `metrics-port` neighbor harness so occupied-port behavior is tested with a valid inert stub modid, then rerun all seven cells.
- Diagnose/rerun bench baseline comparison before describing nightly bench as green.
- Rebase and merge #13 first. Process #15 before #14 once #15 is non-draft and reviewed. Do not merge #14 until parity rereviews current head and named non-vacuous gates pass.
