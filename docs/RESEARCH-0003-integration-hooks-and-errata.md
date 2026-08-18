# RESEARCH-0003: Integrate-Don't-Reinvent, Hookable Add-Ons, and Errata

**Status:** Living document. First pass 2026-08-18; **independently verified
and corrected in place the same day** — see §7 for the verification log,
including four of this document's own claims that did not survive. Corrections
are marked inline as **[corrected 2026-08-18]**.
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
(`StatisticWindow`).

**[corrected 2026-08-18 — additions, nothing retracted]** Two details this
first pass omitted, both load-bearing for the soft dependency:

- `tps()` and `mspt()` are declared **`@Nullable`** (`cpuProcess`, `cpuSystem`,
  `gc`, `placeholders` are `@NonNull`). spark can be installed and still
  decline to answer, so the null path is required, not defensive padding. A
  `/weft report` cross-check must render "spark present, no MSPT window" as a
  distinct state from "spark absent."
- The dependency shape has a precedent on this exact platform: ServerCore's own
  `gradle.properties` on `ver/1.21.1` carries `spark_api=0.1-SNAPSHOT`. A
  14M-download NeoForge/Fabric server mod already consumes `spark-api` as a
  soft dependency; Weft would not be pioneering the pattern.

Two conclusions, and they point opposite directions:

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

**[corrected 2026-08-18]** Two things in the paragraph above need
tightening, neither of which changes the finding's direction:

1. **"Default config: `activation-range: 16`, `tick-interval: 20`" is
   misleading as written.** Those are the defaults of the
   `default-activation-type` block *inside* the feature. The feature itself
   ships **`activation-range.enabled: false`** on `ver/1.21.1`, as do its
   Dynamic Performance Checks (`dynamic.enabled: false`). Entity Activation
   Range is opt-in, not on-by-default. This matters for the posture: Weft can
   only see the modid (RFC-0003 §4 forbids reading a neighbor's config), so a
   modid-keyed `yield` necessarily over-yields for the majority of ServerCore
   installs. That is still the right direction — stacking two activation
   throttles compounds behavior divergence — but it is a conscious trade with
   R4 force-enable as the escape hatch, not a free win. Say so in the registry.
2. **"Skips the whole entity tick" is too flat.** Verified from
   `docs/config/DEFAULT.md` on `ver/1.21.1` plus the module layout: an inactive
   entity gets a full tick every `tick-interval` (default 20) — "the interval
   between 'active' ticks whilst the entity is inactive" — and between those it
   gets a cheap per-type *inactive* tick (their
   `activation_range/inactive_ticks/` mixin set covers `LivingEntity`, `Mob`,
   `AgeableMob`, `GoalSelector`, `Villager`, `ItemEntity`, `Arrow` and others),
   with immunity checks that force a real tick when an entity is falling or
   takes damage. Plus `skip-non-immune` (default false) skips 1/4 of ticks
   *inside* the range.

So the mechanism is **whole-tick gating down to 1/`tick-interval`, with a
cheap bookkeeping substitute in between** — not an unconditional skip. The
consequence the first pass drew is unaffected: movement and physics do not run
on a gated tick, which is both why it reaches the movement/physics majority and
why it diverges from vanilla.

The precise, defensible version — and it is still a real differentiation, just
a narrower one:

- **ServerCore gates the whole entity tick** past the activation range
  (Spigot/Paper activation-range semantics), down to one full tick every
  `tick-interval`. That reaches the movement/physics majority of entity cost.
  It also **diverges from vanilla behavior** — that is the trade being made,
  and their own docs say so: activation range "can still slow down mobfarms and
  break very specific technical contraptions."
- **WS-1 throttles AI frequency only** (sensing, goal/target selectors), and
  deliberately keeps movement, navigation, brains, and despawn accounting
  per-tick, gated by a hard behavior-parity test inside 32 blocks
  (`ws1BehaviorParityNearPlayers`).
- So the honest claim is not "nobody does this on NeoForge." It is:
  **"the vanilla-parity-preserving version of this is what has no NeoForge
  equivalent; the behavior-diverging version is a solved, shipped, widely
  installed feature."**
- **[added 2026-08-18]** And the parity-preserving half of that claim now has
  direct supporting evidence rather than an absence of counter-evidence:
  ServerCore has a `feature/dynamic-brain-activation` branch whose last commit
  is **2022-07-28, "Added experimental port of Dynamic Brain Activation (from
  pufferfish)"**, and no `brain`/DAB sources exist on `ver/1.21.1` or on `main`.
  Somebody ported this exact technique into this exact mod and it never
  shipped. That is a stronger position for WS-1 than the first pass claimed,
  not a weaker one.

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
`SIMULATION_DISTANCE`, and `VIEW_DISTANCE` against a `target-mspt: 35`.
**[corrected 2026-08-18 — this overlap is bigger than the first pass found.]**
Dynamic Performance Checks are off by default, but ServerCore's separate
`mob-spawning` section has **no enable flag at all** — it is always active, and
its own comment reads "This setting lets you modify the values in vanilla's
mobcap implementation." Its shipped values equal vanilla's (monster 70,
creature 10, ambient 15, water_ambient 20, …), so nothing changes numerically
out of the box, but ServerCore owns that code path unconditionally. **So the
spawn-density overlap is unconditional, while the activation overlap is
opt-in — the reverse of what the first pass implied.** Weft's
P1 spawn-density service now **constructs the `SpawnState` vanilla consumes**
— counts, per-player local caps, spawn-potential charges — and is
authoritative by default. Two systems independently rewriting mobcap inputs is
not merely duplicated work; it is a plausible source of "spawning is broken and
neither mod's log mentions the other." This needs a stated posture, and the
R7 neighbor-boot matrix should cover it.

