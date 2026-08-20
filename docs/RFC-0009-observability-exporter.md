# RFC-0009: Observability Exporter (WS-7) — Telemetry Egress Surface

**Status:** Draft 3 — **surface reviewed and approved 2026-08-17** (§14);
**implemented, gates green back-to-back** (§10), overhead measured (§9.3).
§3.11 records the corrections the implementation surfaced. These names are API.
**Depends on:** RFC-0001 §4.4 (guard forensics), §8.4 (telemetry), §9.1 (the
"your tick is 61% mod X" number); RFC-0002 §WS-7 (this RFC is its amendment)
and §WS-8 (the overhead gate lives there); RFC-0003 (all seven rules — this is
a module and walks the ladder); RESEARCH-0003 §4.1 (the `WeftTelemetry` hook
shape); RESEARCH-0004 §5 (registry standing rule).
**Scope:** A serialization and transport job. Weft already measures almost
everything below; this RFC decides the *names*, the *formats*, and which of
the brief's series cannot be delivered honestly as specified.

---

## 0. Why an RFC rather than an amendment to RFC-0002 §WS-7

Three reasons, in order of weight:

1. The surface is ~55 series names, an event envelope and a committed JSON
   schema. All of it is API on the day it ships.
2. Seven of the brief's series **cannot be produced from what Weft measures
   today** without either lying about what the number is or adding timing
   probes to the tick path. §2 lists each with a verdict. That is a design
   discussion, not a paragraph.
3. The brief's own framing cites a rescope that this repo withdrew (§1).

RFC-0002 §WS-7 gets a two-line pointer to this document, not a rewrite.

## 1. Scope: the RESEARCH-0001 §4 rescope is already settled, in this direction

The WS-7 brief instructs us to incorporate "the rescope from RESEARCH-0001 §4:
do not build a competing Prometheus exporter ecosystem — emit Weft's own
telemetry in formats the existing tooling already consumes."

**That rescope was withdrawn by its own author on 2026-08-18** (errata E3).
RESEARCH-0001 §4 carries the strike-through, RESEARCH-0003 §4.1 records the
reasoning, RESEARCH-0004 §4 lists it as closed, and RFC-0002 §WS-7 is marked
*scope settled*. The withdrawal is narrow and correct: `spark-api` is
read-only (six accessors on `Spark`, no registration path) and no NeoForge
1.21.1 exporter exists to emit into, so there is no tool to push
region/lane/graph attribution *into*.

What survived the withdrawal is the *format* half — "emit standard
Prometheus/OpenMetrics text at a scrape endpoint so the existing
Prometheus/Grafana stack consumes it with zero Weft-specific tooling" — and
that is precisely what the brief then asks us to build. **The instruction and
the settled scope describe the same build; only the citation is stale.**
Nothing in this RFC changes as a result. It is recorded here so the next
reader does not re-open E3 for a fourth time.

## 2. Inventory: what is already measured, and the seven exceptions

The brief's core constraint — *this is serialization, not measurement* — is
worth restating as a rule with teeth: **every series below names its existing
source, or is marked as a deviation.** Verdicts:

- **SERIALIZE** — the value exists; the exporter reads and formats it.
- **AGGREGATE** — the *measurement* exists; only the accumulation is new
  (a `LongAdder` on a path that already runs, or a histogram fed from values
  already timed). No new `nanoTime` on any path.
- **NEW PROBE** — new timing on the tick path. Requires justification and a
  measured budget. There is exactly one, in two parts (§9.2).
- **RENAME / DROP** — the brief's name asserts something Weft does not know.

