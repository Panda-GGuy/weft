# Rule: NeoForge mixins

Applies when editing weft-neoforge mixins/config/hooks.

- Ownership/tick-loop mixins: fail-loud (require=1) when silent miss is dangerous
- Optional perf mixins: fail-soft (require=0) + runtime applied check + self-disable
- One module, one switch (RFC-0003 R1)
- Do not widen mixin surface to dodge an engine redesign
- Parallel paths must account for RFC-0006 hazards (chunk read, RNG, registries, BE lookup off-thread)
- Default flags OFF for regionized/legacy/partition/parallel/mail/shard until parity approves
- Keep counters/probes for GameTests