Recommended registry rows (RFC-0003 R3). **[applied 2026-08-18]** — these are
now in `weft-sandbox/src/main/resources/weft-neighbors.toml` with the modid
read from `neoforge/src/main/resources/META-INF/neoforge.mods.toml` on
`ver/1.21.1`, covered by an R7 boot cell and pinned by
`ShippedNeighborRegistryTest`:

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

**[corrected 2026-08-18 — three findings, one of which inverts a risk
assessment]**

1. **"Optionally" does not mean off by default. Parallel light updates are ON
   by default.** Verified from the declared config on branch `ver/1.21.1`: the
   file is `config/scalablelux.properties`, the key is `parallelism`, and its
   default is **`-1`**, which resolves to
   `max(1, Runtime.getRuntime().availableProcessors() / 3)`. "Optionally" means
   *tunable* (set `parallelism` explicitly to serialize), not *opt-in*. On any
   host with ≥6 cores the default is ≥2 light threads. Everywhere this document
   says ScalableLux's parallel mode "may" service light updates from its own
   pool, read: **does, unless the operator turned it down.**
2. **The NeoForge 1.21.1 build is pre-release.** The `1.21.1` line is
   `ver/1.21.1` (Fabric, `mod_version=0.1.0.1`) plus a separate NeoForge port
   branch `port/neoforge/1.21.1` carrying two patches; the only published
   NeoForge 1.21.1 artifacts are `0.1.0+beta.1+neoforge` and
   `0.1.0+beta.2+neoforge`, both marked pre-release. Calling it "a mature
   project" on *Weft's exact platform* overstates it — the WS-4.3 lane is
   **contested, not closed**. The yield still stands (their lane, their
   ownership) but the framing was too strong.
3. **It rewrites the class RFC-0006 waved through.** Its mixin set on
   `ver/1.21.1` includes `lightengine/ThreadedLevelLightEngineMixin` — the
   exact class RFC-0006 §3 cleared as "thread-safe by design (mailbox
   enqueue)." That is the concrete link between this section and errata E11.

Modid and license verified from `src/main/resources/fabric.mod.json` on
`ver/1.21.1`: `"id": "scalablelux"`, `"license": "LGPL-3.0-only"`. Note it also
declares `"provides": ["starlight"]` — relevant if a `starlight` row is ever
added to the registry, so the two do not double-match.

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
   Neither project has verified that interaction. **[corrected 2026-08-18 — see
   E11: the light engine *was* in RFC-0006, cleared by assertion rather than
   absent from it. The audit gap is real; the description of it was wrong.]**

