window.BENCHMARK_DATA = {
  "lastUpdate": 1787025616402,
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
        "date": 1787025615888,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2535.6483802531643,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 896.9830868868567,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 73.3135951830258,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.334326011548628,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 52.31275803568958,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 53.89906080893972,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 123.05736545277777,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 65.41988843712394,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 982.1150275374424,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.09852761427317,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.81687577847288,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13887.72405399748,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 64692.37316129032,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20629.165046938775,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 777.8293667247907,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}