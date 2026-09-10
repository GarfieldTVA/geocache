package dev.garfield.cinefx.client.api;

/** Implement this when CineFX's built-in block/text primitives are not enough (beams, meshes, portals, etc.). */
@FunctionalInterface
public interface CustomWorldRenderer {
    void render(CustomRenderContext context);
}