Recommended posture: `cooperate` for the profiler and P1 services (no
interaction), and an **audit item, not a posture, for P2**: add the light
engine to RFC-0006's shared-structure audit, and until it is cleared, treat
`scalablelux` + `parallelRegions` as a combination the R7 matrix must
explicitly boot and (ideally) parity-test rather than assume.

**[applied 2026-08-18]** — now in `weft-neighbors.toml`, with an R7 boot cell
and a `ShippedNeighborRegistryTest` case that asserts the P2 postures stay
*absent*, so a future edit cannot quietly seed one:

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
co-installable — modid **`moonrise`** (verified from
`neoforge/src/main/resources/META-INF/neoforge.mods.toml`), with a
**`mc/1.21.1` branch carrying a real `neoforge` module** (MC 1.21/1.21.1,
NeoForge 21.1.79) in the Tuinity repo. License **GPLv3** — so consuming a
published API surface is fine and copying anything is not.

**[corrected 2026-08-18 — the `Cooperate` posture is valid for P0/P1 and is
NOT established for P2.]** RESEARCH-0002 §1 concluded Moonrise touches "None
material," reasoning from its lack of a tick-ownership claim. That reasoning is
sound and the conclusion is still right for the profiler and the P1 services.
But Moonrise's own README lists **"Chunk system rewrite"** and **Starlight**
among the Paper patches it carries, and RFC-0006 hazards 1–4 build Weft's
worker chunk read path specifically on vanilla `ServerChunkCache` internals
(`getVisibleChunkIfPresent` → `ChunkHolder.getChunkIfPresent(FULL)`, justified
by that map being a volatile snapshot the parked main thread is not mutating).
A neighbor that replaces those internals wholesale invalidates the
justification, not merely the performance assumption — and the failure modes
are hazard 1's deadlock or hazard 3's silent wrongness. Filed as RFC-0006
hazard 20 (candidate); no P2 posture is seeded for `moonrise` until it closes.
This is the same category of finding as E11, discovered the same way.

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
scanning as the verifier rather than the sole source of truth.
**[added 2026-08-18 — this pattern already exists in this ecosystem, which is
worth more than the idea being novel.]** Moonrise's own
`neoforge.mods.toml` on `mc/1.21.1` carries a
`"ferritecore:disabled_options" = ["replaceNeighborLookup", "replacePropertyMap"]`
key: one mod declaring, in its own jar metadata, which of a neighbor's features
must stand down. Same shape, same transport, shipping today on NeoForge 1.21.1.
Whatever Weft designs here should look like that rather than inventing a new
channel — and it is evidence that neighbors will populate such a key. Self-declared
Tier 0/1 that fails the mixin-overlap scan gets rejected and logged — trust
but verify. That keeps "unknown mods are serialized, never guessed parallel"
intact while letting the ecosystem carry the maintenance load.

---

## 5. Errata — fact-check of the existing document set

Verified against live primary sources and the repo state at `cd39aed`
(2026-08-18). "Correct" means checked, not merely unchallenged.

