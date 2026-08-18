package dev.weft.neoforge.legacy;

/**
 * Applied-check marker (RFC-0003 R2): {@code LevelBlockEntityUnitMixin}
 * adds this to {@code Level}, so {@code LegacyRouting.hooksApplied()} can
 * verify the per-BE-unit seam actually exists at runtime — belt-and-braces
 * on top of the fail-loud mixin config.
 */
public interface LegacyLaneBlockEntityMarker {
}
