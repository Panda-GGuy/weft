# TESTING-0001 — How Weft gets tested, and why that changed

**Status:** standing strategy. Supersedes nothing; fills a gap nothing covered.
**Written:** 2026-08-18, the day four crashes were found in about two hours.

This is not a runbook. Commands rot; the reasoning behind them is what a future
session needs, and reference implementations live in `scripts/lab/`.

---

## 1. The finding that forced this document

On 2026-08-18 the suite stood at 23 gametests and two benchmarks, all green,
twice back to back. Then `parallelRegions` was enabled on one person's
single-player world for the first time. Inside roughly two hours it produced
**four distinct crashes**, every one of them a hard fault:

| | What it took to trigger |
|---|---|
| Hazard 21 | a villager opened a door |
| Hazard 22 | booting a world that had forceloaded chunks |
| Hazard 23 | turning on the two flags the shipped config turns on |
| Hazard 24 | pressing a teleport key |

Plus two instruments that were reporting false numbers: `weft_tps` published a
flat 20 on a server measurably doing 14, and `weft_block_entities_ticking`
published the sum of region ids.

None of that is a story about careless rigs. **Every rig was built to prove one
mechanism, and every one of them succeeded at that.** The problem is what
follows from it, and it is structural rather than an oversight:

- `p2parallelbench` sets `blockEntitySharding = false`, because isolating the
  variable is what makes its number mean anything.
- `p2shardbench` sets `parallelRegions = false`, for the same good reason.
- Every rig forceloads a fixed grid up front and never moves a player, because a
  stable world is what makes a measurement reproducible.
- Every rig spawns zombies and passive mobs, because they are cheap and uniform.

Each choice is correct in isolation and they share an unstated premise: **that
the world holds still, and that one thing is on at a time.** Neither is true of
a world someone is playing. So the class of bug that survives a green suite is
predictable: *interactions between features, and anything that happens while the
world is changing shape.*

---

## 2. The four kinds of test, and what each is for

### 2.1 Mechanism gates — "does this feature do what it claims?"

The existing gametests. One variable, everything else off, an assertion about
behaviour. `p2partition` holding two islands to bit-identical end states is the
model. These are load-bearing and nothing here reduces their importance.

**What they cannot do:** tell you whether two features that both pass will pass
together, or whether either survives a world in motion.

### 2.2 Combination gates — "does the shipped configuration work?"

New as of hazard 23, and the cheapest lesson available: **a recommended
configuration is itself a claim, and needs its own gate.** `TESTBUILD-0001`
shipped two flags on; that pair deadlocked; both single-flag benchmarks stayed
green throughout.

The rule is now in RFC-0006 §5: *no configuration may be shipped as recommended
unless some gate exercises it as shipped.* Combinatorial explosion is not an
excuse — you do not need every pair, only the ones you tell people to use.

A combination gate does not need to assert much. `p2combined` mostly needs to
*finish*: the deadlock it guards is caught by the test never completing. Which
is worth knowing — a hang is a legitimate and very strong failure signal, and it
costs one `timeoutTicks`.

### 2.3 The live soak — "does it survive a world that changes?"

The gap all four hazards came through. A soak is not a benchmark and must not be
judged like one; its output is *did anything trip*, not a number.

What a soak has to contain is derived directly from what each hazard needed, and
this list is the useful part of this document:

| Property | Why | Found |
|---|---|---|
| `Brain`-based mobs (villagers) and doors | AI that writes to level-wide structures | 21 |
| Chunk status changing under a running section | boot load-in, ticket release, pre-generation sweeps | 22, 24 |
| Block entities **on chunk boundaries** | one-block neighbour reads that cross into another chunk | 24 |
| Wide-reach block entities (vaults, trial spawners) | the serial-tail path, and >50% of units on a real world | 24 |
| ≥2 regions with real mass in each | the fan-out only engages at two buckets | 23 |
| Every shipped flag on at once | interactions | 23 |
| A player joining, moving, teleporting, leaving | ticket churn no script would think to write | 22, 24 |
| Real mod block entities (Create, AE2) | the entire mod-facing surface | — |
| A pre-generator running throughout | sustained load/unload churn | 24 |

That last row deserves emphasis: **install the actual mods.** For most of this
project's life the lab ran Weft alone, while the thing being tested is a mod
whose whole purpose is to run other mods. Copying a real pack's server-capable
jars into the dev server's mods folder takes one command and multiplies what a
soak covers. It should have been the default from the beginning.

### 2.4 Measurements — "is it faster, and do we believe the number?"

Covered in depth by `SectionAb`'s class documentation, which is the honest
record of three failed attempts. In brief: time the section not the tick,
interleave A/B/A/B rather than pairing, measure with the profiler off (it is
server-thread-confined, so it silently stops billing work that moves to
workers), and **carry a negative control** — a condition whose answer is known,
so a null result and a broken instrument are distinguishable.

