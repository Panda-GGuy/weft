# weft-engine memory

## Standing notes
- Increment 6 (`ownerMailRouting`) is SHIPPED in code: config flag (WeftConfig:222, default OFF, requires partitionedTicking via applyActive), routing (`OwnerMail.runOwned`, server-thread-only lookup at INGEST), bucket-head drains in both sections of `RegionizedTicking`, stranded-mail sink + deactivation flush, counters + `/weft status` line, `p2mail` gametest batch, engine `RegionMailTest`. Testbuild config even flips it on (`testbuild/weft-common.toml:78`).
- Vanilla 1.21.1 evidence (decompile-verified, see `docs/NOTE-0001-serverlevel-tick-window-evidence.md`): the window between `entityTickList.forEach` (ServerLevel:400) and `tickBlockEntities()` (ServerLevel:428) contains only `profilerfiller.pop()` — RFC-0007 §4's go/no-go audit item is a GO, seam does not move.
- Vanilla pending-BE semantics: iteration set fixed at `tickBlockEntities` entry (pending merged Level:552-555); entity-stage adds land live (`tickingBlockEntities` false during entity section) and tick same tick; mid-tick adds defer one tick. `PendingUnits` replicates this per region.
- Increment 7 scaffolding landed (branch crew/engine-inc7): engine `PendingUnits<T>` (region pkg) + `WeftScheduler.runOwnedFused(List<FusedRegionTask>, parallel)` (one context, ordered stages, single join, serial-equivalent path, counted even on crash); loader `singleJoinTick` flag (default OFF, requires partitionedTicking AND ownerMailRouting, resolves in `RegionizedTicking.applyActive`, test switch `setSingleJoin`, status-line probe). NO tick-path behavior keyed on the flag yet.

## Open threads
- Inc 7 loader wiring (weft-neoforge surface): per-region ticker capture must feed `PendingUnits` instead of the global `blockEntityTickers` iteration; the `LevelRegionTickMixin` collection pass must move BEFORE entity fan-out; NeoForge's `freshBlockEntities` onLoad flush needs the same per-region treatment; `p2fuse` gametest (two-island, A's BE done while B's entities run). Hazard re-audit: RFC-0006 mitigations that assume "server thread parked between sections" (sectionEndTasks drain, hazard 14/21 defers) must re-anchor to the single join.
- Hazard-23 interaction: `shardThisSection` predicts fan-out from bucket count per section; under fusion the fan-out decision is per-tick, prediction logic must follow.

## Lessons
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: decompiled sources live in `~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar` — extract with ZipFile, don't guess vanilla behavior.
- 2026-08-19: `apply_patch` heredocs break on CRLF in this PowerShell env; use `[System.IO.File]::ReadAllText/WriteAllText` with explicit `` `r`n `` anchors for CRLF loader files (engine files are LF).