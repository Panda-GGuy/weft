window.BENCHMARK_DATA = {
  "lastUpdate": 1787034788329,
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
      }
    ]
  }
}