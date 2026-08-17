package dev.weft.neoforge.regiontick;

/**
 * Applied-check marker (RFC-0003 R2): implemented onto {@code ServerLevel} by
 * {@code ServerLevelRegionTickMixin}. The mixin lives in the fail-loud config
 * ({@code weft.mixins.json}, R2 reserves that for tick-ownership hooks), so a
 * failed application crashes at load rather than silently not owning the tick
 * — the marker is belt-and-braces for the module ledger and the parity suite.
 */
public interface RegionizedEntityTickMarker {
}
