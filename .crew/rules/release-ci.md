# Rule: release and CI

Applies to Gradle and .github/workflows.

- Core build has no Minecraft requirement; NeoForge module is -PwithNeoForge
- Workflows: build, bench, chaos, neighbors, release must match real gates
- Do not fix flaky correctness by deleting assertions
- Status text in README must match flag defaults and CI reality
- Ignore local lab logs; do not commit gametest-*.log noise
