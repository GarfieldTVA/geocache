package dev.garfield.cinefx.client.api;

import java.util.List;

/**
 * Bridge for shader/dynamic-light mods. Return true when this backend consumed the frame.
 * Backends receive all CineFX lights together so they can upload/batch them in one GPU operation.
 */
@FunctionalInterface
public interface LightingBackend {
    boolean apply(List<LightFrame> lights);
}
