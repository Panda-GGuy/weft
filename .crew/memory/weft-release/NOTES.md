# weft-release memory

## Standing notes
- Root `.gitignore` intentionally covers local `gametest-*.log` and `labserver-*.log` harness artifacts; do not broaden this to source-like names or generic `*.log` without review.
- README workflow status must track `chaos.yml`, `neighbors.yml`, and `bench.yml`; neighbor matrix currently has seven cells and covers cooperate, yield, self-disable, and refuse.
- Shipping defaults verified 2026-08-19: async pathfinding and authoritative spawn density ON; activation, region/partition/parallel ticking, owner mail, entity/block-entity sharding, legacy lane, and metrics OFF.

## Open threads
- Current seven-cell neighbor matrix lacks a dated green-run claim in README; confirm through CI before claiming it proven.
- P2 remains NOT READY until parity, chaos/R7 under opt-in flags, and Create/AE2 soak exit criteria are green.
- Reconcile P2 numbering: RFC-0007 reserves increment 7 for the planned single-join tick, while `WeftConfig` and RFC-0008 label block-entity sharding increment 7. RFC-0007 also still says owner-mail increment 6 is in progress despite implemented/green gate text elsewhere.

## Lessons
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
- 2026-08-19: dated evidence must stay scoped to matrix that ran; distinguish original four-cell neighbor proof from current seven-cell workflow.
- 2026-08-19: release workflow body had lagged from P1 shadow services to authoritative P1; audit tag-release copy alongside README status.
- 2026-08-19: reverified all five workflow files parse; README claims match chaos (four torn saves), seven-cell R7 neighbors, and nightly/manual bench gates. Ignore probes cover nested harness logs without swallowing unrelated `.log` files.
