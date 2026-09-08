package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/** Convenience factories for dynamic light requests. */
public final class SceneLights {
    private static final double FOREVER = Double.POSITIVE_INFINITY;

    private SceneLights() { }

    public static SceneLight point(String key, Vec3d offset, int argb, double intensity, double radius) {
        return new SceneLight(key, 0.0, FOREVER, 0, ConflictPolicy.ALLOW, SceneLight.Kind.POINT,
                offset, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(argb),
                ScalarTrack.constant(intensity), ScalarTrack.constant(radius),
                new Vec3d(0.0, -1.0, 0.0), 0.0, 180.0);
    }

    public static SceneLight spot(String key, Vec3d offset, Vec3d direction, int argb,
                                  double intensity, double radius, double innerConeDegrees, double outerConeDegrees) {
        return new SceneLight(key, 0.0, FOREVER, 0, ConflictPolicy.ALLOW, SceneLight.Kind.SPOT,
                offset, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(argb),
                ScalarTrack.constant(intensity), ScalarTrack.constant(radius), direction,
                innerConeDegrees, outerConeDegrees);
    }
}
