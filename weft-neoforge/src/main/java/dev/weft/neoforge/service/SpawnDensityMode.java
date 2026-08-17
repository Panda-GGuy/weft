package dev.weft.neoforge.service;

/**
 * The spawn-density service's one switch (RFC-0003 R1), three positions.
 * SHADOW is the P1 proving mode (vanilla authoritative, we compute and
 * diff); AUTHORITATIVE is the graduation (we compute and vanilla uses it,
 * falling back to its own synchronous scan any tick our result isn't
 * fresh). Graduation evidence: 65k-entity stress run in shadow mode with
 * zero service failures and all parity deltas explained by the by-design
 * one-tick staleness (2026-08-16, see README Status).
 */
public enum SpawnDensityMode {
    OFF,
    SHADOW,
    AUTHORITATIVE
}
