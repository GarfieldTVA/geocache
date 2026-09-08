package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/**
 * A dynamic visual light request. CineFX itself does not rewrite vanilla block-light values;
 * client lighting integrations consume this through LightingBackend so no chunk/light-engine rebuild is required.
 */
public record SceneLight(
        String key,
        double startTick,
        double endTick,
        int priority,
        ConflictPolicy conflictPolicy,
        Kind kind,
        Vec3d baseOffset,
        TransformTrack transform,
        MotionCurve motion,
        ColorTrack color,
        ScalarTrack intensity,
        ScalarTrack radius,
        Vec3d direction,
        double innerConeDegrees,
        double outerConeDegrees
) implements SceneElement {
    public enum Kind { POINT, SPOT }

    public SceneLight {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("light key is required");
        if (!Double.isFinite(startTick) || Double.isNaN(endTick) || endTick < startTick) {
            throw new IllegalArgumentException("invalid light time range");
        }
        conflictPolicy = conflictPolicy == null ? ConflictPolicy.ALLOW : conflictPolicy;
        kind = kind == null ? Kind.POINT : kind;
        baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
        transform = transform == null ? TransformTrack.identity() : transform;
        motion = motion == null ? MotionCurve.none() : motion;
        color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        intensity = intensity == null ? ScalarTrack.constant(1.0) : intensity;
        radius = radius == null ? ScalarTrack.constant(8.0) : radius;
        direction = direction == null ? new Vec3d(0.0, -1.0, 0.0) : direction;
        if (direction.lengthSquared() < 1.0e-12) direction = new Vec3d(0.0, -1.0, 0.0);
        direction = direction.normalize();
        if (!Double.isFinite(innerConeDegrees) || !Double.isFinite(outerConeDegrees)
                || innerConeDegrees < 0.0 || outerConeDegrees < innerConeDegrees || outerConeDegrees > 180.0) {
            throw new IllegalArgumentException("spot cone must satisfy 0 <= inner <= outer <= 180");
        }
    }
}
