package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * High-level building blocks for Fortnite-style live events.
 * Every preset expands to normal SceneElement data, so mods can copy it and tweak individual tracks.
 */
public final class EventFx {
    private EventFx() { }

    public static EventElement.Camera shake(String key, double start, double duration,
                                            double translation, double rotationDegrees, double frequency) {
        return new EventElement.Camera(key, start, start + duration, 100, ConflictPolicy.ALLOW,
                EventElement.CameraMode.ADDITIVE, Vec3d.ZERO, TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(
                        Keyframe.at(0.0, translation, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(duration, 0.0)),
                ScalarTrack.of(
                        Keyframe.at(0.0, rotationDegrees, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(duration, 0.0)),
                ScalarTrack.constant(frequency), null);
    }

    public static EventElement.Overlay flash(String key, double start, double duration, int rgb, double strength) {
        int argb = 0xFF000000 | (rgb & 0x00FFFFFF);
        return new EventElement.Overlay(key, start, start + duration, 110, ConflictPolicy.ALLOW,
                ColorTrack.constant(argb),
                ScalarTrack.of(Keyframe.at(0.0, clamp01(strength), Easing.EASE_OUT_CUBIC), Keyframe.at(duration, 0.0)),
                ScalarTrack.constant(0.0), ScalarTrack.constant(strength * 0.7),
                ScalarTrack.of(Keyframe.at(0.0, strength, Easing.EASE_OUT_CUBIC), Keyframe.at(duration, 0.0)));
    }

    public static EventElement.Overlay letterbox(String key, double start, double end, double strength) {
        double duration = Math.max(0.001, end - start);
        double edge = Math.min(10.0, duration * 0.25);
        return new EventElement.Overlay(key, start, end, 20, ConflictPolicy.ALLOW,
                ColorTrack.constant(0x00000000), ScalarTrack.constant(0.0),
                ScalarTrack.of(
                        Keyframe.at(0.0, 0.0, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(edge, clamp01(strength)),
                        Keyframe.at(Math.max(edge, duration - edge), clamp01(strength), Easing.EASE_IN_CUBIC),
                        Keyframe.at(duration, 0.0)),
                ScalarTrack.constant(0.0), ScalarTrack.constant(0.0));
    }

    public static EventElement.AudioCue sound(String key, double atTick, Identifier soundId,
                                              Vec3d offset, float volume, float pitch, boolean spatial) {
        return new EventElement.AudioCue(key, atTick, atTick + 6.0, 50, ConflictPolicy.ALLOW,
                soundId, offset, volume, pitch, spatial, "master");
    }

    public static EventElement.Marker marker(String key, double atTick, String name, Map<String, String> parameters) {
        return new EventElement.Marker(key, atTick, atTick + 10.0, 0, ConflictPolicy.ALLOW, name, parameters);
    }

    /**
     * Explosion/landing/portal-impact package: shockwave, flash, camera shake, particles,
     * temporary dynamic light, spatial sound and a marker for gameplay mods.
     */
    public static List<SceneElement> impact(String key, Vec3d offset, int argb,
                                            Identifier soundId, Identifier particleId, double strength) {
        double s = Math.max(0.05, strength);
        int rgb = argb & 0x00FFFFFF;
        ColorTrack fadeColor = ColorTrack.of(
                Keyframe.at(0.0, 0xFF000000 | rgb, Easing.EASE_OUT_CUBIC),
                Keyframe.at(18.0, rgb));
        ScalarTrack radius = ScalarTrack.of(
                Keyframe.at(0.0, 0.15, Easing.EASE_OUT_CUBIC),
                Keyframe.at(18.0, 8.0 * s));
        ScalarTrack thickness = ScalarTrack.of(
                Keyframe.at(0.0, 0.55 * s, Easing.EASE_OUT_CUBIC),
                Keyframe.at(18.0, 0.03));

        SceneElement.Ring ring = new SceneElement.Ring(key + "/shockwave", 0.0, 18.0, 80,
                ConflictPolicy.ALLOW, offset, radius, thickness, 64,
                TransformTrack.identity(), MotionCurve.none(), fadeColor);

        SceneLight light = new SceneLight(key + "/light", 0.0, 12.0, 70, ConflictPolicy.ALLOW,
                SceneLight.Kind.POINT, offset, TransformTrack.identity(), MotionCurve.none(),
                ColorTrack.constant(0xFF000000 | rgb),
                ScalarTrack.of(Keyframe.at(0.0, 7.0 * s, Easing.EASE_OUT_CUBIC), Keyframe.at(12.0, 0.0)),
                ScalarTrack.of(Keyframe.at(0.0, 14.0 * s, Easing.EASE_OUT_CUBIC), Keyframe.at(12.0, 2.0)),
                new Vec3d(0, -1, 0), 0.0, 180.0);

        EventElement.Emitter emitter = new EventElement.Emitter(key + "/debris", 0.0, 12.0, 60,
                ConflictPolicy.ALLOW, particleId, EventElement.EmitterShape.SPHERE, offset,
                TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0.0, 420.0 * s, Easing.EASE_OUT_CUBIC), Keyframe.at(12.0, 25.0)),
                ScalarTrack.constant(0.55 * s), ScalarTrack.constant(0.32 * s), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFF000000 | rgb), Math.max(32, (int)Math.min(2048, 280 * s)));

        return List.of(
                ring,
                light,
                emitter,
                shake(key + "/shake", 0.0, 16.0, 0.12 * s, 2.2 * s, 1.35),
                flash(key + "/flash", 0.0, 8.0, rgb, Math.min(1.0, 0.85 * s)),
                sound(key + "/sound", 0.0, soundId, offset, (float)Math.min(4.0, 1.2 * s), 1.0F, true),
                marker(key + "/marker", 0.0, "impact", Map.of("strength", Double.toString(s)))
        );
    }

