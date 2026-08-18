# RESEARCH-0001: Performance Mod Landscape (Aug 2026) — Gap Analysis for Weft

**Status:** Living document, first pass
**Purpose:** Ground Weft's roadmap in what actually exists right now, not what
existed when RFC-0001 was first drafted. The single biggest finding here is
that Weft is **no longer the only project regionizing NeoForge** — several
others have appeared. This document is honest about that competition and
re-derives what's still genuinely unclaimed.
**Method:** Live web research (Modrinth/CurseForge mod pages, GitHub repos,
project READMEs) conducted this session. Everything below is dated; re-check
before treating any of it as permanent.
**Revised 2026-08-18** (second pass, errata E1–E5 from RESEARCH-0003 §5):
§1 ServerCore and the WS-1 openness claim (→ §1.1), §4/§7 action 2 (WS-7
rescope, withdrawn), §7.1 (survey gaps). **Download figures throughout this
document are from the first pass and were NOT re-verified** — Modrinth and
CurseForge were unreachable from the verifying session's network. Treat every
"NNM+" number here as a first-pass figure of unknown currency, and re-verify
before citing any of them publicly.

---

## 1. The mature, non-competitive layer (don't touch, cooperate)

Same-thread algorithmic optimization is a solved, saturated space and Weft
was never trying to compete here — RFC-0003 already treats these as
cooperate-tier neighbors:

- **[Lithium](https://modrinth.com/mod/lithium)** (60M+ downloads,
  Fabric/NeoForge/Quilt) — general tick-logic algorithmic speedups.
