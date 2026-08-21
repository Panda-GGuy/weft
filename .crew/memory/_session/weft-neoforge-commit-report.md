# weft-neoforge commit report

- Branch: `crew/neoforge-inc7`
- Rebased code/report head before this report: `299bf6dc76cb4c2fe7860c8403302211cecac96b`
- Rebase base: `origin/main` at `a50886b4582718e2126d93e5ed0117493ab33d2f`
- Report commit: recorded after commit below; pushed head is authoritative.

## Commands run

- `git fetch origin` — updated `origin/main` from `d9caacb` to `a50886b` (PR #22 merge).
- `git rebase origin/main` — PASS; replayed eight branch commits without conflicts. No shared-memory conflict occurred, so no manual conflict resolution was needed.
- `.\gradlew.bat :weft-neoforge:compileJava :weft-neoforge:compileGametestJava -PwithNeoForge --no-daemon` — PASS (`BUILD SUCCESSFUL`).
- `.\gradlew.bat build -PwithNeoForge --no-daemon` — PASS (`BUILD SUCCESSFUL`, 27 actionable tasks: 3 executed, 24 up-to-date).
- `git diff --check` — PASS.
- `rg -n -F '.define("singleJoinTick", false)' weft-neoforge/src/main/java/dev/weft/neoforge/WeftConfig.java` — confirmed `singleJoinTick` remains default OFF.

## Evidence and leftover risks

- Existing `p2fuse` gate proves non-vacuous two-region fused task engagement, worker-thread fan-out, and independent-island state equivalence. Earlier full-suite evidence reached and passed `p2fuse`.
- Full `runGameTestServer` was not rerun in this checkpoint. Prior runs remain blocked later by optional `parallelregionsentitysection`: `Entity is already tracked!` from `ChunkMap.addEntity` / `PersistentEntitySectionManager.processPendingLoads`. No fused task appears in that crash stack; test isolation/tracker root cause remains open.
- Deterministic cross-region stage-overlap, pending-unit, and forced fallback assertions are still missing from `p2fuse`; PR #14 must remain draft until those gates and full-suite evidence exist.
- Equal-heap ON/OFF Stressmark is tied (about 39 vs 40 MSPT). This branch makes no multi-region win or default-ON claim. All P2 flags, including `singleJoinTick`, remain default OFF.

## Next owner

- `weft-neoforge`: add deterministic `p2fuse` overlap/pending/fallback probes without weakening canonical ordering; keep PR #14 draft and `singleJoinTick` OFF.
- `weft-parity`: isolate `parallelregionsentitysection` entity-tracker contamination, then provide repeat full-suite evidence.
