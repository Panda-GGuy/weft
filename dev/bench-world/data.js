window.BENCHMARK_DATA = {
  "lastUpdate": 1787285628577,
  "repoUrl": "https://github.com/Panda-GGuy/weft",
  "entries": {
    "Weft world bench": [
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "id": "e9a07b682a57b87d1c6bd772046ecac7f18fbfc1",
          "message": "feat(profiler): AI-vs-movement sub-attribution of entity samples (WS-1 sizing)\n\nPart A of the WS-1 widening work (RFC-0002): TickSample gains aiNanos,\nthe slice of an entity tick spent inside Mob.serverAiStep (sensing, goal/\ntarget selectors, navigation, brain/custom step, controls - exactly the\nuniverse WS-1 gating can widen into). New required-config MobMixin times\nthe slice; WeftProfiler's timing stack becomes {start, aiAccum, aiStart}\nframes so passenger AI still attributes to the vehicle sample.\n\nThe analyzer now splits the entity phase into AI vs movement/physics and\nadds a *measured* widened-gating projection (aiNanos * (interval-1)/interval\nover throttleable samples) next to the existing whole-sample upper bound;\n/weft report prints both. ws1EntityPhaseReduction records the AI slice in\nboth phases so weft-bench.json carries the sizing nightly.\n\nEngine stays free of net.minecraft imports; old TickSample constructors\nkept (aiNanos=0) so legacy callers and benches are untouched.\n\nCo-Authored-By: Claude Fable 5 <noreply@anthropic.com>",
          "timestamp": "2026-08-17T06:27:35Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/e9a07b682a57b87d1c6bd772046ecac7f18fbfc1"
        },
        "date": 1786948261846,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 37.7355,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 30.462,
            "unit": "ms/tick",
            "extra": "19.3% entity-phase reduction (acceptance bar: >=30%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.8023,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "28bcb3d2af9d12292ea7a292f7a0a0f687b136c1",
          "message": "Create RESEARCH-0003-integration-hooks-and-errata.md",
          "timestamp": "2026-08-18T03:14:53Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/28bcb3d2af9d12292ea7a292f7a0a0f687b136c1"
        },
        "date": 1787025836234,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 35.5344,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 7.2033,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 20.3% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.3614,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.8% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 28.3377,
            "unit": "ms/tick",
            "extra": "20.3% entity-phase reduction (acceptance bar: >=30%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 652388 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 34.3394,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 33.3932,
            "unit": "ms/tick",
            "extra": "2.8% entity-phase reduction; 3954 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 10.1032,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 8.0269,
            "unit": "ms/tick",
            "extra": "20.6% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4939 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 69,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 135 ticks served async"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.0248,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 33.5268,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 37.356 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 34.8447,
            "unit": "ms/tick",
            "extra": "-3.9% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 39.026 ms; 296/300 ticks served async"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "id": "94e5ed9dcd4b6c1f3618799d1abf50a183e2265f",
          "message": "docs: remap cited commit SHAs after the history rewrite\n\nThe identity rewrite changed every commit SHA, so the three SHAs cited in\nthe docs pointed at commits that no longer exist. Remapped via\nfilter-repo's commit-map:\n\n  8973061 -> cf9bb78  (RFC-0007:42,  merge of PR #1)\n  c2fd0df -> edfff01  (RFC-0007:123, pregen churn fix)\n  1f217cd -> cd39aed  (RESEARCH-0003:387, Moonrise neighbor)\n\nCo-Authored-By: Claude Opus 5 <noreply@anthropic.com>",
          "timestamp": "2026-08-18T06:07:25Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/94e5ed9dcd4b6c1f3618799d1abf50a183e2265f"
        },
        "date": 1787034787542,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 34.6561,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.5031,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.9% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.6941,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (5.7% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 29.6863,
            "unit": "ms/tick",
            "extra": "14.3% entity-phase reduction (acceptance bar: >=30%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 653566 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 35.0958,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 33.9475,
            "unit": "ms/tick",
            "extra": "3.3% entity-phase reduction; 3969 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.3923,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.7671,
            "unit": "ms/tick",
            "extra": "17.3% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 5045 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 72,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 131 ticks served async"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.9586,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, ONE region, partitioned ticking with blockEntitySharding OFF; 1600 ticking block entities across 400 chunks; p95 1.825 ms; 300 measured ticks"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.2908,
            "unit": "ms/tick",
            "extra": "same run, blockEntitySharding ON (4 colour passes): 0.74x speedup, -34.6% full-tick MSPT reduction; p95 1.828 ms; 1200 shard passes over 158700 units, max 36 concurrent chunks"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 5.829,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 33.432,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 36.299 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 32.7216,
            "unit": "ms/tick",
            "extra": "2.1% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 34.933 ms; 47/300 ticks served async"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "id": "94e5ed9dcd4b6c1f3618799d1abf50a183e2265f",
          "message": "docs: remap cited commit SHAs after the history rewrite\n\nThe identity rewrite changed every commit SHA, so the three SHAs cited in\nthe docs pointed at commits that no longer exist. Remapped via\nfilter-repo's commit-map:\n\n  8973061 -> cf9bb78  (RFC-0007:42,  merge of PR #1)\n  c2fd0df -> edfff01  (RFC-0007:123, pregen churn fix)\n  1f217cd -> cd39aed  (RESEARCH-0003:387, Moonrise neighbor)\n\nCo-Authored-By: Claude Opus 5 <noreply@anthropic.com>",
          "timestamp": "2026-08-18T06:07:25Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/94e5ed9dcd4b6c1f3618799d1abf50a183e2265f"
        },
        "date": 1787112581022,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 32.6006,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 4.7957,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 14.7% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.0714,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.1% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 25.8501,
            "unit": "ms/tick",
            "extra": "20.7% entity-phase reduction (acceptance bar: >=30%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 654170 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 31.8708,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 31.3304,
            "unit": "ms/tick",
            "extra": "1.7% entity-phase reduction; 3948 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.0617,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 5.9696,
            "unit": "ms/tick",
            "extra": "34.1% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4972 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 70,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 132 ticks served async"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.9795,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, ONE region, partitioned ticking with blockEntitySharding OFF; 1600 ticking block entities across 400 chunks; p95 2.024 ms; 300 measured ticks"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.3417,
            "unit": "ms/tick",
            "extra": "same run, blockEntitySharding ON (4 colour passes): 0.73x speedup, -37.0% full-tick MSPT reduction; p95 2.165 ms; 1200 shard passes over 165600 units, max 36 concurrent chunks"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.7002,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 30.1244,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 34.401 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 29.8466,
            "unit": "ms/tick",
            "extra": "0.9% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.604 ms; 7/300 ticks served async"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "id": "d1095cce959485e49227d68439c84277f5f6ed49",
          "message": "ci: allow manual workflow_dispatch for on-demand JAR builds",
          "timestamp": "2026-08-20T04:11:17Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/d1095cce959485e49227d68439c84277f5f6ed49"
        },
        "date": 1787199100628,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 34.8228,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.5125,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.8% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.6763,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (5.6% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 29.7166,
            "unit": "ms/tick",
            "extra": "14.7% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 653603 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 69.59,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (5.512 -> 1.676 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 32.9828,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 32.9017,
            "unit": "ms/tick",
            "extra": "0.2% entity-phase reduction; 3956 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 14.999,
            "unit": "ms/tick",
            "extra": "OFF median 14.999 ms (p95 31.996, n=388); ON@10s median 15.011 ms (p95 17.938, n=388, 3 scrapes) = +0.08%; CONTROL ON@every-tick median 14.932 ms (p95 23.320, n=388, 81 scrapes) = -0.45%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 15.0111,
            "unit": "ms/tick",
            "extra": "OFF median 14.999 ms (p95 31.996, n=388); ON@10s median 15.011 ms (p95 17.938, n=388, 3 scrapes) = +0.08%; CONTROL ON@every-tick median 14.932 ms (p95 23.320, n=388, 81 scrapes) = -0.45%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 0.0806,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 14.999 ms (p95 31.996, n=388); ON@10s median 15.011 ms (p95 17.938, n=388, 3 scrapes) = +0.08%; CONTROL ON@every-tick median 14.932 ms (p95 23.320, n=388, 81 scrapes) = -0.45%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": -0.4474,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 14.999 ms (p95 31.996, n=388); ON@10s median 15.011 ms (p95 17.938, n=388, 3 scrapes) = +0.08%; CONTROL ON@every-tick median 14.932 ms (p95 23.320, n=388, 81 scrapes) = -0.45%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2571,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.263 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2446,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.871 ms; 1.05x vs serial (p95 1.45x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 1.0581,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.725,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.46x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 73,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 133 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.1663,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.622,
            "unit": "ms/tick",
            "extra": "16.8% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4991 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2724,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.835 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.7019,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 2.127 ms; 0.39x vs serial (p95 0.39x); 2280 shard passes over 314640 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.8302,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.2873,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.64x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 26.4864,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 29.958 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 25.3541,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 28.093 ms; 1.04x vs serial (p95 1.07x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 27.8725,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 26.5256,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.05x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 27.1741,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 30.553 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 14.0973,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 17.439 ms; 1.93x vs serial (p95 1.75x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 30.6787,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 17.49,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.75x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 7.1501,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 34.8722,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 37.960 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 33.1742,
            "unit": "ms/tick",
            "extra": "4.9% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 36.648 ms; 54/300 ticks served async"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "163c85ca6ed1f018ede6ffe0f3042736d76b1d38",
          "message": "Merge pull request #26 from Panda-GGuy/crew/lead-state-2026-08-20\n\ndocs(crew): reconcile state after #23/#24/#15",
          "timestamp": "2026-08-21T03:53:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/163c85ca6ed1f018ede6ffe0f3042736d76b1d38"
        },
        "date": 1787285627580,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 37.0229,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 6.1229,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 16.5% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.7277,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (5.7% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 30.3059,
            "unit": "ms/tick",
            "extra": "18.1% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 655069 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 71.7835,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (6.123 -> 1.728 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 34.8078,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 34.4021,
            "unit": "ms/tick",
            "extra": "1.2% entity-phase reduction; 3938 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 15.0546,
            "unit": "ms/tick",
            "extra": "OFF median 15.055 ms (p95 17.525, n=388); ON@10s median 14.751 ms (p95 16.081, n=388, 2 scrapes) = -2.02%; CONTROL ON@every-tick median 15.285 ms (p95 19.436, n=388, 79 scrapes) = +1.53%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 14.7505,
            "unit": "ms/tick",
            "extra": "OFF median 15.055 ms (p95 17.525, n=388); ON@10s median 14.751 ms (p95 16.081, n=388, 2 scrapes) = -2.02%; CONTROL ON@every-tick median 15.285 ms (p95 19.436, n=388, 79 scrapes) = +1.53%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": -2.0198,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 15.055 ms (p95 17.525, n=388); ON@10s median 14.751 ms (p95 16.081, n=388, 2 scrapes) = -2.02%; CONTROL ON@every-tick median 15.285 ms (p95 19.436, n=388, 79 scrapes) = +1.53%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 1.5318,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 15.055 ms (p95 17.525, n=388); ON@10s median 14.751 ms (p95 16.081, n=388, 2 scrapes) = -2.02%; CONTROL ON@every-tick median 15.285 ms (p95 19.436, n=388, 79 scrapes) = +1.53%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.271,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.324 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2635,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.957 ms; 1.03x vs serial (p95 1.38x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.8345,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.8632,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.97x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 71,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 132 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 10.3849,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 6.498,
            "unit": "ms/tick",
            "extra": "37.4% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 5010 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2316,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.667 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.5491,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.692 ms; 0.42x vs serial (p95 0.39x); 2280 shard passes over 275880 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.7926,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.0679,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.74x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 27.9957,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 31.365 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 26.7482,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 29.839 ms; 1.05x vs serial (p95 1.05x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 29.7085,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 28.8142,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.03x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 28.1841,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 31.675 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 14.9128,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 22.696 ms; 1.89x vs serial (p95 1.40x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 32.112,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 19.0933,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.68x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.1868,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 35.9113,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 39.278 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 35.8411,
            "unit": "ms/tick",
            "extra": "0.2% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 38.608 ms; 297/300 ticks served async"
          }
        ]
      }
    ]
  }
}