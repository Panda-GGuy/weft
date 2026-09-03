window.BENCHMARK_DATA = {
  "lastUpdate": 1788423165634,
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
        "date": 1787034596251,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 1934.4110158486442,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 667.6319469155908,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 71.5506345307156,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 7.121497149687002,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 50.58456263459259,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 48.6135626368732,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 91.02215248636364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 55.97189391941841,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 599.0574238319336,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 157.85747774636624,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 15.73013277694443,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 9880.862451590874,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 46076.92183216706,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 14455.088753101867,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 605.9981917162038,
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
            "name": "Panda-GGuy",
            "username": "Panda-GGuy",
            "email": "218838703+Panda-GGuy@users.noreply.github.com"
          },
          "id": "94e5ed9dcd4b6c1f3618799d1abf50a183e2265f",
          "message": "docs: remap cited commit SHAs after the history rewrite\n\nThe identity rewrite changed every commit SHA, so the three SHAs cited in\nthe docs pointed at commits that no longer exist. Remapped via\nfilter-repo's commit-map:\n\n  8973061 -> cf9bb78  (RFC-0007:42,  merge of PR #1)\n  c2fd0df -> edfff01  (RFC-0007:123, pregen churn fix)\n  1f217cd -> cd39aed  (RESEARCH-0003:387, Moonrise neighbor)\n\nCo-Authored-By: Claude Opus 5 <noreply@anthropic.com>",
          "timestamp": "2026-08-18T06:07:25Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/94e5ed9dcd4b6c1f3618799d1abf50a183e2265f"
        },
        "date": 1787112381993,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2542.2683840555046,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 924.3275251186193,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 80.27849773591866,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.074327438179791,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 59.64691617286486,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 59.16825693709738,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 124.63184139444445,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 70.10090473501586,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 983.8279478354414,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.8674541550907,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 24.91486297361478,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13237.199210657025,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 64450.91754737904,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20962.213291666667,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 777.1099359451157,
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
          "id": "d269116ecd50e9faedd50c226da71272b30c8340",
          "message": "Merge pull request #12 from Panda-GGuy/crew/engine-inc7\n\nfeat(p2): RFC-0007 inc7 scaffold - singleJoinTick + fused task API",
          "timestamp": "2026-08-20T02:54:12Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/d269116ecd50e9faedd50c226da71272b30c8340"
        },
        "date": 1787198772499,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2204.1282095352244,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 793.6286409854285,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 62.550616514705744,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 3.370211662095619,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 40.48036007067368,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 40.6860359552396,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 103.22266305999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 57.56375681212751,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 859.7354642104256,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 236.51418262020988,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 18.387834840602654,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 10077.616869346733,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 54245.173567567566,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 15323.586503566432,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 626.1447402697188,
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
          "id": "163c85ca6ed1f018ede6ffe0f3042736d76b1d38",
          "message": "Merge pull request #26 from Panda-GGuy/crew/lead-state-2026-08-20\n\ndocs(crew): reconcile state after #23/#24/#15",
          "timestamp": "2026-08-21T03:53:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/163c85ca6ed1f018ede6ffe0f3042736d76b1d38"
        },
        "date": 1787285315277,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2543.1958308618055,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 911.3834337229334,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 73.38962782163713,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.22826324756082,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 52.427616517952174,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 53.676604211812524,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 123.00422822222222,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 67.86728322771566,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 981.2399297883909,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.6867416530004,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.97368879433712,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 14736.489018426097,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 64151.453118749996,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 21114.82015002193,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 780.3741791318693,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787371413378,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2537.3557506784286,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 901.165369357855,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 75.18167819613306,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.233111732322792,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 59.48525960305076,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 59.11401968812321,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 124.55980343055555,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 68.48366955640968,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 981.6399737734382,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.4006018894634,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.721534947267024,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13297.276751598372,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 65137.87583225806,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 21124.761183070175,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 786.8611453590693,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787458097241,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2531.422345376123,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 896.9248489155555,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 73.25013775899973,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.188876433872764,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 52.44871867742986,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 51.44317463459496,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 123.7128989111111,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 67.49517257799144,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 980.8845728974408,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 270.9370881222385,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.956462374956573,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13386.974145324382,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 59829.73212352942,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20861.4769650988,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 784.2945347270298,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787544814374,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2209.2664625106336,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 807.8949214128166,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 64.58169189055607,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 3.551422228428018,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 42.339302126056545,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 41.98124295063385,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 104.93466847999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 56.43004176940889,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 859.8659727292709,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 236.59637482331772,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 18.70161117372773,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 11433.54404533291,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 50189.175325000004,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 16979.57524036316,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 630.6902035681682,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787630844975,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2865.1678841508756,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 1071.2893585450352,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 89.96226497553694,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.393519576266401,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 60.70456586679147,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 59.58022756560022,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 133.08900372500003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 69.86344456235335,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 1109.580589922141,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 305.03985769484194,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 24.218271623531553,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 11983.63346979346,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 69713.46991724138,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20442.046641934176,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 804.8549775170934,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787717400184,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2215.4013636451214,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 797.892966264351,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 63.13358455103993,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 3.439300474673014,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 41.252806267306006,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 41.248531309369696,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 103.61819957999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 52.94088479952118,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 860.0614793556322,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 237.03723696142,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 18.89978104396075,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 9090.027747567165,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 50355.06783500001,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 16670.5340112758,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 672.2839547902264,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787840574058,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2353.6073808059873,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 812.8494484245401,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 75.7914321807228,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 9.366925169017016,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 63.303340808928795,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 52.986340102673125,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 110.60126313777778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 64.8826422439963,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 738.2009608646057,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 190.1443459087766,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 20.199764153064308,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 10943.227677018993,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 56232.88413888889,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 16768.664946467117,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 743.7474654209411,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787931147501,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2337.0990446676437,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 813.2611716842221,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 71.87086839552116,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 9.320718578941177,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 40.38205475351269,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 47.26974936114524,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 128.6278091,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 81.25855238660802,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 795.5098895634268,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 220.39651372797886,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 24.033146394034233,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 11894.70801084147,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 58058.22184571428,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20862.562830176117,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 841.6146039098006,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1787998597864,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2542.948282065233,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 906.5733750374745,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 76.07502405790808,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.348637106922562,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 60.213023117240866,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 59.37390026766476,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 122.9971923777778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 68.26971363623618,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 980.7586043084018,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.3690095451688,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.780342198223686,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13529.391704,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 65212.275587096774,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20566.169053902584,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 791.8818916787443,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788081807679,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2556.0862307980115,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 897.514656821964,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 77.57575018375407,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.226317130012982,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 60.51258667797307,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 61.42722307381821,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 123.91387890555556,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 68.95643317777308,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 982.1514807164294,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.6318128663098,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.836834213895028,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13702.365235770378,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 60584.35813654189,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 21440.911206657514,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 783.8513495290201,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788170213504,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2342.483917354384,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 810.6979845857243,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 79.32755413552425,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 9.677907428690615,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 51.06642826084574,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 49.20304549447023,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 127.83780147499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 86.98952177565525,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 793.8539257260403,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 221.20896439869398,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 24.721763339382864,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 12378.622734314853,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 63754.20381875,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 19517.784879611652,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 862.026374591821,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788252538033,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2543.785533582705,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 899.2310896692776,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 77.16378031819924,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.207033791099399,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 60.93686222731425,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 61.02976581813848,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 122.04422982222222,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 74.45496001197901,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 982.1775979283193,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.4124216652682,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 23.733165809197537,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13077.258480615918,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 64575.43174637096,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 19962.266752158415,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 772.23474023327,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788336167997,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2208.7319604818344,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 794.9671696285152,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 63.90705007448846,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 3.378581425999554,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 42.37108682726324,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 42.69995129053426,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 109.07050178,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 52.98941941898248,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 859.7762929871867,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 236.5258151062967,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 18.95639991648078,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 9964.621957450372,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 53889.823978947374,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 15533.315145187837,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 630.9101766235848,
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
          "id": "7270a365a10139456987b7158c500f0a974fee7e",
          "message": "Merge pull request #28 from Panda-GGuy/crew/lead-state-h24\n\ndocs(crew): record the abandoned-gate and rebase-conflict lessons",
          "timestamp": "2026-08-21T06:24:15Z",
          "url": "https://github.com/Panda-GGuy/weft/commit/7270a365a10139456987b7158c500f0a974fee7e"
        },
        "date": 1788423164803,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.serialOneRegion",
            "value": 2548.025509898467,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.EntityShardingBench.shardedOneRegion",
            "value": 922.9194567597763,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.GraphCommitBench.computeAndRoute",
            "value": 77.13462777371325,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.MailboxBench.post256Drain",
            "value": 4.267875121615492,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.emptyRegions",
            "value": 60.78912272520115,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.PipelineTickBench.tinyWork",
            "value": 58.85754970511393,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionChurnStormBench.pregenChurnStorm",
            "value": 142.91119826071426,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.engine.jmh.RegionMergeSplitBench.mergeChainThenSplit",
            "value": 68.91545890390574,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"false\"} )",
            "value": 982.0834963416182,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationPhaseBench.entityPhase ( {\"throttled\":\"true\"} )",
            "value": 271.2423449305945,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.ActivationSchedulerBench.decisionNearMidFar",
            "value": 24.51594681545328,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordeFlowField",
            "value": 13071.843394643918,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.hordePerMobAStar",
            "value": 60120.6290117647,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathFlatGrid",
            "value": 20024.09731491089,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.weft.services.jmh.PathfindingBench.longPathHierarchical",
            "value": 782.2778928814525,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}