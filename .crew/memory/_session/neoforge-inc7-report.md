# NeoForge increment 7 report

Branch: `crew/neoforge-inc7`

## Delivered

- Wired `singleJoinTick` behind existing default-OFF config chain (`regionized + partitioned + ownerMailRouting`). Flag OFF leaves old paths untouched.
- Entity call-site now captures region buckets for fused mode; following BE seam performs vanilla collection, then dispatches one `FusedRegionTask` per region with ordered mail/entity/BE stages.
- Added per-level/per-region `PendingUnits` containers for ticker and NeoForge fresh-BE work. Entity-stage additions land live for same-tick BE execution; BE-stage additions defer. Topology split/merge changes rebalance retained units before dispatch. Deactivation flushes retained work back to vanilla lists.
- Re-anchored `sectionEndTasks` (hazards 14/21) after single fused join. Hazard 23: no nested shard submit under parallel fused tasks; only serial one-region fused path may shard.
- Preserved hazard-24/25 serial entity tail and added fail-loud cross-owner checks for BE/fresh additions.
- Added `/weft status` fused tick/region-task counts and `p2fuse` two-island engagement/equivalence gate.

## Verification

- `./gradlew build -PwithNeoForge` — PASS.
- `./gradlew :weft-neoforge:compileGametestJava -PwithNeoForge` — PASS.
- `git diff --check` — PASS.
- Full `runGameTestServer`: `p2fuse` ran and passed, then later optional `parallelregionsentitysection` crashed with `Entity is already tracked!` in `ChunkMap.addEntity` / `PersistentEntitySectionManager.processPendingLoads`. This is outside fused task stack and occurred after p2fuse completion. Therefore branch is build-green but full GameTest suite is not green; do not claim full gate.

## Gate / handoff

PR-ready wiring behind OFF. Named remaining gate: isolate/fix existing GameTest entity-tracker contamination, then rerun full suite. No default-ON change. No issue closes.