| # | Document | Claim | Verdict |
|---|---|---|---|
| E1 | RESEARCH-0001 §1, RFC-0002 WS-1 | WS-1 "has no existing NeoForge equivalent. Confirmed open" / "no clean NeoForge equivalent exists" | **Wrong as written.** ServerCore's Entity Activation Range ships on NeoForge 1.21.1. Narrow the claim to the vanilla-parity-preserving variant (§3.1). **UPHELD and APPLIED 2026-08-18** in RESEARCH-0001 §1.1 and RFC-0002 WS-1. Strengthened on verification: ServerCore's own abandoned `feature/dynamic-brain-activation` branch (last commit 2022-07-28) is positive evidence that the parity-preserving variant is unshipped here, not merely unfound. |
| E2 | RESEARCH-0001 §1 | ServerCore = "breeding caps, async login, entity limits, dynamic view distance"; "nothing above touches Weft's actual territory" | **Incomplete and wrong in conclusion.** Omits Entity Activation Range and dynamic mobcap retuning; both are Weft territory (§3.1). **UPHELD and APPLIED**; ServerCore moved out of RESEARCH-0001 §1's non-competitive list into a new §1.1. Refined: the spawn-density overlap is **unconditional** (their `mob-spawning` section has no enable flag), while the activation overlap is opt-in (`enabled: false`) — the reverse of what §3.1 first implied. |
| E3 | RESEARCH-0001 §7 action 2 | Rescope WS-7 from "build an exporter" to "integrate with existing tooling" | **Withdrawn by its own author.** `spark-api` is read-only and no NeoForge 1.21.1 exporter exists to emit into. RFC-0002 WS-7 as written is correct (§4.1). **UPHELD and APPLIED**: RESEARCH-0001 §4 and §7 action 2 both struck with a do-not-re-action note, and WS-7 in RFC-0002 marked scope-settled. `spark-api`'s six-accessor read-only surface re-verified from source; `tps()`/`mspt()` additionally found to be `@Nullable`. |
| E4 | RESEARCH-0001 (all) | Survey omits ScalableLux, Noisium, Radium, Saturn — all NeoForge 1.21.1 | **Gap.** ScalableLux is the material one: optional *parallel* light updates, overlapping WS-4.3 and unaudited against P2 (§3.2). |
| E5 | RESEARCH-0001 §1–2 | Download counts: Lithium 60M+, FerriteCore 70M+, ServerCore 6M+, ModernFix 42M+, Krypton 21M+ | **Stale, not false** (all are "+"). ~~Modrinth 2026-08-18: 119.3M / 140.8M / 14.5M / 73.1M / 39.0M.~~ **[corrected 2026-08-18: the replacement figures could NOT be re-verified.** `modrinth.com` and `api.modrinth.com` were unreachable from the verifying session (egress policy), so neither the original nor the refreshed numbers were confirmed. Every download figure in this document set is now marked unverified in RESEARCH-0001's header. Do not cite any of them publicly without a fresh check.**] The qualitative point survives on other evidence: ServerCore is a neighbor that matters because of *what it ships* (§3.1), not because of its download count.** |
| E6 | RESEARCH-0001 §1 | Lithium listed "Fabric/NeoForge/Quilt" | **Correct**, and now confirmed official rather than Connector-borne — re-verified 2026-08-18 straight from Lithium's own README: "Lithium supports two mod loaders: Fabric and the NeoForge." **[corrected: the "26 `1.21.1` NeoForge versions" count came from the Modrinth API and could not be re-checked (egress-blocked). The loader claim is verified; the version count is not.]** |
| E7 | RFC-0003 §3 vs `weft-neighbors.toml` | The §3 table and the R3 registry have drifted | **Confirmed drift.** Table names Krypton, C2ME, ModernFix, Lithium-family, spark, Moonrise + 4 refusals; registry has 9 entries and no Krypton/C2ME/ModernFix/Moonrise. R3 says data-not-code; the data is behind the doc. **UPHELD, and the drift is bidirectional** — the registry also holds an async-pathfinding yield the table never mentioned. Exact delta: 5 table rows missing from the registry (Krypton, C2ME, ModernFix, ServerCore, Moonrise), 1 registry concept missing from the table. **APPLIED** in RFC-0003 §3/§3.1: two verified modids added to the registry, the async-pathfinding row added to the table, the three unverified modids labelled decided-but-not-enforced rather than guessed, and a standing rule that no row enters the registry without a modid from jar metadata plus an R7 cell. |
| E8 | RESEARCH-0002 §1, RFC-0003 | Moonrise as a co-installable `Cooperate` neighbor | **Correct and now version-verified.** Tuinity/Moonrise has an `mc/1.21.1` branch with a `neoforge` module, modid `moonrise`. **Caveat for future citations:** `modrinth.com/mod/moonrise` is an *unrelated* mod about the moon's sky path — never link it. |
| E9 | RESEARCH-0001 §2 | Krypton/C2ME/VMP Fabric-only; Connector mitigates | **Correct, and understated.** C2ME confirmed Fabric-only; Connector's primary supported version is **1.21.1**, i.e. Weft's exact target. Minor imprecision: a dormant `vmp-forge` project exists with no 1.21+ builds. |
| E10 | RFC-0002 WS-4.3 | "Light propagation batches where vanilla's engine leaves room" | **Contested territory — upheld, with one softening.** ScalableLux owns this lane on 1.21.1; **[corrected 2026-08-18]** its NeoForge 1.21.1 builds are pre-release only (`0.1.0+beta.1/2+neoforge`), so "contested" is right but "closed" would not be. **APPLIED:** WS-4.3 marked contested in RFC-0002 with `ws4_light = "yield"` seeded, and excluded from WS-4's acceptance criterion while contested. |
| E11 | RFC-0006 audit list | This row claimed "the **light engine** is absent from the audit" | **THIS ROW WAS WRONG. [corrected 2026-08-18.]** `ThreadedLevelLightEngine` **was** in RFC-0006 §3 — in the closing "Thread-safe by design (no treatment needed)" clearance list, cleared on the one-line grounds "mailbox enqueue." Absent from the *numbered* hazard table, present in the audit. The gap is therefore not omission but **evidence asymmetry**: every numbered row cites the decompiled 1.21.1 source, this one cites a parenthetical, and the parenthetical covers the enqueue being a mailbox without establishing that the bookkeeping *between* a worker's `setBlockState` and that mailbox is free of shared mutable state. **The audit item stands and is APPLIED** as RFC-0006 §3.1 hazard 19 (candidate), the stale clearance is withdrawn, and hazard 19 is now an exit criterion for `parallelRegions` going default-ON. Sharper than the original claim, and reached by checking rather than by trusting this row. |
| E11b | RFC-0003 §3 / RESEARCH-0002 §1 | Moonrise: "None material — no tick-ownership claim" → `Cooperate` | **[new, found while verifying E11, 2026-08-18.]** Correct for P0/P1, **not established for P2.** Moonrise's README lists a *chunk system rewrite* among its ported Paper patches, and RFC-0006 hazards 1–4 justify Weft's worker chunk read path on vanilla `ServerChunkCache` internals specifically. Filed as RFC-0006 hazard 20 (candidate); Moonrise's row narrowed in RFC-0003 §3 and no P2 posture seeded. |
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

**Status as of 2026-08-18 (second pass).** Actions 1–4 and 8 are **done** —
see §7. Actions 5, 6 and 7 remain open: 5 needs a product sign-off, 6 is
scoped-not-built, and 7 needs a network path to Modrinth that the verifying
session did not have.

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

## 7. Verification log — second pass, 2026-08-18

Every claim this document makes about a third party was re-derived from that
party's own repository (branch-pinned, jar metadata and config docs only — no
source was read for its implementation, and nothing was copied), and every
claim about Weft was re-derived from this repo at `28bcb3d`. Method note: the
verifying session could reach `github.com` and `raw.githubusercontent.com` but
**not** `modrinth.com`, `api.modrinth.com` or `curseforge.com`, so no download
count or published-version matrix in this document set is verified. Branch-level
and release-level facts below come from the repositories themselves.

