# RESEARCH-0003: Integrate-Don't-Reinvent, Hookable Add-Ons, and Errata

**Status:** Living document, first pass (2026-08-18)
**Purpose:** Two jobs in one pass. (1) Find work that already exists and
**hook into it** instead of rebuilding it, and — where something is partial,
absent, or closed to integration — name the **hook Weft should expose** so the
gap can be filled by whoever gets there first. (2) **Fact-check** the existing
RFC/RESEARCH set against live primary sources and against the repo's own
current state, and correct what has gone stale or was overstated. Errata
includes corrections to *this author's own* prior documents.
**Method:** Modrinth v2 API (project + per-version loader queries, filtered to
`1.21.1`), GitHub API/raw for READMEs, config docs, published API interfaces
and `mods.toml`/`fabric.mod.json` mod ids. No source code was copied from any
project; API method signatures are quoted only where they are the public
integration contract. Every claim below is dated — re-check before treating it
as permanent.

---

## 1. What changed since RESEARCH-0001

RESEARCH-0001 surveyed *competitors*. This pass surveys *neighbors that are
already good at something Weft needs*, which turns out to be a different and
more useful list. Three things moved:

- **Lithium is officially NeoForge now**, not Fabric-plus-Connector. Its own
  README: "Lithium supports two mod loaders: Fabric and the NeoForge," latest
  release `0.25.1` for Minecraft `26.2.x` on both. Modrinth confirms 26
  distinct `1.21.1` versions with a `neoforge` loader.
- **Two NeoForge 1.21.1 mods occupy territory RESEARCH-0001 recorded as open**
  — one of them in a *threading* lane. Details in §3; these are the
  consequential findings of this pass.
- **The direction of integration is asymmetric in a way that settles a
  pending question.** spark's public API is read-only. That single fact
  decides WS-7's scope (§2.1, §4.1) and retracts one of RESEARCH-0001's
  recommended actions.

---

## 2. Integrate, don't reinvent

### 2.1 spark — consume it; you cannot push into it

