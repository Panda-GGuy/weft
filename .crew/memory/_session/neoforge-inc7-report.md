# NeoForge increment 7 report

Branch: `crew/neoforge-inc7`

## Delivered

- Wired `singleJoinTick` behind existing default-OFF config chain (`regionized + partitioned + ownerMailRouting`). Flag OFF leaves old paths untouched.
- Entity call-site now captures region buckets for fused mode; following BE seam performs vanilla collection, then dispatches one `FusedRegionTask` per region with ordered mail/entity/BE stages.
- Added per-level/per-region `PendingUnits` containers for ticker and NeoForge fresh-BE work. Entity-stage additions land live for same-tick BE execution; BE-stage additions defer. Topology split/merge changes rebalance retained units before dispatch. Deactivation flushes retained work back to vanilla lists.
- Re-anchored `sectionEndTasks` (hazards 14/21) after single fused join. Hazard 23: no nested shard submit under parallel fused tasks; only serial one-region fused path may shard.
- Preserved hazard-24/25 safety and added fail-loud cross-owner checks for BE/fresh additions. Fusion cannot use a post-join serial entity tail because an unsafe entity may add a same-tick BE; affected ticks now keep owner order and conservatively stand down fan-out. Unmapped fused ownership fails loud.
- Fresh-BE ticks stand down fan-out while `onLoad` work exists; steady-state ticks remain one parallel task per region. Ticker/fresh additions made on the server thread between sections are retained for the next owner BE stage.
- Fixed OFF transition ordering so retained units restore to vanilla only after fused interception is disabled, avoiding self-recapture.
- Added `/weft status` fused tick/region-task counts and `p2fuse` two-island engagement/equivalence gate.

## Verification

- `./gradlew build -PwithNeoForge` — PASS.
- `./gradlew :weft-neoforge:compileGametestJava -PwithNeoForge` — PASS.
- `git diff --check` — PASS.
- Earlier full `runGameTestServer`: `p2fuse` ran and passed, then later optional `parallelregionsentitysection` crashed with `Entity is already tracked!` in `ChunkMap.addEntity` / `PersistentEntitySectionManager.processPendingLoads`.
- Final full `runGameTestServer` rerun reached the same known optional tracker crash before suite completion. This remains outside fused task stacks. Therefore branch is build-green but full GameTest suite is not green; do not claim full gate.

## Gate / handoff

PR-ready wiring behind OFF. Named remaining gate: isolate/fix existing GameTest entity-tracker contamination, then rerun full suite. No default-ON change. No issue closes.
