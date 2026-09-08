package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/** Anchor-local transform. Rotation is expressed in degrees around X/Y/Z. */
public record Transform(Vec3d translation, Vec3d rotationDegrees, Vec3d scale) {
    public static final Transform IDENTITY = new Transform(Vec3d.ZERO, Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0));

    public Transform {
        translation = translation == null ? Vec3d.ZERO : translation;
        rotationDegrees = rotationDegrees == null ? Vec3d.ZERO : rotationDegrees;
        scale = scale == null ? new Vec3d(1.0, 1.0, 1.0) : scale;
    }

    public static Transform translation(double x, double y, double z) {
        return new Transform(new Vec3d(x, y, z), Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0));
    }

    public static Transform rotation(double xDeg, double yDeg, double zDeg) {
        return new Transform(Vec3d.ZERO, new Vec3d(xDeg, yDeg, zDeg), new Vec3d(1.0, 1.0, 1.0));
    }

    public static Transform scale(double uniform) {
        return scale(uniform, uniform, uniform);
    }

    /** Independent X/Y/Z scaling; negative components mirror that axis. */
    public static Transform scale(double x, double y, double z) {
        return new Transform(Vec3d.ZERO, Vec3d.ZERO, new Vec3d(x, y, z));
    }

    public Transform combine(Transform other) {
        return new Transform(
                translation.add(other.translation),
                rotationDegrees.add(other.rotationDegrees),
                new Vec3d(scale.x * other.scale.x, scale.y * other.scale.y, scale.z * other.scale.z));
    }

    public static Transform lerp(Transform a, Transform b, double t) {
        return new Transform(
                lerp(a.translation, b.translation, t),
                lerp(a.rotationDegrees, b.rotationDegrees, t),
                lerp(a.scale, b.scale, t));
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }
}
