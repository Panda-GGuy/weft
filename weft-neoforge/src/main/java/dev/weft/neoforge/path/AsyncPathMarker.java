package dev.weft.neoforge.path;

/**
 * RFC-0003 R2 runtime applied-check for the WS-2 async pathfinding mixin:
 * {@code PathNavigationAsyncMixin} implements this on {@code PathNavigation};
 * if the (fail-soft, require=0) mixin did not apply, the interface is absent
 * and the module self-disables to vanilla-synchronous pathfinding.
 */
public interface AsyncPathMarker {
}