---

## 3. Diagnostic disciplines that actually found root causes

Four habits did the work today. They generalise better than any of the fixes.

### 3.1 Distinguish a hang from a crash before anything else

They present identically to a player — "the game froze" — and lead to completely
different investigations. A crash leaves a report; a hang leaves nothing, which
is why hazard 23 was initially reported as a crash and had no crash file.

The exporter answers on its own thread, so it keeps serving while the tick loop
is dead. **Scrape twice and diff the tick counter.** Frozen counter with a live
endpoint is a hang; endpoint gone is a dead process. This one check redirected
the whole hazard-23 investigation in about ten seconds.

It also generalises to an automated watch: a monitor that takes a thread dump
itself and classifies before alerting is worth far more than one that pings a
human on every 90-second pause. The first version of ours cried wolf on a
pause menu.

### 3.2 Take the thread dump while the JVM is alive

Hang evidence is perishable — it dies when the player closes the game. Two dumps
minutes apart showing the **same awaited task object** is what turned "it seems
stuck" into "this is a permanent deadlock, here is the object". Find the process
by command line, not with `jps`: a launcher runs its own JVM that `jps` will not
list.

### 3.3 Read the call path before theorising

Hazard 22's first root cause was wrong, and the crash report said so. The
diagnosis was "chunk status doesn't hold still, this needs a region-readiness
invariant, its own RFC" — an architectural conclusion. The three stack traces
were all *short-reach reads at a chunk boundary*, which meant nothing was
reaching far and the real cause was a promotion future the parked main thread
could not complete. Bounded fix, same day.

The general form: an architectural explanation that arrives before the traces
have been read is usually the expensive way to be wrong.

### 3.4 Count every safety concession, and read the count

When a fail-loud guard is relaxed, publish a counter for the relaxed path. Twice
today that counter earned its cost immediately:

- The border-read counter caught the **first attempt at the hazard-22 fix**
  being scoped too broadly — 8,260,234 border reads in thirty seconds on a rig
  whose real border ring is a few hundred chunks. Scoping it correctly dropped
  that to 21,712.
- The hazard-24 gate's first version billed its deferrals to `unmappedUnits`, an
  invariant that must stay 0, and a soak reported 14,647 "unmapped" units. That
  would have silently broken an existing gate's assertion on any world with
  churn.

Both were found by looking at a number and finding it absurd. Neither was
findable by any test that existed.

---

## 4. Instruments lie in a specific direction

Three of today's false numbers shared a shape: **each one erred toward looking
healthy.**

- `weft_tps` used a median and capped at 20, so a server with 45% of ticks over
  budget published exactly the value meaning "fine".
- `weft_mspt_seconds` measures work inside `tickServer`, while the main thread
  also drains queued tasks between ticks — where a pre-generator's chunk work
  lives. 25 ms reported against a 71 ms real mean period.
- `weft_block_entities_ticking` summed region ids and read `6` on a world ticking
  thousands.

So: **a gauge that reads healthy is a claim requiring evidence, exactly like a
speedup.** Prefer publishing the distribution over the summary (the tick-period
histogram was what disproved the TPS gauge), and prefer a number that includes
the bad cases over one that is robust to them — robustness to outliers is right
for outlier *detection* and wrong for a rate.

---

## 5. Honest limits of the lab

- **The client is invisible to it.** The lab runs a dedicated server; rendering,
  Sodium/Iris interactions and client-side crashes cannot be reproduced there.
- **Scripted play is cruder than real play.** Hazard 24 came from a teleport at a
  moment no script would have chosen. A soak narrows the gap; it does not close
  it, and a real person on a real world remains the most productive test this
  project has.
- **A soak proves absence badly.** Zero faults in fifteen minutes is weak
  evidence; the value is in the faults it does find, and in fixes it re-verifies.
- **Never quote timing from a soak.** It churns deliberately and usually shares
  the machine. Numbers come from the benchmarks, with their controls.

---

## 6. What to actually do in a new session

1. Run the suite. It is fast and it catches regressions.
2. If any flag combination changed, check a combination gate exists for it.
3. Bring the lab up with a real pack installed and run a soak while doing
   anything else. It costs nothing to leave running.
4. If something freezes: hang or crash first (§3.1), dump while it lives (§3.2),
   read the traces before forming a theory (§3.3).
5. If a number looks good, distrust it until a control or a distribution backs it
   (§4, and `SectionAb`).

The one-line version, which is the only part worth memorising:

> **Mechanism gates prove features. Interactions and moving worlds are what
> break them, and neither shows up in a rig that holds still with one flag on.**