[spark](https://github.com/lucko/spark) is the profiler most admins already
run (20.4M downloads; `1.21.1` builds for `neoforge`, `fabric`, `forge`). It
publishes a real API module, [`spark-api`](https://github.com/lucko/spark),
under **MIT** — deliberately more permissive than spark's own license, i.e.
intended for third-party consumption. Its whole surface, as declared on the
`Spark` interface:

```
DoubleStatistic<CpuUsage> cpuProcess();
DoubleStatistic<CpuUsage> cpuSystem();
DoubleStatistic<TicksPerSecond> tps();
GenericStatistic<DoubleAverageInfo, MillisPerTick> mspt();
Map<String, GarbageCollector> gc();
PlaceholderResolver placeholders();
```

Obtained via `SparkProvider.get()`; statistics are windowed
(`StatisticWindow`). Two conclusions, and they point opposite directions:

**Use it (three concrete places).**

1. **WS-6.2 (GC attribution) gets its data source for free.** RFC-0002 §WS-6.2
   proposes labelling tick spikes caused by GC pauses, sourced from
   "JFR / GarbageCollectorMXBean deltas per tick." `spark-api` already hands
   over a `Map<String, GarbageCollector>` per collector. Weft still has to do
   the *hard* part — correlating a pause window against a tick and attributing
   the allocation source — which is exactly why RESEARCH-0001 §5 called WS-6
   open, and it stays open. But the collection plumbing should be a soft
   dependency, not new code, when spark is present.
2. **`/weft report` should cross-check itself against the number admins
   trust.** Weft's profiler and spark measure overlapping things; a report that
   prints its own MSPT next to spark's `mspt()` for the same window is both
   more credible and a live self-check on the profiler's accounting. If the
   two disagree materially, that is a Weft bug worth surfacing, not hiding.
3. **Any adaptive/degradation logic should read a trusted TPS source.** If Weft
   ever gates behavior on server health, `tps()`/`mspt()` from spark beats a
   second homegrown estimator — and when spark is absent, Weft's own numbers
   are the fallback, not the primary.

Posture: **soft** dependency, `cooperate` (RFC-0003 rung 1), zero behavior
change when absent. `spark` is already in `weft-neighbors.toml` as
`profiler = "cooperate"`; that stays right, this just gives it teeth.

**But you cannot push into it.** There is no registration, no custom-statistic
or custom-source entry point — `spark-api` is strictly a read surface. So
Weft's region/lane/graph attribution *cannot* be surfaced through spark, no
matter how much nicer that would be for users. See §4.1: this is what keeps
WS-7 as written, and it retracts RESEARCH-0001 §7 action 2.

### 2.2 Chunky — a real API, and a real reason to use it

[Chunky](https://github.com/pop4959/Chunky) (16.5M downloads; `1.21.1` on
`neoforge`, `forge`, `fabric`, plus bukkit/paper/**folia**/spigot/sponge) is
the de-facto pre-generator and exposes `ChunkyAPI`:

```
int version();
boolean isRunning(String world);
boolean startTask(String world, String shape, double centerX, double centerZ,
                  double radiusX, double radiusZ, String pattern);
boolean pauseTask(String world);  boolean continueTask(String world);
boolean cancelTask(String world);
void onGenerationProgress(Consumer<GenerationProgressEvent>);
void onGenerationComplete(Consumer<GenerationCompleteEvent>);
```

The useful integration is not "let Weft pregen chunks." It is **profiler
hygiene**, and it fixes a real measurement bug waiting to happen: a Chunky run
loads and generates thousands of chunks with no players near them. That is the
least representative tick profile a server will ever produce, and it is
exactly the shape that would inflate Weft's *hypothetical region count* — the
headline number `/weft report` sells adoption on. `isRunning(world)` plus the
progress/complete listeners let Weft mark those samples and either exclude
them or label the report "measured during pre-generation — not representative."

Secondary note worth recording: Chunky ships a **`folia`** platform module.
Region-aware pregeneration already has a working precedent; if P2 ever needs
one, this is the reference, and Chunky is a neighbor to talk to rather than a
subsystem to duplicate.

Posture: **cooperate**, soft dependency, plus a profiler-side suppression
rule. Not currently in the registry.

### 2.3 Sinytra Connector — 1.21.1 is its primary target

RESEARCH-0001 §2 argued the Fabric-only networking/worldgen gap (Krypton,
C2ME, VMP) is partly closed by [Connector](https://github.com/Sinytra/Connector).
Verified and *stronger* than stated: Connector's own docs say **"1.21.1 is our
primary supported version. This is the one that will receive new fixes and
compatibility improvements,"** with 1.20.1 in critical-bugfix-only LTS. 1.21.1
is precisely Weft's target platform. C2ME remains Fabric-only at the mod level
(confirmed: its repo describes itself as a Fabric mod, no NeoForge port) —
so §2's reasoning holds and its practical relevance to Weft users is higher
than the original wording implied.

---

## 3. Territory collisions RESEARCH-0001 missed

These are the load-bearing findings of this pass. Both are NeoForge, both have
`1.21.1` builds, and both change a posture or a differentiation claim.

### 3.1 ServerCore's Entity Activation Range — WS-1's territory is *not* open

[ServerCore](https://github.com/Wesley1808/ServerCore) (modid **`servercore`**,
verified in its NeoForge `neoforge.mods.toml`; MIT; 14.5M downloads; `1.21.1`
on `neoforge` and `fabric`) ships **Entity Activation Range**, described by its
own docs as "a port based off of Spigot's and PaperMC's implementation, but
more configurable." Default config: `activation-range: 16`,
`tick-interval: 20`, `wakeup-interval: -1`, plus `skip-non-immune`. It works by
"skipping certain entity ticks based on the distance to players and other
factors."

RESEARCH-0001 §1 lists ServerCore under "the mature, non-competitive layer"
with the summary "breeding caps, async login, entity limits, dynamic view
distance" and concludes "nothing above touches Weft's actual territory," and
then asserts WS-1 "has **no existing NeoForge equivalent**. Confirmed open."
RFC-0002 WS-1 says the same in different words: "no clean NeoForge equivalent
exists." **Both are wrong as written**, and this needs correcting before it
appears in anything public-facing.

The precise, defensible version — and it is still a real differentiation, just
a narrower one:

- **ServerCore skips the whole entity tick** past the activation range
  (Spigot/Paper activation-range semantics). That reaches the movement/physics
  majority of entity cost. It also **diverges from vanilla behavior** — that
  is the trade being made.
- **WS-1 throttles AI frequency only** (sensing, goal/target selectors), and
  deliberately keeps movement, navigation, brains, and despawn accounting
  per-tick, gated by a hard behavior-parity test inside 32 blocks
  (`ws1BehaviorParityNearPlayers`).
- So the honest claim is not "nobody does this on NeoForge." It is:
  **"the vanilla-parity-preserving version of this is what has no NeoForge
  equivalent; the behavior-diverging version is a solved, shipped, 14.5M-
  download feature."**

This also **explains the WS-1 ceiling instead of leaving it as a mystery.** The
README records WS-1 measuring 15–21.5% against a ≥30% bar, with AI
sub-attribution showing the whole AI step is only ~19–20% of the entity phase —
so the bar is unreachable while movement stays per-tick. ServerCore is the
existence proof of the other route: the ≥30% is available *if* you accept
whole-tick skipping. That should be an explicit, opt-in, clearly-labelled
aggressive tier (see §4.5), not a bar Weft keeps failing while pretending the
technique doesn't exist.

Second, distinct overlap in the same mod: ServerCore's **Dynamic Performance
Checks** adjust `MOBCAP_PERCENTAGE`, `CHUNK_TICK_DISTANCE`,
`SIMULATION_DISTANCE`, and `VIEW_DISTANCE` against a `target-mspt: 35`. Weft's
P1 spawn-density service now **constructs the `SpawnState` vanilla consumes**
— counts, per-player local caps, spawn-potential charges — and is
authoritative by default. Two systems independently rewriting mobcap inputs is
not merely duplicated work; it is a plausible source of "spawning is broken and
neither mod's log mentions the other." This needs a stated posture, and the
R7 neighbor-boot matrix should cover it.

Recommended registry rows (RFC-0003 R3 — proposal, not applied here):

```toml
[servercore]
# Entity Activation Range = Spigot/Paper whole-tick skip past a distance.
# Strictly wider than WS-1's AI-frequency throttling. One owner per subsystem:
# theirs runs (RFC-0003 rung 2).
activation = "yield"
# Dynamic Performance Checks retune MOBCAP_PERCENTAGE; Weft's P1 service
# constructs the SpawnState vanilla reads. Two writers on one input.
spawn_density = "yield"
```

### 3.2 ScalableLux — optional *parallel* light updates, on NeoForge 1.21.1

[ScalableLux](https://github.com/RelativityMC/ScalableLux) (modid
**`scalablelux`**; LGPL-3.0; 12.1M downloads; Modrinth shows `1.21.1` builds
for **`neoforge`** and `fabric`) is Starlight-derived, and its README goes
further than Starlight: it "optionally allows for **parallel light updates**,
bringing significant performance improvement in high-speed world generation
and heavy light updates scenarios," made possible by keeping Starlight's
"stateless" design.

RESEARCH-0001 does not mention it at all, and RFC-0003's neighbor table has no
lighting row. This matters more than a missing survey entry, for two reasons:

1. **RFC-0002 WS-4.3 explicitly claims "light propagation batches."** That is
   ScalableLux's lane, occupied on Weft's exact target platform by a mature
   project. WS-4.3 should be a **yield**, or dropped, unless a profiler signal
   says otherwise.
2. **It is a second concurrency engine, in a lane Weft does not claim.** This
   is not a Tier-3 tick-ownership conflict — the light engine is not the tick
   loop, and orthogonal parallelism is exactly what Weft's ownership model
   should be able to coexist with. But "should" is doing real work in that
   sentence. Once P2 region workers tick entities and block entities
   concurrently, block changes made *on a worker* will enqueue light updates,
   and ScalableLux's parallel mode may service them from its own pool.
   Neither project has verified that interaction. RFC-0006's audit closed
   `getChunk`, `level.random`, the entity/BE registries, neighbor-update
   chains, and `changeDimension` against decompiled 1.21.1 — **the light
   engine is not in that list**, and this finding says it should be, whether or
   not ScalableLux is installed.

Recommended posture: `cooperate` for the profiler and P1 services (no
interaction), and an **audit item, not a posture, for P2**: add the light
engine to RFC-0006's shared-structure audit, and until it is cleared, treat
`scalablelux` + `parallelRegions` as a combination the R7 matrix must
explicitly boot and (ideally) parity-test rather than assume.

```toml
[scalablelux]
profiler = "cooperate"
spawn_density = "cooperate"
# WS-4.3 (light propagation batching) is their territory; ours yields.
ws4_light = "yield"
# regionized_ticking/parallelRegions: posture deliberately unset pending the
# RFC-0006 light-engine audit item. Do not seed a posture we have not tested.
```

### 3.3 Three more NeoForge 1.21.1 neighbors the survey omitted

| Mod | What | Relevance | Proposed posture |
|---|---|---|---|
| [Noisium](https://github.com/Steveplays28/noisium) (23.5M, LGPL-3.0, `neoforge`+`fabric` on 1.21.1) | Algorithmic worldgen optimization (not concurrency) | Overlaps **WS-4.1** (worldgen noise kernels) at the algorithm layer | Measure first, then `yield` or compose — WS-4.1 is SIMD on the same math, so this is the one case where "both run" may genuinely be faster. Do not guess. |
| [Radium](https://modrinth.com/mod/radium) (6.1M, LGPL-3.0, `neoforge` on 1.21.1) | Unofficial Lithium fork for Forge/NeoForge | Now largely redundant with official NeoForge Lithium, but installed in existing packs | Same postures as `lithium`; mutually exclusive with it |
| [Saturn](https://modrinth.com/mod/saturn) (5.2M, LGPL-3.0, `neoforge` on 1.21.1) | Memory-usage optimization | Adjacent to **WS-5** (off-heap/SoA), no tick-ownership claim | `cooperate`; re-check when WS-5 stage 2 touches chunk internals |

### 3.4 Faster Random — archived, but a parity hazard worth a named row

[Faster Random](https://modrinth.com/mod/faster-random) has `neoforge` builds
for 1.21.1 and 6.3M downloads, and was **archived 2025-12-27** with an
unusually candid retirement notice from its own author: performance results
were inconsistent, the JVM may force it to disable itself, and any gain "may
outweigh the impacts to vanilla parity on your world." It is now labelled
for pre-existing worlds only.

It earns a row anyway, and not for performance overlap: it **replaces
`RandomSource` implementations**, which is precisely what RFC-0006 does when
server levels swap to `ThreadSafeLegacyRandomSource` for parallel regions. Two
mods replacing the same RNG is a *world-parity* interaction, and the failure
mode is silent divergence, not a crash — the single worst category under
"correctness is never opt-in." Because the mod is archived, the right move is
cheap: seed a `refuse` for `regionized_ticking` with a message that names the
reason, so an operator with a legacy Faster Random world gets told to choose
rather than discovering it in a desync.

---

## 4. Add-on possibilities — hooks Weft should expose

Each of these exists because something is *absent or closed to integration*,
not because Weft wants to own more surface. Ordered by evidence strength.

### 4.1 `WeftTelemetry` export surface — keep WS-7 as written

Evidence: `spark-api` has no ingest path (§2.1), and exporter coverage on
Weft's exact platform is thin — the CurseForge Prometheus Exporter mod lists
Forge/Fabric/NeoForge builds but its **latest NeoForge build is 1.21.4, not
1.21.1**; FabricExporter is Fabric-only; UnifiedMetrics has no 1.21.1. So
there is nothing on NeoForge 1.21.1 to push Weft's region/lane/graph
attribution into.

Therefore **RESEARCH-0001 §7 action 2 is withdrawn.** Its recommendation —
rescope WS-7 from "build an exporter" to "emit into existing tools" — assumed
an ingest path that does not exist. RFC-0002 WS-7 as currently written
(OpenMetrics/Prometheus scrape endpoint + a Grafana dashboard JSON in-repo) is
the correct scope and needs no change. The refinement worth adding: emit
**standard OpenMetrics text at a scrape endpoint** so the existing
Grafana/Prometheus stack consumes it with zero Weft-specific tooling — that is
the "integrate with the ecosystem" spirit, achieved through the format rather
than through another mod's API.

Hook shape: a small `WeftTelemetry` registry in `weft-api` that any Weft
module (and, in principle, any other mod) publishes named gauges/counters
into, with WS-7's endpoint as the only consumer. Modules stay independent
(R1), and the endpoint stays a pure reader.

### 4.2 `ActivationPolicy` SPI — the honest route to the ≥30% bar

Evidence: §3.1. WS-1's bar is unreachable while movement stays per-tick; the
technique that reaches it (whole-tick skip) is shipped by ServerCore and
diverges from vanilla. Weft should stop treating that as off-limits and start
treating it as **a policy decision that belongs to the operator**.

Hook shape: the activation module's per-entity decision becomes an SPI with
two shipped implementations — the current vanilla-parity AI-frequency tiers
(default) and an explicitly-labelled aggressive whole-tick-skip tier
(opt-in, off by default, refuses to enable while `servercore`'s own activation
range is active, R4-logged as user-chosen). A third-party implementation can
replace either. This converts a failing acceptance criterion into an honest
menu, and RFC-0002's WS-1 acceptance should be split accordingly: ≥30% for the
aggressive tier, and a separately-stated realistic target for the
parity-preserving tier.

### 4.3 `BlockReadSource` / collision-shape provider SPI

Evidence: RESEARCH-0002 §2.3 (Moonrise's `getblock`/collision caching targets
exactly the movement/physics majority WS-1 cannot reach) plus §3.1 above
(nobody else does the parity-preserving version). Moonrise is confirmed
co-installable — modid **`moonrise`**, with a **`mc/1.21.1` branch carrying a
`neoforge` module** in the Tuinity repo, so RESEARCH-0002's `Cooperate` posture
is valid on Weft's target platform and not just in principle.

Hook shape: a narrow read-side SPI for block-state and collision-shape lookups
used by Weft's own shard/region workers, so that whichever cache is present —
Moonrise's, a future Weft one, or none — serves the same call site, and so a
cache implementation is required to be thread-safe *by contract* before shard
workers may use it. This is the piece that makes the movement/physics 80%
attackable at all under WS-10.

### 4.4 Chunk-ticket priority hints (not a chunk pipeline)

Evidence: RESEARCH-0002 §2.2 recommended a chunk-IO/ticket-scheduling
workstream candidate. This pass adds the constraint that makes it safe:
Moonrise and C2ME **both** own chunk-loading rewrites and are mutually
incompatible for exactly that reason (Moonrise's own compatibility table lists
C2ME as incompatible). Building a third pipeline buys Weft into a two-front
conflict on NeoForge 1.21.1 — where, incidentally, only the Moonrise front
exists natively, C2ME being Fabric-only.

Hook shape: **hints, not ownership.** Weft knows which regions are hot, which
are player-adjacent, and which are merely ticket-held. Expose that as an
advisory priority signal a chunk-system implementation *may* consume, and
consume it in Weft's own loader only if no neighbor claims the lane. This
keeps RESEARCH-0002 §2.2's value without inheriting its conflict.

### 4.5 A public tier-declaration surface for R3

Evidence: `weft-neighbors.toml` currently has nine entries (`spark`,
`lithium`, two async-pathfinding modid guesses, `alternate_current`, and the
four Folia-port refusals). RFC-0003 §3's table names Krypton, C2ME, ModernFix,
FerriteCore-class mods and now Moonrise, none of which are in the registry
(§5, errata E7). Hand-maintaining this is Weft's stated differentiator and
also, structurally, Weft's bottleneck — the exact "manual allowlist" failure
mode RESEARCH-0001 §3 criticizes the competition for.

Hook shape: let a mod **declare its own tier and territories** in its own jar
(a small `weft.compat.toml`, or `@WeftSafe` metadata), with Weft's Tier-0..3
scanning as the verifier rather than the sole source of truth. Self-declared
Tier 0/1 that fails the mixin-overlap scan gets rejected and logged — trust
but verify. That keeps "unknown mods are serialized, never guessed parallel"
intact while letting the ecosystem carry the maintenance load.

---

## 5. Errata — fact-check of the existing document set

Verified against live primary sources and the repo state at `cd39aed`
(2026-08-18). "Correct" means checked, not merely unchallenged.

| # | Document | Claim | Verdict |
|---|---|---|---|
| E1 | RESEARCH-0001 §1, RFC-0002 WS-1 | WS-1 "has no existing NeoForge equivalent. Confirmed open" / "no clean NeoForge equivalent exists" | **Wrong as written.** ServerCore's Entity Activation Range ships on NeoForge 1.21.1. Narrow the claim to the vanilla-parity-preserving variant (§3.1). |
| E2 | RESEARCH-0001 §1 | ServerCore = "breeding caps, async login, entity limits, dynamic view distance"; "nothing above touches Weft's actual territory" | **Incomplete and wrong in conclusion.** Omits Entity Activation Range and dynamic mobcap retuning; both are Weft territory (§3.1). |
| E3 | RESEARCH-0001 §7 action 2 | Rescope WS-7 from "build an exporter" to "integrate with existing tooling" | **Withdrawn by its own author.** `spark-api` is read-only and no NeoForge 1.21.1 exporter exists to emit into. RFC-0002 WS-7 as written is correct (§4.1). |
| E4 | RESEARCH-0001 (all) | Survey omits ScalableLux, Noisium, Radium, Saturn — all NeoForge 1.21.1 | **Gap.** ScalableLux is the material one: optional *parallel* light updates, overlapping WS-4.3 and unaudited against P2 (§3.2). |
| E5 | RESEARCH-0001 §1–2 | Download counts: Lithium 60M+, FerriteCore 70M+, ServerCore 6M+, ModernFix 42M+, Krypton 21M+ | **Stale, not false** (all are "+"). Modrinth 2026-08-18: 119.3M / 140.8M / 14.5M / 73.1M / 39.0M. Worth refreshing; the ServerCore figure understates a neighbor that matters. |
| E6 | RESEARCH-0001 §1 | Lithium listed "Fabric/NeoForge/Quilt" | **Correct**, and now confirmed official rather than Connector-borne (26 `1.21.1` NeoForge versions). |
| E7 | RFC-0003 §3 vs `weft-neighbors.toml` | The §3 table and the R3 registry have drifted | **Confirmed drift.** Table names Krypton, C2ME, ModernFix, Lithium-family, spark, Moonrise + 4 refusals; registry has 9 entries and no Krypton/C2ME/ModernFix/Moonrise. R3 says data-not-code; the data is behind the doc. |
| E8 | RESEARCH-0002 §1, RFC-0003 | Moonrise as a co-installable `Cooperate` neighbor | **Correct and now version-verified.** Tuinity/Moonrise has an `mc/1.21.1` branch with a `neoforge` module, modid `moonrise`. **Caveat for future citations:** `modrinth.com/mod/moonrise` is an *unrelated* mod about the moon's sky path — never link it. |
| E9 | RESEARCH-0001 §2 | Krypton/C2ME/VMP Fabric-only; Connector mitigates | **Correct, and understated.** C2ME confirmed Fabric-only; Connector's primary supported version is **1.21.1**, i.e. Weft's exact target. Minor imprecision: a dormant `vmp-forge` project exists with no 1.21+ builds. |
| E10 | RFC-0002 WS-4.3 | "Light propagation batches where vanilla's engine leaves room" | **Contested territory.** ScalableLux owns this lane on NeoForge 1.21.1; make it a yield or drop it (§3.2). |
| E11 | RFC-0006 audit list | Shared structures cleared for parallel regions: `getChunk`, `level.random`, entity/BE registries, neighbor-update chains, sub-tick counter, BE-ticker adds, `changeDimension` | **Gap, not an error.** The **light engine** is absent from the audit; worker-side block changes enqueue light work. Add it as an audit item independent of ScalableLux (§3.2). |
| E12 | RFC-0001 §8 competitive table | "Lithium / ServerCore … long-term, absorb the load-bearing ones" | **Internal tension with RFC-0003 §4**, which forbids "per-feature shims replicating a neighbor's behavior when yielding." Reword to "coordinate mixin targets," or state the exception explicitly. |
| E13 | README / RFC-0002 WS-1 | WS-1 measured 15–21.5% vs a ≥30% acceptance bar; AI step ~19–20% of entity phase | **Internally consistent and honestly reported.** The bar itself is the problem, not the measurement — see §4.2 for splitting it. |
| E14 | RESEARCH-0001 §3 | Four NeoForge regionization competitors; graph layer unclaimed everywhere | **Graph-layer half re-verified.** A Modrinth sweep for Create/AE2/Mekanism performance or threading addons on NeoForge 1.21.1 returns nothing in that lane (one AE2 addon, unrelated). Competitor maturity claims not re-checked this pass — RESEARCH-0001 §7 action 4 already schedules that. |
| E15 | Both RESEARCH docs | "Living document" status, dated claims | **Correct practice, and load-bearing.** E5 and E1 both arose from single-pass claims aging; keep the dating discipline. |

Not re-checked this pass, and flagged as such rather than silently assumed:
Forgia/NeoFolia/Foliage/Eturlia maturity (RESEARCH-0001 §3), the JEP 439 /
JEP 490 Generational ZGC facts behind WS-6.1 (verified against openjdk.org in
an earlier session, unchanged since), and RFC-0006's per-structure vanilla
1.21.1 audit findings, which need decompiled sources rather than web
research to confirm.

---

## 6. Recommended actions

1. **Correct E1/E2 in RESEARCH-0001 §1 and RFC-0002 WS-1** — narrow the "no
   NeoForge equivalent" claim to the parity-preserving variant, and move
   ServerCore out of the "non-competitive layer" framing. This is the one item
   that matters before any public-facing description of WS-1 ships.
2. **Add `servercore` and `scalablelux` to `weft-neighbors.toml`** with the
   postures in §3.1/§3.2, and add both to the R7 neighbor-boot matrix. Leave
   ScalableLux's `regionized_ticking` posture deliberately unset until E11 is
   closed.
3. **Add the light engine to RFC-0006's shared-structure audit (E11)** as a
   P2 work item, independent of whether ScalableLux is installed.
4. **Close the RFC-0003 §3 / registry drift (E7)** in whichever direction is
   truthful — either seed the missing modids or mark the table's extra rows as
   "posture decided, modid unconfirmed."
5. **Split WS-1's acceptance criterion (§4.2)** into a parity-preserving
   target and an opt-in aggressive tier, rather than carrying a bar the
   technique cannot clear.
6. **Take the two cheap integrations** — `spark-api` as a soft dependency for
   WS-6.2's GC data plus a `/weft report` cross-check, and `ChunkyAPI`'s
   `isRunning`/progress listeners to stop pre-generation from poisoning the
   profiler's headline region estimate.
7. **Refresh E5's download figures** whenever RESEARCH-0001 is next touched.
8. Treat §4.1's conclusion as settled: **WS-7 stays as written.**

## 7. Sources

- [spark (GitHub)](https://github.com/lucko/spark) — `spark-api`
  `Spark`/`SparkProvider`/`statistic` surface, MIT submodule license ·
  [spark (Modrinth)](https://modrinth.com/mod/spark)
- [Chunky (GitHub)](https://github.com/pop4959/Chunky) — `ChunkyAPI`,
  platform modules incl. `folia` · [Chunky (Modrinth)](https://modrinth.com/plugin/chunky)
- [ServerCore (GitHub)](https://github.com/Wesley1808/ServerCore) ·
  [default config docs](https://github.com/Wesley1808/ServerCore/blob/master/docs/config/DEFAULT.md) ·
  [Entity Activation Range wiki](https://github.com/Wesley1808/ServerCore/wiki/Entity-Activation-Range) ·
  [ServerCore (Modrinth)](https://modrinth.com/mod/servercore)
- [ScalableLux (GitHub)](https://github.com/RelativityMC/ScalableLux) ·
  [ScalableLux (Modrinth)](https://modrinth.com/mod/scalablelux)
- [Lithium (GitHub)](https://github.com/CaffeineMC/lithium) ·
  [Lithium (Modrinth)](https://modrinth.com/mod/lithium)
- [Moonrise (GitHub)](https://github.com/Tuinity/Moonrise) — branch
  `mc/1.21.1`, modid `moonrise`
- [C2ME (GitHub)](https://github.com/RelativityMC/C2ME-fabric) ·
  [Sinytra Connector (GitHub)](https://github.com/Sinytra/Connector)
- [Noisium (GitHub)](https://github.com/Steveplays28/noisium) ·
  [Radium (Modrinth)](https://modrinth.com/mod/radium) ·
  [Saturn (Modrinth)](https://modrinth.com/mod/saturn) ·
  [Faster Random (Modrinth)](https://modrinth.com/mod/faster-random) ·
  [FerriteCore (Modrinth)](https://modrinth.com/mod/ferrite-core) ·
  [ModernFix (Modrinth)](https://modrinth.com/mod/modernfix)
- [Prometheus Exporter (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/prometheus-exporter) ·
  [FabricExporter (Modrinth)](https://modrinth.com/mod/fabricexporter)
- Modrinth v2 API (`/project/{slug}`, `/project/{slug}/version?game_versions=["1.21.1"]`)
  and GitHub API/raw (`mods.toml`, `fabric.mod.json`, published API
  interfaces) — queried 2026-08-18.

*End of RESEARCH-0003, first pass.*
