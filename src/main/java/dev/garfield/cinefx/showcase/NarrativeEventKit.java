package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransformTrack;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.ConflictPolicy;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared vocabulary for narrative showcases. Helpers deliberately encode anticipation/action/aftermath patterns. */
final class NarrativeEventKit {
    private NarrativeEventKit() { }

    static SceneBuilder base(Identifier id, double duration, String story) {
        return SceneBuilder.create(id).duration(duration).priority(200)
                .meta("showcase", "narrative-mega-event")
                .meta("story", story)
                .meta("terrain_adaptive", "true")
                .meta("rhythm", "15-30-second-beats")
                .meta("duration_ticks", Double.toString(duration));
    }

    static void ambience(SceneBuilder b, String prefix, double end, int sky, int fog, int tint) {
        b.add(new EventElement.Atmosphere(prefix + "_atmosphere", 0, end, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(sky), ColorTrack.constant(fog), ScalarTrack.constant(0.025),
                ScalarTrack.constant(0.0), ScalarTrack.constant(280.0), ScalarTrack.constant(0.58),
                ScalarTrack.constant(0.85), ScalarTrack.constant(0.22)));
        b.add(new AdvancedEventElement.Sky(prefix + "_sky", 0, end, 12, ConflictPolicy.REPLACE_LOWER, null,
                ColorTrack.constant(sky), ColorTrack.constant(fog), ScalarTrack.constant(0.72),
                ScalarTrack.constant(1.05), ScalarTrack.constant(0.08), ScalarTrack.constant(0.16),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(end, 28.0)), Map.of("story", prefix)));
        b.add(new UltraEventElement.PostProcess(prefix + "_grade", 0, end, 16, ConflictPolicy.REPLACE_LOWER,
                Map.of(UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(0.12),
                        UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(0.12)),
                ColorTrack.constant(tint), ScalarTrack.constant(14.0), ScalarTrack.constant(6.0),
                Map.of("fallback", "subtle")));
    }

    static SceneElement.WorldText text(String key, double start, double end, String value, Vec3d position, int color) {
        return new SceneElement.WorldText(key, start, end, 210, ConflictPolicy.REPLACE_LOWER, value,
                Identifier.ofVanilla("default"), position,
                TransformTrack.constant(Transform.scale(1.45)), MotionCurve.none(), ColorTrack.constant(color),
                0x42000000, true, true, true);
    }

    static EventElement.AudioCue sound(String key, double tick, Identifier sound, Vec3d offset, float volume, float pitch) {
        return new EventElement.AudioCue(key, tick, tick + 16, 180, ConflictPolicy.ALLOW,
                sound, offset, volume, pitch, true, "master");
    }

    static AdvancedEventElement.AudioLayer loop(String key, double start, double end, Identifier sound, double volume, double pitch) {
        return new AdvancedEventElement.AudioLayer(key, start, end, 18, ConflictPolicy.ALLOW, sound,
                ScalarTrack.constant(volume), ScalarTrack.constant(pitch), ScalarTrack.constant(0.0),
                true, false, 60, 80, Map.of("story_layer", key));
    }

    static PathTrack route(double duration, Vec3d... points) {
        if (points.length < 2) throw new IllegalArgumentException("route needs at least two points");
        PathTrack.Point[] out = new PathTrack.Point[points.length];
        for (int i = 0; i < points.length; i++) {
            double tick = duration * i / (double)(points.length - 1);
            Easing easing = i == points.length - 1 ? Easing.LINEAR : Easing.EASE_IN_OUT_SINE;
            out[i] = PathTrack.Point.at(tick, points[i], easing);
        }
        return PathTrack.catmullRom(out);
    }

    static ComplexElement.Node node(String key, double start, double end, PathTrack route, double bank) {
        return new ComplexElement.Node(key, start, end, 70, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), route.asMotionCurve(true, bank), true);
    }

    static ComplexElement.Mesh mesh(String key, double start, double end, String parent, Identifier model,
                                    Vec3d offset, Vec3d scale, int tint, double emissive, String fallback) {
        return new ComplexElement.Mesh(key, start, end, 80, ConflictPolicy.ALLOW, parent, model, null, offset,
                transform(scale), MotionCurve.none(), ColorTrack.constant(tint), ScalarTrack.constant(1.0),
                ScalarTrack.constant(emissive), true, 950.0, Map.of("fallback_shape", fallback));
    }

    static void movingMesh(SceneBuilder b, String key, double start, double end, Identifier model,
                           PathTrack path, Vec3d scale, int tint, double emissive, String fallback, double bank) {
        String root = key + "_root";
        b.add(node(root, start, end, path, bank));
        b.add(mesh(key, start, end, root, model, Vec3d.ZERO, scale, tint, emissive, fallback));
    }

    static ComplexElement.Actor mob(String key, double start, double end, String parent, Identifier type,
                                    Vec3d offset, Vec3d scale, String clip) {
        return new ComplexElement.Actor(key, start, end, 95, ConflictPolicy.ALLOW, parent,
                ComplexElement.ActorKind.ENTITY, type, null, null,
                Map.of("ground_snap", "true", "locomotion", "auto"), offset, transform(scale), MotionCurve.none(),
                List.of(animation(clip, clip.equals("run") || clip.equals("panic") ? 1.2 : 0.75)),
                List.of(), List.of(), null, ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1.0),
                ScalarTrack.constant(0.0), true, 800.0);
    }

    static ComplexElement.Actor sentinel(String key, double start, double end, String parent, Vec3d scale, int tint) {
        ComplexElement.AnimationLayer animation = new ComplexElement.AnimationLayer(
                Identifier.of("cinefx", "showcase"), ScalarTrack.constant(1.0), ScalarTrack.constant(0.85),
                0.0, true, ComplexElement.BlendMode.OVERRIDE, Map.of("source", "gltf"));
        ComplexElement.MorphTrack morph = new ComplexElement.MorphTrack("pulse",
                ScalarTrack.of(Keyframe.at(0, 0.1), Keyframe.at(160, 0.9, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(360, 0.25), Keyframe.at(620, 1.0)));
        return new ComplexElement.Actor(key, start, end, 110, ConflictPolicy.ALLOW, parent,
                ComplexElement.ActorKind.CUSTOM_MODEL, PremiumShowcase.MODEL, "RiftSentinel", null,
                Map.of("quality", "premium"), Vec3d.ZERO, transform(scale), MotionCurve.none(),
                List.of(animation), List.of(), List.of(morph), null, ColorTrack.constant(tint),
                ScalarTrack.constant(1.0), ScalarTrack.constant(0.32), true, 900.0);
    }

    static ComplexElement.AnimationLayer animation(String clip, double speed) {
        return new ComplexElement.AnimationLayer(Identifier.of("cinefx", clip), ScalarTrack.constant(1.0),
                ScalarTrack.constant(speed), 0.0, true, ComplexElement.BlendMode.OVERRIDE, Map.of());
    }

    static AdvancedEventElement.Crowd crowd(String key, double start, double end, Identifier type,
                                            int count, double radius, double zCenter, String mode,
                                            double moveRadius, double moveSpeed, String clip) {
        ArrayList<AdvancedEventElement.CrowdAgent> agents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297;
            double r = 2.0 + radius * Math.sqrt((i + 0.5) / count);
            agents.add(new AdvancedEventElement.CrowdAgent(new Vec3d(Math.cos(a) * r, 0,
                    zCenter + Math.sin(a) * r), Math.toDegrees(-a), i * 3.7, i % 8));
        }
        return new AdvancedEventElement.Crowd(key, start, end, 48, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.ENTITY, type, "StoryNPC", agents,
                List.of(animation(clip, clip.equals("panic") || clip.equals("run") ? 1.18 : 0.72)),
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF),
                ScalarTrack.constant(1.0), true, 720.0,
                Map.of("locomotion", mode, "move_radius", Double.toString(moveRadius),
                        "move_speed", Double.toString(moveSpeed), "ground_snap", "true"));
    }

    static UltraEventElement.CameraRig camera(String key, double start, double end, PathTrack path,
                                              Vec3d lookAt, double fovA, double fovB, double shakeT, double shakeR) {
        return new UltraEventElement.CameraRig(key, start, end, 240, ConflictPolicy.REPLACE_LOWER,
                UltraEventElement.CameraRigMode.RAIL, path, lookAt, null,
                ScalarTrack.constant(0.0), ScalarTrack.of(Keyframe.at(0, fovA), Keyframe.at(end - start, fovB)),
                ScalarTrack.constant(14.0), ScalarTrack.constant(5.0), ScalarTrack.constant(shakeT),
                ScalarTrack.constant(shakeR), ScalarTrack.constant(1.7), true,
                Map.of("show_hud", "false", "show_hand", "false"));
    }

    static AdvancedEventElement.PlayerControl control(String key, double start, double end) {
        return new AdvancedEventElement.PlayerControl(key, start, end, 245, ConflictPolicy.REPLACE_LOWER,
                true, true, true, false, ScalarTrack.constant(0.0), ScalarTrack.constant(1.0),
                ScalarTrack.constant(-1.0), false, false);
    }

    static UltraEventElement.WorldDeform deform(String key, double start, double end, UltraEventElement.DeformMode mode,
                                                Vec3d center, double radius, double amplitude, int color) {
        return new UltraEventElement.WorldDeform(key, start, end, 125, ConflictPolicy.ALLOW, center, mode,
                ScalarTrack.of(Keyframe.at(0, Math.max(1.0, radius * 0.08)),
                        Keyframe.at(Math.min(420.0, end - start), radius, Easing.EASE_OUT_CUBIC)),
                ScalarTrack.constant(amplitude), ScalarTrack.constant(1.35),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.min(360.0, end - start), 1.0)),
                null, ColorTrack.constant(color), true, Map.of("terrain_snap", "true"));
    }

    static UltraEventElement.ParticleField field(String key, double start, double end, Identifier particle,
                                                 Vec3d center, double rate, int color, int max,
                                                 List<UltraEventElement.Force> forces) {
        return new UltraEventElement.ParticleField(key, start, end, 90, ConflictPolicy.ALLOW, null, particle, center,
                AdvancedTransformTrack.identity(), MotionCurve.none(), ScalarTrack.constant(rate),
                ScalarTrack.constant(74.0), ScalarTrack.constant(0.55), ScalarTrack.constant(1.0),
                ColorTrack.constant(color), forces, max, true, true, 650.0,
                Map.of("spawn_radius", "20", "story_field", key));
    }

    static UltraEventElement.Force force(UltraEventElement.ForceKind kind, double strength, double radius, long seed) {
        return new UltraEventElement.Force(kind, Vec3d.ZERO, new Vec3d(0, 1, 0), ScalarTrack.constant(strength),
                ScalarTrack.constant(radius), ScalarTrack.constant(1.2), seed);
    }

    static UltraEventElement.Force directional(Vec3d direction, double strength, double radius, long seed) {
        return new UltraEventElement.Force(UltraEventElement.ForceKind.DIRECTIONAL, Vec3d.ZERO, direction,
                ScalarTrack.constant(strength), ScalarTrack.constant(radius), ScalarTrack.constant(1.0), seed);
    }

    static UltraEventElement.PortalSurface portal(String key, double start, double end, Vec3d position,
                                                  Vec3d size, int color, double distortion) {
        return new UltraEventElement.PortalSurface(key, start, end, 115, ConflictPolicy.ALLOW, null,
                UltraEventElement.PortalMode.DIMENSION_VIEW, position, AdvancedTransformTrack.identity(), MotionCurve.none(),
                size, null, Vec3d.ZERO,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.min(90.0, end - start), 1.0, Easing.EASE_OUT_CUBIC)),
                ColorTrack.constant(color), ScalarTrack.constant(distortion), 2, 850.0,
                Map.of("premium", "render-target-eligible"));
    }

    static UltraEventElement.LightRig light(String key, double start, double end, String parent,
                                            Vec3d offset, int color, double intensity, double radius, double volumetric) {
        return new UltraEventElement.LightRig(key, start, end, 130, ConflictPolicy.ALLOW, parent, offset,
                AdvancedTransformTrack.identity(), MotionCurve.none(), List.of(
                new UltraEventElement.RigLight(UltraEventElement.LightKind.POINT, Vec3d.ZERO, new Vec3d(0, -1, 0),
                        ColorTrack.constant(color), ScalarTrack.constant(intensity), ScalarTrack.constant(radius),
                        ScalarTrack.constant(0.0), ScalarTrack.constant(180.0), true, ScalarTrack.constant(volumetric))),
                ScalarTrack.constant(1.0), 900.0, Map.of("narrative_light", key));
    }

    static AdvancedEventElement.Attachment beam(String key, double start, double end, String parent,
                                                Vec3d from, Vec3d to, double width, int color) {
        return new AdvancedEventElement.Attachment(key, start, end, 145, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.BeamPayload(from, to, ScalarTrack.constant(width), ColorTrack.constant(color)), 1100.0);
    }

    static AdvancedEventElement.Attachment attachedParticles(String key, double start, double end, String parent,
                                                             Vec3d offset, Identifier particle, double rate,
                                                             double spread, double speed) {
        return new AdvancedEventElement.Attachment(key, start, end, 105, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, offset, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.EmitterPayload(particle, EventElement.EmitterShape.SPHERE,
                        ScalarTrack.constant(rate), ScalarTrack.constant(spread), ScalarTrack.constant(speed),
                        ScalarTrack.constant(1.0), ColorTrack.constant(0xFFFFFFFF), 4096), 1100.0);
    }

    static SceneElement.Ring ring(String key, double start, double end, Vec3d center,
                                  double from, double to, double thickness, int color) {
        return new SceneElement.Ring(key, start, end, 105, ConflictPolicy.ALLOW, center,
                ScalarTrack.of(Keyframe.at(0, from, Easing.EASE_OUT_CUBIC), Keyframe.at(end - start, to)),
                ScalarTrack.constant(thickness), 96, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(color));
    }

    static UltraEventElement.Fracture fracture(String key, double start, double end, String parent, Identifier model,
                                               int shards, int tint, Vec3d impulse, double force) {
        return new UltraEventElement.Fracture(key, start, end, 175, ConflictPolicy.ALLOW, parent, model,
                UltraEventElement.FractureMode.VORONOI, shards, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                impulse, ScalarTrack.constant(force), ScalarTrack.constant(0.04), ScalarTrack.constant(0.978),
                ScalarTrack.constant(8.0), ColorTrack.constant(tint), ScalarTrack.constant(1.0), true, false, 1100.0,
                Map.of("fracture_duration", Double.toString(Math.max(80.0, end - start))));
    }

    static UltraEventElement.MaterialEffect material(String key, double start, double end, String target,
                                                     UltraEventElement.MaterialMode mode, int color) {
        return new UltraEventElement.MaterialEffect(key, start, end, 165, ConflictPolicy.ALLOW, target, mode,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.min(180.0, end - start), 1.0, Easing.EASE_OUT_CUBIC)),
                ScalarTrack.constant(0.08), ColorTrack.constant(color), ScalarTrack.constant(1.4),
                ScalarTrack.constant(0.45), new Vec3d(0, 1, 0), Map.of("story_material", key));
    }

    static SceneElement.Block block(String key, double start, double end, BlockState state, Vec3d position, Vec3d scale) {
        return new SceneElement.Block(key, start, end, 40, ConflictPolicy.ALLOW, state, null, position,
                TransformTrack.of(Keyframe.at(0, new Transform(Vec3d.ZERO, Vec3d.ZERO, new Vec3d(0.05, 0.05, 0.05)), Easing.EASE_OUT_BACK),
                        Keyframe.at(Math.min(120.0, end - start), new Transform(Vec3d.ZERO, Vec3d.ZERO, scale))),
                MotionCurve.none(), false, 0, false);
    }

    static void sector(SceneBuilder b, String prefix, double start, double end, BlockState state,
                       Vec3d center, int count, int color) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297;
            double r = 2.5 + (i % 6) * 1.8;
            Vec3d p = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            b.add(block(prefix + "_" + i, start + i * 5.0, end, state, p,
                    new Vec3d(0.65 + (i % 3) * 0.12, 1.0 + (i % 5) * 0.55, 0.65 + (i % 2) * 0.15)));
        }
        b.add(ring(prefix + "_boundary", start, Math.min(end, start + 500), center.add(0, 0.08, 0),
                0.5, 10.0, 0.18, color));
    }

    static AdvancedTransformTrack transform(Vec3d scale) {
        return new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                Vec3Track.angles(Keyframe.at(0, Vec3d.ZERO)), Vec3Track.constant(scale), Vec3Track.constant(Vec3d.ZERO));
    }

    static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
