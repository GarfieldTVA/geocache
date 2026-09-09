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
import dev.garfield.cinefx.api.EventSignals;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reference scene that deliberately exercises every CineFX Ultra channel together. */
public final class UltraShowcase {
    public static final Identifier SCENE = id("showcase/ultra_everything");
    public static final Identifier OVERLOAD = id("showcase/ultra_overload");
    public static final Identifier BUNDLE = id("showcase/ultra_assets");
    private static boolean registered;

    private UltraShowcase() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PremiumShowcase.register();
        AssetBundle.Registry.register(new AssetBundle(BUNDLE, List.of(), List.of(PremiumShowcase.MODEL),
                Map.of("purpose", "ultra-all-systems-self-test")));
        CineFxApi.register(scene());
        CineFxApi.register(overloadScene());
    }

    public static EventProgram program() {
        EventProgram.Builder builder = EventProgram.builder(id("program/ultra_everything"), "preload");
        builder.phase(new EventProgram.Phase("preload",
                List.of(EventProgram.Action.preload(BUNDLE)), List.of(), List.of(
                new EventProgram.Transition("show", 0,
                        EventProgram.Condition.any(EventProgram.Condition.assetsReady(BUNDLE), EventProgram.Condition.after(80)),
                        List.of()))));
        builder.phase(new EventProgram.Phase("show",
                List.of(EventProgram.Action.play(SCENE)), List.of(), List.of(
                // An external mod can call EventSignals.emit(handle, "overload") at any time.
                new EventProgram.Transition("overload", 100, EventSignals.condition("overload"), List.of()),
                new EventProgram.Transition("done", 0, EventProgram.Condition.after(980), List.of()))));
        builder.phase(new EventProgram.Phase("overload",
                List.of(EventProgram.Action.stop(SCENE), EventProgram.Action.play(OVERLOAD)), List.of(), List.of(
                new EventProgram.Transition("done", 0, EventProgram.Condition.after(340), List.of()))));
        builder.phase(new EventProgram.Phase("done",
                List.of(EventProgram.Action.stop(SCENE), EventProgram.Action.stop(OVERLOAD)), List.of(), List.of()));
        return builder.meta("showcase", "ultra-everything").meta("signal", "overload").build();
    }

    private static SceneDefinition scene() {
        double d = 1000.0;
        SceneBuilder b = SceneBuilder.create(SCENE).duration(d).priority(180)
                .meta("showcase", "ultra")
                .meta("tests", "postfx,lighting,fracture,softbody,ik,particle-fields,camera,audio,materials,deform,portal,editor");

        b.add(new EventElement.Atmosphere("ultra_atmosphere", 0, d, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(0xFF07111E), ColorTrack.constant(0xFF051021), ScalarTrack.constant(0.025),
                ScalarTrack.constant(0), ScalarTrack.constant(260), ScalarTrack.constant(0.22),
                ScalarTrack.constant(0.75), ScalarTrack.constant(0.35)));

        b.add(new UltraEventElement.PostProcess("ultra_post", 0, d, 190, ConflictPolicy.REPLACE_LOWER,
                Map.ofEntries(
                        Map.entry(UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(0.42)),
                        Map.entry(UltraEventElement.PostEffect.DEPTH_OF_FIELD, ScalarTrack.constant(0.22)),
                        Map.entry(UltraEventElement.PostEffect.MOTION_BLUR, ScalarTrack.constant(0.08)),
                        Map.entry(UltraEventElement.PostEffect.CHROMATIC_ABERRATION,
                                ScalarTrack.of(Keyframe.at(0, 0.03), Keyframe.at(520, 0.08), Keyframe.at(760, 0.28), Keyframe.at(d, 0.04))),
                        Map.entry(UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(0.36)),
                        Map.entry(UltraEventElement.PostEffect.FILM_GRAIN, ScalarTrack.constant(0.10)),
                        Map.entry(UltraEventElement.PostEffect.LENS_DIRT, ScalarTrack.constant(0.18)),
                        Map.entry(UltraEventElement.PostEffect.HEAT_HAZE, ScalarTrack.constant(0.03)),
                        Map.entry(UltraEventElement.PostEffect.UNDERWATER_REFRACTION, ScalarTrack.constant(0.0)),
                        Map.entry(UltraEventElement.PostEffect.GLITCH,
                                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(650, 0.0), Keyframe.at(730, 0.38), Keyframe.at(800, 0.02))),
                        Map.entry(UltraEventElement.PostEffect.TRANSITION,
                                ScalarTrack.of(Keyframe.at(0, 0.7, Easing.EASE_OUT_CUBIC), Keyframe.at(35, 0.0), Keyframe.at(950, 0.0), Keyframe.at(d, 1.0)))),
                ColorTrack.constant(0x081D6A80), ScalarTrack.constant(14), ScalarTrack.constant(6),
                Map.of("profile", "ultra_showcase")));

        AdvancedTransformTrack actorTransform = new AdvancedTransformTrack(
                Vec3Track.of(Keyframe.at(0, new Vec3d(0, 0, 0)), Keyframe.at(500, new Vec3d(0, 1.2, 0), Easing.EASE_IN_OUT_SINE),
                        Keyframe.at(d, new Vec3d(0, 0.25, 0))),
                Vec3Track.angles(Keyframe.at(0, new Vec3d(0, -18, 0)), Keyframe.at(500, new Vec3d(0, 20, 0)), Keyframe.at(d, new Vec3d(0, -10, 0))),
                Vec3Track.constant(new Vec3d(4.8, 4.8, 4.8)), Vec3Track.constant(Vec3d.ZERO));
        ComplexElement.AnimationLayer anim = new ComplexElement.AnimationLayer(id("showcase"), ScalarTrack.constant(1),
                ScalarTrack.constant(1), 0, true, ComplexElement.BlendMode.OVERRIDE, Map.of());
        ComplexElement.MorphTrack morph = new ComplexElement.MorphTrack("pulse",
                ScalarTrack.of(Keyframe.at(0, 0.05), Keyframe.at(180, 0.85, Easing.EASE_OUT_BACK),
                        Keyframe.at(420, 0.15), Keyframe.at(690, 1.0), Keyframe.at(d, 0.25)));
        b.add(new ComplexElement.Actor("ultra_sentinel", 0, d, 90, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.CUSTOM_MODEL, PremiumShowcase.MODEL, "UltraSentinel", null,
                Map.of("shadow_plane_y", "0.03", "quality", "ultra"), new Vec3d(0, 0.3, 14),
                actorTransform, MotionCurve.none(), List.of(anim), List.of(), List.of(morph), new Vec3d(0, 2.0, 12),
                ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1), ScalarTrack.constant(0.22), true, 520));

        b.add(new UltraEventElement.LightRig("hero_lighting", 0, d, 100, ConflictPolicy.ALLOW, "ultra_sentinel",
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), List.of(
                new UltraEventElement.RigLight(UltraEventElement.LightKind.SPOT, new Vec3d(-2, 5, -2), new Vec3d(0.2, -0.7, 1),
                        ColorTrack.constant(0xFF5CE7FF), ScalarTrack.constant(5.5), ScalarTrack.constant(28),
                        ScalarTrack.constant(18), ScalarTrack.constant(42), true, ScalarTrack.constant(0.85)),
                new UltraEventElement.RigLight(UltraEventElement.LightKind.AREA, new Vec3d(3, 2, 1), new Vec3d(-1, -0.15, 0),
                        ColorTrack.constant(0xFFFF4FD2), ScalarTrack.constant(3.2), ScalarTrack.constant(18),
                        ScalarTrack.constant(30), ScalarTrack.constant(68), true, ScalarTrack.constant(0.40)),
                new UltraEventElement.RigLight(UltraEventElement.LightKind.POINT, new Vec3d(0, 1.8, 2.5), new Vec3d(0, 0, 1),
                        ColorTrack.constant(0xFFB8FAFF), ScalarTrack.constant(2.4), ScalarTrack.constant(12),
                        ScalarTrack.constant(0), ScalarTrack.constant(180), false, ScalarTrack.constant(0.18))),
                ScalarTrack.of(Keyframe.at(0, 0.4), Keyframe.at(80, 1.0, Easing.EASE_OUT_CUBIC), Keyframe.at(d, 0.8)),
                520, Map.of("hero", "true")));

        b.add(new UltraEventElement.PortalSurface("ultra_portal", 60, d, 80, ConflictPolicy.ALLOW, null,
                UltraEventElement.PortalMode.DIMENSION_VIEW, new Vec3d(0, 6.2, 29),
                new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                        Vec3Track.angles(Keyframe.at(0, new Vec3d(0, 0, 0))),
                        Vec3Track.constant(new Vec3d(1, 1, 1)), Vec3Track.constant(Vec3d.ZERO)),
                MotionCurve.none(), new Vec3d(9, 12, 0.2), id("showcase/void_cataclysm"),
                new Vec3d(0, 18, 28), ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(80, 1.0, Easing.EASE_OUT_CUBIC)),
                ColorTrack.constant(0xFF7AEFFF), ScalarTrack.of(Keyframe.at(0, 0.15), Keyframe.at(500, 0.7)),
                2, 620, Map.of("premium", "render-target-eligible")));

        b.add(new UltraEventElement.ProceduralRig("sentinel_ik", 80, d, 120, ConflictPolicy.ALLOW,
                "ultra_sentinel", List.of(
                new UltraEventElement.IkGoal("root_joint>tip_joint", UltraEventElement.IkMode.CCD,
                        "tip_joint", "root_joint", new Vec3d(0, 0, 0), "ultra_portal",
                        ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(100, 0.85), Keyframe.at(d, 1.0)),
                        8, 0.01, Map.of("yaw_offset", "-4")),
                new UltraEventElement.IkGoal("tip_joint", UltraEventElement.IkMode.AIM,
                        "tip_joint", "", new Vec3d(0, 2.5, -2), null, ScalarTrack.constant(0.45),
                        3, 0.02, Map.of())), ScalarTrack.constant(1), Map.of("solver", "client")));

        b.add(new UltraEventElement.MaterialEffect("sentinel_hologram", 0, 260, 115, ConflictPolicy.ALLOW,
                "ultra_sentinel", UltraEventElement.MaterialMode.HOLOGRAM,
                ScalarTrack.of(Keyframe.at(0, 0.9), Keyframe.at(220, 0.18)), ScalarTrack.constant(0.08),
                ColorTrack.constant(0xFF70F7FF), ScalarTrack.constant(2.0), ScalarTrack.constant(1.2),
                new Vec3d(0, 1, 0), Map.of()));
        b.add(new UltraEventElement.MaterialEffect("sentinel_scan", 260, 700, 116, ConflictPolicy.ALLOW,
                "ultra_sentinel", UltraEventElement.MaterialMode.SCAN, ScalarTrack.constant(0.55),
                ScalarTrack.constant(0.06), ColorTrack.constant(0xFFFF71E9), ScalarTrack.constant(1.2),
                ScalarTrack.constant(1.8), new Vec3d(0, 1, 0), Map.of()));
        b.add(new UltraEventElement.MaterialEffect("sentinel_corruption", 700, d, 117, ConflictPolicy.ALLOW,
                "ultra_sentinel", UltraEventElement.MaterialMode.CORRUPTION,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(150, 0.82, Easing.EASE_OUT_CUBIC)),
                ScalarTrack.constant(0.10), ColorTrack.constant(0xFFFF3FCF), ScalarTrack.constant(3.0),
                ScalarTrack.constant(2.0), new Vec3d(0, 1, 0), Map.of()));

        List<UltraEventElement.SoftPoint> points = new ArrayList<>();
        List<UltraEventElement.SoftLink> links = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            points.add(new UltraEventElement.SoftPoint(new Vec3d(-2.1 + i * 0.15, 3.6 - i * 0.55, -0.6), 1.0, i == 0));
            if (i > 0) links.add(new UltraEventElement.SoftLink(i - 1, i, 0.57, 0.92));
        }
        b.add(new UltraEventElement.SoftBody("energy_chain", 0, d, 70, ConflictPolicy.ALLOW, "ultra_sentinel",
                UltraEventElement.SoftBodyMode.CHAIN, points, links, Vec3d.ZERO, AdvancedTransformTrack.identity(),
                MotionCurve.none(), ScalarTrack.constant(0.025), ScalarTrack.constant(1.4), ScalarTrack.constant(0.986),
                ScalarTrack.constant(0.045), ColorTrack.constant(0xFF77EFFF), true, 7, 420,
                Map.of("purpose", "secondary-motion")));

        b.add(new UltraEventElement.ParticleField("energy_vortex", 40, d, 68, ConflictPolicy.ALLOW, "ultra_sentinel",
                Identifier.ofVanilla("electric_spark"), new Vec3d(0, 2.0, 0), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0, 80.0), Keyframe.at(220, 850.0), Keyframe.at(760, 1800.0), Keyframe.at(d, 380.0)),
                ScalarTrack.constant(65), ScalarTrack.constant(0.28), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFF7EEDFF), List.of(
                new UltraEventElement.Force(UltraEventElement.ForceKind.VORTEX, Vec3d.ZERO, new Vec3d(0, 1, 0),
                        ScalarTrack.constant(2.7), ScalarTrack.constant(9), ScalarTrack.constant(1.5), 11),
                new UltraEventElement.Force(UltraEventElement.ForceKind.ATTRACTOR, new Vec3d(0, 2.2, 0), Vec3d.ZERO,
                        ScalarTrack.constant(1.25), ScalarTrack.constant(8), ScalarTrack.constant(1.2), 22),
                new UltraEventElement.Force(UltraEventElement.ForceKind.TURBULENCE, Vec3d.ZERO, Vec3d.ZERO,
                        ScalarTrack.constant(0.9), ScalarTrack.constant(12), ScalarTrack.constant(1), 33),
                new UltraEventElement.Force(UltraEventElement.ForceKind.DRAG, Vec3d.ZERO, Vec3d.ZERO,
                        ScalarTrack.constant(0.8), ScalarTrack.constant(100), ScalarTrack.constant(1), 44)),
                50000, true, true, 520, Map.of("spawn_radius", "3.2")));

        b.add(new UltraEventElement.WorldDeform("ground_reaction", 300, 900, 65, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.03, 14), UltraEventElement.DeformMode.PULSE,
                ScalarTrack.of(Keyframe.at(0, 2.0), Keyframe.at(180, 14.0, Easing.EASE_OUT_CUBIC), Keyframe.at(600, 22.0)),
                ScalarTrack.constant(2.8), ScalarTrack.constant(1.4), ScalarTrack.constant(1.0), null,
                ColorTrack.constant(0xFF45DAFF), true, Map.of()));
        b.add(new UltraEventElement.WorldDeform("ground_fissure", 660, d, 66, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 14), UltraEventElement.DeformMode.FISSURE, ScalarTrack.constant(24),
                ScalarTrack.constant(4.2), ScalarTrack.constant(1),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(130, 1.0, Easing.EASE_OUT_CUBIC)), null,
                ColorTrack.constant(0xFFFF4FD7), true, Map.of()));

        b.add(new UltraEventElement.Fracture("hero_fracture", 720, 970, 85, ConflictPolicy.ALLOW, null,
                PremiumShowcase.MODEL, UltraEventElement.FractureMode.VORONOI, 1400, new Vec3d(0, 3.5, 14),
                AdvancedTransformTrack.identity(), MotionCurve.none(), new Vec3d(0, 0.55, 0.4),
                ScalarTrack.constant(3.4), ScalarTrack.constant(0.045), ScalarTrack.constant(0.982),
                ScalarTrack.constant(4.8), ColorTrack.constant(0xFFC9FAFF), ScalarTrack.constant(0.92),
                true, false, 520, Map.of("fracture_duration", "190")));

        PathTrack cameraPath = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-13, 6, -4), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(220, new Vec3d(-8, 9, 3), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(500, new Vec3d(10, 7, 2), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(760, new Vec3d(8, 4, 9), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(d, new Vec3d(-9, 6, 4)));
        b.add(new UltraEventElement.CameraRig("hero_camera", 0, d, 200, ConflictPolicy.REPLACE_LOWER,
                UltraEventElement.CameraRigMode.RAIL, cameraPath, new Vec3d(0, 3.0, 14), "ultra_sentinel",
                ScalarTrack.of(Keyframe.at(0, -2.0), Keyframe.at(500, 3.0), Keyframe.at(d, 0.0)),
                ScalarTrack.of(Keyframe.at(0, 68.0), Keyframe.at(500, 58.0), Keyframe.at(750, 74.0), Keyframe.at(d, 64.0)),
                ScalarTrack.constant(13), ScalarTrack.constant(4),
                ScalarTrack.of(Keyframe.at(0, 0.015), Keyframe.at(700, 0.03), Keyframe.at(730, 0.22), Keyframe.at(850, 0.03)),
                ScalarTrack.of(Keyframe.at(0, 0.15), Keyframe.at(730, 2.2), Keyframe.at(850, 0.2)),
                ScalarTrack.constant(1.7), true, Map.of()));

        b.add(new UltraEventElement.SpatialAudio("reactor_audio", 0, d, 50, ConflictPolicy.ALLOW,
                "ultra_sentinel", Identifier.ofVanilla("block.beacon.ambient"), new Vec3d(0, 2, 0),
                AdvancedTransformTrack.identity(), MotionCurve.none(), ScalarTrack.constant(0.65),
                ScalarTrack.of(Keyframe.at(0, 0.86), Keyframe.at(750, 1.25)), ScalarTrack.constant(42),
                ScalarTrack.constant(0.08), UltraEventElement.ReverbPreset.ARENA, ScalarTrack.constant(0.35),
                true, true, true, Map.of("category", "AMBIENT")));

        b.add(new UltraEventElement.EditorMarker("marker_intro", 80, 81, 0, ConflictPolicy.ALLOW,
                "LIGHT RIG ONLINE", 0xFF66EFFF, Map.of("section", "lighting")));
        b.add(new UltraEventElement.EditorMarker("marker_ik", 260, 261, 0, ConflictPolicy.ALLOW,
                "IK + SCAN", 0xFFFF6CE8, Map.of("section", "rig")));
        b.add(new UltraEventElement.EditorMarker("marker_fracture", 720, 721, 0, ConflictPolicy.ALLOW,
                "FRACTURE", 0xFFFFA45B, Map.of("section", "destruction")));

        return b.build();
    }

    private static SceneDefinition overloadScene() {
        double d = 360;
        SceneBuilder b = SceneBuilder.create(OVERLOAD).duration(d).priority(240)
                .meta("showcase", "ultra-overload").meta("branch", "signal.overload");
        b.add(new UltraEventElement.PostProcess("overload_post", 0, d, 250, ConflictPolicy.REPLACE_LOWER,
                Map.ofEntries(
                        Map.entry(UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(0.8)),
                        Map.entry(UltraEventElement.PostEffect.CHROMATIC_ABERRATION, ScalarTrack.constant(0.65)),
                        Map.entry(UltraEventElement.PostEffect.GLITCH, ScalarTrack.constant(0.75)),
                        Map.entry(UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(0.55)),
                        Map.entry(UltraEventElement.PostEffect.FILM_GRAIN, ScalarTrack.constant(0.28)),
                        Map.entry(UltraEventElement.PostEffect.TRANSITION,
                                ScalarTrack.of(Keyframe.at(0, 0.35), Keyframe.at(18, 0.0), Keyframe.at(315, 0.0), Keyframe.at(d, 1.0)))),
                ColorTrack.constant(0x18FF203F), ScalarTrack.constant(8), ScalarTrack.constant(2.5), Map.of()));
        b.add(new UltraEventElement.LightRig("overload_lights", 0, d, 200, ConflictPolicy.ALLOW, null,
                new Vec3d(0, 4, 14), AdvancedTransformTrack.identity(), MotionCurve.none(), List.of(
                new UltraEventElement.RigLight(UltraEventElement.LightKind.POINT, Vec3d.ZERO, new Vec3d(0, -1, 0),
                        ColorTrack.constant(0xFFFF274E), ScalarTrack.constant(9), ScalarTrack.constant(35),
                        ScalarTrack.constant(0), ScalarTrack.constant(180), true, ScalarTrack.constant(1.0))),
                ScalarTrack.of(Keyframe.at(0, 1.0), Keyframe.at(30, 0.2), Keyframe.at(55, 1.0), Keyframe.at(80, 0.1), Keyframe.at(d, 1.0)),
                520, Map.of()));
        b.add(new UltraEventElement.Fracture("overload_fracture", 20, d, 180, ConflictPolicy.ALLOW, null,
                PremiumShowcase.MODEL, UltraEventElement.FractureMode.RADIAL, 3800, new Vec3d(0, 4, 14),
                AdvancedTransformTrack.identity(), MotionCurve.none(), new Vec3d(0, 0.35, 0), ScalarTrack.constant(7.5),
                ScalarTrack.constant(0.055), ScalarTrack.constant(0.974), ScalarTrack.constant(9),
                ColorTrack.constant(0xFFFFA4CB), ScalarTrack.constant(1), true, false, 620,
                Map.of("fracture_duration", "260")));
        b.add(new UltraEventElement.ParticleField("overload_particles", 0, d, 170, ConflictPolicy.ALLOW, null,
                Identifier.ofVanilla("electric_spark"), new Vec3d(0, 5, 14), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(4200), ScalarTrack.constant(46), ScalarTrack.constant(0.65), ScalarTrack.constant(1.2),
                ColorTrack.constant(0xFFFF74D5), List.of(
                new UltraEventElement.Force(UltraEventElement.ForceKind.EXPLOSION, Vec3d.ZERO, Vec3d.ZERO,
                        ScalarTrack.constant(6), ScalarTrack.constant(18), ScalarTrack.constant(1.4), 99),
                new UltraEventElement.Force(UltraEventElement.ForceKind.TURBULENCE, Vec3d.ZERO, Vec3d.ZERO,
                        ScalarTrack.constant(1.8), ScalarTrack.constant(30), ScalarTrack.constant(1), 101),
                new UltraEventElement.Force(UltraEventElement.ForceKind.DRAG, Vec3d.ZERO, Vec3d.ZERO,
                        ScalarTrack.constant(0.5), ScalarTrack.constant(100), ScalarTrack.constant(1), 102)),
                90000, true, true, 620, Map.of("spawn_radius", "5")));
        b.add(new UltraEventElement.WorldDeform("overload_fissure", 0, d, 160, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 14), UltraEventElement.DeformMode.FISSURE, ScalarTrack.constant(38),
                ScalarTrack.constant(8), ScalarTrack.constant(1.2),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(90, 1.0, Easing.EASE_OUT_CUBIC)), null,
                ColorTrack.constant(0xFFFF2D63), true, Map.of()));
        b.add(new UltraEventElement.PortalSurface("overload_portal", 0, d, 150, ConflictPolicy.ALLOW, null,
                UltraEventElement.PortalMode.KALEIDOSCOPE, new Vec3d(0, 9, 28), AdvancedTransformTrack.identity(),
                MotionCurve.none(), new Vec3d(16, 18, 0.2), SCENE, Vec3d.ZERO, ScalarTrack.constant(1),
                ColorTrack.constant(0xFFFF398E), ScalarTrack.constant(1.2), 3, 620, Map.of()));
        b.add(new UltraEventElement.EditorMarker("overload_marker", 20, 21, 0, ConflictPolicy.ALLOW,
                "OVERLOAD BRANCH", 0xFFFF456E, Map.of("signal", "overload")));
        return b.build();
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
