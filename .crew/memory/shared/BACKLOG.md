# Shared backlog (living)

- [x] Verify OmniRoute provider/model ids live (2026-08-20)
- [x] Pin real fullModel ids in .crew/ROUTING.md
- [x] Create OmniRoute combos: weft/plan, weft/code, weft/correctness, weft/perf, weft/audit, weft/cheap
- [x] OpenCode free confirmed connected (oc/*); CHEAP=oc/big-pickle
- [x] Inc7 scaffold on main (PendingUnits, runOwnedFused, singleJoinTick OFF, NOTE-0001)
- [x] #16 Moonrise posture MERGED (`72e4df7`), issue CLOSED (R7 cell + negative control)
- [x] Parity GameTest suite gates every PR (#24, `17eb70f`) - previously nightly-only
- [x] #15 MERGED (`65a727a`): `p2navdefer` + hazard-25 counter split; #3 CLOSED
- [x] PR #25 fan-out honesty MERGED (`7cbce4a`); verified live in all 3 states
- [x] PR #27 MERGED (`30455f4`): `unreadyBlockEntityUnits` counter for hazard 24
- [x] PR #14 rebased onto main (`4b26705`) and compiling; parity gate now judges it
- [ ] NOW: #6 `p2evictionchurn` BLOCKED - 5 rigs failed on the precondition, not
      the assertion. Vanilla guarantees radius-2 generated FULL around any
      entity-ticking chunk, so a forced-grid gametest cannot make a neighbour
      absent. Next: real ticket churn (moving player / Chunky sweep) or a soak
      harness. Four rejected designs are in `weft-parity/NOTES.md` - read first.
- [ ] NOW: PR #14 review - needs deterministic `p2fuse` overlap/pending/fallback
      proof; flag stays OFF
- [ ] READY: a world with `entity_buckets > 1` AND `owned parallel > 0`. No
      parallel-region win is claimable until one exists - every bench so far ran
      at 1 region, where parallelRegions is architecturally inert
- [ ] READY: RFC/README/field-bench status hygiene (weft-release)
- [ ] READY: weft-audit re-anchor review after corrected single-join patch
- [ ] P2 soak under flags (Create/AE2, chaos, neighbors; never Moonrise+parallel)
- [ ] Entity sharding research path (deferred; pushEntities hazard) per RFC-0008
- [ ] P3 graph layer consumer + first adapter (activate weft-graph)
- [ ] Top-pack gauntlet automation expansion
- [ ] Production default policy (dedicated vs integrated server)

Queue detail: .crew/memory/shared/NEXT-QUEUE.md