| Brief's series | Existing source | Verdict |
|---|---|---|
| `weft_tick_duration_seconds` | the boundary delta at the **existing** `MinecraftServerMixin` `tickServer` HEAD hook — *not* `TickProfiler`, whose `tickBoundary` only records while the `profiler` module is on (R1, §4) | SERIALIZE |
| `weft_tick_phase_duration_seconds{phase}` | `WeftScheduler.lastPhaseTimings()`; the scheduler ticks unconditionally from `WeftMod.onVanillaTick`, so this is populated even with every P2 flag off | SERIALIZE (+ caveat §3.1) |
| `weft_tps` | derived from tick duration | SERIALIZE |
| `weft_regions{level}` · `weft_region_chunks` | `RegionTopology.managerFor(level)` → `RegionManager.all()`, `Region.chunks()` | SERIALIZE |
| `weft_region_buckets` | `RegionizedTicking.lastEntityPartition()` / `lastBlockEntityPartition()` lengths | SERIALIZE |
| `weft_region_merges_total` · `weft_region_splits_total` | **absent** — `RegionManager` merges in `addChunk` and splits in `recomputeSplits` without counting either | AGGREGATE (two `LongAdder`s on the chunk-load / between-tick paths; not the tick path) |
| `weft_region_tick_duration_seconds{level}` | **absent** — `RegionizedTicking` keeps per-bucket *unit counts*, never durations | **NEW PROBE** (§9.2) |
| `weft_barrier_wait_seconds` | **absent** | **NEW PROBE** (§9.2, same gate) |
| `weft_worker_queue_depth{pool}` | `ForkJoinPool` (the field is typed `ExecutorService`; widen it) | SERIALIZE |
| `weft_worker_utilization_ratio{pool}` | **absent, and the obvious implementation is a lie** — see §3.3 | RENAME + rebuild honestly |
| `weft_mail_messages_total{type,outcome}` | `OwnerMail` (`routedToRegion`, `inlineFallback`, `drainedTasks`, `flushedTasks`); global-inbox posts uncounted | AGGREGATE |
| `weft_mail_delivery_seconds` | **absent**, and measuring it means stamping every message | **DROP from v1** (§3.4) |
| `weft_legacy_lane_duration_seconds` | `LegacyLane.lastTickNanos()` | SERIALIZE |
| `weft_legacy_mod_cost_seconds_total{modid}` | `LegacyLane.costByModNanos()` — **the flagship** | SERIALIZE |
| `weft_legacy_extractions_total{modid,kind}` | per-mod unit counts exist (`unitsByMod`); the entity/BE split exists only globally (`LegacyRouting.deferredEntities` / `deferredBlockEntities`) | AGGREGATE (make those two counters per-modid — the call site already holds the modid) |
| `weft_unit_cost_seconds_total{source,type}` · `weft_unit_ai_cost_seconds_total{type}` | `TickSample.nanos` / `.aiNanos` | AGGREGATE (§3.2 — the profiler window is *rolling*; a counter must be monotonic) |
| `weft_entities{level,type}` | `EntityCensus` | SERIALIZE |
| `weft_block_entities{level,type}` | **no BE census exists** — only per-tick ticker counts | NARROW to `weft_block_entities_ticking{level}` (§3.5) |
| `weft_service_requests_total{service,outcome}` | `WeftServices.SpawnStats`, `PathfindingHooks` counters | SERIALIZE |
| `weft_service_latency_seconds{service}` | only *last* values retained (`captureNanosLast`, `buildNanosLast`, `computeNanos`) | AGGREGATE (histogram fed from values already timed) |
| `weft_spawn_density_fallbacks_total` | `SpawnStats.fallbackTicks` | SERIALIZE |
| `weft_spawn_density_verify_diff` | **magnitude is not retained** — only clean/mismatch tick counts and a human `lastMismatch` string | RENAME (§3.6) |
| `weft_path_requests_total{outcome}` | `submitted` / `filled` / `noPath` | RENAME the label values (§3.7) |
| `weft_guard_trips_total{kind,severity}` | `WeftGuards` keeps **one** global `AtomicLong`; forensics exist only inside the exception message | AGGREGATE (§3.8) |
| `weft_module_state{module,state}` · `weft_module_selfdisable_total{module,reason}` | `WeftModules.resolve()` returns *formatted strings*; transitions untracked | AGGREGATE (§3.9) |
| `weft_jvm_heap_bytes{area}` | `MemoryMXBean` | SERIALIZE |
| `weft_jvm_gc_pause_seconds` (histogram) | **absent** — a pause histogram needs a GC notification listener, which is WS-6.2's territory | NARROW to a counter pair (§8.2) |

## 3. Surface decisions where the brief's name must change

### 3.1 Engine phases are not vanilla sections

`weft_tick_phase_duration_seconds{phase}`'s seven labels map exactly onto
`TickPhase`. But those are the *engine pipeline's* phases, and today vanilla's
entity and block-entity ticking still happens inside vanilla's own tick,
wrapped by `RegionizedTicking` — not inside phase `REGION`. An operator
reading `REGION = 0.01ms` would conclude region work is free.

Fix: ship the phase histogram as-is (the phase genuinely took that long) and
add a second, unambiguous series for the wrapped vanilla sections:

```
weft_section_duration_seconds   histogram   labels: section (ENTITY|BLOCK_ENTITY), level
```

This is where `SectionSamples` already points — it exists because P2 measured
full-tick MSPT to judge a block-entity-section change and could not tell a
1.31x win from a 0.85x loss. The section ruler is the one an operator wants.

### 3.2 Counters must be monotonic; the profiler window is not

`TickProfiler` keeps a *rolling* window (default 100 ticks) and
`snapshotWindow()` returns it. Summing that window into
`weft_unit_cost_seconds_total` would produce a gauge that walks up and down
while wearing a `_total` suffix — a `rate()` over it returns nonsense.

Fix: accumulate a **since-boot** per-`(source,type)` total at the tick
boundary, separately from the window the report renders. That is one map
update per distinct type per tick, at the boundary, not per sample. The
rolling window keeps serving `/weft report` unchanged.

### 3.3 `weft_worker_utilization_ratio` — rebuilt, because the easy version lies

The naive implementation is `ForkJoinPool.getActiveThreadCount() /
getParallelism()`, sampled on the scrape thread. Weft's fan-out is *barriered
inside a vanilla tick section*: it lasts a fraction of a 50 ms tick, so a 10 s
sample reads ~0 almost every time, and an operator would read that as idle
workers rather than as a sampling artefact.

Fix: compute a real ratio at the section boundary from the §9.2 probe —
`sum(bucket durations) / (barrier wall time × buckets)` — a genuine
work-conservation figure over the interval that matters. Plus two honest raw
series: `weft_worker_queue_depth{pool}` (nonzero means real backlog) and
`weft_worker_steals_total{pool}` (`ForkJoinPool.getStealCount()`, monotonic).
The utilisation gauge is therefore **gated on the same probe** as region tick
duration, and absent when the probe is off.

### 3.4 `weft_mail_delivery_seconds` — dropped from v1

Delivery latency needs a per-message timestamp: a `nanoTime` at post and at
drain, on a path RFC-0001 §8.4 explicitly pools to keep GC flat. And the
number would be near-constant by construction — mail posted before a section
begins is applied at that section's head, the same tick (RFC-0007 §3.2). The
interesting failure is *stranded* mail, and that is a count, not a latency:
covered by the `stranded_reroute` outcome. Revisit if off-thread routing lands
(RFC-0007 §5 / increment 7).

