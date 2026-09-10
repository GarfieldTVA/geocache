package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.AdvancedTransformTrack;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.ConflictPolicy;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Large deterministic set pieces. They render as one logical batch instead of thousands of entities. */
final class NarrativeSetPieces {
    private static final Identifier DEBRIS_MODEL = Identifier.of("cinefx", "model/debris_cube");

    private NarrativeSetPieces() { }

    /** A dense wall/building volume that detonates into independently ballistic chunks. */
    static ComplexElement.InstanceBatch gravityExplosion(String key, double start, double end, Vec3d center,
                                                         int count, Vec3d bounds, double blast, double gravity,
                                                         int tint, boolean staged) {
        ArrayList<ComplexElement.InstanceSpec> specs = new ArrayList<>(count);
        double durationSeconds = Math.max(0.5, (end - start) / 20.0);
        for (int i = 0; i < count; i++) {
            long h = mix(i * 0x9E3779B9L + key.hashCode());
            double rx = signed(h, 0);
            double ry = unit(h, 11);
            double rz = signed(h, 22);
            Vec3d base = new Vec3d(rx * bounds.x * 0.5, ry * bounds.y, rz * bounds.z * 0.5);
            Vec3d radial = new Vec3d(base.x / Math.max(1.0, bounds.x), 0.18 + ry * 0.45,
                    base.z / Math.max(1.0, bounds.z));
            if (radial.lengthSquared() < 1.0e-6) radial = new Vec3d(signed(h, 5), 0.4, signed(h, 17));
            radial = radial.normalize();
            double speed = blast * (0.45 + unit(h, 29) * 0.9);
            Vec3d velocity = radial.multiply(speed).add(0, 1.5 + unit(h, 37) * blast * 0.42, 0);
            Vec3d spin = new Vec3d(signed(h, 7) * 190, signed(h, 19) * 240, signed(h, 31) * 210);
            double delayTicks = staged ? unit(h, 43) * Math.min(180.0, (end - start) * 0.32) : 0.0;
            MotionCurve motion = ballistic(velocity, spin, gravity, durationSeconds, delayTicks, false);
            double s = 0.25 + unit(h, 53) * 0.72;
            specs.add(new ComplexElement.InstanceSpec(key + "#" + i, base,
                    scale(s, 0.22 + unit(h, 59) * 0.95, s), motion,
                    ColorTrack.constant(vary(tint, signed(h, 47) * 0.18)), ScalarTrack.constant(1.0), 0.0, 1.0, i & 7));
        }
        return batch(key, start, end, center, specs, Map.of(
                "fallback_shape", "box", "set_piece", "gravity_explosion", "gravity", Double.toString(gravity)));
    }

    /** Thousands of chunks spiral upward as if the environment is being ripped into a singularity. */
    static ComplexElement.InstanceBatch singularityLift(String key, double start, double end, Vec3d center,
                                                        int count, double radius, double height, int tint) {
        ArrayList<ComplexElement.InstanceSpec> specs = new ArrayList<>(count);
        double duration = Math.max(1.0, end - start);
        for (int i = 0; i < count; i++) {
            long h = mix(i * 0xD1B54A32D192ED03L + key.hashCode());
            double a = unit(h, 0) * Math.PI * 2.0;
            double r = Math.sqrt(unit(h, 13)) * radius;
            Vec3d base = new Vec3d(Math.cos(a) * r, unit(h, 24) * 1.8, Math.sin(a) * r);
            double phase = a + unit(h, 35) * 2.0;
            double turns = 1.2 + unit(h, 47) * 2.8;
            double targetRadius = 1.2 + unit(h, 56) * 4.5;
            double rise = height * (0.55 + unit(h, 7) * 0.65);
            Vec3d spin = new Vec3d(signed(h, 19) * 220, signed(h, 29) * 280, signed(h, 41) * 190);
            MotionCurve motion = (tick, seed) -> {
                double u = smooth(clamp(tick / duration));
                double currentR = r + (targetRadius - r) * u;
                double currentA = phase + turns * Math.PI * 2.0 * u;
                Vec3d target = new Vec3d(Math.cos(currentA) * currentR, base.y + rise * u,
                        Math.sin(currentA) * currentR);
                return new Transform(target.subtract(base), spin.multiply(u), new Vec3d(1, 1, 1));
            };
            double s = 0.28 + unit(h, 61) * 0.65;
            specs.add(new ComplexElement.InstanceSpec(key + "#" + i, base,
                    scale(s, s * (0.6 + unit(h, 17) * 1.2), s), motion,
                    ColorTrack.constant(vary(tint, signed(h, 52) * 0.22)), ScalarTrack.constant(1.0), 0, 1, i & 7));
        }
        return batch(key, start, end, center, specs, Map.of(
                "fallback_shape", "box", "set_piece", "singularity_lift", "terrain_snap", "true"));
    }

