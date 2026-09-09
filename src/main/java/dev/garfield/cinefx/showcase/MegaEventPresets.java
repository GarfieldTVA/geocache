package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransformTrack;
import dev.garfield.cinefx.api.CineFxApi;
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
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-form, Fortnite-style reference events. Each preset is at least five minutes at 20 TPS and
 * intentionally alternates player-controlled spectacle with short cinematic camera beats.
 */
public final class MegaEventPresets {
    public static final Identifier ZEROPOINT = id("mega/zeropoint_collapse");
    public static final Identifier INVASION = id("mega/starfall_invasion");
    public static final Identifier TITAN = id("mega/titan_reckoning");
    public static final Identifier NEXUS = id("mega/chrono_nexus");

    private static final LinkedHashMap<String, Identifier> CATALOG = new LinkedHashMap<>();
    private static boolean registered;

    private MegaEventPresets() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PremiumShowcase.register();
        register("event_zeropoint", zeroPointCollapse());
        register("event_invasion", starfallInvasion());
        register("event_titan", titanReckoning());
        register("event_nexus", chronoNexus());
    }

    public static Map<String, Identifier> catalog() { return Map.copyOf(CATALOG); }

    /** 6:00 — the island cracks open, the Zero Point rises, realities collide, then the arena rebuilds. */
    private static SceneDefinition zeroPointCollapse() {
        double d = 7200.0;
        SceneBuilder b = base(ZEROPOINT, d, "zero-point-collapse");
        b.add(atmosphere("zp_atmosphere", 0, d, 0xFF6EA4C8, 0xFF8DB0BE, 0.018, 0.18));
        b.add(sky("zp_sky", 0, d, 0xFF8DBFE8, 0xFF153761, 0.05, 0.08));
        b.add(post("zp_post", 0, d, 0x08008CFF, 0.18, 0.16));
        b.add(audio("zp_ambience", 0, d, "block.beacon.ambient", 0.42, 0.82));

        addArena(b, "zp_arena", 0, d, Blocks.SMOOTH_STONE.getDefaultState(), 13, 2.6, 0xFF74D8FF);
        addMovingCrowd(b, "zp_civilians", 100, 5900, Identifier.ofVanilla("villager"), 56, 21, 18,
                "wander", 4.0, 0.030, "walk");

        b.add(worldText("zp_warning", 120, 880, "ANOMALY DETECTED", new Vec3d(0, 8.5, 13), 1.45, 0xFFB9F4FF));
        b.add(cameraRig("zp_intro_cam", 0, 300,
                path(new Vec3d(-15, 7, -9), new Vec3d(-10, 9, 4), new Vec3d(8, 7, 2), new Vec3d(12, 5, 10), 300),
                new Vec3d(0, 3.5, 15), 68, 58, 0.018, 0.12));
        b.add(control("zp_intro_control", 0, 300));

        b.add(new UltraEventElement.WorldDeform("zp_first_pulse", 650, 1800, 110, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.03, 16), UltraEventElement.DeformMode.PULSE,
                ScalarTrack.of(Keyframe.at(0, 1.0), Keyframe.at(500, 22.0, Easing.EASE_OUT_CUBIC), Keyframe.at(1150, 35.0)),
                ScalarTrack.constant(2.4), ScalarTrack.constant(1.0), ScalarTrack.constant(1.0), null,
                ColorTrack.constant(0xFF54E6FF), true, Map.of()));
        b.add(new UltraEventElement.ParticleField("zp_energy_field", 650, 6200, 80, ConflictPolicy.ALLOW, null,
                Identifier.ofVanilla("electric_spark"), new Vec3d(0, 2.8, 16), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0, 120.0), Keyframe.at(1000, 900.0), Keyframe.at(3600, 1800.0), Keyframe.at(5550, 600.0)),
                ScalarTrack.constant(70.0), ScalarTrack.constant(0.34), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFF73EDFF), List.of(
                force(UltraEventElement.ForceKind.VORTEX, 3.0, 16.0, 11),
                force(UltraEventElement.ForceKind.ATTRACTOR, 1.6, 12.0, 22),
                force(UltraEventElement.ForceKind.TURBULENCE, 0.7, 20.0, 33)),
                70000, true, true, 420, Map.of("spawn_radius", "4.5")));

        b.add(new ComplexElement.Node("zp_core_root", 900, 6500, 100, ConflictPolicy.ALLOW, null,
                new Vec3d(0, -7.5, 16), AdvancedTransformTrack.identity(), riseCurve(1100, 1450, 11.0), true));
        b.add(new ComplexElement.Mesh("zp_core", 900, 6500, 105, ConflictPolicy.ALLOW, "zp_core_root",
                PremiumShowcase.MODEL, null, Vec3d.ZERO,
                transformScale(new Vec3d(3.3, 3.3, 3.3), 0, 180), MotionCurve.none(),
                ColorTrack.constant(0xFFBDF9FF), ScalarTrack.constant(1.0), ScalarTrack.constant(0.7), true, 700,
                Map.of("fallback_shape", "crystal")));
        b.add(lightRig("zp_core_lights", 900, 6500, "zp_core_root", 0xFF56E7FF, 7.5, 34, 0.85));
        b.add(attachEmitter("zp_core_sparks", 900, 6500, "zp_core_root", Vec3d.ZERO, "end_rod", 420, 3.2, 0.45));

        for (int i = 0; i < 4; i++) {
            double start = 1750 + i * 620.0;
            double angle = Math.PI * 2.0 * i / 4.0;
            Vec3d p = new Vec3d(Math.cos(angle) * 20, 7 + i * 1.3, 16 + Math.sin(angle) * 20);
            b.add(portal("zp_rift_" + i, start, 5700, p, new Vec3d(7, 10, 0.2),
                    (i & 1) == 0 ? 0xFFFF57DB : 0xFF64E9FF, 0.75 + i * 0.08));
            b.add(ring("zp_rift_wave_" + i, start, start + 420, new Vec3d(p.x, 0.08, p.z), 0.5, 24, 0xFF90F4FF));
        }

        addRealitySpikes(b, "zp_spike", 2200, 6000, 28, 18, Blocks.AMETHYST_BLOCK.getDefaultState());
        b.add(cameraRig("zp_mid_cam", 3250, 3540,
                path(new Vec3d(-24, 12, 20), new Vec3d(-9, 8, 32), new Vec3d(11, 11, 30), new Vec3d(22, 7, 18), 290),
                new Vec3d(0, 4, 16), 66, 54, 0.025, 0.18));
        b.add(control("zp_mid_control", 3250, 3540));

        PathTrack guardians = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-22, 0, 6)),
                PathTrack.Point.at(900, new Vec3d(-8, 0, 18)),
                PathTrack.Point.at(1800, new Vec3d(9, 0, 12)),
                PathTrack.Point.at(2800, new Vec3d(18, 0, 25)),
                PathTrack.Point.at(3800, new Vec3d(0, 0, 17)));
        b.add(new ComplexElement.Node("zp_guardian_path", 2600, 6400, 70, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), guardians.asMotionCurve(true, 0), true));
        b.add(actor("zp_guardian", 2600, 6400, "zp_guardian_path", Identifier.ofVanilla("iron_golem"), Vec3d.ZERO,
                new Vec3d(2.4, 2.4, 2.4), "run", Map.of()));

        b.add(new UltraEventElement.WorldDeform("zp_fissure", 4450, 6200, 130, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 16), UltraEventElement.DeformMode.FISSURE,
                ScalarTrack.constant(44.0), ScalarTrack.constant(7.0), ScalarTrack.constant(1.0),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(700, 1.0, Easing.EASE_OUT_CUBIC)), null,
                ColorTrack.constant(0xFFFF55DE), true, Map.of()));
        b.add(new UltraEventElement.Fracture("zp_core_fracture", 5550, 6250, 160, ConflictPolicy.ALLOW, "zp_core_root",
                PremiumShowcase.MODEL, UltraEventElement.FractureMode.RADIAL, 3200, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), MotionCurve.none(), new Vec3d(0, 0.8, 0),
                ScalarTrack.constant(6.0), ScalarTrack.constant(0.042), ScalarTrack.constant(0.98), ScalarTrack.constant(7.0),
                ColorTrack.constant(0xFFD9FBFF), ScalarTrack.constant(1.0), true, false, 720,
                Map.of("fracture_duration", "450")));
        b.add(cameraRig("zp_final_cam", 5480, 6260,
                path(new Vec3d(-20, 8, 5), new Vec3d(-7, 16, 10), new Vec3d(6, 20, 22), new Vec3d(23, 11, 19), 780),
                new Vec3d(0, 5, 16), 62, 78, 0.08, 0.55));
        b.add(control("zp_final_control", 5480, 6260));
        b.add(new UltraEventElement.WorldDeform("zp_rebuild", 6250, d, 150, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 16), UltraEventElement.DeformMode.REBUILD,
                ScalarTrack.of(Keyframe.at(0, 4.0), Keyframe.at(800, 36.0)), ScalarTrack.constant(4.0),
                ScalarTrack.constant(1.0), ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(850, 1.0)),
                null, ColorTrack.constant(0xFF7DE7A0), true, Map.of()));
        b.add(worldText("zp_complete", 6700, d, "REALITY STABILIZED", new Vec3d(0, 8.5, 13), 1.6, 0xFFA9FFD0));
        return b.build();
    }

    /** 5:30 — an alien fleet arrives, ground forces evacuate, the mothership fires and is destroyed. */
    private static SceneDefinition starfallInvasion() {
        double d = 6600.0;
        SceneBuilder b = base(INVASION, d, "starfall-invasion");
        b.add(atmosphere("inv_atmosphere", 0, d, 0xFF6C83A5, 0xFF4E5D71, 0.026, 0.35));
        b.add(sky("inv_sky", 0, d, 0xFF758CB8, 0xFF09152D, 0.18, 0.28));
        b.add(post("inv_post", 0, d, 0x08153B78, 0.14, 0.25));
        b.add(audio("inv_hum", 0, d, "block.beacon.ambient", 0.38, 0.72));
        addArena(b, "inv_base", 0, d, Blocks.STONE_BRICKS.getDefaultState(), 15, 2.8, 0xFF6EA8FF);
        addMovingCrowd(b, "inv_evacuating", 500, 4900, Identifier.ofVanilla("villager"), 72, 27, 18,
                "panic", 7.0, 0.065, "panic");

        PathTrack shipPath = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-130, 70, -180)),
                PathTrack.Point.at(1200, new Vec3d(-55, 56, -75)),
                PathTrack.Point.at(2800, new Vec3d(12, 45, -25)),
                PathTrack.Point.at(4800, new Vec3d(5, 39, 20)),
                PathTrack.Point.at(6100, new Vec3d(0, 34, 22)));
        b.add(new ComplexElement.Node("inv_mothership", 0, 6250, 100, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), shipPath.asMotionCurve(true, -7), true));
        b.add(new ComplexElement.Mesh("inv_mothership_mesh", 0, 6250, 105, ConflictPolicy.ALLOW, "inv_mothership",
                id("model/mothership"), null, Vec3d.ZERO, transformScale(new Vec3d(13, 4.5, 26), 0, 0), MotionCurve.none(),
                ColorTrack.constant(0xFFD4E8FF), ScalarTrack.constant(1.0), ScalarTrack.constant(0.22), true, 1000,
                Map.of("fallback_shape", "ship")));
        b.add(attachLight("inv_engine_l", 0, 6250, "inv_mothership", new Vec3d(-5.5, 0, -9), 0xFF58D9FF, 6.5, 32));
        b.add(attachLight("inv_engine_r", 0, 6250, "inv_mothership", new Vec3d(5.5, 0, -9), 0xFF58D9FF, 6.5, 32));
        b.add(attachEmitter("inv_exhaust", 0, 6250, "inv_mothership", new Vec3d(0, 0, -10), "end_rod", 520, 2.5, 0.7));

        b.add(cameraRig("inv_arrival_cam", 0, 360,
                path(new Vec3d(-18, 7, -7), new Vec3d(-12, 10, 8), new Vec3d(6, 12, 12), new Vec3d(15, 7, 20), 360),
                new Vec3d(0, 28, -15), 70, 56, 0.02, 0.08));
        b.add(control("inv_arrival_control", 0, 360));
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2.0 * i / 7.0;
            Vec3d pos = new Vec3d(Math.cos(a) * 30, 24 + (i % 3) * 5, 12 + Math.sin(a) * 30);
            b.add(portal("inv_drop_rift_" + i, 1200 + i * 110, 4200, pos, new Vec3d(5, 7, 0.2), 0xFF7F7CFF, 0.55));
        }
        b.add(new UltraEventElement.ParticleField("inv_meteor_storm", 1550, 4800, 82, ConflictPolicy.ALLOW, null,
                Identifier.ofVanilla("ash"), new Vec3d(0, 28, 15), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0, 120.0), Keyframe.at(900, 1600.0), Keyframe.at(3000, 500.0)),
                ScalarTrack.constant(55.0), ScalarTrack.constant(0.75), ScalarTrack.constant(1.2),
                ColorTrack.constant(0xFFFFA56D), List.of(
                directional(new Vec3d(0.35, -1.0, 0.12), 2.2, 80.0, 100),
                force(UltraEventElement.ForceKind.TURBULENCE, 0.45, 40.0, 101)),
                85000, true, true, 600, Map.of("spawn_radius", "35")));

        addMovingCrowd(b, "inv_raiders", 1800, 5600, Identifier.ofVanilla("pillager"), 48, 23, 24,
                "march", 10.0, 0.055, "run");
        b.add(new UltraEventElement.WorldDeform("inv_impacts", 1900, 5100, 125, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 18), UltraEventElement.DeformMode.CRACK,
                ScalarTrack.of(Keyframe.at(0, 4.0), Keyframe.at(1900, 35.0)), ScalarTrack.constant(4.0),
                ScalarTrack.constant(1.8), ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(1200, 1.0)), null,
                ColorTrack.constant(0xFFFF955C), true, Map.of()));
        b.add(attachBeam("inv_main_beam", 3650, 4550, "inv_mothership", new Vec3d(0, -3, 3),
                new Vec3d(0, -72, 42), 1.3, 0xFF7AEEFF));
        b.add(ring("inv_beam_impact", 3820, 4750, new Vec3d(0, 0.08, 34), 1.0, 56.0, 0xFFB2F8FF));
        b.add(cameraRig("inv_beam_cam", 3600, 4240,
                path(new Vec3d(-25, 9, 32), new Vec3d(-9, 15, 25), new Vec3d(7, 11, 34), new Vec3d(23, 8, 20), 640),
                new Vec3d(0, 18, 20), 68, 60, 0.05, 0.35));
        b.add(control("inv_beam_control", 3600, 4240));

        b.add(new UltraEventElement.Fracture("inv_ship_break", 5350, 6250, 170, ConflictPolicy.ALLOW, "inv_mothership",
                id("model/mothership"), UltraEventElement.FractureMode.DIRECTIONAL, 5200, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), MotionCurve.none(), new Vec3d(0.4, 0.35, -0.8),
                ScalarTrack.constant(8.0), ScalarTrack.constant(0.035), ScalarTrack.constant(0.976), ScalarTrack.constant(10.0),
                ColorTrack.constant(0xFFFFD5B4), ScalarTrack.constant(1.0), true, false, 1100,
                Map.of("fracture_duration", "720")));
        b.add(cameraRig("inv_final_cam", 5350, 6250,
                path(new Vec3d(-30, 12, 5), new Vec3d(-12, 25, 12), new Vec3d(14, 29, 18), new Vec3d(28, 16, 26), 900),
                new Vec3d(0, 34, 20), 64, 82, 0.10, 0.7));
        b.add(control("inv_final_control", 5350, 6250));
        b.add(new UltraEventElement.WorldDeform("inv_aftershock", 5850, d, 140, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 18), UltraEventElement.DeformMode.REBUILD,
                ScalarTrack.of(Keyframe.at(0, 5.0), Keyframe.at(700, 42.0)), ScalarTrack.constant(3.2), ScalarTrack.constant(1.0),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(700, 1.0)), null, ColorTrack.constant(0xFF91C8FF), true, Map.of()));
        b.add(worldText("inv_victory", 6300, d, "STARFALL REPULSED", new Vec3d(0, 9, 15), 1.6, 0xFFC7F5FF));
        return b.build();
    }

    /** 5:50 — a giant titan rises, walks through the arena, fights the sky, then petrifies and collapses. */
    private static SceneDefinition titanReckoning() {
        double d = 7000.0;
        SceneBuilder b = base(TITAN, d, "titan-reckoning");
        b.add(atmosphere("tr_atmosphere", 0, d, 0xFFD3A96E, 0xFFA27042, 0.045, 0.62));
        b.add(sky("tr_sky", 0, d, 0xFFE2AE66, 0xFF43241D, 0.12, 0.0));
        b.add(post("tr_post", 0, d, 0x08FF8D42, 0.13, 0.24));
        b.add(audio("tr_rumble", 0, d, "block.portal.ambient", 0.34, 0.62));
        addArena(b, "tr_ruins", 0, d, Blocks.POLISHED_ANDESITE.getDefaultState(), 14, 3.0, 0xFFFFB870);

        PathTrack titanPath = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(0, -13, 38)),
                PathTrack.Point.at(900, new Vec3d(0, 0, 35)),
                PathTrack.Point.at(2500, new Vec3d(-10, 0, 24)),
                PathTrack.Point.at(4300, new Vec3d(10, 0, 16)),
                PathTrack.Point.at(5700, new Vec3d(1, 0, 11)),
                PathTrack.Point.at(6700, new Vec3d(0, -6, 13)));
        b.add(new ComplexElement.Node("tr_titan_path", 300, 6900, 100, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), titanPath.asMotionCurve(true, 0), true));
        b.add(actor("tr_titan", 300, 6900, "tr_titan_path", Identifier.ofVanilla("iron_golem"), Vec3d.ZERO,
                new Vec3d(7.5, 7.5, 7.5), "walk", Map.of()));
        b.add(new ComplexElement.Shadow("tr_shadow", 300, 6900, 40, ConflictPolicy.ALLOW, "tr_titan_path",
                ComplexElement.ShadowMode.GEOMETRY, null, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(0.58), ScalarTrack.constant(0.75), ScalarTrack.constant(9.0), 700));
        b.add(attachEmitter("tr_dust", 300, 6900, "tr_titan_path", new Vec3d(0, 0.4, 0), "ash", 500, 7, 0.5));
        b.add(lightRig("tr_titan_light", 700, 6200, "tr_titan_path", 0xFFFF9C4A, 4.0, 24, 0.38));

        b.add(cameraRig("tr_wakeup_cam", 220, 820,
                path(new Vec3d(-18, 5, 4), new Vec3d(-11, 10, 18), new Vec3d(8, 14, 24), new Vec3d(20, 7, 15), 600),
                new Vec3d(0, 14, 34), 70, 58, 0.03, 0.30));
        b.add(control("tr_wakeup_control", 220, 820));
        b.add(new UltraEventElement.WorldDeform("tr_awakening", 300, 1800, 120, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 36), UltraEventElement.DeformMode.FISSURE,
                ScalarTrack.of(Keyframe.at(0, 3.0), Keyframe.at(900, 34.0)), ScalarTrack.constant(6.0), ScalarTrack.constant(1.0),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(1000, 1.0)), null,
                ColorTrack.constant(0xFFFFA45D), true, Map.of()));
        addMovingCrowd(b, "tr_fleeing", 800, 5000, Identifier.ofVanilla("villager"), 64, 25, 18,
                "panic", 8.0, 0.07, "panic");

        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2.0 / 18.0;
            Vec3d p = new Vec3d(Math.cos(angle) * 17, 0.02, 18 + Math.sin(angle) * 17);
            b.add(growingBlock("tr_obelisk_" + i, 1450 + i * 28, 5600, Blocks.CUT_COPPER.getDefaultState(), p,
                    new Vec3d(1.0, 4.0 + (i % 5), 1.0), 160));
        }
        b.add(new UltraEventElement.ParticleField("tr_storm", 2200, 5800, 75, ConflictPolicy.ALLOW, null,
                Identifier.ofVanilla("ash"), new Vec3d(0, 18, 18), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.of(Keyframe.at(0, 220.0), Keyframe.at(1200, 1500.0), Keyframe.at(3300, 450.0)),
                ScalarTrack.constant(72.0), ScalarTrack.constant(0.5), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFFFFC085), List.of(
                force(UltraEventElement.ForceKind.VORTEX, 2.1, 30.0, 60),
                directional(new Vec3d(0.7, 0.0, 0.2), 0.9, 80.0, 61)),
                force(UltraEventElement.ForceKind.TURBULENCE, 0.6, 36.0, 62)),
                65000, true, true, 500, Map.of("spawn_radius", "28")));

        b.add(portal("tr_sky_rift", 3100, 5400, new Vec3d(0, 30, 9), new Vec3d(14, 9, 0.2), 0xFF65DFFF, 0.95));
        b.add(new AdvancedEventElement.Attachment("tr_titan_beam", 3450, 4650, 140, ConflictPolicy.ALLOW,
                "tr_titan_path", null, AdvancedEventElement.InheritMode.FULL, new Vec3d(0, 11, 0),
                AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.BeamPayload(Vec3d.ZERO, new Vec3d(0, 22, -20), ScalarTrack.constant(1.1),
                        ColorTrack.constant(0xFFFFAD56)), 900));
        b.add(cameraRig("tr_battle_cam", 3400, 4100,
                path(new Vec3d(-22, 8, 13), new Vec3d(-8, 18, 26), new Vec3d(10, 20, 20), new Vec3d(25, 10, 10), 700),
                new Vec3d(0, 14, 20), 66, 60, 0.07, 0.48));
        b.add(control("tr_battle_control", 3400, 4100));

        b.add(new UltraEventElement.MaterialEffect("tr_petrify", 5100, 6400, 160, ConflictPolicy.ALLOW,
                "tr_titan", UltraEventElement.MaterialMode.FREEZE,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(950, 1.0, Easing.EASE_IN_OUT_SINE)),
                ScalarTrack.constant(0.09), ColorTrack.constant(0xFFBFC8D1), ScalarTrack.constant(1.5),
                ScalarTrack.constant(0.4), new Vec3d(0, 1, 0), Map.of()));
        b.add(new UltraEventElement.Fracture("tr_titan_collapse", 6150, 6900, 170, ConflictPolicy.ALLOW,
                "tr_titan_path", id("model/titan_proxy"), UltraEventElement.FractureMode.VORONOI, 4400, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), MotionCurve.none(), new Vec3d(0.25, 0.15, 0.3),
                ScalarTrack.constant(4.8), ScalarTrack.constant(0.05), ScalarTrack.constant(0.98), ScalarTrack.constant(5.0),
                ColorTrack.constant(0xFFC5B39A), ScalarTrack.constant(1.0), true, false, 900,
                Map.of("fracture_duration", "620")));
        b.add(cameraRig("tr_collapse_cam", 6100, 6900,
                path(new Vec3d(-18, 6, -2), new Vec3d(-8, 13, 8), new Vec3d(9, 17, 18), new Vec3d(22, 7, 13), 800),
                new Vec3d(0, 10, 13), 64, 76, 0.11, 0.8));
        b.add(control("tr_collapse_control", 6100, 6900));
        b.add(worldText("tr_end", 6750, d, "THE TITAN SLEEPS", new Vec3d(0, 8, 13), 1.6, 0xFFFFD7A3));
        return b.build();
    }

    /** 6:30 — reality cycles through void, ice, desert and neon before the world rewinds and recombines. */
    private static SceneDefinition chronoNexus() {
        double d = 7800.0;
        SceneBuilder b = base(NEXUS, d, "chrono-nexus");
        b.add(atmosphere("nx_atmosphere", 0, d, 0xFF7A9AC0, 0xFF7796A8, 0.022, 0.25));
        b.add(sky("nx_sky", 0, d, 0xFF759FE0, 0xFF0D2148, 0.20, 0.42));
        b.add(post("nx_post", 0, d, 0x08136AFF, 0.19, 0.24));
        b.add(audio("nx_audio", 0, d, "block.amethyst_block.resonate", 0.40, 0.92));
        addArena(b, "nx_platform", 0, d, Blocks.QUARTZ_BLOCK.getDefaultState(), 14, 2.7, 0xFF78EFFF);
        b.add(worldText("nx_title", 80, 780, "CHRONO NEXUS // SYNCHRONIZING", new Vec3d(0, 8, 14), 1.35, 0xFFA9F7FF));

        b.add(portal("nx_center", 100, 7450, new Vec3d(0, 9, 23), new Vec3d(12, 15, 0.2), 0xFF65EAFF, 0.8));
        b.add(cameraRig("nx_intro_cam", 0, 320,
                path(new Vec3d(-16, 6, -7), new Vec3d(-9, 11, 9), new Vec3d(8, 13, 13), new Vec3d(16, 8, 20), 320),
                new Vec3d(0, 7, 22), 68, 57, 0.02, 0.10));
        b.add(control("nx_intro_control", 0, 320));

        // Four reality phases. Each visually changes the arena without touching authoritative world blocks.
        realityPhase(b, "void", 900, 2350, 0xFFFF62ED, Blocks.OBSIDIAN.getDefaultState(),
                UltraEventElement.DeformMode.SINK, Identifier.ofVanilla("portal"));
        realityPhase(b, "ice", 2350, 3800, 0xFF9FEAFF, Blocks.PACKED_ICE.getDefaultState(),
                UltraEventElement.DeformMode.GROW, Identifier.ofVanilla("snowflake"));
        realityPhase(b, "desert", 3800, 5250, 0xFFFFB66F, Blocks.SANDSTONE.getDefaultState(),
                UltraEventElement.DeformMode.WAVE, Identifier.ofVanilla("ash"));
        realityPhase(b, "neon", 5250, 6700, 0xFF62F3FF, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(),
                UltraEventElement.DeformMode.PULSE, Identifier.ofVanilla("electric_spark"));

        addMovingCrowd(b, "nx_travelers", 800, 6900, Identifier.ofVanilla("villager"), 52, 22, 18,
                "orbit", 5.5, 0.024, "walk");
        PathTrack chronoGuardPath = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-18, 0, 9)),
                PathTrack.Point.at(1300, new Vec3d(-7, 0, 20)),
                PathTrack.Point.at(2600, new Vec3d(12, 0, 23)),
                PathTrack.Point.at(3900, new Vec3d(17, 0, 10)),
                PathTrack.Point.at(5200, new Vec3d(0, 0, 16)));
        b.add(new ComplexElement.Node("nx_guard_path", 1100, 6500, 80, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), chronoGuardPath.asMotionCurve(true, 0), true));
        b.add(actor("nx_guard", 1100, 6500, "nx_guard_path", Identifier.ofVanilla("allay"), Vec3d.ZERO,
                new Vec3d(3.2, 3.2, 3.2), "idle", Map.of()));

        for (int i = 0; i < 6; i++) {
            double start = 1350 + i * 820.0;
            double angle = i * Math.PI * 2.0 / 6.0;
            Vec3d p = new Vec3d(Math.cos(angle) * 22, 8 + (i % 2) * 5, 18 + Math.sin(angle) * 22);
            b.add(portal("nx_satellite_" + i, start, Math.min(d, start + 2300), p,
                    new Vec3d(5.5, 8, 0.2), i % 2 == 0 ? 0xFFFF64D8 : 0xFF65EFFF, 0.7));
        }

        b.add(cameraRig("nx_phase_cam_a", 2250, 2520,
                path(new Vec3d(-24, 9, 19), new Vec3d(-8, 13, 31), new Vec3d(10, 11, 29), new Vec3d(22, 8, 15), 270),
                new Vec3d(0, 5, 18), 64, 56, 0.02, 0.15));
        b.add(control("nx_phase_control_a", 2250, 2520));
        b.add(cameraRig("nx_phase_cam_b", 5150, 5440,
                path(new Vec3d(22, 10, 12), new Vec3d(8, 16, 27), new Vec3d(-10, 13, 31), new Vec3d(-23, 8, 18), 290),
                new Vec3d(0, 7, 20), 64, 59, 0.025, 0.18));
        b.add(control("nx_phase_control_b", 5150, 5440));

        b.add(new UltraEventElement.WorldDeform("nx_rewind", 6700, 7600, 180, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 18), UltraEventElement.DeformMode.REBUILD,
                ScalarTrack.of(Keyframe.at(0, 5.0), Keyframe.at(700, 48.0)), ScalarTrack.constant(6.0),
                ScalarTrack.constant(2.0), ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(600, 1.0)),
                null, ColorTrack.constant(0xFFFFFFFF), true, Map.of("direction", "reverse")));
        b.add(new UltraEventElement.ParticleField("nx_rewind_particles", 6700, 7600, 160, ConflictPolicy.ALLOW, null,
                Identifier.ofVanilla("end_rod"), new Vec3d(0, 8, 18), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(2200.0), ScalarTrack.constant(80.0), ScalarTrack.constant(0.65), ScalarTrack.constant(1.0),
                ColorTrack.constant(0xFFD8FBFF), List.of(
                force(UltraEventElement.ForceKind.ATTRACTOR, 3.4, 40.0, 300),
                force(UltraEventElement.ForceKind.VORTEX, -2.5, 32.0, 301)),
                95000, true, true, 600, Map.of("spawn_radius", "32")));
        b.add(cameraRig("nx_final_cam", 6700, 7600,
                path(new Vec3d(-27, 12, 1), new Vec3d(-11, 22, 12), new Vec3d(11, 25, 25), new Vec3d(28, 13, 18), 900),
                new Vec3d(0, 8, 20), 62, 80, 0.075, 0.58));
        b.add(control("nx_final_control", 6700, 7600));
        b.add(worldText("nx_complete", 7480, d, "TIMELINE MERGED", new Vec3d(0, 8, 14), 1.65, 0xFFD8FFFF));
        return b.build();
    }

    private static void realityPhase(SceneBuilder b, String name, double start, double end, int color,
                                     BlockState block, UltraEventElement.DeformMode deform, Identifier particle) {
        b.add(new UltraEventElement.WorldDeform("nx_" + name + "_deform", start, end, 105, ConflictPolicy.ALLOW,
                new Vec3d(0, 0.02, 18), deform,
                ScalarTrack.of(Keyframe.at(0, 3.0), Keyframe.at(350, 30.0)), ScalarTrack.constant(4.2),
                ScalarTrack.constant(1.2), ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(400, 1.0)), null,
                ColorTrack.constant(color), true, Map.of()));
        b.add(new UltraEventElement.ParticleField("nx_" + name + "_field", start, end, 75, ConflictPolicy.ALLOW, null,
                particle, new Vec3d(0, 10, 18), AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(850.0), ScalarTrack.constant(65.0), ScalarTrack.constant(0.38), ScalarTrack.constant(1.0),
                ColorTrack.constant(color), List.of(force(UltraEventElement.ForceKind.VORTEX, 1.4, 28.0, name.hashCode())),
                50000, true, true, 500, Map.of("spawn_radius", "25")));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0 / 16.0;
            Vec3d pos = new Vec3d(Math.cos(a) * (10 + i % 4), 0, 18 + Math.sin(a) * (10 + i % 4));
            b.add(growingBlock("nx_" + name + "_pillar_" + i, start + 20 + i * 9, end, block, pos,
                    new Vec3d(0.75, 2.5 + i % 5, 0.75), 150));
        }
    }

    private static void addArena(SceneBuilder b, String prefix, double start, double end, BlockState state,
                                 int radius, double pillarHeight, int ringColor) {
        int index = 0;
        for (int x = -radius; x <= radius; x += 3) {
            for (int z = -radius; z <= radius; z += 3) {
                if (x * x + z * z > radius * radius) continue;
                if (((x + z) & 3) != 0) continue;
                b.add(growingBlock(prefix + "_floor_" + index++, start, end, state,
                        new Vec3d(x, -0.01, 18 + z), new Vec3d(1.0, 0.12, 1.0), 80));
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0 / 16.0;
            Vec3d p = new Vec3d(Math.cos(a) * radius, 0, 18 + Math.sin(a) * radius);
            b.add(growingBlock(prefix + "_pillar_" + i, start + i * 5, end, state, p,
                    new Vec3d(0.8, pillarHeight + (i % 4) * 0.35, 0.8), 120));
        }
        b.add(ring(prefix + "_ring", start, end, new Vec3d(0, 0.08, 18), radius - 1.0, radius - 0.5, ringColor));
    }

    private static void addRealitySpikes(SceneBuilder b, String prefix, double start, double end,
                                         int count, double radius, BlockState state) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297;
            double r = 4.0 + radius * Math.sqrt((i + 0.5) / count);
            Vec3d p = new Vec3d(Math.cos(a) * r, 0, 16 + Math.sin(a) * r);
            b.add(growingBlock(prefix + '_' + i, start + i * 11, end, state, p,
                    new Vec3d(0.7, 2.2 + (i % 7) * 0.65, 0.7), 180));
        }
    }

    private static void addMovingCrowd(SceneBuilder b, String key, double start, double end, Identifier entityType,
                                       int count, double radius, double zCenter, String locomotion,
                                       double moveRadius, double moveSpeed, String clip) {
        ArrayList<AdvancedEventElement.CrowdAgent> agents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297;
            double r = 3.0 + radius * Math.sqrt((i + 0.5) / count);
            agents.add(new AdvancedEventElement.CrowdAgent(
                    new Vec3d(Math.cos(angle) * r, 0, zCenter + Math.sin(angle) * r),
                    Math.toDegrees(-angle), i * 3.0, i % 8));
        }
        b.add(new AdvancedEventElement.Crowd(key, start, end, 40, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.ENTITY, entityType, "EventNPC", agents,
                List.of(animation(clip, clip.equals("run") || clip.equals("panic") ? 1.15 : 0.72)),
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF),
                ScalarTrack.constant(1.0), true, 650,
                Map.of("locomotion", locomotion, "move_radius", Double.toString(moveRadius),
                        "move_speed", Double.toString(moveSpeed))));
    }

    private static ComplexElement.Actor actor(String key, double start, double end, String parent, Identifier type,
                                              Vec3d offset, Vec3d scale, String clip, Map<String, String> appearance) {
        return new ComplexElement.Actor(key, start, end, 90, ConflictPolicy.ALLOW, parent,
                ComplexElement.ActorKind.ENTITY, type, null, null, appearance, offset,
                transformScale(scale, 0, 0), MotionCurve.none(), List.of(animation(clip, 1.0)), List.of(), List.of(),
                null, ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1.0), ScalarTrack.constant(0.0), true, 800);
    }

    private static ComplexElement.AnimationLayer animation(String clip, double speed) {
        return new ComplexElement.AnimationLayer(Identifier.of("cinefx", clip), ScalarTrack.constant(1.0),
                ScalarTrack.constant(speed), 0, true, ComplexElement.BlendMode.OVERRIDE, Map.of());
    }

    private static SceneBuilder base(Identifier id, double duration, String name) {
        return SceneBuilder.create(id).duration(duration).priority(180)
                .meta("showcase", "mega-event").meta("preset", name).meta("duration_ticks", Double.toString(duration));
    }

    private static void register(String name, SceneDefinition scene) {
        CineFxApi.register(scene);
        CATALOG.put(name, scene.id());
    }

    private static EventElement.Atmosphere atmosphere(String key, double start, double end, int sky, int fog,
                                                      double density, double wind) {
        return new EventElement.Atmosphere(key, start, end, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(sky), ColorTrack.constant(fog), ScalarTrack.constant(density),
                ScalarTrack.constant(0.0), ScalarTrack.constant(260.0), ScalarTrack.constant(0.5),
                ScalarTrack.constant(1.0), ScalarTrack.constant(wind));
    }

    private static AdvancedEventElement.Sky sky(String key, double start, double end, int horizon, int zenith,
                                                double eclipse, double aurora) {
        return new AdvancedEventElement.Sky(key, start, end, 20, ConflictPolicy.REPLACE_LOWER, null,
                ColorTrack.constant(horizon), ColorTrack.constant(zenith), ScalarTrack.constant(0.72),
                ScalarTrack.constant(1.15), ScalarTrack.constant(eclipse), ScalarTrack.constant(aurora),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.max(1.0, end - start), 38.0)), Map.of());
    }

    private static UltraEventElement.PostProcess post(String key, double start, double end, int tint,
                                                       double bloom, double vignette) {
        return new UltraEventElement.PostProcess(key, start, end, 185, ConflictPolicy.REPLACE_LOWER,
                Map.of(
                        UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(bloom),
                        UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(vignette),
                        UltraEventElement.PostEffect.DEPTH_OF_FIELD, ScalarTrack.constant(0.12),
                        UltraEventElement.PostEffect.CHROMATIC_ABERRATION, ScalarTrack.constant(0.025),
                        UltraEventElement.PostEffect.MOTION_BLUR, ScalarTrack.constant(0.035)),
                ColorTrack.constant(tint), ScalarTrack.constant(13.0), ScalarTrack.constant(5.0), Map.of());
    }

    private static AdvancedEventElement.AudioLayer audio(String key, double start, double end, String sound,
                                                         double volume, double pitch) {
        return new AdvancedEventElement.AudioLayer(key, start, end, 25, ConflictPolicy.ALLOW,
                Identifier.ofVanilla(sound), ScalarTrack.constant(volume), ScalarTrack.constant(pitch),
                ScalarTrack.constant(0.0), true, false, 80, 100, Map.of());
    }

    private static UltraEventElement.CameraRig cameraRig(String key, double start, double end, PathTrack path,
                                                         Vec3d lookAt, double fromFov, double toFov,
                                                         double shakeT, double shakeR) {
        return new UltraEventElement.CameraRig(key, start, end, 220, ConflictPolicy.REPLACE_LOWER,
                UltraEventElement.CameraRigMode.RAIL, path, lookAt, null,
                ScalarTrack.constant(0.0), ScalarTrack.of(Keyframe.at(0, fromFov), Keyframe.at(end - start, toFov)),
                ScalarTrack.constant(12.0), ScalarTrack.constant(4.0), ScalarTrack.constant(shakeT),
                ScalarTrack.constant(shakeR), ScalarTrack.constant(1.7), true,
                Map.of("show_hud", "false", "show_hand", "false"));
    }

    private static AdvancedEventElement.PlayerControl control(String key, double start, double end) {
        return new AdvancedEventElement.PlayerControl(key, start, end, 230, ConflictPolicy.REPLACE_LOWER,
                true, true, true, false, ScalarTrack.constant(0.0), ScalarTrack.constant(1.0),
                ScalarTrack.constant(-1.0), false, false);
    }

    private static PathTrack path(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double duration) {
        return PathTrack.catmullRom(PathTrack.Point.at(0, a, Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(duration * 0.33, b, Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(duration * 0.66, c, Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(duration, d));
    }

    private static UltraEventElement.PortalSurface portal(String key, double start, double end, Vec3d position,
                                                          Vec3d size, int color, double distortion) {
        return new UltraEventElement.PortalSurface(key, start, end, 90, ConflictPolicy.ALLOW, null,
                UltraEventElement.PortalMode.DIMENSION_VIEW, position, AdvancedTransformTrack.identity(), MotionCurve.none(),
                size, null, Vec3d.ZERO,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(100, 1.0, Easing.EASE_OUT_CUBIC)),
                ColorTrack.constant(color), ScalarTrack.constant(distortion), 2, 650,
                Map.of("premium", "render-target-eligible"));
    }

    private static UltraEventElement.LightRig lightRig(String key, double start, double end, String parent,
                                                       int color, double intensity, double radius, double volumetric) {
        return new UltraEventElement.LightRig(key, start, end, 120, ConflictPolicy.ALLOW, parent,
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), List.of(
                new UltraEventElement.RigLight(UltraEventElement.LightKind.POINT, Vec3d.ZERO, new Vec3d(0, -1, 0),
                        ColorTrack.constant(color), ScalarTrack.constant(intensity), ScalarTrack.constant(radius),
                        ScalarTrack.constant(0.0), ScalarTrack.constant(180.0), true, ScalarTrack.constant(volumetric))),
                ScalarTrack.constant(1.0), 800, Map.of());
    }

    private static UltraEventElement.Force force(UltraEventElement.ForceKind kind, double strength, double radius, long seed) {
        return new UltraEventElement.Force(kind, Vec3d.ZERO, new Vec3d(0, 1, 0), ScalarTrack.constant(strength),
                ScalarTrack.constant(radius), ScalarTrack.constant(1.2), seed);
    }

    private static UltraEventElement.Force directional(Vec3d direction, double strength, double radius, long seed) {
        return new UltraEventElement.Force(UltraEventElement.ForceKind.DIRECTIONAL, Vec3d.ZERO, direction,
                ScalarTrack.constant(strength), ScalarTrack.constant(radius), ScalarTrack.constant(1.0), seed);
    }

    private static SceneElement.Ring ring(String key, double start, double end, Vec3d center,
                                          double fromRadius, double toRadius, int color) {
        return new SceneElement.Ring(key, start, end, 70, ConflictPolicy.ALLOW, center,
                ScalarTrack.of(Keyframe.at(0, fromRadius, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(Math.max(1.0, end - start), toRadius)), ScalarTrack.constant(0.28), 80,
                TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(color));
    }

    private static SceneElement.Block growingBlock(String key, double start, double end, BlockState state,
                                                   Vec3d offset, Vec3d finalScale, double growTicks) {
        return new SceneElement.Block(key, start, end, 35, ConflictPolicy.ALLOW, state, null, offset,
                TransformTrack.of(
                        Keyframe.at(0, new Transform(Vec3d.ZERO, Vec3d.ZERO, new Vec3d(0.04, 0.04, 0.04)), Easing.EASE_OUT_BACK),
                        Keyframe.at(growTicks, new Transform(Vec3d.ZERO, Vec3d.ZERO, finalScale))),
                MotionCurve.none(), false, 0, false);
    }

    private static AdvancedTransformTrack transformScale(Vec3d scale, double yawStart, double yawEnd) {
        return new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                Vec3Track.angles(Keyframe.at(0, new Vec3d(0, yawStart, 0)), Keyframe.at(600, new Vec3d(0, yawEnd, 0))),
                Vec3Track.constant(scale), Vec3Track.constant(Vec3d.ZERO));
    }

    private static AdvancedEventElement.Attachment attachLight(String key, double start, double end, String parent,
                                                                Vec3d offset, int color, double intensity, double radius) {
        return new AdvancedEventElement.Attachment(key, start, end, 120, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, offset, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.LightPayload(SceneLight.Kind.POINT, new Vec3d(0, -1, 0), ColorTrack.constant(color),
                        ScalarTrack.constant(intensity), ScalarTrack.constant(radius), 0, 180), 1000);
    }

    private static AdvancedEventElement.Attachment attachEmitter(String key, double start, double end, String parent,
                                                                  Vec3d offset, String particle, double rate,
                                                                  double spread, double speed) {
        return new AdvancedEventElement.Attachment(key, start, end, 100, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, offset, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.EmitterPayload(Identifier.ofVanilla(particle), EventElement.EmitterShape.SPHERE,
                        ScalarTrack.constant(rate), ScalarTrack.constant(spread), ScalarTrack.constant(speed),
                        ScalarTrack.constant(1.0), ColorTrack.constant(0xFFFFFFFF), 4096), 1000);
    }

    private static MotionCurve riseCurve(double start, double end, double height) {
        return (tick, seed) -> {
            double t = Math.max(0.0, Math.min(1.0, (tick - start) / Math.max(1.0, end - start)));
            return Transform.translation(0, height * Easing.EASE_OUT_CUBIC.apply(t), 0);
        };
    }

    private static SceneElement.WorldText worldText(String key, double start, double end, String text,
                                                    Vec3d offset, double scale, int color) {
        return new SceneElement.WorldText(key, start, end, 190, ConflictPolicy.REPLACE_LOWER, text,
                Identifier.ofVanilla("default"), offset, TransformTrack.constant(Transform.scale(scale)), MotionCurve.none(),
                ColorTrack.constant(color), 0x50000000, true, true, true);
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
