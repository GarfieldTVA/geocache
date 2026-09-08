package dev.garfield.cinefx.api;

/**
 * Resolves two effects that claim the same visual resource (screen channel or occupied block cell).
 * Rendering is processed from highest priority to lowest priority.
 */
public enum ConflictPolicy {
    /** Does not reserve the resource. Several effects may render together. */
    ALLOW,
    /** Skip this effect when the resource is already reserved. */
    DENY,
    /** Higher priority reservation wins; lower priority effects are skipped. */
    REPLACE_LOWER,
    /** Render even when another effect already owns the resource. Use deliberately. */
    FORCE
}
