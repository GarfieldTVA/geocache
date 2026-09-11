package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic cinematic spline path. The path is sampled analytically from scene time;
 * no per-object integration or physics tick is required.
 */
public final class PathTrack {
    public enum Interpolation { LINEAR, CATMULL_ROM, BEZIER }

    public record Point(
            double tick,
            Vec3d position,
            Vec3d inHandle,
            Vec3d outHandle,
            Easing easingToNext,
            CubicBezier bezierToNext
    ) {
        public Point {
            if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite");
            position = position == null ? Vec3d.ZERO : position;
            easingToNext = easingToNext == null ? Easing.LINEAR : easingToNext;
        }

        public Point(double tick, Vec3d position, Vec3d inHandle, Vec3d outHandle, Easing easingToNext) {
            this(tick, position, inHandle, outHandle, easingToNext, null);
        }

        public static Point at(double tick, Vec3d position) {
            return new Point(tick, position, null, null, Easing.LINEAR, null);
        }

        public static Point at(double tick, Vec3d position, Easing easing) {
            return new Point(tick, position, null, null, easing, null);
        }

        public static Point bezier(double tick, Vec3d position, Vec3d inHandle, Vec3d outHandle, Easing easing) {
            return new Point(tick, position, inHandle, outHandle, easing, null);
        }

        public static Point bezierTiming(double tick, Vec3d position, Vec3d inHandle, Vec3d outHandle, CubicBezier curve) {
            return new Point(tick, position, inHandle, outHandle, Easing.LINEAR, curve);
        }

        public double interpolate(double rawFraction) {
            return bezierToNext == null ? easingToNext.apply(rawFraction) : bezierToNext.apply(rawFraction);
        }
    }

    public record Sample(Vec3d position, Vec3d tangent) { }

    private final List<Point> points;
    private final Interpolation interpolation;

    public PathTrack(List<Point> points, Interpolation interpolation) {
        if (points == null || points.size() < 2) throw new IllegalArgumentException("A path needs at least two points");
        ArrayList<Point> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(Point::tick));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).tick() <= sorted.get(i - 1).tick()) throw new IllegalArgumentException("Path ticks must be strictly increasing");
        }
        this.points = List.copyOf(sorted);
        this.interpolation = interpolation == null ? Interpolation.CATMULL_ROM : interpolation;
    }

    public static PathTrack catmullRom(Point... points) { return new PathTrack(List.of(points), Interpolation.CATMULL_ROM); }
    public static PathTrack bezier(Point... points) { return new PathTrack(List.of(points), Interpolation.BEZIER); }
    public static PathTrack linear(Point... points) { return new PathTrack(List.of(points), Interpolation.LINEAR); }

    public List<Point> points() { return points; }
    public Interpolation interpolation() { return interpolation; }

    public Sample sample(double tick) {
        if (tick <= points.getFirst().tick()) {
            Vec3d tangent = points.get(1).position().subtract(points.getFirst().position()).normalize();
            return new Sample(points.getFirst().position(), safeTangent(tangent));
        }
        if (tick >= points.getLast().tick()) {
            Vec3d tangent = points.getLast().position().subtract(points.get(points.size() - 2).position()).normalize();
            return new Sample(points.getLast().position(), safeTangent(tangent));
        }

        int segment = segmentFor(tick);
        Point a = points.get(segment), b = points.get(segment + 1);
        double raw = (tick - a.tick()) / (b.tick() - a.tick());
        double t = a.interpolate(raw);

        Vec3d position;
        Vec3d tangent;
        switch (interpolation) {
            case LINEAR -> {
                position = lerp(a.position(), b.position(), t);
                tangent = b.position().subtract(a.position());
            }
            case BEZIER -> {
                Vec3d c1 = a.outHandle() == null ? lerp(a.position(), b.position(), 1.0 / 3.0) : a.position().add(a.outHandle());
                Vec3d c2 = b.inHandle() == null ? lerp(a.position(), b.position(), 2.0 / 3.0) : b.position().add(b.inHandle());
                position = bezier(a.position(), c1, c2, b.position(), t);
                tangent = bezierDerivative(a.position(), c1, c2, b.position(), t);
            }
            case CATMULL_ROM -> {
                Vec3d p0 = points.get(Math.max(0, segment - 1)).position();
                Vec3d p1 = a.position();
                Vec3d p2 = b.position();
                Vec3d p3 = points.get(Math.min(points.size() - 1, segment + 2)).position();
                position = catmull(p0, p1, p2, p3, t);
                tangent = catmullDerivative(p0, p1, p2, p3, t);
            }
            default -> throw new IllegalStateException("Unexpected interpolation: " + interpolation);
        }
        return new Sample(position, safeTangent(tangent));
    }

    public MotionCurve asMotionCurve(boolean orientToPath, double bankDegrees) {
        return (tick, seed) -> {
            Sample sample = sample(tick);
            Vec3d rotation = Vec3d.ZERO;
            if (orientToPath) {
                Vec3d tangent = sample.tangent();
                double horizontal = Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z);
                double yaw = Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
                double pitch = Math.toDegrees(-Math.atan2(tangent.y, horizontal));
                rotation = new Vec3d(pitch, yaw, bankDegrees);
            }
            return new Transform(sample.position(), rotation, new Vec3d(1.0, 1.0, 1.0));
        };
    }

    private int segmentFor(double tick) {
        int low = 0, high = points.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (points.get(mid).tick() <= tick) low = mid; else high = mid;
        }
        return low;
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3d bezier(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double u = 1.0 - t;
        return p0.multiply(u * u * u).add(p1.multiply(3.0 * u * u * t)).add(p2.multiply(3.0 * u * t * t)).add(p3.multiply(t * t * t));
    }

    private static Vec3d bezierDerivative(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double u = 1.0 - t;
        return p1.subtract(p0).multiply(3.0 * u * u).add(p2.subtract(p1).multiply(6.0 * u * t)).add(p3.subtract(p2).multiply(3.0 * t * t));
    }

    private static Vec3d catmull(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new Vec3d(
                0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t + (2*p0.x - 5*p1.x + 4*p2.x - p3.x) * t2 + (-p0.x + 3*p1.x - 3*p2.x + p3.x) * t3),
                0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t + (2*p0.y - 5*p1.y + 4*p2.y - p3.y) * t2 + (-p0.y + 3*p1.y - 3*p2.y + p3.y) * t3),
                0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t + (2*p0.z - 5*p1.z + 4*p2.z - p3.z) * t2 + (-p0.z + 3*p1.z - 3*p2.z + p3.z) * t3));
    }

    private static Vec3d catmullDerivative(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        return new Vec3d(
                0.5 * ((-p0.x + p2.x) + 2.0 * (2*p0.x - 5*p1.x + 4*p2.x - p3.x) * t + 3.0 * (-p0.x + 3*p1.x - 3*p2.x + p3.x) * t2),
                0.5 * ((-p0.y + p2.y) + 2.0 * (2*p0.y - 5*p1.y + 4*p2.y - p3.y) * t + 3.0 * (-p0.y + 3*p1.y - 3*p2.y + p3.y) * t2),
                0.5 * ((-p0.z + p2.z) + 2.0 * (2*p0.z - 5*p1.z + 4*p2.z - p3.z) * t + 3.0 * (-p0.z + 3*p1.z - 3*p2.z + p3.z) * t2));
    }

    private static Vec3d safeTangent(Vec3d tangent) {
        return tangent.lengthSquared() < 1.0e-10 ? new Vec3d(0.0, 0.0, 1.0) : tangent.normalize();
    }
}
