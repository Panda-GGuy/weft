window.BENCHMARK_DATA = {
  "lastUpdate": 1788170534132,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787371684484,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 29.4393,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 3.9778,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 13.5% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.2331,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.9% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 25.0446,
            "unit": "ms/tick",
            "extra": "14.9% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 655672 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 69.0018,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (3.978 -> 1.233 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 29.2426,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 29.0267,
            "unit": "ms/tick",
            "extra": "0.7% entity-phase reduction; 3966 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 11.4366,
            "unit": "ms/tick",
            "extra": "OFF median 11.437 ms (p95 14.533, n=388); ON@10s median 11.465 ms (p95 14.389, n=388, 2 scrapes) = +0.24%; CONTROL ON@every-tick median 11.968 ms (p95 21.004, n=388, 68 scrapes) = +4.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 11.4645,
            "unit": "ms/tick",
            "extra": "OFF median 11.437 ms (p95 14.533, n=388); ON@10s median 11.465 ms (p95 14.389, n=388, 2 scrapes) = +0.24%; CONTROL ON@every-tick median 11.968 ms (p95 21.004, n=388, 68 scrapes) = +4.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 0.2445,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 11.437 ms (p95 14.533, n=388); ON@10s median 11.465 ms (p95 14.389, n=388, 2 scrapes) = +0.24%; CONTROL ON@every-tick median 11.968 ms (p95 21.004, n=388, 68 scrapes) = +4.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 4.6511,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 11.437 ms (p95 14.533, n=388); ON@10s median 11.465 ms (p95 14.389, n=388, 2 scrapes) = +0.24%; CONTROL ON@every-tick median 11.968 ms (p95 21.004, n=388, 68 scrapes) = +4.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.3204,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.400 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2788,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.848 ms; 1.15x vs serial (p95 1.65x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.9419,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.8862,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.06x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 71,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 127 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 8.3973,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.1874,
            "unit": "ms/tick",
            "extra": "14.4% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4873 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2909,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.965 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.5742,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.467 ms; 0.51x vs serial (p95 0.66x); 2280 shard passes over 288420 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.9191,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.1014,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.83x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 24.3222,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 28.693 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 23.1739,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 26.385 ms; 1.05x vs serial (p95 1.09x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 25.7302,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 23.9928,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.07x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 23.1159,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 27.672 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 13.2316,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 20.094 ms; 1.75x vs serial (p95 1.38x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 25.2002,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 16.7528,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.50x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.6664,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 26.2932,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 28.557 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 26.4009,
            "unit": "ms/tick",
            "extra": "-0.4% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 31.092 ms; 30/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787458280007,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 19.5855,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 3.156,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 16.1% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 0.7626,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.8% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 15.9878,
            "unit": "ms/tick",
            "extra": "18.4% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 654538 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 75.8375,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (3.156 -> 0.763 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 18.3159,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 18.3433,
            "unit": "ms/tick",
            "extra": "-0.1% entity-phase reduction; 3926 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 6.7624,
            "unit": "ms/tick",
            "extra": "OFF median 6.762 ms (p95 7.272, n=388); ON@10s median 6.845 ms (p95 9.177, n=388, 2 scrapes) = +1.22%; CONTROL ON@every-tick median 8.749 ms (p95 65.715, n=388, 163 scrapes) = +29.38%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 6.8447,
            "unit": "ms/tick",
            "extra": "OFF median 6.762 ms (p95 7.272, n=388); ON@10s median 6.845 ms (p95 9.177, n=388, 2 scrapes) = +1.22%; CONTROL ON@every-tick median 8.749 ms (p95 65.715, n=388, 163 scrapes) = +29.38%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 1.217,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 6.762 ms (p95 7.272, n=388); ON@10s median 6.845 ms (p95 9.177, n=388, 2 scrapes) = +1.22%; CONTROL ON@every-tick median 8.749 ms (p95 65.715, n=388, 163 scrapes) = +29.38%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 29.381,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 6.762 ms (p95 7.272, n=388); ON@10s median 6.845 ms (p95 9.177, n=388, 2 scrapes) = +1.22%; CONTROL ON@every-tick median 8.749 ms (p95 65.715, n=388, 163 scrapes) = +29.38%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2131,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.610 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.1784,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.578 ms; 1.19x vs serial (p95 1.05x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.6189,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.5442,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.14x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 71,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 103 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 4.5779,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 3.1605,
            "unit": "ms/tick",
            "extra": "31.0% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 5040 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.1632,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.287 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.2749,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.690 ms; 0.59x vs serial (p95 0.42x); 2280 shard passes over 300960 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.4408,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 0.6665,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.66x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 13.386,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 16.654 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 12.4629,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 14.619 ms; 1.07x vs serial (p95 1.14x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 14.3462,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 13.2815,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.08x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 13.5939,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 17.449 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 6.9804,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 9.414 ms; 1.95x vs serial (p95 1.85x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 14.3762,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 8.1407,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.77x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 5.3688,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 18.4301,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 24.043 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 16.6499,
            "unit": "ms/tick",
            "extra": "9.7% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 20.250 ms; 70/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787545054894,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 25.8117,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 3.8833,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.0% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 0.9399,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.4% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 21.4801,
            "unit": "ms/tick",
            "extra": "16.8% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 652891 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 75.7953,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (3.883 -> 0.940 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 26.2358,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 25.411,
            "unit": "ms/tick",
            "extra": "3.1% entity-phase reduction; 3970 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 9.6762,
            "unit": "ms/tick",
            "extra": "OFF median 9.676 ms (p95 12.357, n=388); ON@10s median 9.682 ms (p95 13.327, n=388, 2 scrapes) = +0.06%; CONTROL ON@every-tick median 10.201 ms (p95 16.851, n=388, 62 scrapes) = +5.42%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 9.6824,
            "unit": "ms/tick",
            "extra": "OFF median 9.676 ms (p95 12.357, n=388); ON@10s median 9.682 ms (p95 13.327, n=388, 2 scrapes) = +0.06%; CONTROL ON@every-tick median 10.201 ms (p95 16.851, n=388, 62 scrapes) = +5.42%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 0.0648,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 9.676 ms (p95 12.357, n=388); ON@10s median 9.682 ms (p95 13.327, n=388, 2 scrapes) = +0.06%; CONTROL ON@every-tick median 10.201 ms (p95 16.851, n=388, 62 scrapes) = +5.42%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 5.4214,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 9.676 ms (p95 12.357, n=388); ON@10s median 9.682 ms (p95 13.327, n=388, 2 scrapes) = +0.06%; CONTROL ON@every-tick median 10.201 ms (p95 16.851, n=388, 62 scrapes) = +5.42%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2107,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.711 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2052,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.653 ms; 1.03x vs serial (p95 1.09x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.7619,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.6148,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.24x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 70,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 134 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 7.135,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 4.8502,
            "unit": "ms/tick",
            "extra": "32.0% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 5015 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2173,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.570 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.3519,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.561 ms; 0.62x vs serial (p95 0.36x); 2280 shard passes over 314640 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.6403,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 0.7141,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.90x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 20.5367,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 23.384 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 19.429,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 21.798 ms; 1.06x vs serial (p95 1.07x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 21.7783,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 20.5807,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.06x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 20.4396,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 25.047 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 10.4754,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 16.192 ms; 1.95x vs serial (p95 1.55x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 22.5593,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 13.5377,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.67x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 11.1292,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 26.1539,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 34.245 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 24.6049,
            "unit": "ms/tick",
            "extra": "5.9% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 26.410 ms; 198/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787631156074,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 35.4499,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.4953,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.5% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.2116,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.5% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 27.1196,
            "unit": "ms/tick",
            "extra": "23.5% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 654937 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 77.9513,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (5.495 -> 1.212 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 33.1743,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 33.4542,
            "unit": "ms/tick",
            "extra": "-0.8% entity-phase reduction; 3906 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 13.8077,
            "unit": "ms/tick",
            "extra": "OFF median 13.808 ms (p95 15.734, n=388); ON@10s median 13.978 ms (p95 18.684, n=388, 3 scrapes) = +1.23%; CONTROL ON@every-tick median 14.339 ms (p95 22.221, n=388, 80 scrapes) = +3.85%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 13.9782,
            "unit": "ms/tick",
            "extra": "OFF median 13.808 ms (p95 15.734, n=388); ON@10s median 13.978 ms (p95 18.684, n=388, 3 scrapes) = +1.23%; CONTROL ON@every-tick median 14.339 ms (p95 22.221, n=388, 80 scrapes) = +3.85%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 1.2342,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 13.808 ms (p95 15.734, n=388); ON@10s median 13.978 ms (p95 18.684, n=388, 3 scrapes) = +1.23%; CONTROL ON@every-tick median 14.339 ms (p95 22.221, n=388, 80 scrapes) = +3.85%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 3.8454,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 13.808 ms (p95 15.734, n=388); ON@10s median 13.978 ms (p95 18.684, n=388, 3 scrapes) = +1.23%; CONTROL ON@every-tick median 14.339 ms (p95 22.221, n=388, 80 scrapes) = +3.85%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2748,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.227 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2757,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.229 ms; 1.00x vs serial (p95 1.00x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.8535,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.7456,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.14x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 74,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 128 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 8.8172,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.9991,
            "unit": "ms/tick",
            "extra": "9.3% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4968 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.231,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.883 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.4894,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.373 ms; 0.47x vs serial (p95 0.64x); 2280 shard passes over 288420 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.7933,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.0732,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.74x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 27.0152,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 30.632 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 25.6235,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 27.878 ms; 1.05x vs serial (p95 1.10x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 28.719,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 27.4755,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.05x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 26.4442,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 30.147 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 14.1798,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 17.801 ms; 1.86x vs serial (p95 1.69x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 29.7461,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 18.1849,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.64x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 7.1681,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 32.5043,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 34.652 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 33.266,
            "unit": "ms/tick",
            "extra": "-2.3% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 36.162 ms; 147/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787717713377,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 34.5731,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.3192,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.4% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.6866,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (5.9% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 28.8185,
            "unit": "ms/tick",
            "extra": "16.6% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 653610 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 68.2925,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (5.319 -> 1.687 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 33.2913,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 33.0789,
            "unit": "ms/tick",
            "extra": "0.6% entity-phase reduction; 3962 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 12.9447,
            "unit": "ms/tick",
            "extra": "OFF median 12.945 ms (p95 16.399, n=388); ON@10s median 13.034 ms (p95 23.410, n=388, 2 scrapes) = +0.69%; CONTROL ON@every-tick median 13.960 ms (p95 22.983, n=388, 77 scrapes) = +7.84%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 13.0341,
            "unit": "ms/tick",
            "extra": "OFF median 12.945 ms (p95 16.399, n=388); ON@10s median 13.034 ms (p95 23.410, n=388, 2 scrapes) = +0.69%; CONTROL ON@every-tick median 13.960 ms (p95 22.983, n=388, 77 scrapes) = +7.84%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 0.6906,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 12.945 ms (p95 16.399, n=388); ON@10s median 13.034 ms (p95 23.410, n=388, 2 scrapes) = +0.69%; CONTROL ON@every-tick median 13.960 ms (p95 22.983, n=388, 77 scrapes) = +7.84%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 7.8447,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 12.945 ms (p95 16.399, n=388); ON@10s median 13.034 ms (p95 23.410, n=388, 2 scrapes) = +0.69%; CONTROL ON@every-tick median 13.960 ms (p95 22.983, n=388, 77 scrapes) = +7.84%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.239,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.364 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.233,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.115 ms; 1.03x vs serial (p95 1.22x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 1.1307,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.6776,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.67x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 67,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 134 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.9083,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.1154,
            "unit": "ms/tick",
            "extra": "28.2% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4976 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.438,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.971 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.6754,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.434 ms; 0.65x vs serial (p95 0.68x); 2280 shard passes over 300960 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 1.0867,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.4844,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.73x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 25.1571,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 28.950 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 24.0852,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 26.477 ms; 1.04x vs serial (p95 1.09x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 26.3398,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 25.2118,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.04x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 25.6875,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 30.107 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 13.2415,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 16.931 ms; 1.94x vs serial (p95 1.78x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 28.2685,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 15.8102,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.79x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 6.3964,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 29.8076,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 32.736 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 30.1194,
            "unit": "ms/tick",
            "extra": "-1.0% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.261 ms; 42/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787840898622,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 34.3128,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.4365,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.8% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.1548,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.2% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 27.2361,
            "unit": "ms/tick",
            "extra": "20.6% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 654711 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 78.7586,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (5.436 -> 1.155 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 32.531,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 31.6137,
            "unit": "ms/tick",
            "extra": "2.8% entity-phase reduction; 3927 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 12.4467,
            "unit": "ms/tick",
            "extra": "OFF median 12.447 ms (p95 15.478, n=388); ON@10s median 12.642 ms (p95 15.572, n=388, 2 scrapes) = +1.57%; CONTROL ON@every-tick median 19.386 ms (p95 115.234, n=388, 198 scrapes) = +55.75%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 12.642,
            "unit": "ms/tick",
            "extra": "OFF median 12.447 ms (p95 15.478, n=388); ON@10s median 12.642 ms (p95 15.572, n=388, 2 scrapes) = +1.57%; CONTROL ON@every-tick median 19.386 ms (p95 115.234, n=388, 198 scrapes) = +55.75%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 1.5694,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 12.447 ms (p95 15.478, n=388); ON@10s median 12.642 ms (p95 15.572, n=388, 2 scrapes) = +1.57%; CONTROL ON@every-tick median 19.386 ms (p95 115.234, n=388, 198 scrapes) = +55.75%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 55.7509,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 12.447 ms (p95 15.478, n=388); ON@10s median 12.642 ms (p95 15.572, n=388, 2 scrapes) = +1.57%; CONTROL ON@every-tick median 19.386 ms (p95 115.234, n=388, 198 scrapes) = +55.75%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2786,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.583 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2706,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.590 ms; 1.03x vs serial (p95 0.99x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.7228,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.7413,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.98x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 68,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 124 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.1376,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 6.9406,
            "unit": "ms/tick",
            "extra": "24.0% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4903 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.3099,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.753 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.4971,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.979 ms; 0.62x vs serial (p95 0.77x); 2280 shard passes over 288420 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.7818,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.0722,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.73x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 24.8934,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 27.946 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 24.3397,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 27.531 ms; 1.02x vs serial (p95 1.02x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 26.3308,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 25.9575,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.01x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 23.9819,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 28.316 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 13.4406,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 19.384 ms; 1.78x vs serial (p95 1.46x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 26.0834,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 17.3218,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.51x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 5.3865,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 30.2789,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 33.978 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 30.0528,
            "unit": "ms/tick",
            "extra": "0.7% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.517 ms; 88/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787931466169,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 34.4106,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 5.3006,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 15.4% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.255,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.5% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 27.609,
            "unit": "ms/tick",
            "extra": "19.8% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 654544 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 76.3234,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (5.301 -> 1.255 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 34.2008,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 31.8479,
            "unit": "ms/tick",
            "extra": "6.9% entity-phase reduction; 3947 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 11.8295,
            "unit": "ms/tick",
            "extra": "OFF median 11.830 ms (p95 15.962, n=388); ON@10s median 12.062 ms (p95 15.570, n=388, 2 scrapes) = +1.97%; CONTROL ON@every-tick median 12.747 ms (p95 22.040, n=388, 71 scrapes) = +7.76%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 12.0624,
            "unit": "ms/tick",
            "extra": "OFF median 11.830 ms (p95 15.962, n=388); ON@10s median 12.062 ms (p95 15.570, n=388, 2 scrapes) = +1.97%; CONTROL ON@every-tick median 12.747 ms (p95 22.040, n=388, 71 scrapes) = +7.76%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 1.969,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 11.830 ms (p95 15.962, n=388); ON@10s median 12.062 ms (p95 15.570, n=388, 2 scrapes) = +1.97%; CONTROL ON@every-tick median 12.747 ms (p95 22.040, n=388, 71 scrapes) = +7.76%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 7.7586,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 11.830 ms (p95 15.962, n=388); ON@10s median 12.062 ms (p95 15.570, n=388, 2 scrapes) = +1.97%; CONTROL ON@every-tick median 12.747 ms (p95 22.040, n=388, 71 scrapes) = +7.76%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2674,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.304 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2638,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.162 ms; 1.01x vs serial (p95 1.12x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.9059,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.838,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.08x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 71,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 132 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 9.6913,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 7.8372,
            "unit": "ms/tick",
            "extra": "19.1% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4928 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.309,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.664 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.6004,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.108 ms; 0.51x vs serial (p95 0.60x); 2280 shard passes over 314640 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.8502,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.1741,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.72x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 26.5681,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 29.944 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 25.1007,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 28.136 ms; 1.06x vs serial (p95 1.06x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 28.4583,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 26.0739,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.09x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 26.2124,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 29.811 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 13.9755,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 20.142 ms; 1.88x vs serial (p95 1.48x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 29.5349,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 18.1412,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.63x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 7.3303,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 30.8447,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 34.076 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 30.2529,
            "unit": "ms/tick",
            "extra": "1.9% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.486 ms; 124/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787998887713,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 27.0582,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 4.715,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 17.4% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 0.9072,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.3% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 20.8582,
            "unit": "ms/tick",
            "extra": "22.9% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 655186 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 80.7593,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (4.715 -> 0.907 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 24.5107,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 23.7653,
            "unit": "ms/tick",
            "extra": "3.0% entity-phase reduction; 3845 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 9.8791,
            "unit": "ms/tick",
            "extra": "OFF median 9.879 ms (p95 10.911, n=388); ON@10s median 9.796 ms (p95 10.613, n=388, 2 scrapes) = -0.84%; CONTROL ON@every-tick median 9.999 ms (p95 14.567, n=388, 54 scrapes) = +1.21%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 9.7961,
            "unit": "ms/tick",
            "extra": "OFF median 9.879 ms (p95 10.911, n=388); ON@10s median 9.796 ms (p95 10.613, n=388, 2 scrapes) = -0.84%; CONTROL ON@every-tick median 9.999 ms (p95 14.567, n=388, 54 scrapes) = +1.21%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": -0.8405,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 9.879 ms (p95 10.911, n=388); ON@10s median 9.796 ms (p95 10.613, n=388, 2 scrapes) = -0.84%; CONTROL ON@every-tick median 9.999 ms (p95 14.567, n=388, 54 scrapes) = +1.21%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 1.2105,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 9.879 ms (p95 10.911, n=388); ON@10s median 9.796 ms (p95 10.613, n=388, 2 scrapes) = -0.84%; CONTROL ON@every-tick median 9.999 ms (p95 14.567, n=388, 54 scrapes) = +1.21%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2204,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.517 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2168,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.512 ms; 1.02x vs serial (p95 1.01x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.6392,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.6721,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.95x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 69,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 126 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 7.1511,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 5.2068,
            "unit": "ms/tick",
            "extra": "27.2% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4994 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2213,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.435 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.4795,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.828 ms; 0.46x vs serial (p95 0.53x); 2280 shard passes over 288420 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.6729,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 0.9558,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.70x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 20.6335,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 23.514 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 19.6867,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 21.533 ms; 1.05x vs serial (p95 1.09x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 21.6176,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 20.6584,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.05x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 20.2529,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 22.553 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 10.6284,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 12.485 ms; 1.91x vs serial (p95 1.81x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 21.9244,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 12.781,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.72x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 20.3166,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 26.6425,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 30.076 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 26.5301,
            "unit": "ms/tick",
            "extra": "0.4% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.883 ms; 41/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788082073205,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 26.1798,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 3.5593,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 13.6% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 1.0376,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (4.7% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 22.0154,
            "unit": "ms/tick",
            "extra": "15.9% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 652547 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 70.8495,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (3.559 -> 1.038 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 24.8259,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 24.2396,
            "unit": "ms/tick",
            "extra": "2.4% entity-phase reduction; 3946 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 10.3603,
            "unit": "ms/tick",
            "extra": "OFF median 10.360 ms (p95 11.277, n=388); ON@10s median 10.324 ms (p95 11.860, n=388, 2 scrapes) = -0.35%; CONTROL ON@every-tick median 10.635 ms (p95 16.751, n=388, 64 scrapes) = +2.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 10.3242,
            "unit": "ms/tick",
            "extra": "OFF median 10.360 ms (p95 11.277, n=388); ON@10s median 10.324 ms (p95 11.860, n=388, 2 scrapes) = -0.35%; CONTROL ON@every-tick median 10.635 ms (p95 16.751, n=388, 64 scrapes) = +2.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": -0.3485,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 10.360 ms (p95 11.277, n=388); ON@10s median 10.324 ms (p95 11.860, n=388, 2 scrapes) = -0.35%; CONTROL ON@every-tick median 10.635 ms (p95 16.751, n=388, 64 scrapes) = +2.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 2.6519,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 10.360 ms (p95 11.277, n=388); ON@10s median 10.324 ms (p95 11.860, n=388, 2 scrapes) = -0.35%; CONTROL ON@every-tick median 10.635 ms (p95 16.751, n=388, 64 scrapes) = +2.65%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.2125,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.716 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2113,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.673 ms; 1.01x vs serial (p95 1.06x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 0.9022,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.7161,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.26x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 70,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 131 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 7.4097,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 5.3087,
            "unit": "ms/tick",
            "extra": "28.4% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4898 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2164,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.599 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.378,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.939 ms; 0.57x vs serial (p95 0.64x); 2280 shard passes over 288420 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.7227,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 0.9713,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.74x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 19.6263,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 22.855 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 19.0835,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 21.459 ms; 1.03x vs serial (p95 1.07x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 20.5114,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 20.3655,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.01x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 19.8442,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 23.006 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 10.6003,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 13.243 ms; 1.87x vs serial (p95 1.74x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 21.524,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 12.6085,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.71x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 20.3849,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 24.66,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 31.119 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 23.9299,
            "unit": "ms/tick",
            "extra": "3.0% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 26.084 ms; 49/300 ticks served async"
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788170533118,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "ws1_entity_phase_vanilla_ai",
            "value": 32.1405,
            "unit": "ms/tick",
            "extra": "activation scheduling OFF (baseline)"
          },
          {
            "name": "ws1_entity_phase_ai_slice_vanilla",
            "value": 4.6986,
            "unit": "ms/tick",
            "extra": "AI step (serverAiStep) = 14.6% of the vanilla entity phase; the pool WS-1 gating can address"
          },
          {
            "name": "ws1_entity_phase_ai_slice_activated",
            "value": 0.9934,
            "unit": "ms/tick",
            "extra": "AI step remaining with activation scheduling ON (3.9% of its entity phase)"
          },
          {
            "name": "ws1_entity_phase_activation_scheduling",
            "value": 25.2212,
            "unit": "ms/tick",
            "extra": "21.5% entity-phase reduction (parity-tier floor: >=10%); 2000 passive + 500 hostile mobs, 300 measured ticks/phase; 653983 AI skips, 0 repaths deferred (WS-2 requests avoided)"
          },
          {
            "name": "ws1_ai_slice_reduction",
            "value": 78.8567,
            "unit": "%",
            "extra": "parity-tier acceptance bar: >=50% of the AI-step slice removed (4.699 -> 0.993 ms/tick). This is the pool AI-frequency gating can address; the >=30% entity-phase bar belongs to the opt-in aggressive tier (RFC-0002 WS-1)"
          },
          {
            "name": "ws2_entity_phase_sync_pathfinding",
            "value": 31.2082,
            "unit": "ms/tick",
            "extra": "async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_entity_phase_async_pathfinding",
            "value": 30.6724,
            "unit": "ms/tick",
            "extra": "1.7% entity-phase reduction; 3869 requests routed off-thread; 2000 passive + 500 hostile mobs, 300 measured ticks/phase"
          },
          {
            "name": "ws7_exporter_overhead_mspt_off",
            "value": 11.5394,
            "unit": "ms/tick",
            "extra": "OFF median 11.539 ms (p95 14.323, n=388); ON@10s median 11.991 ms (p95 15.483, n=388, 2 scrapes) = +3.91%; CONTROL ON@every-tick median 20.392 ms (p95 112.611, n=388, 217 scrapes) = +76.72%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_mspt_on",
            "value": 11.9906,
            "unit": "ms/tick",
            "extra": "OFF median 11.539 ms (p95 14.323, n=388); ON@10s median 11.991 ms (p95 15.483, n=388, 2 scrapes) = +3.91%; CONTROL ON@every-tick median 20.392 ms (p95 112.611, n=388, 217 scrapes) = +76.72%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_overhead_pct",
            "value": 3.9106,
            "unit": "percent",
            "extra": "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. OFF median 11.539 ms (p95 14.323, n=388); ON@10s median 11.991 ms (p95 15.483, n=388, 2 scrapes) = +3.91%; CONTROL ON@every-tick median 20.392 ms (p95 112.611, n=388, 217 scrapes) = +76.72%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "ws7_exporter_control_overhead_pct",
            "value": 76.7208,
            "unit": "percent",
            "extra": "Negative control: same exporter scraped 200x more often. If this is resolvable and the 10s figure is not, 'unmeasurable at 10s' is a supported claim rather than an absence of evidence. OFF median 11.539 ms (p95 14.323, n=388); ON@10s median 11.991 ms (p95 15.483, n=388, 2 scrapes) = +3.91%; CONTROL ON@every-tick median 20.392 ms (p95 112.611, n=388, 217 scrapes) = +76.72%. 6 interleaved phases (OFF / ON@200t / ON@1t, twice), 200 ticks each, 5 skipped per phase, region timing on, 1200 passive mobs + 1 bot"
          },
          {
            "name": "p2_be_sharding_control_section_serial",
            "value": 0.24,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 1.204 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_section_sharded",
            "value": 0.2296,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 0.859 ms; 1.05x vs serial (p95 1.40x); 0 shard passes over 0 units, max 0 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_serial",
            "value": 1.0198,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_control_mspt_sharded",
            "value": 0.6743,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 1.51x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "spawn_density_live_spawns",
            "value": 70,
            "unit": "mobs",
            "extra": "monsters spawned through the authoritative SpawnState in 150 ticks (doMobSpawning on, midnight); 130 ticks served async"
          },
          {
            "name": "ws2_stress_entity_phase_sync_pathfinding",
            "value": 8.6474,
            "unit": "ms/tick",
            "extra": "300-zombie maze horde, async pathfinding OFF (baseline)"
          },
          {
            "name": "ws2_stress_entity_phase_async_pathfinding",
            "value": 6.2993,
            "unit": "ms/tick",
            "extra": "27.2% entity-phase reduction on the WS-2 acceptance world (300 zombies, sealed-keep maze, every repath runs to the A* node budget); 4944 requests routed off-thread, 300 measured ticks/phase"
          },
          {
            "name": "p2_be_sharding_section_serial",
            "value": 0.2692,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding OFF; p95 0.605 ms; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_section_sharded",
            "value": 0.5361,
            "unit": "ms/tick",
            "extra": "median block-entity-section wall time, blockEntitySharding ON; p95 1.333 ms; 0.50x vs serial (p95 0.45x); 2280 shard passes over 300960 units, max 36 concurrent chunks; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_serial",
            "value": 0.7504,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_be_sharding_mspt_sharded",
            "value": 1.3358,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, blockEntitySharding ON; 0.56x full-tick (Amdahl-bounded by the rest of the tick); ONE region, 1600 ticking block entities across 400 chunks, 4 colour passes; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_serial",
            "value": 24.9001,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 28.783 ms; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_entity_section_parallel",
            "value": 23.5585,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 26.547 ms; 1.06x vs serial (p95 1.08x); 0 worker threads seen; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_serial",
            "value": 25.779,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_control_mspt_parallel",
            "value": 25.1366,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.03x full-tick (Amdahl-bounded by the rest of the tick); 1 region(s), 1760 mobs, 1-1 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_serial",
            "value": 25.342,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, partitionedTicking ON / parallelRegions OFF; p95 28.472 ms; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_entity_section_parallel",
            "value": 13.6261,
            "unit": "ms/tick",
            "extra": "median entity-section wall time, parallelRegions ON; p95 21.159 ms; 1.86x vs serial (p95 1.35x); 2 worker threads seen; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_serial",
            "value": 28.069,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions OFF; 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "p2_parallel_regions_mspt_parallel",
            "value": 17.394,
            "unit": "ms/tick",
            "extra": "vanilla getAverageTickTimeNanos, parallelRegions ON; 1.61x full-tick (Amdahl-bounded by the rest of the tick); 8 region(s), 1760 mobs, 8-8 buckets/section; 442+442 measured ticks (8 skipped/phase, 6 phases interleaved)"
          },
          {
            "name": "loadgen_fresh_chunk_load",
            "value": 7.7891,
            "unit": "ms/chunk",
            "extra": "bot walked 192 fresh chunks to FULL status, flat world"
          },
          {
            "name": "p1_end_to_end_mspt_vanilla",
            "value": 29.4927,
            "unit": "ms/tick",
            "extra": "full-tick MSPT, all P1 services off; p95 31.785 ms; 2500 countable passive mobs, 300 measured ticks"
          },
          {
            "name": "p1_end_to_end_mspt_p1_services",
            "value": 29.8477,
            "unit": "ms/tick",
            "extra": "-1.2% full-tick MSPT reduction with P1 services at shipping defaults (spawn-density AUTHORITATIVE incl. verify ticks, asyncPathfinding=true); p95 33.726 ms; 240/300 ticks served async"
          }
        ]
      }
    ]
  }
}