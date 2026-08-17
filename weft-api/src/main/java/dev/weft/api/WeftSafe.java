package dev.weft.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a class (or a whole package via package-info) is safe to run
 * on Weft's parallel region workers: it does not mutate shared static state,
 * does not touch world state it does not own, and confines any caches to
 * thread-local or concurrent structures.
 *
 * <p>Annotated code is promoted from the legacy lane (Tier 2) to verified
 * (Tier 1) — see RFC-0001 §7.1. This is a promise made by the mod author;
 * Weft's dev-mode race detector exists to check it before you ship it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface WeftSafe {
    /** Optional note for the compat database (e.g. what was audited). */
    String value() default "";
}
