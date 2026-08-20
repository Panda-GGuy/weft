# weft-neoforge commit report

- Branch: `crew/neoforge-inc7`
- SHA: `1bf784cc40ceb802463198ee09c76bf98c4de5c4`
- Push: `origin/crew/neoforge-inc7` already matches SHA; `git push origin crew/neoforge-inc7` returned `Everything up-to-date`.

## Commands run

- `git status --short --branch` — clean at boot; branch matched origin.
- `git log -5 --oneline` — inspected landed increment history.
- `rg -n "singleJoinTick" ...` — confirmed config remains `.define("singleJoinTick", false)`.
- `git diff --check` — PASS.
- `./gradlew.bat :weft-neoforge:compileJava -PwithNeoForge --no-daemon` with workspace-local `GRADLE_USER_HOME` — BLOCKED before compile: dependency downloads failed with `There is not enough space on the disk`.
- `git push origin crew/neoforge-inc7` — `Everything up-to-date`.

## Leftover risks

- Current machine has critically low disk space; focused compile could not be repeated. Commit `1bf784c` already records prior green `./gradlew build -PwithNeoForge` and `compileGametestJava` evidence.
- Full GameTest suite remains red after `p2fuse` passes because optional `parallelregionsentitysection` hits known `Entity is already tracked!` tracker contamination.
- `singleJoinTick` remains default OFF. Do not enable by default without parity approval.

## Next owner

- `weft-parity`: isolate/fix GameTest entity-tracker contamination and rerun full suite.
- `weft-neoforge`: rerun focused compile after disk space is restored; make no default-ON change.