### 7.1 Confirmed

| Claim | Source of truth | Result |
|---|---|---|
| ServerCore modid, license, platforms | `neoforge.mods.toml` + `gradle.properties`, branch `ver/1.21.1` | `servercore`, MIT, `enabled_platforms=fabric,neoforge`, MC 1.21.1 / NeoForge 21.1.230 ✅ |
| ServerCore activation range = Spigot/Paper whole-tick gating; movement does not run | `docs/config/DEFAULT.md` + `activation_range/` module layout, `ver/1.21.1` | ✅ with a correction to the description — see §3.1 item 2 |
| ServerCore retunes `MOBCAP_PERCENTAGE` vs `target-mspt: 35` | same | ✅, and the overlap is *larger* than claimed — see §3.1 |
| ScalableLux modid, license | `fabric.mod.json`, branch `ver/1.21.1` | `scalablelux`, LGPL-3.0-only ✅ (also `provides: ["starlight"]`) |
| ScalableLux parallel light updates are real, and exist on the 1.21.1 line | `README.md` on `ver/1.21.1`; branch `port/neoforge/1.21.1` | ✅ real, ✅ on 1.21.1 — but see §3.2 for the default and the pre-release status |
| `spark-api` is MIT and read-only | `spark-api/LICENSE.txt` + `Spark.java` / `SparkProvider.java` | ✅ MIT; exactly 6 accessors, no registration path. Added: `tps()`/`mspt()` are `@Nullable` |
| ChunkyAPI surface | `common/.../api/ChunkyAPI.java` | ✅ `version`, `isRunning`, `startTask`, `pauseTask`, `continueTask`, `cancelTask`, `onGenerationProgress`, `onGenerationComplete`. `folia` and `neoforge` platform modules both present |
| Moonrise modid + `mc/1.21.1` with a real `neoforge` module | `neoforge/src/main/resources/META-INF/neoforge.mods.toml`, branch `mc/1.21.1` | ✅ `moonrise`, NeoForge 21.1.79, MC 1.21/1.21.1. Added: GPLv3 |
| Moonrise ❌ C2ME, ✅ Lithium, ✅ FerriteCore | Moonrise README compatibility table, `mc/1.21.1` | ✅ verbatim |
| Lithium is officially NeoForge | Lithium README | ✅ verbatim quote |
| RFC-0003 §3 ↔ registry drift | this repo | ✅ real, and bidirectional — exact delta in RFC-0003 §3.1 |