    /** A ready-made portal opening sequence with atmosphere, rings, particles, light and camera tension. */
    public static List<SceneElement> portalOpen(String key, Vec3d offset, int argb,
                                                Identifier soundId, double radius, double duration) {
        double d = Math.max(20.0, duration);
        double r = Math.max(0.5, radius);
        int rgb = argb & 0x00FFFFFF;

        SceneElement.Ring outer = new SceneElement.Ring(key + "/outer", 0.0, d, 50, ConflictPolicy.ALLOW,
                offset,
                ScalarTrack.of(Keyframe.at(0.0, 0.05, Easing.EASE_OUT_BACK), Keyframe.at(d * 0.28, r)),
                ScalarTrack.of(Keyframe.at(0.0, r * 0.4, Easing.EASE_OUT_CUBIC), Keyframe.at(d * 0.3, 0.08)),
                96, TransformTrack.identity(), MotionCurve.none(),
                ColorTrack.of(Keyframe.at(0.0, 0x00000000), Keyframe.at(d * 0.12, 0xFF000000 | rgb)));

        EventElement.Emitter swirl = new EventElement.Emitter(key + "/swirl", 0.0, d, 45, ConflictPolicy.ALLOW,
                Identifier.of("minecraft", "portal"), EventElement.EmitterShape.DISC, offset,
                TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0.0, 20.0), Keyframe.at(d * 0.25, 280.0), Keyframe.at(d, 120.0)),
                ScalarTrack.constant(r), ScalarTrack.constant(0.08), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFF000000 | rgb), 512);

        SceneLight glow = new SceneLight(key + "/glow", 0.0, d, 40, ConflictPolicy.ALLOW,
                SceneLight.Kind.POINT, offset, TransformTrack.identity(), MotionCurve.none(),
                ColorTrack.constant(0xFF000000 | rgb),
                ScalarTrack.of(Keyframe.at(0.0, 0.0), Keyframe.at(d * 0.25, 5.0), Keyframe.at(d, 3.0)),
                ScalarTrack.constant(r * 5.0), new Vec3d(0, -1, 0), 0.0, 180.0);

        EventElement.Atmosphere atmosphere = new EventElement.Atmosphere(key + "/atmosphere", 0.0, d, 20,
                ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(0x55000000 | rgb), ColorTrack.constant(0x66000000 | rgb),
                ScalarTrack.of(Keyframe.at(0.0, 0.0), Keyframe.at(d * 0.4, 0.22)),
                ScalarTrack.constant(0.0), ScalarTrack.constant(Math.max(24.0, r * 12.0)),
                ScalarTrack.constant(0.72), ScalarTrack.constant(0.35), ScalarTrack.constant(0.55));

        return List.of(
                outer, swirl, glow, atmosphere,
                shake(key + "/rumble", 0.0, d, 0.025, 0.35, 0.55),
                letterbox(key + "/bars", 0.0, d, 0.7),
                sound(key + "/sound", 0.0, soundId, offset, 1.4F, 0.85F, true),
                marker(key + "/opened", d * 0.28, "portal_open", Map.of("radius", Double.toString(r)))
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
