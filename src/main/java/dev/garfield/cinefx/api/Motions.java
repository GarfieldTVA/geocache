package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/** Ready-to-use analytic motion curves. Compose them with MotionCurve#and. */
public final class Motions {
    private Motions() { }

    /** Constant acceleration: p=v*t+1/2*a*t² and the same for angular motion. */
    public static MotionCurve ballistic(Vec3d velocityPerTick, Vec3d accelerationPerTickSquared,
                                        Vec3d angularVelocityDegPerTick, Vec3d angularAccelerationDegPerTickSquared) {
        Vec3d v = velocityPerTick == null ? Vec3d.ZERO : velocityPerTick;
        Vec3d a = accelerationPerTickSquared == null ? Vec3d.ZERO : accelerationPerTickSquared;
        Vec3d w = angularVelocityDegPerTick == null ? Vec3d.ZERO : angularVelocityDegPerTick;
        Vec3d alpha = angularAccelerationDegPerTickSquared == null ? Vec3d.ZERO : angularAccelerationDegPerTickSquared;
        return (tick, seed) -> {
            double t = Math.max(0.0, tick);
            Vec3d p = v.multiply(t).add(a.multiply(0.5 * t * t));
            Vec3d r = w.multiply(t).add(alpha.multiply(0.5 * t * t));
            return new Transform(p, r, new Vec3d(1.0, 1.0, 1.0));
        };
    }

    public static MotionCurve gravity(Vec3d launchVelocity, double gravityPerTickSquared) {
        return ballistic(launchVelocity, new Vec3d(0.0, -Math.abs(gravityPerTickSquared), 0.0), Vec3d.ZERO, Vec3d.ZERO);
    }

    /** Smooth vertical levitation around the base position. */
    public static MotionCurve levitate(double amplitudeBlocks, double periodTicks, double phaseRadians) {
        if (periodTicks <= 0.0) throw new IllegalArgumentException("periodTicks must be > 0");
        return (tick, seed) -> Transform.translation(0.0,
                Math.sin((tick / periodTicks) * Math.PI * 2.0 + phaseRadians) * amplitudeBlocks,
                0.0);
    }

    /** Orbit around the local Y axis. */
    public static MotionCurve orbit(double radius, double periodTicks, double verticalOffset, boolean faceTangent) {
        if (periodTicks <= 0.0) throw new IllegalArgumentException("periodTicks must be > 0");
        return (tick, seed) -> {
            double angle = (tick / periodTicks) * Math.PI * 2.0;
            Vec3d pos = new Vec3d(Math.cos(angle) * radius, verticalOffset, Math.sin(angle) * radius);
            Vec3d rot = faceTangent ? new Vec3d(0.0, -Math.toDegrees(angle) + 90.0, 0.0) : Vec3d.ZERO;
            return new Transform(pos, rot, new Vec3d(1.0, 1.0, 1.0));
        };
    }

    public static MotionCurve spin(double xDegPerTick, double yDegPerTick, double zDegPerTick) {
        return (tick, seed) -> Transform.rotation(xDegPerTick * tick, yDegPerTick * tick, zDegPerTick * tick);
    }

    /** Deterministic, smooth-ish pseudo-random shake. Good for debris, not for camera transforms. */
    public static MotionCurve jitter(double amplitude, double frequency) {
        return (tick, seed) -> {
            double x = noise(tick * frequency + seed * 0.001, seed) * amplitude;
            double y = noise(tick * frequency + 31.7 + seed * 0.001, seed ^ 0x5DEECE66DL) * amplitude;
            double z = noise(tick * frequency + 73.2 + seed * 0.001, seed ^ 0x9E3779B97F4A7C15L) * amplitude;
            return Transform.translation(x, y, z);
        };
    }

    private static double noise(double x, long seed) {
        double a = Math.sin(x * 1.913 + seed * 0.000001) * 0.55;
        double b = Math.sin(x * 3.177 + 1.37 + seed * 0.0000007) * 0.30;
        double c = Math.sin(x * 7.113 + 5.71 + seed * 0.0000013) * 0.15;
        return a + b + c;
    }
}
