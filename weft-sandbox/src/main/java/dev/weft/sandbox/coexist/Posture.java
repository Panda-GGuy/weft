package dev.weft.sandbox.coexist;

/**
 * Default posture toward a known neighbor mod for one Weft module
 * (RFC-0003 §1 ladder, §3 seed table).
 */
public enum Posture {
    /** Rung 1: both run — they genuinely compose. */
    COOPERATE,
    /** Rung 2: the neighbor owns this territory; our module parks itself. */
    YIELD,
    /** Rung 4: true ownership conflict — loud "choose one" report. */
    REFUSE;

    /** Parse a registry value ({@code "cooperate" | "yield" | "refuse"}). */
    public static Posture parse(String value) {
        return switch (value) {
            case "cooperate" -> COOPERATE;
            case "yield" -> YIELD;
            case "refuse" -> REFUSE;
            default -> throw new IllegalArgumentException("Unknown posture: " + value);
        };
    }
}
