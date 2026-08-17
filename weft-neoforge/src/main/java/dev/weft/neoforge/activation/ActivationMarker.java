package dev.weft.neoforge.activation;

/**
 * Applied to {@link net.minecraft.world.entity.Mob} by the WS-1 throttle
 * mixin. Its presence on the class is the runtime "did my hook actually
 * apply?" check (RFC-0003 R2): the optimization mixin config is fail-soft
 * ({@code required: false}, {@code defaultRequire: 0}), so if foreign
 * patches kept it from applying, the interface is missing and the module
 * self-disables (ladder rung 3) instead of half-running.
 */
public interface ActivationMarker {
}
