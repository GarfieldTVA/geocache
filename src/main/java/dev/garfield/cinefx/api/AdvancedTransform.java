package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/**
 * Transform with an explicit animated pivot. Scale is fully non-uniform and may
 * be negative for mirroring. Rotation is expressed in degrees X/Y/Z.
 */
public record AdvancedTransform(Vec3d translation, Vec3d rotationDegrees, Vec3d scale, Vec3d pivot) {
    public static final AdvancedTransform IDENTITY = new AdvancedTransform(
            Vec3d.ZERO, Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0), Vec3d.ZERO);

    public AdvancedTransform {
        translation = translation == null ? Vec3d.ZERO : translation;
        rotationDegrees = rotationDegrees == null ? Vec3d.ZERO : rotationDegrees;
        scale = scale == null ? new Vec3d(1.0, 1.0, 1.0) : scale;
        pivot = pivot == null ? Vec3d.ZERO : pivot;
    }

    public Transform basic() {
        return new Transform(translation, rotationDegrees, scale);
    }
}
