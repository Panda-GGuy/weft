# Weft laws (always)

These bind every crew agent. Details: `docs/RFC-0001-weft-architecture.md`.

1. **Correctness is never opt-in.** Unknown mods run serialized (legacy lane). Never silently parallelize them.
2. **Ownership, not locks.** Mutable simulation state has one owner; cross-owner work uses mailboxes or explicit transfer.
3. **No Minecraft in pure modules.** `weft-engine`, `weft-api`, `weft-services` must not import `net.minecraft.*` or `net.neoforged.*` (build enforces).
4. **Seams before threads.** Parity gates exist before concurrency becomes load-bearing. Flags default OFF until exit criteria say otherwise.
5. **Fail-soft optimizations; fail-loud ownership.** Perf mixins may self-disable. Tick-ownership seams fail loud.
6. **Coexistence ladder.** cooperate -> yield -> self-disable -> refuse. Never mystery-corrupt a pack.
7. **Profiler-led speed.** Perf work cites measurement; model benches are not in-world claims.
8. **Vanilla save compatibility.** No custom save-format games; chaos kill-save stays sacred.
9. **Honest status.** README/RFC status matches flags and tests. No vibes-based "done."
10. **Portable memory.** Durable lessons go under `.crew/memory/**` so any clone can resume.

## Build reminders

- Core: `./gradlew build`
- With mod: `./gradlew build -PwithNeoForge`
- Engine benches: `./gradlew :weft-engine:jmh :weft-services:jmh`

## Design authority

- Architecture: `docs/RFC-0001-weft-architecture.md`
- Workstreams: `docs/RFC-0002-modernization-workstreams.md`
- Coexistence: `docs/RFC-0003-coexistence-policy.md`
- Sharding: `docs/RFC-0004-entity-sharding.md` + audit `docs/RFC-0008-intra-region-sharding-audit.md`
- Parity: `docs/RFC-0005-vanilla-parity-suite.md`
- Parallel regions: `docs/RFC-0006-parallel-region-execution.md`
- Free-running / mail: `docs/RFC-0007-free-running-regions.md`
