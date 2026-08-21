# Held-back work (not on a branch tip yet)

## `p2fusefallback-positive-control.patch`

**What it is.** A positive control GameTest for `fusedSerialFallbacks`, in
`PartitionedTickingGameTests`. Apply with:

```
git apply .crew/wip/p2fusefallback-positive-control.patch
```

Verified to apply cleanly against `1aff76c`.

**Why it exists.** The `p2fuse` gate proves sustained fan-out by computing
`fannedOut = fusedTicks - fusedSerialFallbacks`. In a healthy rig the
subtrahend is `0` every tick, so that assertion cannot distinguish a working
counter from one that is wired up wrong and can never increment. This test
forces the stand-down (places a fresh block entity, which makes `hasFresh`
true and keeps the tick on the server thread) and asserts the counter moved,
then cleared.

**It already paid for itself.** It is what found the fused fresh-BE leak fixed
in `1aff76c`: `duringStandDown=0` on the first run proved the counter never
moved, and the follow-up `52 of 52` proved `fresh` never emptied.

**Why it is NOT committed.** It adds a GameTest batch. Adding *any* batch
reshuffles GameTest batch order, and that exposes a **pre-existing order
dependence** in `p2memoryreach` / `brainMobsNeverReachAWorker`:

```
brainmobsneverreachaworker failed!
Entity section ran 1 bucket(s); with mobs in both islands it must fan out
```

That test reads `lastEntityPartition()`, a global last-section probe, and it
only fans out if the preceding batch left topology in the right shape.

**Proof it is pre-existing, not caused by the engine fix** (this is the check
that matters — do not re-litigate it from scratch):

| Working tree | Result |
|---|---|
| engine fix only, gametests at `HEAD` | **26/26 pass** |
| engine fix + `p2fuse` strengthening (no new batch) | **26/26 pass** |
| engine fix + new `p2fusefallback` batch | `brainmobs` fails |

So the engine fix is exonerated; the new batch only *reveals* the flake.

### Next session

1. Fix the order dependence in `brainMobsNeverReachAWorker`. It should force
   its own topology/fan-out precondition instead of inheriting whatever the
   previous batch left in the global probe. (Related known-unreachable note:
   a ticking rig keeps its own neighbourhood loaded by construction.)
2. Then apply the patch, run the suite twice, and commit the control.
3. Only after that is `p2fuse`'s sustained-fan-out assertion fully
   load-bearing.

### Separately observed flake (not order-related)

One run of `p2parallel` failed with `entity=[2] be=[2, 16] A=2 B=16` — the
entity probe saw one island where the BE probe saw both. It passed on an
immediate re-run with an identical tree. Unexplained; worth a look, since a
single-bucket entity section is the same shape `brainmobs` trips on.
