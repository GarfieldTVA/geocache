package dev.garfield.cinefx.client.api;

import net.minecraft.client.gui.DrawContext;

/**
 * Optional GPU/shader integration point. Return true when the backend consumed the frame.
 * CineFX's safe fallback handles tint/exposure/vignette but cannot guarantee true saturation/contrast under every shader mod.
 */
@FunctionalInterface
public interface PostFxBackend {
    boolean render(DrawContext context, GradeFrame frame);
}