### 3.5 There is no block-entity census

`EntityCensus` exists; nothing equivalent tracks block entities, so
`weft_block_entities{level,type}` has no source. What *is* free is the count of
tickers each section saw, which `RegionizedTicking` already partitions:
`weft_block_entities_ticking{level}`. No `type` label in v1 — inventing one
means a full BE walk per scrape, which is a measurement job in a different
workstream.

### 3.6 `weft_spawn_density_verify_diff` — renamed

A gauge named `_diff` promises a retained numeric residual. What exists is
`parityTicks` / `parityMismatchTicks` and a human-readable `lastMismatch`
string. Ship the two counters:

```
weft_spawn_density_parity_ticks_total            counter   label: level
weft_spawn_density_parity_mismatch_ticks_total   counter   label: level
```

`1 - rate(mismatch)/rate(ticks)` is the parity rate — alertable, and true.

### 3.7 There is no "coalesced" path outcome

WS-2 counts `submitted` / `filled` / `noPath`. Recompute throttling lives in a
separate mixin and is counted nowhere. Shipping `outcome="coalesced"` would
ship a label value nothing can ever produce. Outcomes: `filled`, `no_path`,
with `submitted` as the request total. Add `coalesced` the day something
increments it.

### 3.8 Guard trips need a structured record, not a new probe

`WeftGuards` increments one global `AtomicLong` and builds forensics *only as
an exception message string*. WS-7 needs `{kind, severity}` and the RFC-0001
§4.4 forensics (ownership context, target, degradation taken) as fields.

This is a change to `WeftGuards`, not a probe: **allocation happens only on a
trip**, which is already an exceptional path that either throws or routes a
mutation as mail. Shape: a `GuardTrip` record plus a single
`Consumer<GuardTrip>` listener slot (null when observability is off — R6).
`kind` = `region_mutation` | `shard_mutation`; `severity` = `dev_throw` |
`degraded_to_mail` | `hardened_throw`, i.e. the mode-dependent outcome, which
is the thing that decides whether a human must be paged.

### 3.9 The module-state label set comes from `/weft status`, not from a guess

The brief lists three states (`ACTIVE|YIELDED|DISABLED`). The real ladder has
seven `CoexistencePolicy.State` values, and `SELF_DISABLED` and `REFUSED` are
exactly the two an operator most needs to see. `WeftModules.label()` already
collapses them into the five the R5 table prints.

Decision: **derive the `state` label from `label()` itself**, lower-cased —
`active`, `yielded`, `self_disabled`, `disabled`, `refused`. The metric and
`/weft status` then cannot disagree, which is the R5 promise in metric form.
Prerequisite: `WeftModules.resolve()` gains a structured sibling returning the
resolutions, and the string renderer becomes a formatter over it.

### 3.10 Prometheus text format by default, OpenMetrics by negotiation

RFC-0002 §WS-7 says "emit standard **OpenMetrics text**"; the WS-7 brief says
"**text exposition format**". These are two different (though closely related)
formats — OpenMetrics requires the `_total` suffix on the exposed counter name
and a terminating `# EOF`, and uses a different content type.

Decision: serve **Prometheus text format** (`text/plain; version=0.0.4;
charset=utf-8`) as the default response, and OpenMetrics
(`application/openmetrics-text; version=1.0.0; charset=utf-8`) when the
scraper's `Accept` header asks for it. That is what real exporters do and what
Prometheus itself negotiates, so both readings of the RFC are satisfied by one
endpoint and neither is guessed at. `promtool check metrics` reads the former,
which is the §10 gate.

### 3.11 Three further corrections, found while implementing

Each of these is the same failure the rest of §3 catches — a name asserting
something Weft does not know — found one layer deeper than the review pass
reached. Recorded here rather than quietly shipped.

**`weft_unit_cost_seconds_total` is a gauge, not a counter.** §3.2 proposed
accumulating a since-boot per-`(source,type)` total at the tick boundary. That
accumulation is a map merge per *sample* — O(entities + block entities) per
tick, on the server thread. That is a measurement job with a different overhead
budget, which is exactly what the brief's core constraint forbids. The honest
surface is what the profiler actually produces: a **rolling window**. So
`weft_unit_cost_seconds{source,type}`, `weft_unit_ticks{source,type}` and
`weft_unit_ai_cost_seconds{type}` are gauges over the current profiler window,
read at scrape time from `snapshotWindow()` (documented safe from any thread),
at zero tick-path cost. `weft_profiler_window_ticks` carries the denominator.
The quantity an operator wants — "what share of my tick is mod X" — is a ratio
within one window anyway.

**`weft_entities`' second label is `category`, not `type`.** `EntityCensus`
tracks by `MobCategory`, because that is what the mobcap is expressed in. There
is no per-type entity census, and inventing one means a full entity walk per
scrape. Per-type *ticked* counts remain available from `weft_unit_ticks`.

**`weft_tick_duration_seconds` is `weft_tick_period_seconds`, and MSPT is a
separate gauge.** The available measurement at the existing `tickServer` HEAD
hook is the head-to-head interval — the tick *period*. `MinecraftServer` sleeps
in its loop to hold 20 TPS, so that reads ~50 ms on any server keeping up
regardless of load, and shipping it as "tick duration" would tell an operator
their tick was saturated when it was idle. The period is the right input for
`weft_tps` and for outlier detection; MSPT arrives as
`weft_mspt_seconds`, vanilla's own 100-tick mean — the number `/tps` and spark
report, and therefore the one an admin can cross-check. A per-tick *work*
duration would need a second injection into `tickServer`, and §8's R2 claim is
that this module needs no mixins at all; keeping that promise is worth more than
a histogram of a figure vanilla already averages.

