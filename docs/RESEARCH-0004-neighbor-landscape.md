# RESEARCH-0004: Consolidated Neighbor Landscape

**Status:** Living document, first pass 2026-08-18. This is the *consolidated*
view — RESEARCH-0001 (competitor survey), RESEARCH-0002 (Moonrise methodology)
and RESEARCH-0003 (integration hooks + errata) each hold the reasoning and the
citations; this document holds the conclusions in the form a future session
needs them, so the landscape does not have to be re-derived from three
documents and a TOML file every time somebody asks "can we ship next to X?".
**Purpose:** Answer one question quickly and correctly: for any given
third-party mod, is it in `weft-neighbors.toml`, what posture does it carry,
and if it has no row — is that because nobody looked, or because somebody
looked and deliberately declined to seed one?
**Method:** No independent research. Every claim below is carried over from
RESEARCH-0001/0002/0003 and `weft-neighbors.toml`, all of which cite primary
sources (jar metadata, config docs, READMEs, published API signatures) read on
each project's branch targeting MC 1.21.1. Nothing was copied from any
project's implementation — see §5.

**Sources.** `docs/RESEARCH-0001-performance-mod-landscape-2026.md` (competitor
survey), `docs/RESEARCH-0002-moonrise-methodology-study.md` (`a3b9350`),
`docs/RESEARCH-0003-integration-hooks-and-errata.md` (`28bcb3d`, living
document + errata on our own prior research), `docs/RFC-0003-coexistence-policy.md`
(`1f217cd` added the Moonrise row; §3/§3.1 rewritten 2026-08-18),
`weft-sandbox/src/main/resources/weft-neighbors.toml` (the data R3 resolves).

**Verify before you cite.** Everything below is a dated claim about a moving
ecosystem, and the second pass found that four first-pass claims had already
gone wrong — including one that inverted a risk assessment. Two standing rules
came out of it:

1. **No modid enters `weft-neighbors.toml` without being read from actual jar
   metadata** (`neoforge.mods.toml` / `fabric.mod.json`) on the branch targeting
   MC 1.21.1, **plus an R7 boot cell**. A wrong modid silently never matches and
   the registry looks like it works.
2. **Postures nobody has booted are prose, not data.** RFC-0003 §3's table is
   prose; the registry is the truth at runtime. Where they disagree, the table
   is the bug.

**Network caveat (2026-08-18):** the verifying session could reach `github.com`
and `raw.githubusercontent.com` but **not** Modrinth or CurseForge. No download
count or published-version matrix in the doc set is verified. Do not quote one.

## 1. In the registry (verified modids, R7-covered)

