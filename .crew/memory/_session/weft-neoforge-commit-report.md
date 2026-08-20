# weft-neoforge commit report

- Branch: `crew/neoforge-inc7`
- Prior landed SHA: `9f4557e69dacd597046d7893b2d267fc29bb9277`
- Checkpoint SHA: pending this report commit
- Push target: `origin/crew/neoforge-inc7`

## Commands run

- `git status --short --branch` and `git log -5 --oneline` — branch matched origin; only uncommitted crew lesson was meaningful dirty work.
- `rg -n --glob '*.java' --glob '*.toml' --glob '*.md' 'singleJoinTick|single_join_tick' .` — confirmed `WeftConfig.java` still uses `.define("singleJoinTick", false)`.
- `git diff --check` — PASS (line-ending warning only).
- `./gradlew.bat :weft-neoforge:compileJava -PwithNeoForge --no-daemon` — BLOCKED during configuration by unreadable global Gradle transform metadata at `C:\Users\Panda\.gradle\caches\8.14\transforms\39ff6dc936bdefdcd26ab663d9630bb8\metadata.bin`.
- Workspace-local `GRADLE_USER_HOME`, `--offline` retry — BLOCKED before compile because fresh cache lacked `net.neoforged.moddev:2.0.144`; wrapper download also reduced free disk to about 1.4 GB.
- `git ls-remote --heads origin crew/neoforge-inc7` — origin remained at prior landed SHA before checkpoint.

## Leftover risks

- Focused compile could not be repeated on this machine. Prior commit `1bf784c` records green `./gradlew build -PwithNeoForge` and `compileGametestJava` evidence.
- Full GameTest suite remains red after `p2fuse` passes because optional `parallelregionsentitysection` hits known `Entity is already tracked!` tracker contamination.
- `singleJoinTick` remains default OFF. Equal-heap Stressmark is tied (~39 vs ~40 MSPT), so no multi-region win or default-ON claim is justified.
- `.gradle-user-neoforge/` is untracked local cache output from verification and is intentionally not committed.

## Next owner

- `weft-parity`: isolate/fix GameTest entity-tracker contamination and rerun full suite.
- `weft-neoforge`: rerun focused compile after Gradle cache/disk repair; gather deterministic multi-bucket `p2fuse` evidence; keep `singleJoinTick` OFF.
