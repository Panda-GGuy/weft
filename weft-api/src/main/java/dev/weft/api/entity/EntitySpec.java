package dev.weft.api.entity;

/**
 * A pending entity spawn recorded in an {@link EntityEffectLog} (RFC-0004
 * §2.3). Handles are loader-defined, registry-stable longs, same convention
 * as the graph layer's block/item handles.
 */
public record EntitySpec(long typeHandle, double x, double y, double z) {
}