**`weft_service_latency_seconds` is `weft_service_last_latency_seconds`, a
gauge.** §2 filed this as AGGREGATE — "a histogram fed from values already
timed". The values already timed are *last* values: `captureNanosLast` and
friends are overwritten every tick. Feeding a histogram from them at the
snapshot cadence would produce a once-a-second sample of a per-tick quantity
presented as a distribution, and its quantiles would describe the sampler rather
than the service. A real histogram needs accumulation on the capture path, which
is a measurement change in P1's territory, not a serialization one. Three gauges
labelled by `stage` (`capture`, `build`, `compute`) say exactly what is known.

**`weft_region_chunks` is sampled once a second, not once per scrape.** It is a
distribution over a live population, and a Prometheus histogram is cumulative
over time. Observing each region's chunk count at the snapshot cadence makes it
a genuine histogram whose quantiles mean "the distribution of region sizes over
the last hour". Emitting an instantaneous population as bucket counts would
produce a `_count` that moves in both directions, which breaks
`histogram_quantile` in the same way §3.2's counter would have broken `rate`.

## 4. Absent, not zero — R1 expressed in the metric surface

`observability` may not depend on any sibling module (R1), and most of its
sources *are* siblings. When a source module is inactive, the exporter
**omits those series entirely** rather than exporting `0`.

A zero is a measurement claim. `weft_legacy_mod_cost_seconds_total{modid=...}`
at 0 says "mod X costs nothing"; absent says "Weft is not attributing legacy
cost right now", which is the truth when `legacy_lane` is off. Prometheus and
Grafana both handle absent series natively (`absent()`, no-data panels), and
this is the convention its own exporters follow.

Two consequences worth stating so nobody "fixes" them later:

- `weft_tick_duration_seconds` reads the **server's** tick times, not
  `TickProfiler`, precisely so tick duration survives `/weft profile off`.
- The dashboard's per-mod legacy panel is empty on a server with the lane off.
  That is correct, and the panel says so.

## 5. Event stream

Newline-delimited JSON, one object per line, rotating file sink. Envelope,
present on every line:

```json
{"v":1,"ts":"2026-08-17T14:03:11.482Z","kind":"guard_trip",
 "server_id":"<stable per install>","weft_version":"0.1.0-alpha",
 "mc_version":"1.21.1","data":{}}
```

`v` is the envelope version, so a consumer can reject rather than
misinterpret. `ts` is RFC 3339, UTC, millisecond precision. `server_id` is a
random UUID generated once and persisted beside the world — **not** derived
from IP, hostname, or player data. An event stream an operator ships to a log
aggregator must not smuggle identifying data.

Kinds, all with `data` payloads pinned by the committed schema:

| kind | payload |
|---|---|
| `guard_trip` | `kind`, `severity`, `thread`, `context_kind`, `context_owner`, `target_kind`, `target_id`, `degradation`, `stack` (top N frames) |
| `module_state_change` | `module`, `from`, `to`, `reason`, `neighbor` (when yielded/refused) |
| `service_fallback` | `service`, `level`, `reason`, `consecutive`, `latched` |
| `region_merge` / `region_split` | `level`, `region_ids`, `result_ids`, `chunks_before`, `chunks_after` |
| `profiler_snapshot` | the §6 document, inline |
| `startup_posture` | the full RFC-0003 R5 table as structured rows, once at boot |
| `config_change` | `key`, `from`, `to`, `source` (`file` / `command`) |
| `tick_outlier` | `tick`, `duration_seconds`, `median_seconds`, `factor`, `top_sources` (top N `{source,type,seconds}` from that tick), `phase_breakdown` |

`tick_outlier` needs the rolling median maintained anyway; `guard_trip` is the
highest-value line in the file.

**Rotation:** size-triggered at `eventStreamMaxMB`, one `.1` predecessor kept,
writes on a dedicated single-thread executor with a bounded queue. Queue full
⇒ **drop and count** (`weft_event_stream_dropped_total`); a telemetry sink must
never apply backpressure to the tick. Rotation and formatting both happen off
the server thread.

## 6. Profiler snapshot document

The same content `/weft report` renders, as JSON: window size, ticks analysed,
`totalNanos` / `spatialNanos` / `globalNanos`, the entity AI split,
hypothetical regions with chunk counts and cost, `speedupByWorkers`, the WS-1
projection fields, and `topTypes`. Field names mirror
`RegionizabilityAnalyzer.Report`'s accessors so the two cannot drift.

Reachable two ways: `/weft report --json` (writes `weft-report.json` beside the
existing `weft-report.txt`) and as a `profiler_snapshot` event. **The text
report is unchanged and stays the human surface** — including
`ReportFormatter`'s `\n`-only, ASCII-safe output contract, which exists for
Minecraft chat and cp1252 consoles and must not be "modernised" here.

## 7. Configuration

New `[observability]` section in `weft-common.toml` — the first section in a
so-far flat file, via `ModConfigSpec.Builder.push`/`pop`.