    /** A block mass is thrown as one object; useful for a Titan ripping a structure out of the ground. */
    static ComplexElement.InstanceBatch rigidStructure(String key, double start, double end, String parent,
                                                       Vec3d center, int count, Vec3d bounds, int tint) {
        ArrayList<ComplexElement.InstanceSpec> specs = new ArrayList<>(count);
        int sideX = Math.max(2, (int)Math.ceil(Math.cbrt(count) * (bounds.x / Math.max(1.0, bounds.y))));
        int sideZ = Math.max(2, (int)Math.ceil(Math.cbrt(count) * (bounds.z / Math.max(1.0, bounds.y))));
        int layerArea = sideX * sideZ;
        for (int i = 0; i < count; i++) {
            int y = i / layerArea;
            int rem = i % layerArea;
            int x = rem % sideX;
            int z = rem / sideX;
            Vec3d base = new Vec3d((x / (double)Math.max(1, sideX - 1) - 0.5) * bounds.x,
                    y * 0.72, (z / (double)Math.max(1, sideZ - 1) - 0.5) * bounds.z);
            long h = mix(i * 31L + key.hashCode());
            double s = 0.52 + unit(h, 17) * 0.26;
            specs.add(new ComplexElement.InstanceSpec(key + "#" + i, base, scale(s, s, s), MotionCurve.none(),
                    ColorTrack.constant(vary(tint, signed(h, 33) * 0.12)), ScalarTrack.constant(1.0), 0, 1, i & 3));
        }
        return new ComplexElement.InstanceBatch(key, start, end, 118, ConflictPolicy.ALLOW, parent,
                DEBRIS_MODEL, null, center, AdvancedTransformTrack.identity(), MotionCurve.none(), List.copyOf(specs),
                true, 1200.0, 0, Map.of("fallback_shape", "box", "set_piece", "rigid_structure"));
    }

    /** Exact reverse of a ballistic explosion: starts scattered and reconstructs into the source volume. */
    static ComplexElement.InstanceBatch rewindExplosion(String key, double start, double end, Vec3d center,
                                                        int count, Vec3d bounds, double blast, double gravity, int tint) {
        ArrayList<ComplexElement.InstanceSpec> specs = new ArrayList<>(count);
        double durationSeconds = Math.max(0.5, (end - start) / 20.0);
        for (int i = 0; i < count; i++) {
            long h = mix(i * 0x94D049BB133111EBL + key.hashCode());
            Vec3d base = new Vec3d(signed(h, 0) * bounds.x * 0.5,
                    unit(h, 12) * bounds.y, signed(h, 25) * bounds.z * 0.5);
            Vec3d radial = new Vec3d(signed(h, 6), 0.25 + unit(h, 19) * 0.7, signed(h, 33)).normalize();
            Vec3d velocity = radial.multiply(blast * (0.55 + unit(h, 45) * 0.85));
            Vec3d spin = new Vec3d(signed(h, 9) * 260, signed(h, 29) * 300, signed(h, 51) * 240);
            MotionCurve motion = ballistic(velocity, spin, gravity, durationSeconds, 0.0, true);
            double s = 0.3 + unit(h, 58) * 0.7;
            specs.add(new ComplexElement.InstanceSpec(key + "#" + i, base, scale(s, s, s), motion,
                    ColorTrack.constant(vary(tint, signed(h, 38) * 0.2)), ScalarTrack.constant(1.0), 0, 1, i & 7));
        }
        return batch(key, start, end, center, specs, Map.of(
                "fallback_shape", "box", "set_piece", "rewind_explosion", "reverse", "true"));
    }

