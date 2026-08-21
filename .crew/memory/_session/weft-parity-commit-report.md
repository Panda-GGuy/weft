# weft-parity commit report

- Branch: `crew/parity-close`
- Rebased head before this report: `886fc71dc49fbfb71dccccf9cc05cf11bd555be6`.
- Base: `origin/main` at `a50886b4582718e2126d93e5ed0117493ab33d2f`.
- Rebased `p2navdefer` gate commit: `0dfb58c`.
- Rebased full-suite evidence commit: `886fc71`.
- First post-rebase report checkpoint: `cd87a051a17f540dffcf46cebea4614c95189fe7`.
- Increment: hazard-21 `p2navdefer` gate, measured navigation-deferral path,
  and honest equal-heap evidence checkpoint.
- Default posture: unchanged. Regionized, partitioned, and parallel ticking remain default OFF; no default-ON approval granted.

## Commands run

- `git fetch origin --prune` — passed.
- `git rebase origin/main` — passed; shared-memory conflicts in
  `.crew/memory/shared/BACKLOG.md` and `.crew/memory/shared/PROJECT.md` resolved
  by taking `origin/main` versions as required.
- `.\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge` — passed
  after rebase (`BUILD SUCCESSFUL in 6s`).
- `git diff --check` â€” passed.
- `.\gradlew.bat clean -PwithNeoForge --no-daemon` â€” passed; removed only
  generated build outputs from this worktree.
- `.\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge --no-daemon`
  â€” reached NeoForm `patch`, then failed when C: exhausted free space while
  writing the patched archive (`This archive contains unclosed entries`).
- `.\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge` â€” blocked before compilation because shared Gradle cache metadata was missing at `C:\Users\Panda\.gradle\caches\8.14\kotlin-dsl\accessors\1a795bbada928f935016a29e154fa572\metadata.bin`.
- `$env:GRADLE_USER_HOME='C:\Users\Panda\weft-wt-parity\.gradle-local'; .\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge --no-daemon` â€” rebuilt dependencies, then failed in NeoForm `transformSources` after disk reached 0 bytes free. Temporary workspace cache contents were emptied after this run.
- `.\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge --no-daemon` â€” blocked during configuration by another missing shared-cache file: `C:\Users\Panda\.gradle\caches\8.14\transforms\39ff6dc936bdefdcd26ab663d9630bb8\metadata.bin`.
- `.\gradlew.bat --stop` â€” stopped one stale Gradle daemon before cache repair.
- `.\gradlew.bat :weft-neoforge:compileGametestJava -PwithNeoForge --no-daemon`
  â€” passed (`BUILD SUCCESSFUL in 48s`) after regenerating corrupt/missing cache
  metadata and freeing disk occupied by ignored GameTest output.
- `.\gradlew.bat :weft-neoforge:runGameTestServer -PwithNeoForge --no-daemon`
  â€” passed (`All 25 required tests passed`, `BUILD SUCCESSFUL in 2m 51s`),
  including required batch `p2navdefer`.
- `git diff --check` â€” passed before evidence commit.

## Leftover risks

- Hazard #3 now has focused compile and full-suite evidence and is ready for
  issue/PR review. Closing tracker state remains next-owner work.
- Probe deliberately enters real `sendBlockUpdated` mixin path from an owned worker bucket, while surrounding villager/zombie tick population proves two-region fan-out and serial Brain tail. It is a deterministic gate for deferral mechanics, not organic proof that current vanilla AI happens to emit same update each run.
- Hazard #6 remains OPEN pending `p2evictionchurn`; hazards 19/20, soak, chaos,
  and real-pack evidence remain absent. Default-ON remains BLOCKED.

## Next owner

- weft-parity/lead: review and close #3 using this evidence; then keep #6 with
  parity for a dedicated non-vacuous `p2evictionchurn` gate. Equal-heap
  Stressmark remains tied (~39 vs ~40 MSPT), so it is not default-ON or
  multi-region-win evidence.