- **[FerriteCore](https://modrinth.com/mod/ferrite-core)** (70M+) —
  memory/registry dedup.
- **ServerCore** — **moved out of this layer, see §1.1.** This entry
  originally read "breeding caps, async login, entity limits, dynamic view
  distance," which omitted the two features that put it squarely in Weft's
  territory (errata E2).
- **[ModernFix](https://modrinth.com/mod/modernfix)** (42M+) — startup time,
  memory, cross-loader bug fixes.
- **[Clumps](https://modrinth.com/mod/clumps)** (18M+) — XP orb merging.
- **[Alternate Current](https://modrinth.com/mod/alternate-current)** (5M+) —
  redstone engine rewrite, ~95% faster, vanilla-parity. (RFC-0002 WS-3
  already plans to yield to this.)
- **[AI Improvements](https://modrinth.com/mod/ai-improvements)** (12.6M
  downloads, **Forge/NeoForge**) — worth flagging specifically: this caches
  expensive per-tick AI calculations (entity look-angle helper, optional
  AI-behavior disables). It is **not** what RFC-0002's WS-1 is — it's a
  same-thread caching optimization, not distance-tiered activation
  throttling, and it does not multithread anything. **This bullet originally
  concluded that WS-1 has "no existing NeoForge equivalent. Confirmed open."
  That was wrong as written — see §1.1 for the corrected, narrower claim
  (errata E1).**

Also referenced for context: **[Memory Leak
Fix](https://modrinth.com/mod/memoryleakfix)**,
**[LazyDFU](https://modrinth.com/mod/lazydfu)**,
**[Chunky](https://modrinth.com/plugin/chunky)**,
**[DashLoader](https://modrinth.com/mod/dashloader)**, and
**[spark](https://modrinth.com/mod/spark)** (the profiler most admins already
run — see §4).
Survey source: [10 Best Minecraft Server Optimization Mods for 2026
(wisehosting.com)](https://wisehosting.com/blog/10-best-minecraft-server-optimization-mods).

Everything still listed above is genuinely non-competitive. The original
closing line — "nothing above touches Weft's actual territory" — was wrong
about one entry, corrected next.

### 1.1 Correction (2026-08-18): ServerCore is a neighbor, not a bystander

Verified this pass against ServerCore's own repository at branch `ver/1.21.1`
(the branch targeting MC 1.21.1 / NeoForge 21.1.230; modid `servercore`, MIT,
Fabric + NeoForge), reading `docs/config/DEFAULT.md` and the module layout —
no source was read or copied.

ServerCore ships **Entity Activation Range**, described by its own docs as
"a port based off of Spigot's and PaperMC's implementation, but more
configurable." Mechanically: an out-of-range entity's *whole* tick is gated
down to one full tick every `tick-interval` (default 20), with a cheap
per-type inactive tick in between and immunity exceptions
(`activation-range: 16`, `wakeup-interval: -1`, `skip-non-immune: false`).
Movement and physics do not run on a skipped tick. It also ships **Dynamic
Performance Checks**, which retune `MOBCAP_PERCENTAGE`,
`CHUNK_TICK_DISTANCE`, `SIMULATION_DISTANCE` and `VIEW_DISTANCE` against a
`target-mspt: 35`, and a `mob-spawning` section that mediates vanilla's
mobcap values with no enable flag of its own.

So two Weft territories are occupied, not zero: **WS-1** (entity activation)
and the **P1 spawn-density service** (which constructs the `SpawnState`
vanilla consumes). Both now carry explicit `yield` postures in
`weft-neighbors.toml`, and both are covered by the R7 neighbor-boot matrix.

**The corrected WS-1 claim.** Not "nobody does this on NeoForge," but:

> The **vanilla-parity-preserving** variant of distance-tiered activation has
> no NeoForge equivalent. The **behavior-diverging** variant — whole-tick
> gating, which reaches the movement/physics majority of entity cost by
> giving up parity — is a shipped, widely-installed feature.

That is a narrower differentiation claim, and it is still a real one. Two
facts verified this pass keep it real:

1. **ServerCore's activation range ships disabled** (`enabled: false`), as do
   its Dynamic Performance Checks. It is an opt-in trade an operator makes
   knowingly, and its own docs warn it "can still slow down mobfarms and
   break very specific technical contraptions." WS-1 is gated by a hard
   32-block behavior-parity test (`ws1BehaviorParityNearPlayers`) instead.
2. **Nobody has shipped the parity-preserving variant on this platform.**
   ServerCore has a `feature/dynamic-brain-activation` branch — "Added
   experimental port of Dynamic Brain Activation (from pufferfish)" — whose
   last commit is **2022-07-28**, and no `brain`/DAB sources exist on either
   `ver/1.21.1` or `main`. Somebody tried this exact technique here and it
   never shipped.

This also explains WS-1's measurement ceiling rather than leaving it a
mystery: the whole AI step is only ~19–20% of the entity phase, so a ≥30%
entity-phase bar is unreachable while movement stays per-tick. See
RESEARCH-0003 §4.2 for the split of that acceptance criterion, **signed off
2026-08-18 and now live in RFC-0002 WS-1**: the parity tier is measured
against the AI slice it can actually address, and the ≥30% entity-phase bar
moved to the opt-in aggressive tier.

## 2. Networking/worldgen concurrency — real gap, partially closed by a shim

**[Krypton](https://modrinth.com/mod/krypton)** (21M+, networking stack
optimization), **[C2ME](https://modrinth.com/mod/c2me-fabric)** (16M+,
parallel chunk gen/worldgen), and **[VMP](https://modrinth.com/mod/vmp-fabric)**
(9M+, player-tracking/chunk-send at scale) are all **Fabric-only** at the mod
level — still true as of this research. That looked like a clean NeoForge
gap, but it's meaningfully mitigated by **[Sinytra
Connector](https://github.com/Sinytra/Connector)** (42.5M+ downloads,
actively maintained — [compatibility test
results](https://connector.sinytra.org/compatibility) dated as recently as
2026-08-15, currently on 3.0.0-beta.6): Connector runs Fabric mods on
NeoForge directly, and maintains a public per-mod compatibility test matrix. So NeoForge users *can often* get Krypton/
C2ME/VMP today — but per-mod, tested, not guaranteed, and running through a
translation shim is its own source of behavioral risk. This isn't Weft's
scope (Weft is tick-loop/entity/graph threading, not networking or worldgen
concurrency), but it's worth knowing the gap is smaller than it looks, and
that a native NeoForge async-worldgen module remains a legitimate (if
non-urgent) future idea if Weft ever wants to expand — currently there is no
NeoForge-native C2ME equivalent, only the Connector path.

## 3. Regionized server-tick threading — the big finding: real competition exists

**[Folia](https://github.com/PaperMC/Folia)** (Paper-only) is the canonical
original. As of this research there are now **at least four** projects
specifically porting Folia's regionized model onto NeoForge:

| Project | Approach | Maturity |
|---|---|---|
| **[Forgia](https://github.com/SajmonOriginal/Forgia)** (SajmonOriginal) | NeoForge-native API (`net.neoforged.neoforge.server.threading`), ports Folia's region primitives directly into NeoForge itself | Early — "stage 1," the active bridge still drains tasks on the main thread rather than real parallel region workers |
| **[NeoFolia](https://github.com/NeoFolia/NeoFolia)** | Similar native-API approach, config-driven (`region_threads`, per-system regionized-tick toggles) | Further along than Forgia on config surface; explicitly states mod compat is handled by **manually porting popular mods themselves** once the core stabilizes |
| **[Foliage](https://github.com/Nylhon/Foliage)** (Nylhon) | "NeoForge Hybrid" building on a Folia fork ("Youer") | Less documented, same category |
| **[Eturlia](https://github.com/eturnercus/Core)** (eturnercus/Core) | **Fork-based hybrid**: literal Folia/Paper server core with NeoForge's FML mod loader patched on top (paperweight patch series, 90+ patches) | **Most mature of the four.** v0.2.5, real CI, a headless test client verified Create actually loads and is joinable. Active, detailed release notes. |

Eturlia is a serious, working project, not vaporware — it deserves to be
taken seriously as competition. But its own documentation makes Weft's
differentiation clearer, not weaker. Direct quotes/paraphrases from its own
docs:

- Its own warning, translated: *"Many mods are designed for a single
  Vanilla/Forge server thread and break under Folia's regional model."*
  Same problem RFC-0001 exists to solve.
- *"`ServerTickEvent.Pre/Post` fire per region tick, not once per global
  tick, so listeners are invoked concurrently from every region thread. Mods
  with single-threaded assumptions in tick handlers will misbehave."* —
  **there is no legacy-lane fallback.** Unknown/unverified mods are blindly
  parallelized by default. This is exactly the failure mode RFC-0001's
  "correctness is never opt-in" tenet and Tier 0-3 classification + Legacy
  Lane exist to prevent, and none of the four projects has an equivalent.
  Compatibility is handled by hand: manual per-mod patches (Eturlia's own
  changelog is full of them — registry-rebuild fixes, a specific
  `ProjectileUtilMixin` crash fix for Create), a maintained whitelist
  (`docs/MODDER_POLICY.md`), and a manual pack audit
  (`docs/PACK_COMPAT_ASIC_2026-08.md`). NeoFolia says the same thing in
  different words: mod compat = "we'll port popular mods ourselves."
- Its own Create/Sable "compat modules" are **literal stubs** — quote:
  *"every handler is a stub, no mixins are applied, dependencies are not
  pinned. They are not built by the root project and not covered by CI."*
  **Cross-chunk tech-mod-network parallelism is confirmed unsolved even by
  the most advanced competitor.** This is Weft's graph layer's whole reason
  to exist, and it remains completely unclaimed territory.
- None of the four ships anything like a pre-adoption profiler. All four
  require actually installing the fork/mod and turning regionization on to
  find out if it helps your world. Weft's P0 profiler — install on a stock
  server, get a regionizability estimate before committing to anything — has
  no equivalent among any of them.

### What this changes for Weft

1. **Be honest that "regionize NeoForge" is no longer a novel idea by
   itself.** It's being actively attempted by at least four other projects,
   one of them (Eturlia) materially further along on raw region-threading
   mechanics than Weft's current P0/P1 state.
2. **What stays uniquely Weft's, confirmed by direct evidence from the
   competition's own docs:**
   - Automatic Tier 0-3 classification + Legacy Lane as a *default safety
     net*, not a hand-maintained allowlist. This is the difference between
     "unknown mods are serialized and safe" and "unknown mods are silently
     parallelized and may break," which is the exact failure mode every one
     of the four competitors currently ships.
   - The graph layer for cross-chunk mod networks (Create/AE2/Mekanism) —
     confirmed open everywhere, including in the project furthest along.
   - The P0 profiler as a low-risk, install-first-decide-later adoption
     path — nobody else lets an operator find out if regionization would
     even help before switching server software.
   - Being a **mod on stock NeoForge**, not a forked server distribution —
     lower adoption friction (drop a jar in vs. replace your server jar
     entirely), though worth being honest that a fork *can* reach some
     low-level NMS integration points a mixin-based mod sometimes can't;
     that's a real tradeoff, not a strict Weft advantage.
3. **RFC-0003 update worth making explicitly:** the neighbor table's "other
   threading engines → refuse (Tier 3)" line already covers this generically,
   but now that these are known, named, real projects, it's worth naming them
   explicitly in `weft-neighbors.toml` (Forgia, NeoFolia, Foliage, Eturlia) so
   a user gets a specific, clear refusal message instead of a generic one if
   they somehow have both installed.

## 4. Observability — don't reinvent, integrate

Prometheus/Grafana tooling for Minecraft already exists and is reasonably
mature: multiple independent Prometheus exporters
([Prometheus Exporter](https://www.curseforge.com/minecraft/mc-mods/prometheus-exporter),
[sladkoff/minecraft-prometheus-exporter](https://github.com/sladkoff/minecraft-prometheus-exporter),
[cpburnz/minecraft-prometheus-exporter](https://github.com/cpburnz/minecraft-prometheus-exporter)),
a [published Grafana
dashboard](https://grafana.com/grafana/dashboards/20659-minecraft-modded-1-20-1-forge/),
and newer entrants like [Maykesh's Server Observability
Framework](https://www.curseforge.com/minecraft/mc-mods/maykeshs-server-observability-framework)
(2026, tick timings/entity updates/chunk loading/network packets, low
overhead) and [Observable](https://modrinth.com/mod/observable) (a
LagGoggles successor, Forge/Fabric, tile-entity/tick-time profiling), plus
client-side profilers like [TaskManager](https://modrinth.com/mod/taskmanager).
This section originally recommended that ~~WS-7 be rescoped from "build a
Prometheus exporter" to "emit into these existing tools."~~ **That
recommendation is WITHDRAWN (errata E3, 2026-08-18).** It assumed an ingest
path that does not exist:

- `spark-api` is **read-only**. Its entire published surface is six accessors
  (`cpuProcess`, `cpuSystem`, `tps`, `mspt`, `gc`, `placeholders`) on the
  `Spark` interface, obtained via `SparkProvider.get()`. There is no
  registration, no custom-statistic entry point — verified against the
  `spark-api` sources 2026-08-18. Weft's region/lane/graph attribution cannot
  be pushed into spark at any price.
- Exporter coverage on Weft's *exact* platform is thin: the CurseForge
  Prometheus Exporter's latest NeoForge build is 1.21.4, FabricExporter is
  Fabric-only, UnifiedMetrics has no 1.21.1.

**RFC-0002 WS-7 as written is the correct scope and needs no change.** The one
refinement worth keeping from the original instinct is about *format*, not
about another mod's API: emit standard **OpenMetrics text at a scrape
endpoint**, so the existing Prometheus/Grafana stack consumes it with zero
Weft-specific tooling. That achieves "integrate with the ecosystem" through
the wire format rather than through an integration that isn't offered.

The *other* direction is real and worth taking: consume `spark-api` as a
**soft** dependency where it helps (WS-6.2's GC data, and a `/weft report`
cross-check against the MSPT number admins already trust). Note `tps()` and
`mspt()` are declared `@Nullable` — spark can be present and still decline to
answer, so a null path is required, not optional. See RESEARCH-0003 §2.1/§4.1.

## 5. JVM/GC tooling — still genuinely open

No existing mod does GC-pause attribution inside a live tick profiler, and
no in-game "doctor" command inspects the running JVM's flags against known-
bad configurations. Current community practice is still blog-post-level
(Aikar's flags and similar static advice pages). **WS-6 holds up as
differentiated** — this is real, low-competition territory.

## 6. Summary table

| Area | Competitive state | Weft's position |
|---|---|---|
| Same-thread algorithmic (Lithium/FerriteCore/etc.) | Mature, saturated | Cooperate, don't compete (RFC-0003) |
| Distance-tiered AI throttling (WS-1) | **Parity-preserving variant open on NeoForge; behavior-diverging whole-tick variant shipped by ServerCore** (§1.1, errata E1/E2) | Differentiated, narrowly |
| Networking/worldgen concurrency | Fabric-only, but reachable via Sinytra Connector | Out of scope; minor future option |
| Regionized tick threading | **4 active competitors**, 1 (Eturlia) materially advanced | Contested — win on safety model + graph layer, not on "first to regionize" |
| Auto mod-safety classification + graceful degradation | **Unsolved by every competitor** — all use manual allowlists | Weft's sharpest differentiator |
| Cross-chunk mod-network graph layer (Create/AE2/Mekanism) | **Unsolved everywhere**, including by the most advanced competitor | Weft's sharpest differentiator |
| Pre-adoption profiler (try before you switch) | No equivalent found | Weft's sharpest differentiator |
| Observability/Prometheus | Mature, but **no ingest path on NeoForge 1.21.1** — `spark-api` is read-only (errata E3) | Build WS-7 as written; integrate via the OpenMetrics *format*, and consume `spark-api` one-way |
| JVM/GC doctor tooling | Confirmed open | Differentiated (WS-6) |

## 7. Recommended next actions

1. Update RFC-0003's neighbor table with the four named regionization
   projects as explicit Tier-3 entries.
2. ~~Rescope WS-7 in RFC-0002 from "build an exporter" to "integrate with the
   existing Prometheus/Grafana ecosystem."~~ **WITHDRAWN 2026-08-18 (errata
   E3) — do not re-action this.** `spark-api` is read-only and no NeoForge
   1.21.1 exporter exists to emit into; RFC-0002 WS-7 as written is correct.
   The surviving refinement is a format choice (OpenMetrics text at a scrape
   endpoint) plus a soft `spark-api` *read* dependency. See §4 above and
   RESEARCH-0003 §4.1.
3. When writing any public-facing description of Weft going forward (README,
   wiki, release notes), lead with the safety-net + graph-layer
   differentiation rather than "regionized threading for NeoForge" alone —
   that framing now invites a direct, and not favorable, comparison to
   Eturlia, which is further along on the mechanical regionization part.
4. Worth a periodic re-check (quarterly?) on Eturlia and NeoFolia's progress
   specifically — if either ships real automatic compat detection or a graph-
   layer equivalent, that's a signal to revisit this document immediately,
   not wait for the next scheduled pass.

## 7.1 Known gaps in this survey (errata E4, 2026-08-18)

Four NeoForge 1.21.1 mods this pass omitted entirely, recorded here so the
omission is visible rather than implied-absent. **Modids for all four are
unverified** — do not add any of them to `weft-neighbors.toml` until read out
of actual jar metadata (a wrong modid never matches and the registry silently
looks like it works):

| Mod | What | Weft overlap | Status |
|---|---|---|---|
| ScalableLux | Starlight-derived light engine, **parallel light updates on by default** | **WS-4.3** | Registered as `scalablelux`, modid verified from `fabric.mod.json` on `ver/1.21.1`. See RESEARCH-0003 §3.2 |
| Noisium | Algorithmic worldgen optimization | **WS-4.1** (SIMD noise — same math, different layer) | Open. Needs a profiler number before yield-vs-compose can be decided; do not guess |
| Radium | Unofficial Lithium fork for NeoForge | Same territory as `lithium` | Open, modid unverified |
| Saturn | Memory-usage optimization | Adjacent to **WS-5** | Open, modid unverified |

Also worth a named row eventually, for a *parity* reason rather than a
performance one: **Faster Random** (archived 2025-12-27) replaces
`RandomSource` implementations, which is exactly what RFC-0006 does when
server levels swap to `ThreadSafeLegacyRandomSource` for parallel regions.
Two mods replacing the same RNG is a silent-divergence failure mode, the worst
category under "correctness is never opt-in." Modid unverified — verify before
seeding any posture.

## 8. Sources

- [Lithium](https://modrinth.com/mod/lithium) · [FerriteCore](https://modrinth.com/mod/ferrite-core) · [ServerCore](https://modrinth.com/mod/servercore) · [ModernFix](https://modrinth.com/mod/modernfix) · [Clumps](https://modrinth.com/mod/clumps) · [Alternate Current](https://modrinth.com/mod/alternate-current) · [AI Improvements](https://modrinth.com/mod/ai-improvements) · [Memory Leak Fix](https://modrinth.com/mod/memoryleakfix) · [LazyDFU](https://modrinth.com/mod/lazydfu) · [Chunky](https://modrinth.com/plugin/chunky) · [DashLoader](https://modrinth.com/mod/dashloader) · [spark](https://modrinth.com/mod/spark)
- [Krypton](https://modrinth.com/mod/krypton) · [C2ME](https://modrinth.com/mod/c2me-fabric) · [VMP](https://modrinth.com/mod/vmp-fabric)
- [10 Best Minecraft Server Optimization Mods for 2026 — wisehosting.com](https://wisehosting.com/blog/10-best-minecraft-server-optimization-mods)
- [Sinytra Connector (GitHub)](https://github.com/Sinytra/Connector) · [Connector compatibility test results](https://connector.sinytra.org/compatibility)
- [PaperMC/Folia (GitHub)](https://github.com/PaperMC/Folia)
- [Forgia — SajmonOriginal/Forgia (GitHub)](https://github.com/SajmonOriginal/Forgia)
- [NeoFolia/NeoFolia (GitHub)](https://github.com/NeoFolia/NeoFolia)
- [Foliage — Nylhon/Foliage (GitHub)](https://github.com/Nylhon/Foliage)
- [Eturlia — eturnercus/Core (GitHub)](https://github.com/eturnercus/Core)
- [Prometheus Exporter (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/prometheus-exporter) · [sladkoff/minecraft-prometheus-exporter (GitHub)](https://github.com/sladkoff/minecraft-prometheus-exporter) · [cpburnz/minecraft-prometheus-exporter (GitHub)](https://github.com/cpburnz/minecraft-prometheus-exporter)
- [Minecraft server stats Grafana dashboard](https://grafana.com/grafana/dashboards/20659-minecraft-modded-1-20-1-forge/)
- [Maykesh's Server Observability Framework (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/maykeshs-server-observability-framework)
- [Observable (Modrinth)](https://modrinth.com/mod/observable) · [TaskManager (Modrinth)](https://modrinth.com/mod/taskmanager)

*End of RESEARCH-0001, first pass.*