    /** Suspended debris field that orbits a point without sharing the same motion as the singularity lift. */
    static ComplexElement.InstanceBatch orbitalDebris(String key, double start, double end, Vec3d center,
                                                      int count, double radius, int tint) {
        ArrayList<ComplexElement.InstanceSpec> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long h = mix(i * 0x2545F4914F6CDD1DL + key.hashCode());
            double a = unit(h, 2) * Math.PI * 2;
            double r = radius * (0.35 + unit(h, 14) * 0.65);
            Vec3d base = new Vec3d(Math.cos(a) * r, signed(h, 26) * radius * 0.45, Math.sin(a) * r);
            double speed = 0.008 + unit(h, 39) * 0.022;
            MotionCurve motion = (tick, seed) -> {
                double angle = a + tick * speed;
                Vec3d p = new Vec3d(Math.cos(angle) * r, base.y + Math.sin(tick * speed * 1.7 + a) * 2.0,
                        Math.sin(angle) * r);
                return new Transform(p.subtract(base), new Vec3d(tick * 0.45, tick * 0.7, tick * 0.33), new Vec3d(1, 1, 1));
            };
            double s = 0.2 + unit(h, 51) * 0.65;
            specs.add(new ComplexElement.InstanceSpec(key + "#" + i, base, scale(s, s, s), motion,
                    ColorTrack.constant(vary(tint, signed(h, 44) * 0.18)), ScalarTrack.constant(1.0), 0, 1, i & 7));
        }
        return batch(key, start, end, center, specs, Map.of("fallback_shape", "box", "set_piece", "orbital_debris"));
    }

    private static ComplexElement.InstanceBatch batch(String key, double start, double end, Vec3d center,
                                                      List<ComplexElement.InstanceSpec> specs, Map<String, String> parameters) {
        return new ComplexElement.InstanceBatch(key, start, end, 120, ConflictPolicy.ALLOW, null,
                DEBRIS_MODEL, null, center, AdvancedTransformTrack.identity(), MotionCurve.none(), specs,
                true, 1400.0, 0, parameters);
    }

    private static MotionCurve ballistic(Vec3d velocity, Vec3d spinDegreesPerSecond, double gravity,
                                         double durationSeconds, double delayTicks, boolean reverse) {
        return (localTick, sceneSeed) -> {
            double raw = Math.max(0.0, localTick - delayTicks) / 20.0;
            double t = Math.min(durationSeconds, raw);
            if (reverse) t = durationSeconds - t;
            Vec3d p = velocity.multiply(t).add(0, -0.5 * gravity * t * t, 0);
            Vec3d rotation = spinDegreesPerSecond.multiply(t);
            return new Transform(p, rotation, new Vec3d(1, 1, 1));
        };
    }

    private static AdvancedTransformTrack scale(double x, double y, double z) {
        return new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO), Vec3Track.constant(Vec3d.ZERO),
                Vec3Track.constant(new Vec3d(x, y, z)), Vec3Track.constant(Vec3d.ZERO));
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double smooth(double u) { return u * u * (3.0 - 2.0 * u); }

    private static long mix(long z) {
        z ^= z >>> 33;
        z *= 0xff51afd7ed558ccdl;
        z ^= z >>> 33;
        z *= 0xc4ceb9fe1a85ec53l;
        z ^= z >>> 33;
        return z;
    }

    private static double unit(long h, int shift) {
        long v = Long.rotateLeft(h, shift) >>> 11;
        return (v & ((1L << 53) - 1)) / (double)(1L << 53);
    }

    private static double signed(long h, int shift) { return unit(h, shift) * 2.0 - 1.0; }

    private static int vary(int argb, double amount) {
        int a = (argb >>> 24) & 255;
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        double mul = Math.max(0.45, 1.0 + amount);
        r = (int)Math.max(0, Math.min(255, Math.round(r * mul)));
        g = (int)Math.max(0, Math.min(255, Math.round(g * mul)));
        b = (int)Math.max(0, Math.min(255, Math.round(b * mul)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
