# RFC-0005: The Vanilla-Parity Suite — What "Equivalence" Means

**Status:** Draft 1
**Depends on:** RFC-0001 (§10 testing strategy, §11 P2's exit criterion is
"vanilla parity suite green"), RFC-0004 (§2.5 documents the one deliberate
ordering divergence this suite must eventually judge)
**Scope:** The measurement instrument for every P2 tick-ownership increment.
This document defines *what is asserted*, *why that definition is honest where
vanilla itself is nondeterministic*, and *the ladder of equivalence classes*
later increments must climb. The P1 lesson is the founding rule here: the
graduation gate caught a real correctness bug (the full-chunk-future gate)
because the harness existed **before** the change it judged. P2's changes are
strictly more dangerous, so the suite lands before the first ownership mixin
changes anything.

---

## 1. The instrument

`WorldDigest` (weft-neoforge, `dev.weft.neoforge.parity`) captures a sorted
map of human-readable entries over an arena:

- **Entities** (players excluded): type, custom name, bit-exact position /
  rotation / velocity (hex-rendered doubles — never rounded), age in ticks,
  on-ground flag, health, item stack for item entities, block state for
  falling blocks, passenger count. Keyed by the description itself, so a
  mismatch names the entity, not an index.
- **Block entities**: type plus the *full* NBT of `saveWithoutMetadata`,
  canonicalized (compound keys sorted; list order preserved because list
  order is semantic — inventory slots). Furnace/hopper progress counters make
  this the highest-resolution per-tick canary in the digest.
- **Blocks**: one rolling hash per chunk column over every block state in the
  arena box, so a mismatch localizes to a chunk.

Two runs are **bit-identically equivalent** iff their digests are equal.
`WorldDigest.diff` renders the first differences readably — a bare hash
mismatch is useless for debugging, and this suite exists to be debugged with.

Digest comparisons are only meaningful within one JVM/server instance
(registry ids and NBT canonicalization are stable per run, not per install);
the suite always compares runs from the same server.

## 2. What is deliberately excluded, and why

Vanilla is not deterministic run-to-run, and pretending otherwise produces a
suite that flakes until someone mutes it. Each exclusion is a documented
decision, not an accident:

- **Entity ids and UUIDs.** Fresh every run. UUIDs are pure identity. Ids,
  however, *leak into behavior* wherever vanilla staggers per-entity work by
  id — so ids are not digested but they ARE controlled: each scenario entity
  receives an id from a fixed reserved range before it enters the level. The
  JVM-global allocator is never rewound: async chunk loads may retain an id
  allocated before a rewind and later collide with a newly allocated entity.
- **Absolute game time.** Runs start at different `gameTime`s by construction
  (same world, sequential runs). Anything keyed to absolute time would
  spuriously differ, so entities are digested by explicit fields rather than
  full NBT. Block entities in the suite's scenarios store only *relative*
  counters (cooldowns, burn/cook progress), so their full NBT stays in.
  A scenario element whose NBT embeds absolute time must either be excluded
  from the scenario or given a digest exclusion — and the control phase (§3)
  is what catches the attempt to sneak one in.
- **Light levels.** Vanilla computes lighting asynchronously off-thread;
  values can lag nondeterministically. Nothing in the suite's scenarios
  observes light, and light is not part of the digest.

## 3. The control-run discipline (the load-bearing idea)

Where vanilla is nondeterministic, we do not relax the assertion — we
*control the source* and then **prove the control worked** before judging
Weft:

