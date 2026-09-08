package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.SceneLight;
import net.minecraft.util.math.Vec3d;

/** Fully sampled light request for the current frame. */
public record LightFrame(
        long sceneInstanceId,
        String elementKey,
        SceneLight.Kind kind,
        Vec3d position,
        Vec3d direction,
        int colorArgb,
        double intensity,
        double radius,
        double innerConeDegrees,
        double outerConeDegrees
) { }
