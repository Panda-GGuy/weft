# weft-compat memory

## Lessons (2026-08-20, issue #16)

- A yield posture is only real if it is **transitive**. Check the assignment
  chain, not the top-level flag: `parallel = partitioned && PARALLEL_REGIONS`
  and `partitioned = active && PARTITIONED_TICKING`, so parking
  `regionized_ticking` is what actually disarms worker fan-out. One assignment
  site made this provable; grep for the site rather than trusting the config UI.
- A posture-table line cannot distinguish a **parked** module from a
  **relabelled** one. R7 cells must assert a consequence (no fan-out) and not
  just a label (`YIELDED`), and the cell should ask for the dangerous config on
  purpose so the disarm is what passes the test.
- Always pair an R7 cell with a **negative control** boot: same config, neighbor
  removed. Without it the cell may be passing because the feature never armed.
- Yielded modules print no `extraDetail`, so anything an operator needs to know
  about a yielded module has to be emitted separately or it is invisible.
- Do not over-yield by association. Yield only the modules on the observed
  failure path, and pin the untouched ones with an explicit "posture unset"
  test so a future safety-reflex edit has to argue with a test.
- RFC-0003 §3.1's withheld postures ("candidate" hazards) are worth revisiting
  the moment field evidence lands — absence of evidence was the only reason.

## Standing notes
- (none yet)

## Open threads
- (none yet)

## Lessons
- 2026-08-19: crew scaffold created; prefer durable notes here over chat history.