| modid | What | Posture |
|---|---|---|
| `spark` | Profiler most admins run. `spark-api` is **MIT and read-only** — 6 accessors (`cpuProcess`, `cpuSystem`, `tps`, `mspt`, `gc`, `placeholders`) via `SparkProvider.get()`; `tps()`/`mspt()` are `@Nullable`. **No ingest path**, so WS-7 keeps its own export surface | `profiler = cooperate`; we read it one-way |
| `lithium` | Officially NeoForge now (own README: "two mod loaders: Fabric and the NeoForge"), not Connector-borne | `activation`, `pathfinding = cooperate` |
| `asyncpathfinding`, `async_pathfinding` | Dedicated async-pathfinding mods (best-effort modid seeds) | `pathfinding = yield` |
| `alternate_current` | Redstone engine rewrite | `ws3_redstone = yield` (seed; WS-3 doesn't exist yet) |
| **`servercore`** | MIT, Fabric+NeoForge, `ver/1.21.1` = MC 1.21.1 / NF 21.1.230. **Entity Activation Range**: Spigot/Paper semantics — whole-tick gating to one full tick every `tick-interval` (20), cheap per-type *inactive* tick in between, immunity exceptions; `activation-range: 16`. Movement/physics do **not** run on a gated tick → reaches the movement/physics majority, and diverges from vanilla (own docs: "can break very specific technical contraptions"). **Ships `enabled: false`.** Also: `mob-spawning` mediates vanilla mobcap values with **no enable flag** (unconditional), and optional Dynamic Performance Checks retune `MOBCAP_PERCENTAGE` vs `target-mspt: 35` (`dynamic.enabled: false`) | `activation = yield`, `spawn_density = yield`. The activation yield **deliberately over-yields** (their feature is off by default and RFC-0003 §4 forbids reading a neighbor's config) — R4 force-enable is the escape hatch |
| **`scalablelux`** | LGPL-3.0, Starlight-derived light engine. **Parallel light updates are ON by default**: `config/scalablelux.properties` key `parallelism` defaults to `-1` → `max(1, cores/3)`. "Optionally" in its README means *tunable*, not opt-in. NeoForge 1.21.1 exists (`port/neoforge/1.21.1`) but ships **pre-release only** (`0.1.0+beta.1/2+neoforge`). Its mixin set includes `ThreadedLevelLightEngineMixin`. Fabric jar declares `provides: ["starlight"]` | `profiler`, `spawn_density = cooperate`; `ws4_light = yield`. **`regionized_ticking` / `entity_sharding` deliberately UNSET** pending RFC-0006 hazard 19 — pinned absent by `ShippedNeighborRegistryTest` |
| `forgia`, `neofolia`, `foliage`, `eturlia` | NeoForge Folia ports/hybrids — tick ownership | `regionized_ticking`, `entity_sharding`, `legacy_lane = refuse` (Tier 3) |

## 2. Known, deliberately *not* in the registry

| Mod | Why it matters | Why no row / no posture |
|---|---|---|
| **Moonrise** (`moonrise` — modid verified, `Tuinity/Moonrise` branch `mc/1.21.1`, real `neoforge` module, NF 21.1.79, **GPLv3**) | `Cooperate` for P0/P1 is right: same-thread entity/collision/tracker work, no tick-ownership claim, self-reports ✅ Lithium / ✅ FerriteCore / ❌ C2ME. **But it ports a chunk-system rewrite *and* Starlight** — and RFC-0006 hazards 1–4 justify Weft's worker chunk read path on **vanilla `ServerChunkCache` internals** | **P2 posture unset.** Filed as RFC-0006 hazard 20 (candidate). Also: its `neoforge.mods.toml` carries `"ferritecore:disabled_options"` — a shipping precedent for RESEARCH-0003 §4.5's self-declaration hook. **Never cite `modrinth.com/mod/moonrise`** — that slug is an unrelated mod about the moon's sky path |
| **Noisium** | Algorithmic worldgen optimization, overlaps **WS-4.1**. The one overlap where "both run" may genuinely be faster (WS-4.1 is SIMD on the same math) | Needs a profiler number before yield-vs-compose. Modid unverified. **Left open on purpose — do not guess** |
| **Radium** (Lithium fork), **Saturn** (memory, adjacent WS-5) | Installed in existing packs | Modids unverified |
| **Faster Random** (archived 2025-12-27) | Replaces `RandomSource` — collides with RFC-0006's `ThreadSafeLegacyRandomSource` swap. **World-parity hazard with a silent-divergence failure mode**, not a perf overlap | Modid unverified. Verify before seeding the intended `refuse` for `regionized_ticking` |
| **Krypton**, **C2ME**, **ModernFix** | In RFC-0003 §3's table with decided postures | Modids unconfirmed → decided but **not enforced**. C2ME is Fabric-only at the mod level. Krypton's overlap (WS-9) doesn't exist yet; ModernFix's `cooperate` is already the default for unknown mods |
| **Chunky** | `ChunkyAPI` (`isRunning(world)`, `onGenerationProgress`, `onGenerationComplete`) stops a pregen run from poisoning `/weft report`'s hypothetical-region estimate. Has `neoforge` **and `folia`** platform modules — region-aware pregen has a working precedent | Not yet integrated. Its `neoforge.mods.toml` templates `modId = "${id}"`, so the modid needs resolving before a registry row |

## 3. Platform facts worth not re-deriving

- **Sinytra Connector's primary supported version is 1.21.1** — our exact
  target, with 1.20.1 in critical-bugfix-only LTS. So NeoForge users can often
  reach Krypton/C2ME/VMP through it, per-mod and tested, not guaranteed.
- **Moonrise ❌ C2ME** because both rewrite chunk loading — the same shape as
  Weft's own Tier-3 tick-ownership rule, one layer down. Relevant if Weft ever
  owns chunk loading (RESEARCH-0002 §2.2 / RESEARCH-0003 §4.4: expose *hints*,
  not a third pipeline).
- **ServerCore consumes `spark_api` itself.** Precedent for the soft dependency.
- **ServerCore's `feature/dynamic-brain-activation`** (Pufferfish DAB port) was
  abandoned **2022-07-28** and never merged; no DAB sources on `ver/1.21.1` or
  `main`. Positive evidence that the *parity-preserving* variant of WS-1 is
  genuinely unshipped on NeoForge, not merely unfound.

## 4. Open items this landscape created

| Item | Home | Blocked on |
|---|---|---|
| **Hazard 19 (candidate)** — light-engine enqueue path under worker-side block mutation. RFC-0006 had cleared `ThreadedLevelLightEngine` by assertion ("mailbox enqueue"), not by the per-structure decompile every numbered row rests on. Clearance withdrawn | RFC-0006 §3.1 | Decompiled 1.21.1 sources. Same pass as increment 7's `ServerLevel.tick` audit. **Now an exit criterion for `parallelRegions` → default-ON** |
| **Hazard 20 (candidate)** — a neighbor replacing the chunk system under hazards 1–4 (Moonrise) | RFC-0006 §3.1 | Same decompile pass, or an R7 `moonrise` + `parallelRegions` cell |
| **WS-1 acceptance split** — the ≥30% entity-phase bar is unreachable while movement stays per-tick (whole AI step ≈19–20% of the phase; tracked 15–21.5%, latest 18.6%). RESEARCH-0003 §4.2 proposes an `ActivationPolicy` SPI: parity-preserving AI-tier throttling (default) + an explicitly-labelled aggressive whole-tick-skip tier (opt-in, off by default, refuses to enable while `servercore`'s activation range is active, R4-logged) | RFC-0002 WS-1 | **Product sign-off.** Criterion unchanged until then |
| **WS-4.3 contested** → `ws4_light = yield`, excluded from WS-4's acceptance criterion | RFC-0002 WS-4 | Nothing; done |
| **WS-7 scope settled** — stays as written; `spark-api` has no ingest path. RESEARCH-0001 §7 action 2 struck with a do-not-re-action note | RFC-0002 WS-7 | Nothing; done |
| **Noisium vs WS-4.1** | RFC-0002 WS-4 | A profiler number |
| **Download figures** across the doc set | RESEARCH-0001 | A network path to Modrinth |

## 5. Methodology guardrails for anything in this area

- **Methodology only, never code.** Nothing is copied from ServerCore,
  ScalableLux, Moonrise, Lithium, or any other project. READMEs, configs,
  published API signatures, jar metadata and directory structure only — never
  implementations. Two of these are copyleft (ScalableLux LGPL-3.0, Moonrise
  GPLv3), which makes the rule a licence matter as well as a discipline one.
- **RFC-0003 R3 is data, not code.** Postures live in `weft-neighbors.toml`.
- **No untested postures, no unmeasured claims.** If something needs a profiler
  number before it can be decided, say so and leave it open rather than
  guessing. Noisium vs WS-4.1 is the live example.