```toml
[observability]
metricsEnabled      = false          # opt-in
metricsBindAddress  = "127.0.0.1"    # loopback ONLY by default
metricsPort         = 9940
jvmMetricsEnabled   = true           # see 8.2
eventStreamEnabled  = false
eventStreamPath     = "logs/weft-events.ndjson"
eventStreamMaxMB    = 128            # rotate
maxLabelCardinality = 50             # per label-bearing family; tail -> __other__
tickOutlierFactor   = 4.0            # multiple of the rolling median
regionTimingEnabled = true           # the 9.2 probe; no effect while the module is off
```

**Loopback default, documented loudly.** An unauthenticated metrics port on a
public interface leaks mod list, player counts and world topology. Remote
scraping is the operator's explicit decision, made by changing the bind
address, ideally behind their own reverse proxy. **No authentication and no TLS
on the endpoint** — that is the deployment's job by Prometheus convention, and
a half-built auth scheme is worse than none. The README entry says so in as
many words.

`maxLabelCardinality` applies per label-bearing family: top-N by cost or count,
tail folded into `__other__`. `modid` and `type` are unbounded in principle; a
400-mod pack must not OOM a Prometheus instance on our account.

## 8. RFC-0003 compliance

- **R1 — one module, one switch.** Registers in `WeftModules` as
  `observability`, switched by `metricsEnabled || eventStreamEnabled`. Nothing
  depends on it; it depends on nothing (§4 is how).
- **R2 — fail-soft.** **No mixins at all**, so `hooksApplied` is constant true.
  The runtime failures are a taken port and an unwritable sink: log one clear
  line, self-disable (rung 3), server runs. A telemetry failure must never
  affect the tick.
- **R4 — override.** `forceDisableModules += "observability"` works like every
  other module.
- **R5 — one-glance report.** Appears in the startup posture table and
  `/weft status` with the bound address, whether the sink is writable, and the
  dropped-event count.
- **R6 — yield must be total.** Disabled ⇒ no HTTP server, no writer thread,
  no file handle, no listener registered on `WeftGuards`, no timing probe, no
  allocation. The `WeftTelemetry` publish points are static reads of a
  `volatile boolean` — the same disabled-mode cost the profiler hooks pay.

**One change to the shared ladder this module forced.** Rung 3 can happen
*during* activation, not only before it: the endpoint discovers a taken port and
the sink discovers an unwritable path only when they try. `WeftModules.resolve`
previously reported the state it *asked for*, so the R5 table would have printed
`observability ACTIVE (self-disabled: metrics port ... already in use)` — a line
that contradicts itself, which is exactly the needs-a-debugger confusion R5
exists to prevent. `resolve` now re-reads each module's applied-check after
`applyActive` and downgrades the reported state, so the table, the
`module_state_change` event and `weft_module_state` agree. This is general: any
module that self-disables while starting now reports it. `hooksApplied()` for
this module means "port bindable and sink writable", which is the mixin-free
equivalent of R2's runtime check, and a config reload clears the latch so an
operator who fixes the port and reloads recovers without a restart.

### 8.1 R3: what may enter `weft-neighbors.toml`, and what may not

The brief asks for registry rows for Prometheus Exporter, sladkoff, cpburnz,
Maykesh's SOF and Observable, posture `cooperate`. **Four of those five cannot
be added under this repo's own standing rule, and one can never be.**

RFC-0003 §3.1 / RESEARCH-0004 §5: *a row may not enter the registry without
(a) a modid read from `neoforge.mods.toml` / `fabric.mod.json`, and (b) an R7
matrix cell that boots it. Postures nobody has booted are prose.*

- **sladkoff and cpburnz are Bukkit/Spigot plugins.** They have no modid and
  never will. A registry row for either is a category error.
- **Prometheus Exporter (CurseForge):** its latest NeoForge build is 1.21.4,
  not 1.21.1 (RESEARCH-0003 §4.1, RESEARCH-0004 §4) — it cannot be present on
  Weft's platform today at all.
- **Observable and Maykesh's SOF:** plausible neighbours, modids **not
  verified in this repo**, and RESEARCH-0004 §4 records "a network path to
  Modrinth" as an open blocker. Guessing a modid is the exact failure that
  column exists to prevent: it looks like coverage and matches nothing.

Also: `cooperate` **is already the registry default for unknown mods**, so five
`cooperate` rows would change no behaviour whatsoever. They would be decoration
that reads as coverage.

**What actually deconflicts, and needs no registry at all:**

> The one real conflict is a **port collision**, and a port collision is
> detected by *binding*, not by modid.

`bind()` fails ⇒ log one line naming the address and the error, self-disable
(rung 3), keep serving. This is strictly better than modid detection: it
catches every exporter mod, every Bukkit plugin under a proxy, every unrelated
process on the box, and mods that do not exist yet.

One correction to the brief: we **cannot log which neighbour holds the port.**
Mapping a listening socket to a mod means process/socket inspection, and
RFC-0003 §4 forbids reflecting into neighbours' internals — scraping a
neighbour's HTTP endpoint to identify it is worse. The log line names the
address and says "another process on this host is listening; change
`metricsPort`". Report the port, not the culprit.

