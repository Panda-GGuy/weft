# Release hygiene report

Date: 2026-08-19
Branch: `crew/release-hygiene`
Verdict: NOT READY

## Changes

- Broadened root `.gitignore` from `gametest-run*.log` to `gametest-*.log` and added `labserver-*.log`. These patterns target root/local harness log names only; source files and nested build output remain unaffected.
- Corrected README neighbor-matrix status: current workflow has seven cells and covers `SELF-DISABLED` as well as cooperate/yield/refuse. Kept the dated first-run claim explicit as the then-current four cells.

## Workflow/doc audit

- `chaos.yml`: matches README claim. Nightly and manual workflow runs `scripts/chaos/kill-save-test.sh`; harness defaults to four `save-all`/`kill -9` iterations and requires a clean final recovery boot.
- `neighbors.yml`: nightly and manual seven-cell matrix currently covers `spark`, `lithium`, `asyncpathfinding`, `servercore`, `scalablelux`, `metrics-port`, and `forgia`. README was stale at four cells and omitted the self-disable rung; fixed locally.
- `bench.yml`: matches README claim. Nightly and manual jobs run engine/services JMH plus the NeoForge GameTest benchmark world, publish both series to `bench-data`, and set `fail-on-alert: true` at a `150%` alert threshold. World-bench also checks captured Prometheus exposition with `promtool`.

## Honest-status/flag audit

README flag claims match `WeftConfig` defaults checked here:

- ON: `asyncPathfinding`; `spawnDensityMode = AUTHORITATIVE`.
- OFF: `activationScheduling`, `entitySharding`, `regionizedTicking`, `partitionedTicking`, `parallelRegions`, `blockEntitySharding`, `ownerMailRouting`, `legacyLane`, and `metricsEnabled`.

No defaults changed.

## Verification

- `git check-ignore -v --no-index gametest-foo.log gametest-run123.log labserver-foo.log` resolves all three through root `.gitignore`.
- `git diff --check` passes.
- Workflows were inspected statically; no nightly/manual workflow was dispatched and no full build was run because changes are ignore/docs/memory only.

## Release readiness

NOT READY. P2 parallel/ownership features still ship OFF pending stated parity, chaos/R7-under-flags, and Create/AE2 soak exit criteria. This hygiene pass found no reason to weaken those gates.

## Follow-ups

- Run/confirm current seven-cell neighbor matrix under CI; README only preserves dated proof for the original four-cell dispatch.
- Keep README neighbor count synchronized when cells change, or replace exact count with generated/current evidence.
