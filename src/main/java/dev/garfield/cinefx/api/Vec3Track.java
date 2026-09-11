package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable keyframed 3D vector track. It is useful when translation, rotation,
 * scale or pivot need independent channels instead of a single TransformTrack.
 */
public final class Vec3Track {
    private final List<Keyframe<Vec3d>> keys;
    private final boolean angularDegrees;

    private Vec3Track(List<Keyframe<Vec3d>> keys, boolean angularDegrees) {
        if (keys.isEmpty()) throw new IllegalArgumentException("A Vec3 track needs at least one keyframe");
        ArrayList<Keyframe<Vec3d>> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Keyframe::tick));
        this.keys = List.copyOf(sorted);
        this.angularDegrees = angularDegrees;
    }

    @SafeVarargs
    public static Vec3Track of(Keyframe<Vec3d>... keys) {
        return new Vec3Track(Arrays.asList(keys), false);
    }

    /** Rotation track that interpolates each axis through the shortest degree arc. */
    @SafeVarargs
    public static Vec3Track angles(Keyframe<Vec3d>... keys) {
        return new Vec3Track(Arrays.asList(keys), true);
    }

    public static Vec3Track constant(Vec3d value) {
        return of(Keyframe.at(0.0, value == null ? Vec3d.ZERO : value));
    }

    public static Vec3Track constant(double x, double y, double z) {
        return constant(new Vec3d(x, y, z));
    }

    public List<Keyframe<Vec3d>> keyframes() { return keys; }
    public boolean angularDegrees() { return angularDegrees; }

    public Vec3d sample(double tick) {
        if (keys.size() == 1 || tick <= keys.getFirst().tick()) return keys.getFirst().value();
        if (tick >= keys.getLast().tick()) return keys.getLast().value();

        int low = 0;
        int high = keys.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (keys.get(mid).tick() <= tick) low = mid;
            else high = mid;
        }

        Keyframe<Vec3d> a = keys.get(low);
        Keyframe<Vec3d> b = keys.get(high);
        double span = b.tick() - a.tick();
        double raw = span <= 0.0 ? 1.0 : (tick - a.tick()) / span;
        double t = a.interpolate(raw);
        return angularDegrees ? lerpAngles(a.value(), b.value(), t) : lerp(a.value(), b.value(), t);
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    private static Vec3d lerpAngles(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.x + shortestDegrees(b.x - a.x) * t,
                a.y + shortestDegrees(b.y - a.y) * t,
                a.z + shortestDegrees(b.z - a.z) * t);
    }

    private static double shortestDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
