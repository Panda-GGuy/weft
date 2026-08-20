# Release hygiene report

Date: 2026-08-19 (reverified 2026-08-19)
Branch: `crew/release-hygiene`
Verdict: NOT READY

## Changes

- Broadened root `.gitignore` from `gametest-run*.log` to `gametest-*.log` and added `labserver-*.log`. These patterns target root/local harness log names only; source files and nested build output remain unaffected.
- Corrected README neighbor-matrix status: current workflow has seven cells and covers `SELF-DISABLED` as well as cooperate/yield/refuse. Kept the dated first-run claim explicit as the then-current four cells.
- Corrected README and tag-release copy to match shipping flags: P1 services are authoritative/on by default, while implemented P2 ownership features remain default-off.

## Workflow/doc audit

- `chaos.yml`: matches README claim. Nightly and manual workflow runs `scripts/chaos/kill-save-test.sh`; harness defaults to four `save-all`/`kill -9` iterations and requires a clean final recovery boot.
- `neighbors.yml`: nightly and manual seven-cell matrix currently covers `spark`, `lithium`, `asyncpathfinding`, `servercore`, `scalablelux`, `metrics-port`, and `forgia`. README was stale at four cells and omitted the self-disable rung; fixed locally.
- `bench.yml`: matches README claim. Nightly and manual jobs run engine/services JMH plus the NeoForge GameTest benchmark world, publish both series to `bench-data`, and set `fail-on-alert: true` at a `150%` alert threshold. World-bench also checks captured Prometheus exposition with `promtool`.

All five workflow YAML files parse successfully in a static `PyYAML` check.

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
- `git status --short --untracked-files=all` is clean; no user logs were deleted.
- `git diff --check` passes.
- Workflows were inspected statically; no nightly/manual workflow was dispatched and no full build was run because changes are ignore/docs/memory only.

## Release readiness

NOT READY. P2 parallel/ownership features still ship OFF pending stated parity, chaos/R7-under-flags, and Create/AE2 soak exit criteria. This hygiene pass found no reason to weaken those gates.

## Follow-ups

- Run/confirm current seven-cell neighbor matrix under CI; README only preserves dated proof for the original four-cell dispatch.
- Keep README neighbor count synchronized when cells change, or replace exact count with generated/current evidence.
- Reconcile P2 increment numbering: RFC-0007 calls single-join increment 7,
  while `WeftConfig` and RFC-0008 call block-entity sharding increment 7.
- Refresh RFC-0007's DRAFT/in-progress owner-mail status after numbering is
  settled; code and gate text describe increment 6 as implemented and green.
