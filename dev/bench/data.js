window.BENCHMARK_DATA = {
  "lastUpdate": 1786948104091,
  "repoUrl": "https://github.com/Panda-GGuy/weft",
  "entries": {
    "Weft JMH": [
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
        "date": 1786948103576,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2539.8280950061044,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 908.6254241010523,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 71.58509203030576,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.311164619600658,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 65.644148843131,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 65.35020404729394,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 151.03333520959904,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 980.6902586228628,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.0557596567124,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.93574970460821,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13043.837205381547,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 60437.67400588235,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 19751.619086274506,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 784.9230370513462,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}