1. **Controlled sources.** The GameTest server already pins mob spawning,
   weather, random ticks, and fire off. The harness additionally reseeds
   `level.random` at every run start, seeds every spawned mob's own RNG,
   pins scenario entity ids, freezes time at midnight, spawns mobs raw
   (no `finalizeSpawn` random rolls), makes them persistent and penned, and
   forces every other Weft optimization module inert so exactly one variable
   distinguishes the runs.

   One source is structurally *uncontrollable* and therefore banned from the
   scenario instead: `level.random`'s draw position. Vanilla shuffles the
   global ticking-chunk list with `level.random` every tick, consuming a
   draw count that depends on the whole server's ticking-chunk set — which
   other activity (other test batches' chunk unloads, players) legitimately
   changes between runs. Reseeding at run start cannot fix that, so **no
   scenario element may observably depend on `level.random`** (e.g. a
   dropper ejects into a container — a zero-RNG transfer — never into the
   air, where dispense velocity spread would digest differently every run).
   The control phase is what enforces the ban empirically; it caught exactly
   this via a single diverging dispensed item.
2. **The control phase.** The suite runs the identical scenario **twice
   under pure vanilla ticking** and requires the digests to be equal
   *bit-identically*. If the control fails, the suite fails loudly as a
   harness bug: a nondeterministic harness that "passes" would be worse than
   none, because it certifies nothing.
3. **Only then the judged phase.** With the control green, any A-vs-B
   difference is attributable to Weft — noise has been proven out of the
   experiment, not assumed away.
4. **Vacuous-run guards.** The judged run must show the engine actually
   owned the tick sections (owned-section counters), and the digest must
   show the scenario actually ran (furnace output produced, clocked machines
   fired, all mobs present). A gate that can pass on an empty arena or an
   inert flag is not a gate. This guard caught its first real gap the day it
   was written — the initial scenario's digests compared equal while
   capturing zero entities.

## 4. The equivalence ladder

Not every P2 increment can — or should — meet the same bar. Each increment
declares the class it must pass **before** it can be enabled anywhere:

| Class | Assertion | Applies to |
|---|---|---|
| **E0 — bit-identical** | Digests equal, full stop. | Increment 1 (degenerate serial ownership: same thread, same order — identical by construction, so any mismatch is a seam bug). Also any increment that claims not to change execution (real chunk→region assignment while still serial; the legacy lane running everything). |
| **E1 — order-independent state** | Digests equal after documented order-sensitive fields are canonicalized (e.g. same-tick pickup contention winners), plus E2. | Parallel region execution across *independent* regions — cross-region interleaving has no defined vanilla order (RFC-0001 §6.6). |
| **E2 — conservation invariants** | Totals identical: entity counts by type, total damage dealt, items created/destroyed/moved, breeding events; per-region RNG replay identical at fixed thread count. | WS-10 sharding (RFC-0004 §2.5 documents within-tick interleaving divergence as the one honest tradeoff) — plus E1 canonicalization where applicable. |

The rule: **an increment ships off-by-default until its declared class is
green in CI**, and an increment claiming E0 may not be weakened to E1/E2
after the fact without an RFC note explaining why the stronger claim was
wrong.

## 5. Current status

- The suite (`VanillaParityGameTests`, batch `p2parity`, hard gate) runs the
  three-phase control-then-judge protocol over `ParityScenario`: redstone
  clocks (hopper-pair clocks, comparators, lamp), a sticky piston + observer,
  a clocked dropper feeding a chest, flowing water pushing item entities into
  a collection hopper, a chest-to-chest hopper chain, four furnaces
  mid-smelt, falling-block columns, and ten penned, seeded, named mobs
  (zombies + sheep).
- Increment 1 (`regionizedTicking`, default off) is judged at **E0** and
  green locally and nightly (see README Status for dated numbers).
- Increment 6 (`ownerMailRouting`, default off) is judged at **E0**: routing
  changes *where* owner mail is applied (the owning region's bucket head
  instead of global INGEST), not what any unit computes. The delivery
  contract is "before the owner's simulation each tick," documented in
  RFC-0007 §3.2 — a note here because a future mail client sensitive to
  pre-entity vanilla steps (weather, scheduled ticks) would need E-class
  judgment of its own; every current client is provably indifferent.
- E1/E2 canonicalization and conservation capture are not yet implemented —
  they land with the increments that need them, never later than them.
  Free-running regions in v1 (RFC-0007) deliberately stays within E0/E1;
  a genuinely new class (temporally-decoupled comparison) is only needed by
  v2's per-region TPS isolation, which RFC-0001 §4.3 defers.

*End of RFC-0005 draft 1.*
