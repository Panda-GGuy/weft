package dev.weft.neoforge.activation;

/**
 * Applied onto {@code PathNavigation} by the WS-1 repath-throttle mixin;
 * its presence is the RFC-0003 R2 runtime applied-check (mirroring
 * {@link ActivationMarker} for the AI throttle). A fail-soft mixin that
 * silently didn't apply leaves vanilla repath cadence — this marker is how
 * anything that needs to know (CI engagement checks, /weft status) can tell.
 */
public interface RepathThrottleMarker {
}
