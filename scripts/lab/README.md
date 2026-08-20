# scripts/lab — the live lab

Reference implementations for the testing strategy in
[TESTING-0001](../../docs/TESTING-0001-how-weft-gets-tested.md). Read the
strategy first; these are examples of the *kinds* of test it argues for, not a
fixed runbook, and they are meant to be edited per investigation.

They exist because a green 23-test suite missed four crashes that about two hours
of ordinary play found. The suite is not the problem — every rig proves one
mechanism and succeeds at it. What nothing covered was **feature interactions and
worlds that change shape while sections run.**

| | |
|---|---|
| `install-pack.sh <instance>` | Copies a real pack's server-capable mods into the dev server. The single highest-value step here: for most of this project's life the lab ran Weft alone, while Weft's entire purpose is running other mods. Skips client-only mods, and skips Moonrise deliberately (RFC-0006 hazard 20). |
| `soak.py` | The live soak. Not a benchmark — its output is *did anything trip*. Builds a world with every property the gametest rigs individually lack (see TESTING-0001 §2.3's table) and churns it. Fails fast on nonzero `unmapped units` or `domain trips`. |
| `eviction-repro.py` | Narrow repro for RFC-0006 hazard 24: a block entity on a chunk boundary whose one-block neighbour read crosses into an evicted chunk. A template for "reproduce the crash before fixing it". |
| `hang-triage.sh` | Is it hung or dead? They look identical to a player and lead to opposite investigations, and a hang leaves no crash report. Answers in ten seconds, then dumps twice. |
| [`STRESSMARK.md`](STRESSMARK.md) | Exact fresh-world, pinned-seed Stressmark A/B protocol. It fixes pack, seed, flags, bot load, duration, evidence, and pass criteria while keeping the result separate from soak and default-ON clearance. |

Prerequisites: the dev server with rcon (`weft-neoforge/run/server/server.properties`
already has it), and `metricsEnabled = true` in `weft-common.toml` for anything
reading the exporter.

Two things worth carrying over more than the scripts:

- **Count every safety concession and read the count.** A counter on a relaxed
  fail-loud path caught two bad fixes of mine within one run each, including one
  where a fix scoped too broadly produced 8.26M "border reads" in thirty seconds.
- **A gauge that reads healthy is a claim needing evidence.** Three instruments
  were found lying, all in the flattering direction. Prefer the distribution over
  the summary.
