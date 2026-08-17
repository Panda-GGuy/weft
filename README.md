# bench-data — Weft's benchmark history branch

**This branch is a data store, not code. Do not merge it into `main`.**

If GitHub is showing you a "Compare & pull request" banner for this branch,
ignore it — the banner appears automatically after every benchmark run
pushes here, and merging would dump machine-written JSON/HTML into the
source tree. This branch intentionally shares no history with `main`
(it was seeded as an orphan commit).

## What lives here

Results appended by
[github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark)
every time `.github/workflows/bench.yml` runs (nightly at 03:17 UTC, plus
manual dispatches):

| Path | Written by | Contents |
|---|---|---|
| `dev/bench/` | the **jmh** job | Engine hot-path microbenchmarks (mailboxes, region merge/split, pipeline tick, graph commit, WS-1 decision + phase model, WS-2 pathfinding, WS-10 sharding) |
| `dev/bench-world/` | the **world-bench** job | The WS-8 benchmark world: headless GameTest load generator, 2k passive + 500 hostile mobs — entity-phase ms/tick with WS-1 off/on, fresh-chunk load ms/chunk |

Each directory holds a `data.js` time series (one entry per run, keyed by
commit) and an `index.html` you can open for interactive charts of every
benchmark's history.

## How it's used

`bench.yml` checks this branch out on every run, compares the fresh numbers
against the last recorded entry, appends the new results, and pushes back
here. A job **fails when any benchmark regresses beyond 150%** of its
previous value — a threshold chosen to catch real regressions (an
accidental O(n^2), an allocation storm) while tolerating shared-runner
noise. The failure shows up on the workflow run; nothing on `main` changes.

Deleting this branch loses the regression baseline and history; the next
run would fail until the branch is re-seeded (an empty orphan commit is
enough — see references).

## References

- Workflow: [`.github/workflows/bench.yml`](https://github.com/Panda-GGuy/weft/blob/main/.github/workflows/bench.yml) on `main`
- Design: RFC-0002 WS-8 "Benchmark-as-CI" ([`docs/RFC-0002-modernization-workstreams.md`](https://github.com/Panda-GGuy/weft/blob/main/docs/RFC-0002-modernization-workstreams.md)) — principle #4: "benchmarked or it didn't happen"
- Action docs: <https://github.com/benchmark-action/github-action-benchmark> (`gh-pages-branch: bench-data`, `fail-on-alert`, `alert-threshold: 150%`)
- Charts: open `dev/bench/index.html` / `dev/bench-world/index.html` from this branch
