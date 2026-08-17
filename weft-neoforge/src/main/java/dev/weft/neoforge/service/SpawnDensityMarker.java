package dev.weft.neoforge.service;

/**
 * RFC-0003 R2 applied-check marker: implanted onto {@code ServerChunkCache}
 * by the fail-soft spawn-state mixin. If the mixin did not apply, the class
 * does not carry this interface and the spawn-density module refuses to run
 * AUTHORITATIVE (vanilla's synchronous scan keeps running untouched).
 */
public interface SpawnDensityMarker {
}
