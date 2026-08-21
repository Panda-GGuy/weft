# Shared project memory

Last updated: 2026-08-20 late (reconciled against `origin/main` `65a727a`
after #23/#24/#15 landed)

## What Weft is

Multithreaded server engine for modded Minecraft (NeoForge 1.21.1). Regionized
ticking + graph layer + legacy lane for unknown mods.

## Phase status

- P0 complete (profiler).
- P1 complete (off-thread services; async pathfinding + spawn density defaults ON).
- P2 open. Regionized/partitioned/parallel/owner-mail/BE-shard remain behind
  default-OFF flags.
- Increment 6 owner-mail routing and increment 7 engine scaffold are on main.
- Increment 7 loader fuse wiring is draft PR #14. Head `97b9254` contains code
  head `1bf784c`, which routes
  failed fused readiness through serial owner order and preserves
  entity-before-BE ordering. CI is green, but parity re-review and deterministic
  `p2fuse` overlap/pending/fallback evidence remain required; its full GameTest
  runs still hit the known optional entity-tracker crash before suite completion.
- P3+ not started (graph adapters, race detector, production tag).

## Current field evidence

- 2026-08-20 SP Create/AE2 stress: opt-in run had better measured latency tails,
  but `owned parallel=0` was often observed. Do not attribute result solely to
  multi-region fan-out.
- Moonrise + `parallelRegions` crash is fixed by posture on main (`72e4df7`).
  Never co-enable Moonrise + parallel for soak: hazard 20's first route is
  unbuilt, so co-enabling is unsupported rather than untested.
- Verified live 2026-08-20 on a clean single-player-shaped server with the full
  parallel stack ON: `topology: 1 regions`, `owned serial=2399 parallel=0`.
  One region means `parallelRegions` is architecturally inert - this is the
  configuration the field notes mistook for a win, and it is now printed.
- Durable source: `shared/FIELD-2026-08-20.md`.

## Pull request disposition

- #23 MERGED (`72e4df7`): Moonrise posture + transitive disarm + R7 cell.
- #24 MERGED (`17eb70f`): the parity GameTest suite now runs on every PR. It
  previously ran only on bench.yml's 03:17 UTC nightly, so a PR could be
  reviewed against three green checks with the hard correctness gate unrun.
  Verified green on CI hardware in ~5 min.
- #15 MERGED (`65a727a`): parity harness cleanup + `p2navdefer` + the
  hazard-25 counter split. Two consecutive local 25/25 suites plus CI.
- #25 OPEN: fan-out honesty line in `/weft status`. Verified live in all three
  states (off / DISARMED / active-but-single-bucket).
- #14 fused loader path: still draft, default OFF, and now REBASE-STALE - it
  predates #15's harness changes and the new PR parity gate, so its next push
  will be gated by the full suite automatically. Needs deterministic
  `p2fuse` overlap/pending/fallback proof and two complete suites.
- #13 closed/superseded earlier.

## Open issues

- #6 hazard 24 is the ONLY open issue. Still needs `p2evictionchurn`:
  boundary BE, live-then-evicted neighbour, unrelated fan-out engaged,
  `unreadyUnits > 0`, `unmappedUnits == 0`, serial tail, zero guard trips.
- #16 CLOSED (main `72e4df7`): Moonrise yields tick-ownership modules, disarm
  proven transitively, R7 cell + negative control. RFC-0006 hazard 20 promoted
  candidate -> confirmed. Hazard 20's first route (worker read path expressed
  against a Moonrise-preserved surface) remains unbuilt, so co-enabling is
  unsupported, not merely untested.
- #3 hazard 21 CLOSED (main `65a727a`): `p2navdefer` drives the real mixin seam
  from an owned worker bucket, asserts two-region fan-out with non-server
  threads, deferral completion, fail-loud server-thread drain, and the villager
  serial tail via its own counter.
- #4/#5/#7/#10/#11 closed. #15's harness/accessor cleanup is now on main.

## Release posture

NOT READY. No P2 ownership/parallel flag may default ON. Remaining gates:
#6 `p2evictionchurn`, corrected PR #14 fusion contract, hazards 19/20 re-audit,
real-pack soak, chaos/R7 refresh, and at least one world demonstrating
`entity_buckets > 1` with `owned parallel > 0`. Until that last one exists there
is no measured parallel-region win to claim at all - every bench so far ran at
one region.

## Next work

See `shared/NEXT-QUEUE.md` for actionable parity/release/compat handoffs.

## OmniRoute

Pins and provider fallbacks live in `.crew/ROUTING.md`. On provider limits,
checkpoint branch, dirty files, checks, next command, and blocker before walking
fallbacks; never reset a dirty worktree.

## Hard constraints

See `.crew/laws.md`.
