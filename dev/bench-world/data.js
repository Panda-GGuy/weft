window.BENCHMARK_DATA = {
  "lastUpdate": 1786948262853,
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
      }
    ]
  }
}