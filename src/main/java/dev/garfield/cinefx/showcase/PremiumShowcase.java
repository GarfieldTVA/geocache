package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransformTrack;
import dev.garfield.cinefx.api.AssetBundle;
import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.ConflictPolicy;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.EventProgram;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** One scene dedicated to exercising every built-in premium glTF path at once. */
public final class PremiumShowcase {
    public static final Identifier SCENE = id("showcase/premium_renderer");
    public static final Identifier MODEL = id("model/premium_sentinel");
    public static final Identifier BUNDLE = id("showcase/premium_renderer_assets");
    private static boolean registered;

    private PremiumShowcase() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        AssetBundle.Registry.register(new AssetBundle(BUNDLE, List.of(), List.of(MODEL),
                Map.of("purpose", "premium-renderer-self-test")));
        CineFxApi.register(scene());
    }

    public static EventProgram program() {
        EventProgram.Builder builder = EventProgram.builder(id("program/premium_renderer"), "preload");
        builder.phase(new EventProgram.Phase("preload",
                List.of(EventProgram.Action.preload(BUNDLE)), List.of(),
                List.of(new EventProgram.Transition("play", 0,
                        EventProgram.Condition.any(EventProgram.Condition.assetsReady(BUNDLE), EventProgram.Condition.after(80)),
                        List.of()))));
        builder.phase(new EventProgram.Phase("play",
                List.of(EventProgram.Action.play(SCENE)), List.of(),
                List.of(new EventProgram.Transition("done", 0, EventProgram.Condition.after(900), List.of()))));
        builder.phase(new EventProgram.Phase("done", List.of(EventProgram.Action.stop(SCENE)), List.of(), List.of()));
        return builder.meta("showcase", "premium-renderer").build();
    }

    private static SceneDefinition scene() {
        double duration = 900.0;
        SceneBuilder b = SceneBuilder.create(SCENE).duration(duration).priority(120)
                .meta("showcase", "premium-renderer")
                .meta("tests", "uv,pbr,animation,skinning,morph,socket,geometry-shadow");

        b.add(new EventElement.Atmosphere("premium_atmosphere", 0, duration, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(0xFF071426), ColorTrack.constant(0xFF06111D), ScalarTrack.constant(0.018),
                ScalarTrack.constant(0.0), ScalarTrack.constant(220.0), ScalarTrack.constant(0.4),
                ScalarTrack.constant(0.9), ScalarTrack.constant(0.12)));

        b.add(new AdvancedEventElement.Sky("premium_sky", 0, duration, 20, ConflictPolicy.REPLACE_LOWER, null,
                ColorTrack.constant(0xFF0C3851), ColorTrack.constant(0xFF01040B), ScalarTrack.constant(0.4),
                ScalarTrack.constant(1.4), ScalarTrack.constant(0.35), ScalarTrack.constant(0.55),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(duration, 35.0)), Map.of("style", "premium_lab")));

        AdvancedTransformTrack transform = new AdvancedTransformTrack(
                Vec3Track.of(
                        Keyframe.at(0, new Vec3d(0, 0, 0), Easing.EASE_OUT_CUBIC),
                        Keyframe.at(220, new Vec3d(0, 1.5, 0), Easing.EASE_IN_OUT_SINE),
                        Keyframe.at(440, new Vec3d(0, 0.4, 0), Easing.EASE_IN_OUT_SINE),
                        Keyframe.at(700, new Vec3d(0, 1.0, 0), Easing.EASE_IN_OUT_SINE)),
                Vec3Track.angles(
                        Keyframe.at(0, new Vec3d(0, -18, 0)),
                        Keyframe.at(450, new Vec3d(0, 18, 0)),
                        Keyframe.at(duration, new Vec3d(0, -18, 0))),
                Vec3Track.constant(new Vec3d(4.5, 4.5, 4.5)),
                Vec3Track.constant(Vec3d.ZERO));

        ComplexElement.AnimationLayer animation = new ComplexElement.AnimationLayer(
                id("showcase"), ScalarTrack.constant(1.0), ScalarTrack.constant(1.0),
                0.0, true, ComplexElement.BlendMode.OVERRIDE, Map.of("source", "gltf"));

        ComplexElement.MorphTrack morph = new ComplexElement.MorphTrack("pulse",
                ScalarTrack.of(
                        Keyframe.at(0, 0.0),
                        Keyframe.at(120, 0.85, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(240, 0.15, Easing.EASE_IN_OUT_SINE),
                        Keyframe.at(400, 1.0, Easing.EASE_OUT_BACK),
                        Keyframe.at(620, 0.25, Easing.EASE_IN_OUT_SINE),
                        Keyframe.at(820, 0.9, Easing.EASE_OUT_CUBIC)));

        b.add(new ComplexElement.Actor("premium_sentinel", 0, duration, 60, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.CUSTOM_MODEL, MODEL, "PremiumSentinel", null,
                Map.of("shadow_plane_y", "0.04", "quality", "premium"),
                new Vec3d(0, 0.2, 13), transform, MotionCurve.none(),
                List.of(animation), List.of(), List.of(morph), new Vec3d(0, 2.0, 2.0),
                ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1.0), ScalarTrack.constant(0.3),
                true, 420.0));

        b.add(new AdvancedEventElement.Attachment("socket_beam", 80, duration, 85, ConflictPolicy.ALLOW,
                "premium_sentinel", "weapon_socket", AdvancedEventElement.InheritMode.FULL,
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.BeamPayload(Vec3d.ZERO, new Vec3d(0, 0, 9),
                        ScalarTrack.of(Keyframe.at(0, 0.08), Keyframe.at(100, 0.28, Easing.EASE_OUT_CUBIC),
                                Keyframe.at(220, 0.1)),
                        ColorTrack.constant(0xFF77EEFF)), 500.0));

        b.add(new AdvancedEventElement.Attachment("socket_light", 0, duration, 82, ConflictPolicy.ALLOW,
                "premium_sentinel", "weapon_socket", AdvancedEventElement.InheritMode.FULL,
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.LightPayload(SceneLight.Kind.POINT, new Vec3d(0, 0, 1),
                        ColorTrack.constant(0xFF68E7FF),
                        ScalarTrack.of(Keyframe.at(0, 2.0), Keyframe.at(90, 5.0), Keyframe.at(180, 2.2)),
                        ScalarTrack.constant(18.0), 0, 180), 500.0));

        b.add(new EventElement.Emitter("premium_particles", 0, duration, 35, ConflictPolicy.ALLOW,
                Identifier.ofVanilla("electric_spark"), EventElement.EmitterShape.SPHERE,
                new Vec3d(0, 4.0, 13), TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(130.0), ScalarTrack.constant(3.8), ScalarTrack.constant(0.18),
                ScalarTrack.constant(1.0), ColorTrack.constant(0xFF87F4FF), 1024));

        b.add(new SceneElement.Ring("premium_ring", 20, duration, 30, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.08, 13),
                ScalarTrack.of(Keyframe.at(0, 1.5), Keyframe.at(180, 8.0, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(360, 3.5), Keyframe.at(700, 9.5)),
                ScalarTrack.constant(0.22), 96, TransformTrack.identity(), MotionCurve.none(),
                ColorTrack.constant(0xAA5DE7FF)));

        b.add(new SceneElement.WorldText("premium_label", 0, duration, 90, ConflictPolicy.REPLACE_LOWER,
                "CINEFX // PREMIUM GLTF", Identifier.ofVanilla("default"), new Vec3d(0, 10.5, 13),
                TransformTrack.constant(Transform.scale(1.7)), MotionCurve.none(), ColorTrack.constant(0xFF93F7FF),
                0x60030B12, true, true, true));

        return b.build();
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