**Proposed instead of five inert rows:** a RESEARCH-0004 §2 entry ("Known,
deliberately *not* in the registry") covering all five with the reasons above,
plus an R7 matrix cell that proves the **port-collision self-disable**
end-to-end — occupy 9940, boot, assert `observability SELF-DISABLED` in the R5
table and a clean startup. That cell tests the mechanism that actually fires.

### 8.2 The `weft_jvm_*` decision, made explicitly

The brief asks us to *consider* suppressing JVM series when a neighbour already
exports them. Decision, with reasons:

1. **No detection is possible.** Suppression cannot key on modid (none
   verified) and must not key on probing a neighbour's endpoint (§8.1). It is
   therefore a config toggle, `jvmMetricsEnabled`, or it is nothing.
2. **Default `true`.** On NeoForge 1.21.1 there is *no* shipping exporter mod
   (verified, RESEARCH-0003 §4.1), so for most operators Weft will be the only
   source and the dashboard's heap/GC panel should work out of the box. The
   duplicate-scrape cost of ~6 series is not a real cost. The config comment
   says: turn this off if another exporter already covers your JVM.
3. **Counter pair, not a histogram.** `weft_jvm_gc_pause_seconds` as a
   histogram needs a `GarbageCollectionNotificationInfo` listener — a thread
   and a subscription — and **GC-pause attribution is WS-6.2's territory**
   (RESEARCH-0001 §5). v1 ships what a scrape can read with zero threads:

```
weft_jvm_gc_collections_total   counter   label: collector   (GarbageCollectorMXBean)
weft_jvm_gc_seconds_total       counter   label: collector
weft_jvm_heap_bytes             gauge     label: area (used|committed|max)
```

`rate(gc_seconds_total)/rate(gc_collections_total)` is mean pause, and the
histogram becomes an additive upgrade the day WS-6 owns a GC listener.

## 9. Overhead budget

RFC-0002's criterion is "overhead unmeasurable at 10 s scrape interval". Held
as a **measured** claim, not an assertion.

### 9.1 Rules

- **Collection is off the server thread.** Counters accumulate into structures
  that already exist; the exporter reads and formats on the scrape thread. If a
  scrape would need state the server thread owns, it is snapshotted at a phase
  boundary — never reached across. (`TickProfiler.snapshotWindow()` and
  `LegacyLane.costByModNanos()` are already safe from any thread; the
  `RegionManager` maps are **not** — plain collections mutated on the server
  thread, per RFC-0007 §3.1. Region topology is therefore published into an
  immutable snapshot at the tick boundary, and the scrape reads that.)
- **No allocation on the tick path** attributable to this module.
- **Disabled ⇒ undetectable.** One `volatile boolean` read per publish point.

### 9.2 The one new probe, and why it is allowed

Two `System.nanoTime()` calls **per bucket per section per tick**, plus one pair
around each fan-out barrier. Both are **O(buckets), not O(units)** —
`RegionizedTicking` already builds a `long[] partition` of unit counts per
section, and this fills a parallel `long[] nanos`. On a solo world (one region,
the WS-10 case) that is two `nanoTime` calls per section. The existing profiler
pays two *per entity*.

It buys the three things RFC-0002 §WS-7 names as acceptance panels and that
nothing else in Weft can answer: per-region tick duration, hottest-region
share, and a true worker-utilisation ratio (§3.3).

Gated twice: on the `observability` module being active **and** on
`regionTimingEnabled`. Off ⇒ the array is never allocated (R6).

### 9.3 The gate that decides whether this ships

- A WS-8 JMH case for the metric-collection and exposition-format path. The
  exporter's pure parts live in `weft-engine`, which already has a `jmh` source
  set, so this is in scope for `:weft-engine:jmh`.
- End-to-end **same-run A/B on the benchmark world**, in the style of
  `p1EndToEndMspt`: exporter enabled and being scraped at 10 s versus disabled,
  **interleaved A/B/A/B with leading samples skipped**
  (`SectionSamples.stats(skipLeading)`), reported as median and p95. Cross-run
  deltas on shared runners are mostly variance — the retracted 1.59x is the
  standing reminder, and same-run A/B alone was not enough to kill warmup-
  *order* bias.
- Recorded on `bench-data` through the nightly `bench.yml` gate
  (`customSmallerIsBetter`, 150% threshold) like every other Weft benchmark, so
  a future regression fails CI.

### 9.4 What it measured (2026-08-17)

The harness is `ObservabilityBenchGameTests` — six interleaved phases, OFF /
ON@10s / ON@every-tick, twice over, 200 ticks each, 1200 mobs and a bot.

| Condition | Median MSPT | Delta |
|---|---|---|
| Exporter off | 10.26 / 10.92 ms | — |
| **On, scraped at the 10 s cadence** | 10.30 / 10.91 ms | **+0.42% / −0.12%** |
| Control: on, scraped every tick (200×) | 11.13 / 11.62 ms | **+8.5% / +6.4%** |

**Inside ±0.5% at the shipping cadence, from an instrument that resolves a 200×
cadence at +6 to +8%.** That is the acceptance criterion met as a measured claim.

**The negative control is the load-bearing part**, and it was not in the reviewed
plan. RFC-0002 asks for overhead that is *unmeasurable*; a null result only counts
as evidence if the same harness can be shown to resolve a load it should resolve,
because otherwise "we saw nothing" and "the instrument is broken" produce
identical output.

It earned its place twice over by invalidating two harnesses:

1. The first version scraped from inside `onEachTick`, i.e. on the server thread,
   and the control came back at **+514%** — implying ~56 ms per scrape. That
   number was real and entirely about the test rig: the server thread was blocking
   on a localhost HTTP round trip that no production tick waits for, because
   Prometheus is another process and the exporter answers on its own daemon
   thread.
2. The second version called `WeftModules.resolve()` to start the exporter, which
   re-resolves **every** module from config. The first OFF phase therefore ran
   with the P1 services pinned off and every later phase ran with them on — the
   OFF pool was a mix of two different worlds, which is what produced sign flips
   between runs. The phases now toggle only the module under test, deliberately
   not through the ladder.

Readings taken under either broken harness (+0.1%, +2.6%, −3.4%, −1.4%) are
discarded rather than pooled with the corrected ones.

## 10. Testing — gates before feature

In landing order, mirroring how RFC-0005 preceded the first ownership mixin.

1. **Exposition-format conformance.** Verified fact: **the official Java
   Prometheus client has no text-format parser** — `io.prometheus:
   prometheus-metrics-exposition-textformats:1.8.0` contains writers only
   (checked against the published jar). So "parse it with a real parser" is
   delivered in two pieces:
   - *Unit gate:* `ExpositionParser` in the test source set — a deliberately
     **strict** reader that rejects things the Go parser tolerates, because they
     are things *our* exporter must never emit: a sample with no preceding
     `# TYPE`, a duplicate series, a counter not suffixed `_total`, a histogram
     missing its `+Inf` bucket or with non-cumulative counts, an OpenMetrics
     body without `# EOF`, a Prometheus body with one.
     **Not the reference writer as an oracle**, which was the first plan: its
     output differs from ours in legal, cosmetic ways (`1.0` vs `1`, label
     ordering), so a mismatch would prove nothing and a normaliser strong enough
     to fix that is itself a parser. Runs offline, on every build.
   - *CI gate:* `promtool check metrics` over a scrape captured by the
     gametest. This is the canonical parser, and it **also lints naming and
     unit conventions** — precisely the `_total`/base-unit discipline the
     surface above commits to. `promtool` ships inside
     `prometheus-<ver>.linux-amd64.tar.gz` (verified: v3.13.2 current).
2. **Correctness against a known world.** Gametest on the RFC-0005 parity arena
   (`ParityScenario` — furnaces, hopper clocks and penned mobs already there):
   scrape, then assert the scraped values match what `TickProfiler`,
   `RegionTopology` and `WeftServices` independently report. **Vacuous-run
   guard mandatory**: if the arena produced no ticks, no entities and no BE
   work, the test *fails* rather than passing on empty agreement. That guard
   has already paid for itself once here — RFC-0005's first "green" run was
   comparing identical emptiness.
3. **Event-stream schema validation.** Every emitted kind validates against a
   committed JSON schema. The schema is the contract; it is checked in.
4. **Cardinality cap.** Synthesize more label values than the cap; assert
   `__other__` absorbs the tail and the total series count stays bounded.
5. **Fail-soft, both failures.** Occupy the port, boot, assert `SELF-DISABLED`
   plus one log line plus an unaffected tick. Same for an unwritable
   `eventStreamPath`. The port case doubles as the R7 matrix cell (§8.1).
6. **Overhead.** §9.3, wired nightly with a recorded baseline.

## 11. Module layout

- **`weft-api`** — `WeftTelemetry`: the publish registry (RESEARCH-0003 §4.1's
  proposed shape). Named counters/gauges/histograms that any Weft module — in
  principle any mod — publishes into. Modules stay independent (R1); the
  endpoint stays a pure reader.
- **`weft-engine`** — the exposition formatter, the NDJSON writer with
  rotation, the cardinality cap, the rolling-median outlier detector, and the
  HTTP server (`com.sun.net.httpserver`, JDK-only). **No Minecraft imports**;
  `verifyNoMinecraftImports` enforces it on `check`.
- **`weft-neoforge`** — collectors that read Minecraft-side state, the
  `WeftModules` registration, the `/weft report --json` wiring, the
  `WeftGuards` listener, and the `[observability]` config block.

## 12. Deliverables

1. This RFC plus a two-line amendment pointer in RFC-0002 §WS-7.
2. Implementation per §11.
3. A RESEARCH-0004 §2 entry for the five exporter tools (§8.1) — **not**
   `weft-neighbors.toml` rows — plus the port-collision R7 cell.
4. Grafana dashboard JSON in-repo. Panels: MSPT percentiles with phase and
   section breakdown; region count and hottest-region share; **per-mod
   legacy-lane cost (the flagship)**; guard trips; module posture; worker
   utilisation and queue depth; service health.
5. README status entry in house style: what shipped, what the **measured**
   overhead was, and the honest caveats — including that `weft_jvm_gc_*` is a
   counter pair rather than a pause histogram, and that panels fed by inactive
   modules are legitimately empty.

## 13. Explicitly out of scope

Push-based export (OTLP), remote write, authentication, TLS, a bundled
time-series database, and any UI beyond the dashboard JSON. OTLP would be an
additive surface over the same collection layer; deciding it now is premature.

## 14. Review decisions (resolved 2026-08-17)

All four open questions were put to review before implementation started. The
surface above is the approved one; these are the decisions and the reasons, so
a later reader does not re-litigate them.

1. **§3.4 — `weft_mail_delivery_seconds` is dropped from v1.** ACCEPTED. The
   alternative costs a timestamp per message on a path RFC-0001 §8.4 pools
   deliberately, to measure a quantity that is near-constant by construction.
   `weft_mail_messages_total{outcome="stranded_reroute"}` covers the failure
   that actually matters. Revisit with off-thread routing (RFC-0007 §5).
2. **§8.1 — zero new `weft-neighbors.toml` rows.** ACCEPTED: prose in
   RESEARCH-0004 §2 for all five exporter tools, plus an R7 matrix cell that
   proves the port-collision self-disable end-to-end. RFC-0003 §3.1's standing
   rule holds; the mechanism that actually fires is the one that gets a test.
   This is a deliberate deviation from the WS-7 brief's deliverable list.
3. **§8.2 — `jvmMetricsEnabled` defaults `true`, `weft_jvm_gc_*` is a counter
   pair.** ACCEPTED. No exporter mod ships on NeoForge 1.21.1, so Weft is
   usually the only source; a pause histogram needs a GC listener and belongs
   to WS-6.2.
4. **§9.2 — the new probe ships, double-gated.** ACCEPTED. It is the only
   measurement this workstream adds, it is O(buckets) rather than O(units),
   and its cost is a measured claim via §9.3 rather than an assertion. Without
   it, RFC-0002 §WS-7's own "region count & hottest-region share" acceptance
   criterion could not be met by this workstream at all.

---

## Appendix A — the final metric surface, in one block

This is the list to review. Everything is namespaced `weft_`, base units
(seconds, bytes, ratio), `_total` on counters, `level` = dimension id.
Deviations from the WS-7 brief are marked `[N]` with the section that argues
them. Series marked **(probe)** are absent unless §9.2's probe is on; all
others are absent unless their source module is active (§4).

```
# tick
weft_tick_period_seconds                histogram  head-to-head interval          [3.11]
weft_mspt_seconds                       gauge      vanilla's own 100-tick mean    [3.11]
weft_tick_phase_duration_seconds        histogram  phase (INGEST|REGION|MAIL|COMMIT|
                                                   LEGACY|GLOBAL|EGRESS)
weft_section_duration_seconds           histogram  section (ENTITY|BLOCK_ENTITY), level   [3.1]
weft_tps                                gauge

# regions
weft_regions                            gauge      level
weft_region_chunks_loaded               gauge      level
weft_region_largest_chunks              gauge      level   (hottest-region numerator)
weft_region_chunks                      histogram  level   (sampled 1/s)         [3.11]
weft_region_buckets                     gauge      level, section
weft_region_merges_total                counter    level
weft_region_splits_total                counter    level
weft_region_tick_duration_seconds       histogram  level                        (probe)

# workers / scheduler
weft_worker_utilization_ratio           gauge      pool                         (probe)  [3.3]
weft_worker_queue_depth                 gauge      pool
weft_worker_steals_total                counter    pool                                  [3.3]
weft_barrier_wait_seconds               histogram  section                      (probe)
weft_worker_parallelism                 gauge      pool
weft_owned_sections_total               counter    mode (serial|parallel)

# mail
weft_mail_messages_total                counter    type, outcome (routed|inline_fallback|
                                                   global_fallback|stranded_reroute)
#  weft_mail_delivery_seconds           DROPPED from v1                                  [3.4]

# legacy lane  -- the section 9.1 "your tick is 61% mod X" number
weft_legacy_lane_duration_seconds       histogram
weft_legacy_mod_cost_seconds_total      counter    modid
weft_legacy_extractions_total           counter    modid
weft_legacy_extracted_units_total        counter    kind (entity|be)

# simulation cost attribution (gauges over the profiler window)          [3.2, 3.11]
weft_unit_cost_seconds                  gauge      source (ENTITY|BLOCK_ENTITY|GLOBAL), type
weft_unit_ticks                         gauge      source, type
weft_unit_ai_cost_seconds               gauge      type
weft_profiler_window_ticks              gauge      (the denominator for the three above)
weft_entities                           gauge      level, category                      [3.11]
weft_block_entities_ticking             gauge                                            [3.5]

# services
weft_service_requests_total             counter    service, level, outcome
weft_service_last_latency_seconds       gauge      service, level, stage         [3.11]
weft_spawn_density_latched_off          gauge      level
weft_census_tracked                     gauge      level
weft_census_drift_total                 counter    level
weft_census_reconciles_total            counter    level
weft_spawn_density_fallbacks_total      counter    level
weft_spawn_density_parity_ticks_total   counter    level                                 [3.6]
weft_spawn_density_parity_mismatch_ticks_total  counter  level                           [3.6]
weft_path_requests_total                counter    outcome (submitted|filled|no_path)    [3.7]

# safety -- the ones that should page a human
weft_guard_trips_total                  counter    kind, severity                        [3.8]
weft_module_state                       gauge      module, state (active|yielded|
                                                   self_disabled|disabled|refused); 1/0  [3.9]
weft_module_selfdisable_total           counter    module, reason

# jvm  (suppressed by jvmMetricsEnabled = false)
weft_jvm_gc_collections_total           counter    collector                             [8.2]
weft_jvm_gc_seconds_total               counter    collector                             [8.2]
weft_jvm_heap_bytes                     gauge      area (used|committed|max)

# the exporter's own health
weft_scrapes_total                      counter
weft_scrape_duration_seconds_total      counter
weft_scrape_duplicate_series_total      counter    non-zero = two collectors collide
weft_event_stream_written_total         counter
weft_event_stream_healthy               gauge
weft_event_stream_events_total          counter    kind
weft_event_stream_dropped_total         counter
weft_exporter_build_info                gauge      weft_version, mc_version; 1
```

Event kinds, final: `guard_trip`, `module_state_change`, `service_fallback`,
`region_merge`, `region_split`, `profiler_snapshot`, `startup_posture`,
`config_change`, `tick_outlier`. Envelope per §5.

*End of RFC-0009 draft 1.*