### 7.2 Did not hold up

Four claims failed and are corrected in place above. Being wrong in a living
document is cheap; being wrong in `weft-neighbors.toml` is not, which is why
none of these reached the registry.

1. **§3.2 — "optionally allows for parallel light updates" read as opt-in.**
   Wrong: `parallelism` defaults to `-1` → `max(1, cores/3)`. Parallel light
   updates are **on by default**. This makes hazard 19 more urgent, not less.
2. **E11 — "the light engine is absent from RFC-0006's audit."** Wrong:
   `ThreadedLevelLightEngine` was in the "thread-safe by design" clearance
   list. The real defect is evidence asymmetry, not omission. The audit item
   survives in a sharper form.
3. **§3.1 — "Default config: `activation-range: 16`, `tick-interval: 20`"**
   presented as the shipped state, and **"skips the whole entity tick"** as the
   mechanism. The feature ships `enabled: false`, and the mechanism is
   whole-tick gating to 1/`tick-interval` with a per-type inactive-tick
   substitute. The posture is unchanged; the reasoning behind it is now honest
   about over-yielding.
4. **E5 — refreshed download figures.** Not verifiable; Modrinth unreachable.
   All download figures in this document set are now flagged unverified.

### 7.3 Found while verifying, not in the first pass

- **RFC-0006 hazard 20 (candidate):** Moonrise ports a chunk-system rewrite,
  and hazards 1–4 justify Weft's worker read path on vanilla `ServerChunkCache`
  internals. Its `Cooperate` posture is valid for P0/P1 and unestablished for
  P2 (errata E11b).
- **ServerCore's `feature/dynamic-brain-activation`** (last commit 2022-07-28,
  never merged) — positive evidence for WS-1's narrowed differentiation claim.
- **ServerCore's `mob-spawning` has no enable flag** — the spawn-density
  overlap is unconditional, unlike the activation one.
- **Moonrise declares `"ferritecore:disabled_options"` in its own
  `neoforge.mods.toml`** — §4.5's self-declaration hook already has a shipping
  precedent on this platform.
- **ServerCore consumes `spark_api` itself** (`gradle.properties`) — precedent
  for §2.1's soft dependency.

### 7.4 Still unverified, deliberately left open

- **ScalableLux's `parallelism` default on the NeoForge 1.21.1 port
  specifically.** Read from `Config.java` on the Fabric base `ver/1.21.1`; the
  NeoForge port is a two-patch delta on top and its patch bodies were not read.
  Assume the same default; confirm before relying on it.
- **Noisium vs WS-4.1.** Needs a profiler number, not an opinion. No posture
  seeded.
- **Noisium / Radium / Saturn / Faster Random modids.** Unverified; none added
  to the registry.
- **All download counts and published-version matrices.** Egress-blocked.
- **Hazards 19 and 20 themselves.** Both need decompiled 1.21.1 sources, which
  were not available in this container. They are filed as candidates, and the
  decompile pass increment 7 already needs will close them.

## 8. Sources

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
