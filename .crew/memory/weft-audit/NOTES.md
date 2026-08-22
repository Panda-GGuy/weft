# weft-audit memory

## Standing notes
- 2026-08-22: **singleJoinTick (RFC-0007 §4) hazard audit done, posted on PR #14.**
  Design fuses `[mail drain -> entity -> BE]` into one worker task per region,
  one barrier per level per tick (vs today's two). Classification for BOTH
  vanilla sections happens server-thread-only before either runs (verified in
  code); per-region `PendingUnits` correctly generalizes RFC-0006's
  region-confinement pattern; cross-owner adds fail loud; hazard-23
  (nested-submit deadlock) correctly re-derived and guarded. No data race
  found. Two hazards flagged as new/unaudited (not yet fixed, not yet blocking
  since flag stays OFF): (1) `sectionEndTasks` (hazard-14/21 deferred work)
  drains once after the WHOLE fused join instead of after entity-before-BE as
  today - ordering consequence unverified, no gate covers it; (2) unmapped
  topology lookups for fresh/moved units under fusion throw
  `IllegalStateException` (crash) where the partitioned path degrades to a
  counted serial tail - RFC-0006 hazard 24 needed a live soak to find this
  exact "eviction under churn" gap, and fusion has no equivalent soak yet.
  Also flagged: `forceSerial` stands down the WHOLE LEVEL (not just affected
  units) on any hazard-24/25 hit, so a single Brain mob could serialize an
  entire level's tick every tick it's active - PR's own Stressmark evidence
  used an arena that likely has no Brain mobs (same rig-gap shape as
  RFC-0006's furnace/armor-stand rig that hid hazard 18 for two increments).
  Full report: PR #14 comment https://github.com/Panda-GGuy/weft/pull/14#issuecomment-5382215067
  Did NOT flip singleJoinTick, did NOT clear the seam to ship - that's
  parity's/lead's call. `RegionTopology`'s class doc claims server-thread-only
  reads; fused mode's worker-thread `regionAtBlock` calls on mid-tick
  additions violate that (likely safe by construction - no concurrent writer
  during a fused tick - but undocumented and unasserted).

## Open threads
- Ordering hazard (sectionEndTasks post-fused-join drain) and unmapped-under-churn
  hazard (fail-loud vs graceful tail) both need dedicated gametests before
  singleJoinTick can be considered for "opt-in usable" status (RFC-0006 hazard
  22's bar). Neither is fixed by a design change per se - they need evidence.
- Level-wide forceSerial stand-down cost with Brain mobs present is unmeasured;
  recommend a Stressmark run with villagers before any perf claim either way.

## Lessons
- 2026-08-20: crew audit against live OmniRoute; model pins corrected to cc/cx/xao/gc.
