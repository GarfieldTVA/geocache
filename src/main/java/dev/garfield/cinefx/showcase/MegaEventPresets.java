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

/** Long-form reference events designed to feel like large live-game events rather than short demos. */
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
        register("event_zeropoint", zeroPoint());
        register("event_invasion", invasion());
        register("event_titan", titan());
        register("event_nexus", nexus());
    }

    public static Map<String, Identifier> catalog() { return Map.copyOf(CATALOG); }

    /** 6:00. An energy core rises, fractures reality, opens rifts and rebuilds the arena. */
    private static SceneDefinition zeroPoint() {
        double d = 7200.0;
        SceneBuilder b = base(ZEROPOINT, d, "zero-point-collapse");
        ambience(b, "zp", d, 0xFF6D9BC7, 0xFF23436D, 0x080080FF, 0.18, 0.17);
        arena(b, "zp_arena", d, Blocks.SMOOTH_STONE.getDefaultState(), 14, 0xFF65E8FF);
        crowd(b, "zp_civilians", 160, 5700, Identifier.ofVanilla("villager"), 58, 22, "wander", 4.5, 0.030, "walk");
        b.add(text("zp_warning", 100, 850, "REALITY ANOMALY DETECTED", 0xFFBDF7FF));
        b.add(camera("zp_intro_cam", 0, 320,
                route(new Vec3d(-16, 7, -7), new Vec3d(-9, 11, 8), new Vec3d(9, 9, 12), new Vec3d(15, 6, 20), 320),
                new Vec3d(0, 4, 18), 70.0, 57.0, 0.018, 0.12));
        b.add(control("zp_intro_lock", 0, 320));

        b.add(deform("zp_pulse", 550, 1850, UltraEventElement.DeformMode.PULSE,
                new Vec3d(0, 0.03, 18), 34.0, 2.8, 0xFF64EFFF));
        b.add(field("zp_vortex", 650, 6100, Identifier.ofVanilla("electric_spark"), new Vec3d(0, 4, 18),
                1500.0, 0xFF66EEFF, 70000,
                List.of(force(UltraEventElement.ForceKind.VORTEX, 3.0, 18.0, 11),
                        force(UltraEventElement.ForceKind.ATTRACTOR, 1.6, 14.0, 12),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.65, 25.0, 13))));

        PathTrack coreRise = route(new Vec3d(0, -8, 18), new Vec3d(0, -2, 18),
                new Vec3d(0, 3, 18), new Vec3d(0, 5, 18), 1500);
        b.add(node("zp_core_root", 800, 6600, coreRise.asMotionCurve(true, 0)));
        b.add(mesh("zp_core", 800, 6600, "zp_core_root", PremiumShowcase.MODEL,
                new Vec3d(3.5, 3.5, 3.5), 0xFFC7FAFF, 0.8, "crystal"));
        b.add(light("zp_core_light", 800, 6600, "zp_core_root", 0xFF57E9FF, 8.0, 36.0, 0.9));
        b.add(attachmentParticles("zp_core_sparks", 800, 6600, "zp_core_root", "end_rod", 520.0, 3.2, 0.55));

        for (int i = 0; i < 5; i++) {
            double start = 1700 + i * 620.0;
            double a = Math.PI * 2.0 * i / 5.0;
            Vec3d p = new Vec3d(Math.cos(a) * 22, 8 + (i % 2) * 4, 18 + Math.sin(a) * 22);
            b.add(portal("zp_rift_" + i, start, 5800, p, new Vec3d(7, 10, 0.2),
                    (i & 1) == 0 ? 0xFFFF5BDB : 0xFF62EEFF, 0.8));
            b.add(ring("zp_rift_wave_" + i, start, start + 360, new Vec3d(p.x, 0.08, p.z), 0.5, 25.0, 0xFF8FF5FF));
        }
        spikes(b, "zp_crystal", 2200, 6000, Blocks.AMETHYST_BLOCK.getDefaultState(), 30, 19, 18);

        PathTrack guardianPath = route(new Vec3d(-23, 0, 6), new Vec3d(-8, 0, 22),
                new Vec3d(12, 0, 14), new Vec3d(1, 0, 19), 3600);
        b.add(node("zp_guardian_path", 2500, 6300, guardianPath.asMotionCurve(true, 0)));
        b.add(actor("zp_guardian", 2500, 6300, "zp_guardian_path", Identifier.ofVanilla("iron_golem"),
                new Vec3d(2.6, 2.6, 2.6), "run"));
        b.add(camera("zp_mid_cam", 3200, 3540,
                route(new Vec3d(-25, 12, 19), new Vec3d(-9, 16, 31), new Vec3d(11, 13, 30), new Vec3d(24, 8, 18), 340),
                new Vec3d(0, 5, 18), 66.0, 54.0, 0.025, 0.20));
        b.add(control("zp_mid_lock", 3200, 3540));

        b.add(deform("zp_fissure", 4400, 6250, UltraEventElement.DeformMode.FISSURE,
                new Vec3d(0, 0.02, 18), 48.0, 7.0, 0xFFFF55DE));
        b.add(fracture("zp_core_break", 5520, 6300, "zp_core_root", PremiumShowcase.MODEL,
                3600, 0xFFD9FBFF, new Vec3d(0, 0.8, 0), 6.5));
        b.add(camera("zp_final_cam", 5480, 6300,
                route(new Vec3d(-23, 9, 4), new Vec3d(-8, 19, 10), new Vec3d(8, 23, 24), new Vec3d(25, 12, 18), 820),
                new Vec3d(0, 6, 18), 62.0, 80.0, 0.09, 0.60));
        b.add(control("zp_final_lock", 5480, 6300));
        b.add(deform("zp_rebuild", 6250, d, UltraEventElement.DeformMode.REBUILD,
                new Vec3d(0, 0.02, 18), 42.0, 4.0, 0xFF7CFFAA));
        b.add(text("zp_end", 6750, d, "REALITY STABILIZED", 0xFFB6FFD1));
        return b.build();
    }

    /** 5:30. A fleet invades, crowds evacuate, a mothership fires and is torn apart. */
    private static SceneDefinition invasion() {
        double d = 6600.0;
        SceneBuilder b = base(INVASION, d, "starfall-invasion");
        ambience(b, "inv", d, 0xFF687E9D, 0xFF17263E, 0x08163B80, 0.15, 0.24);
        arena(b, "inv_base", d, Blocks.STONE_BRICKS.getDefaultState(), 15, 0xFF6CAEFF);
        crowd(b, "inv_evacuees", 450, 5000, Identifier.ofVanilla("villager"), 72, 28, "panic", 7.0, 0.064, "panic");

        PathTrack shipRoute = route(new Vec3d(-125, 70, -170), new Vec3d(-50, 56, -70),
                new Vec3d(15, 43, -20), new Vec3d(2, 35, 22), 5900);
        b.add(node("inv_ship_root", 0, 6200, shipRoute.asMotionCurve(true, -7)));
        b.add(mesh("inv_ship", 0, 6200, "inv_ship_root", id("model/mothership"),
                new Vec3d(13, 4.5, 26), 0xFFD7E9FF, 0.25, "ship"));
        b.add(light("inv_engine_a", 0, 6200, "inv_ship_root", 0xFF55DFFF, 6.5, 32.0, 0.5));
        b.add(attachmentParticles("inv_exhaust", 0, 6200, "inv_ship_root", "end_rod", 700.0, 3.0, 0.75));
        b.add(camera("inv_arrival_cam", 0, 360,
                route(new Vec3d(-18, 7, -8), new Vec3d(-12, 12, 7), new Vec3d(7, 14, 13), new Vec3d(17, 8, 20), 360),
                new Vec3d(0, 28, -10), 72.0, 56.0, 0.02, 0.10));
        b.add(control("inv_arrival_lock", 0, 360));

        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2.0 * i / 7.0;
            b.add(portal("inv_drop_" + i, 1100 + i * 120, 4300,
                    new Vec3d(Math.cos(a) * 31, 24 + (i % 3) * 4, 15 + Math.sin(a) * 31),
                    new Vec3d(5.0, 7.5, 0.2), 0xFF817CFF, 0.65));
        }
        b.add(field("inv_meteors", 1500, 4850, Identifier.ofVanilla("ash"), new Vec3d(0, 30, 18),
                1800.0, 0xFFFFAA75, 90000,
                List.of(directional(new Vec3d(0.3, -1.0, 0.12), 2.4, 90.0, 100),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.5, 45.0, 101))));
        crowd(b, "inv_raiders", 1800, 5550, Identifier.ofVanilla("pillager"), 48, 24, "march", 10.0, 0.055, "run");
        b.add(deform("inv_craters", 1850, 5200, UltraEventElement.DeformMode.CRACK,
                new Vec3d(0, 0.02, 18), 38.0, 4.0, 0xFFFF965F));

        b.add(beam("inv_main_beam", 3600, 4550, "inv_ship_root", new Vec3d(0, -3, 3),
                new Vec3d(0, -75, 45), 1.25, 0xFF7AEEFF));
        b.add(ring("inv_impact", 3800, 4750, new Vec3d(0, 0.08, 36), 1.0, 58.0, 0xFFB6F9FF));
        b.add(camera("inv_beam_cam", 3560, 4250,
                route(new Vec3d(-27, 10, 32), new Vec3d(-10, 17, 26), new Vec3d(8, 14, 36), new Vec3d(25, 9, 20), 690),
                new Vec3d(0, 20, 22), 68.0, 59.0, 0.055, 0.38));
        b.add(control("inv_beam_lock", 3560, 4250));

        b.add(fracture("inv_ship_break", 5280, 6250, "inv_ship_root", id("model/mothership"),
                5400, 0xFFFFD6B7, new Vec3d(0.4, 0.35, -0.8), 8.0));
        b.add(camera("inv_final_cam", 5280, 6250,
                route(new Vec3d(-31, 13, 4), new Vec3d(-13, 27, 12), new Vec3d(15, 30, 20), new Vec3d(30, 17, 27), 970),
                new Vec3d(0, 34, 22), 64.0, 82.0, 0.11, 0.75));
        b.add(control("inv_final_lock", 5280, 6250));
        b.add(deform("inv_repair", 5900, d, UltraEventElement.DeformMode.REBUILD,
                new Vec3d(0, 0.02, 18), 44.0, 3.5, 0xFF91C8FF));
        b.add(text("inv_end", 6300, d, "STARFALL REPULSED", 0xFFD0F7FF));
        return b.build();
    }

    /** 5:50. A giant titan wakes, walks through a changing arena, battles a rift and collapses. */
    private static SceneDefinition titan() {
        double d = 7000.0;
        SceneBuilder b = base(TITAN, d, "titan-reckoning");
        ambience(b, "tr", d, 0xFFD0A469, 0xFF6B452C, 0x08FF8D42, 0.14, 0.22);
        arena(b, "tr_ruins", d, Blocks.POLISHED_ANDESITE.getDefaultState(), 15, 0xFFFFB56F);
        crowd(b, "tr_fleeing", 700, 5100, Identifier.ofVanilla("villager"), 64, 26, "panic", 8.0, 0.070, "panic");

        PathTrack titanRoute = route(new Vec3d(0, -13, 39), new Vec3d(-8, 0, 30),
                new Vec3d(11, 0, 18), new Vec3d(0, 0, 12), 6000);
        b.add(node("tr_titan_root", 250, 6800, titanRoute.asMotionCurve(true, 0)));
        b.add(actor("tr_titan", 250, 6800, "tr_titan_root", Identifier.ofVanilla("iron_golem"),
                new Vec3d(7.5, 7.5, 7.5), "walk"));
        b.add(new ComplexElement.Shadow("tr_shadow", 250, 6800, 30, ConflictPolicy.ALLOW, "tr_titan_root",
                ComplexElement.ShadowMode.GEOMETRY, null, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(0.6), ScalarTrack.constant(0.75), ScalarTrack.constant(10.0), 800));
        b.add(attachmentParticles("tr_dust", 250, 6800, "tr_titan_root", "ash", 600.0, 7.0, 0.55));
        b.add(light("tr_titan_light", 700, 6200, "tr_titan_root", 0xFFFF9A4D, 4.5, 26.0, 0.45));
        b.add(camera("tr_wake_cam", 180, 820,
                route(new Vec3d(-19, 6, 4), new Vec3d(-11, 12, 19), new Vec3d(8, 16, 26), new Vec3d(21, 8, 15), 640),
                new Vec3d(0, 15, 35), 72.0, 57.0, 0.035, 0.32));
        b.add(control("tr_wake_lock", 180, 820));
        b.add(deform("tr_awakening", 300, 1850, UltraEventElement.DeformMode.FISSURE,
                new Vec3d(0, 0.02, 37), 36.0, 6.5, 0xFFFFA15D));

        spikes(b, "tr_obelisk", 1400, 5650, Blocks.CUT_COPPER.getDefaultState(), 22, 19, 18);
        b.add(field("tr_storm", 2100, 5850, Identifier.ofVanilla("ash"), new Vec3d(0, 20, 18),
                1650.0, 0xFFFFC086, 70000,
                List.of(force(UltraEventElement.ForceKind.VORTEX, 2.2, 32.0, 60),
                        directional(new Vec3d(0.7, 0.0, 0.2), 0.9, 85.0, 61),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.65, 38.0, 62))));
        b.add(portal("tr_sky_rift", 3000, 5500, new Vec3d(0, 31, 9),
                new Vec3d(15, 10, 0.2), 0xFF65DFFF, 0.95));
        b.add(beam("tr_titan_beam", 3400, 4650, "tr_titan_root", new Vec3d(0, 11, 0),
                new Vec3d(0, 35, -20), 1.1, 0xFFFFAD56));
        b.add(camera("tr_battle_cam", 3350, 4100,
                route(new Vec3d(-24, 9, 12), new Vec3d(-8, 20, 27), new Vec3d(11, 22, 21), new Vec3d(26, 11, 10), 750),
                new Vec3d(0, 15, 20), 67.0, 59.0, 0.075, 0.50));
        b.add(control("tr_battle_lock", 3350, 4100));

        b.add(new UltraEventElement.MaterialEffect("tr_petrify", 5000, 6400, 160, ConflictPolicy.ALLOW,
                "tr_titan", UltraEventElement.MaterialMode.FREEZE,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(1000, 1.0, Easing.EASE_IN_OUT_SINE)),
                ScalarTrack.constant(0.09), ColorTrack.constant(0xFFC5CDD3), ScalarTrack.constant(1.5),
                ScalarTrack.constant(0.4), new Vec3d(0, 1, 0), Map.of()));
        b.add(fracture("tr_collapse", 6100, 6900, "tr_titan_root", id("model/titan_proxy"),
                4600, 0xFFC6B49B, new Vec3d(0.25, 0.15, 0.3), 5.0));
        b.add(camera("tr_collapse_cam", 6050, 6900,
                route(new Vec3d(-20, 7, -2), new Vec3d(-8, 15, 8), new Vec3d(10, 19, 18), new Vec3d(24, 8, 13), 850),
                new Vec3d(0, 11, 13), 64.0, 78.0, 0.12, 0.82));
        b.add(control("tr_collapse_lock", 6050, 6900));
        b.add(text("tr_end", 6720, d, "THE TITAN SLEEPS", 0xFFFFD8A6));
        return b.build();
    }

    /** 6:30. Four realities overwrite the arena, then the entire timeline rewinds and merges. */
    private static SceneDefinition nexus() {
        double d = 7800.0;
        SceneBuilder b = base(NEXUS, d, "chrono-nexus");
        ambience(b, "nx", d, 0xFF789BC2, 0xFF17325B, 0x08136AFF, 0.19, 0.23);
        arena(b, "nx_platform", d, Blocks.QUARTZ_BLOCK.getDefaultState(), 15, 0xFF72EFFF);
        crowd(b, "nx_travelers", 700, 6950, Identifier.ofVanilla("villager"), 54, 23, "orbit", 5.5, 0.024, "walk");
        b.add(text("nx_title", 80, 760, "CHRONO NEXUS // SYNCHRONIZING", 0xFFB2F8FF));
        b.add(portal("nx_center", 100, 7500, new Vec3d(0, 9, 24),
                new Vec3d(12, 15, 0.2), 0xFF65EAFF, 0.85));
        b.add(camera("nx_intro_cam", 0, 330,
                route(new Vec3d(-17, 7, -7), new Vec3d(-9, 12, 9), new Vec3d(9, 14, 14), new Vec3d(17, 9, 21), 330),
                new Vec3d(0, 8, 23), 70.0, 57.0, 0.02, 0.12));
        b.add(control("nx_intro_lock", 0, 330));

        reality(b, "void", 850, 2350, Blocks.OBSIDIAN.getDefaultState(), Identifier.ofVanilla("portal"),
                UltraEventElement.DeformMode.SINK, 0xFFFF61E8);
        reality(b, "ice", 2350, 3850, Blocks.PACKED_ICE.getDefaultState(), Identifier.ofVanilla("snowflake"),
                UltraEventElement.DeformMode.GROW, 0xFFA0EBFF);
        reality(b, "desert", 3850, 5350, Blocks.SANDSTONE.getDefaultState(), Identifier.ofVanilla("ash"),
                UltraEventElement.DeformMode.WAVE, 0xFFFFB570);
        reality(b, "neon", 5350, 6850, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), Identifier.ofVanilla("electric_spark"),
                UltraEventElement.DeformMode.PULSE, 0xFF62F3FF);

        PathTrack guardRoute = route(new Vec3d(-19, 1, 8), new Vec3d(-7, 5, 22),
                new Vec3d(13, 7, 24), new Vec3d(0, 4, 17), 5300);
        b.add(node("nx_guard_root", 1050, 6500, guardRoute.asMotionCurve(true, 0)));
        b.add(actor("nx_guard", 1050, 6500, "nx_guard_root", Identifier.ofVanilla("allay"),
                new Vec3d(3.0, 3.0, 3.0), "idle"));
        for (int i = 0; i < 6; i++) {
            double start = 1300 + i * 800.0;
            double a = Math.PI * 2.0 * i / 6.0;
            b.add(portal("nx_satellite_" + i, start, Math.min(d, start + 2300),
                    new Vec3d(Math.cos(a) * 23, 9 + (i % 2) * 5, 18 + Math.sin(a) * 23),
                    new Vec3d(5.5, 8.0, 0.2), (i & 1) == 0 ? 0xFFFF64D8 : 0xFF65EFFF, 0.72));
        }
        b.add(camera("nx_phase_cam_a", 2250, 2540,
                route(new Vec3d(-25, 10, 19), new Vec3d(-8, 15, 32), new Vec3d(11, 13, 30), new Vec3d(23, 9, 15), 290),
                new Vec3d(0, 6, 18), 65.0, 55.0, 0.025, 0.17));
        b.add(control("nx_phase_lock_a", 2250, 2540));
        b.add(camera("nx_phase_cam_b", 5200, 5500,
                route(new Vec3d(23, 10, 12), new Vec3d(8, 18, 28), new Vec3d(-11, 15, 32), new Vec3d(-24, 9, 18), 300),
                new Vec3d(0, 8, 20), 65.0, 58.0, 0.03, 0.20));
        b.add(control("nx_phase_lock_b", 5200, 5500));

        b.add(deform("nx_rewind", 6750, 7650, UltraEventElement.DeformMode.REBUILD,
                new Vec3d(0, 0.02, 18), 50.0, 6.0, 0xFFFFFFFF));
        b.add(field("nx_rewind_field", 6750, 7650, Identifier.ofVanilla("end_rod"), new Vec3d(0, 9, 18),
                2400.0, 0xFFD9FBFF, 100000,
                List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 3.5, 42.0, 300),
                        force(UltraEventElement.ForceKind.VORTEX, -2.6, 34.0, 301))));
        b.add(camera("nx_final_cam", 6720, 7650,
                route(new Vec3d(-29, 13, 0), new Vec3d(-11, 24, 12), new Vec3d(12, 27, 26), new Vec3d(30, 14, 18), 930),
                new Vec3d(0, 9, 20), 62.0, 82.0, 0.08, 0.62));
        b.add(control("nx_final_lock", 6720, 7650));
        b.add(text("nx_end", 7500, d, "TIMELINE MERGED", 0xFFD9FFFF));
        return b.build();
    }

    private static void reality(SceneBuilder b, String name, double start, double end, BlockState state,
                                Identifier particle, UltraEventElement.DeformMode mode, int color) {
        b.add(deform("nx_" + name + "_deform", start, end, mode, new Vec3d(0, 0.02, 18), 32.0, 4.2, color));
        b.add(field("nx_" + name + "_field", start, end, particle, new Vec3d(0, 11, 18),
                900.0, color, 55000,
                List.of(force(UltraEventElement.ForceKind.VORTEX, 1.5, 30.0, name.hashCode()))));
        spikes(b, "nx_" + name + "_structure", start + 20, end, state, 18, 16, 18);
    }

    private static void ambience(SceneBuilder b, String prefix, double duration, int horizon, int zenith,
                                 int tint, double bloom, double vignette) {
        b.add(new EventElement.Atmosphere(prefix + "_atmosphere", 0, duration, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(horizon), ColorTrack.constant(zenith), ScalarTrack.constant(0.025),
                ScalarTrack.constant(0.0), ScalarTrack.constant(260.0), ScalarTrack.constant(0.5),
                ScalarTrack.constant(1.0), ScalarTrack.constant(0.3)));
        b.add(new AdvancedEventElement.Sky(prefix + "_sky", 0, duration, 20, ConflictPolicy.REPLACE_LOWER, null,
                ColorTrack.constant(horizon), ColorTrack.constant(zenith), ScalarTrack.constant(0.7),
                ScalarTrack.constant(1.1), ScalarTrack.constant(0.15), ScalarTrack.constant(0.25),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(duration, 40.0)), Map.of()));
        b.add(new UltraEventElement.PostProcess(prefix + "_post", 0, duration, 185, ConflictPolicy.REPLACE_LOWER,
                Map.of(UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(bloom),
                        UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(vignette),
                        UltraEventElement.PostEffect.DEPTH_OF_FIELD, ScalarTrack.constant(0.10),
                        UltraEventElement.PostEffect.MOTION_BLUR, ScalarTrack.constant(0.03),
                        UltraEventElement.PostEffect.CHROMATIC_ABERRATION, ScalarTrack.constant(0.02)),
                ColorTrack.constant(tint), ScalarTrack.constant(13.0), ScalarTrack.constant(5.0), Map.of()));
        b.add(new AdvancedEventElement.AudioLayer(prefix + "_audio", 0, duration, 25, ConflictPolicy.ALLOW,
                Identifier.ofVanilla("block.beacon.ambient"), ScalarTrack.constant(0.38), ScalarTrack.constant(0.78),
                ScalarTrack.constant(0.0), true, false, 80, 100, Map.of()));
    }

    private static void arena(SceneBuilder b, String prefix, double duration, BlockState state, int radius, int color) {
        int n = 0;
        for (int x = -radius; x <= radius; x += 3) {
            for (int z = -radius; z <= radius; z += 3) {
                if (x * x + z * z > radius * radius || ((x + z) & 3) != 0) continue;
                b.add(block(prefix + "_floor_" + n++, 0, duration, state,
                        new Vec3d(x, 0.02, 18 + z), new Vec3d(1.0, 0.10, 1.0), 80));
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2.0 * i / 16.0;
            b.add(block(prefix + "_pillar_" + i, i * 5.0, duration, state,
                    new Vec3d(Math.cos(a) * radius, 0.02, 18 + Math.sin(a) * radius),
                    new Vec3d(0.8, 2.7 + (i % 4) * 0.45, 0.8), 120));
        }
        b.add(ring(prefix + "_rim", 0, duration, new Vec3d(0, 0.10, 18), radius - 1.0, radius - 0.5, color));
    }

    private static void spikes(SceneBuilder b, String prefix, double start, double end, BlockState state,
                               int count, double radius, double zCenter) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297;
            double r = 4.0 + radius * Math.sqrt((i + 0.5) / count);
            b.add(block(prefix + '_' + i, start + i * 10.0, end, state,
                    new Vec3d(Math.cos(a) * r, 0.02, zCenter + Math.sin(a) * r),
                    new Vec3d(0.75, 2.2 + (i % 6) * 0.65, 0.75), 170));
        }
    }

    private static void crowd(SceneBuilder b, String key, double start, double end, Identifier type,
                              int count, double radius, String locomotion, double moveRadius,
                              double moveSpeed, String animation) {
        ArrayList<AdvancedEventElement.CrowdAgent> agents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297;
            double r = 3.0 + radius * Math.sqrt((i + 0.5) / count);
            agents.add(new AdvancedEventElement.CrowdAgent(
                    new Vec3d(Math.cos(a) * r, 0, 18 + Math.sin(a) * r), Math.toDegrees(-a), i * 3.0, i % 8));
        }
        b.add(new AdvancedEventElement.Crowd(key, start, end, 40, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.ENTITY, type, "EventNPC", agents,
                List.of(animation(animation, animation.equals("walk") ? 0.75 : 1.15)),
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF),
                ScalarTrack.constant(1.0), true, 700,
                Map.of("locomotion", locomotion, "move_radius", Double.toString(moveRadius),
                        "move_speed", Double.toString(moveSpeed))));
    }

    private static ComplexElement.Node node(String key, double start, double end, MotionCurve motion) {
        return new ComplexElement.Node(key, start, end, 80, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), motion, true);
    }

    private static ComplexElement.Mesh mesh(String key, double start, double end, String parent, Identifier model,
                                            Vec3d scale, int tint, double emissive, String fallback) {
        return new ComplexElement.Mesh(key, start, end, 100, ConflictPolicy.ALLOW, parent, model, null, Vec3d.ZERO,
                transform(scale), MotionCurve.none(), ColorTrack.constant(tint), ScalarTrack.constant(1.0),
                ScalarTrack.constant(emissive), true, 1100, Map.of("fallback_shape", fallback));
    }

    private static ComplexElement.Actor actor(String key, double start, double end, String parent,
                                              Identifier type, Vec3d scale, String clip) {
        return new ComplexElement.Actor(key, start, end, 100, ConflictPolicy.ALLOW, parent,
                ComplexElement.ActorKind.ENTITY, type, null, null, Map.of(), Vec3d.ZERO, transform(scale),
                MotionCurve.none(), List.of(animation(clip, clip.equals("idle") ? 0.7 : 1.0)), List.of(), List.of(),
                null, ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1.0), ScalarTrack.constant(0.0), true, 900);
    }

    private static ComplexElement.AnimationLayer animation(String clip, double speed) {
        return new ComplexElement.AnimationLayer(Identifier.of("cinefx", clip), ScalarTrack.constant(1.0),
                ScalarTrack.constant(speed), 0, true, ComplexElement.BlendMode.OVERRIDE, Map.of());
    }

    private static UltraEventElement.CameraRig camera(String key, double start, double end, PathTrack path,
                                                      Vec3d lookAt, double fovA, double fovB,
                                                      double shakeT, double shakeR) {
        return new UltraEventElement.CameraRig(key, start, end, 220, ConflictPolicy.REPLACE_LOWER,
                UltraEventElement.CameraRigMode.RAIL, path, lookAt, null, ScalarTrack.constant(0.0),
                ScalarTrack.of(Keyframe.at(0, fovA), Keyframe.at(end - start, fovB)),
                ScalarTrack.constant(12.0), ScalarTrack.constant(4.0), ScalarTrack.constant(shakeT),
                ScalarTrack.constant(shakeR), ScalarTrack.constant(1.7), true,
                Map.of("show_hud", "false", "show_hand", "false"));
    }

    private static AdvancedEventElement.PlayerControl control(String key, double start, double end) {
        return new AdvancedEventElement.PlayerControl(key, start, end, 230, ConflictPolicy.REPLACE_LOWER,
                true, true, true, false, ScalarTrack.constant(0.0), ScalarTrack.constant(1.0),
                ScalarTrack.constant(-1.0), false, false);
    }

    private static UltraEventElement.WorldDeform deform(String key, double start, double end,
                                                        UltraEventElement.DeformMode mode, Vec3d center,
                                                        double radius, double amplitude, int color) {
        return new UltraEventElement.WorldDeform(key, start, end, 130, ConflictPolicy.ALLOW, center, mode,
                ScalarTrack.of(Keyframe.at(0, Math.max(1.0, radius * 0.08)),
                        Keyframe.at(Math.max(1.0, (end - start) * 0.55), radius, Easing.EASE_OUT_CUBIC)),
                ScalarTrack.constant(amplitude), ScalarTrack.constant(1.2),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.max(1.0, (end - start) * 0.50), 1.0)),
                null, ColorTrack.constant(color), true, Map.of());
    }

    private static UltraEventElement.ParticleField field(String key, double start, double end, Identifier particle,
                                                         Vec3d position, double rate, int color, int max,
                                                         List<UltraEventElement.Force> forces) {
        return new UltraEventElement.ParticleField(key, start, end, 80, ConflictPolicy.ALLOW, null, particle,
                position, AdvancedTransformTrack.identity(), MotionCurve.none(), ScalarTrack.constant(rate),
                ScalarTrack.constant(70.0), ScalarTrack.constant(0.45), ScalarTrack.constant(1.0),
                ColorTrack.constant(color), forces, max, true, true, 700, Map.of("spawn_radius", "28"));
    }

    private static UltraEventElement.PortalSurface portal(String key, double start, double end, Vec3d position,
                                                          Vec3d size, int color, double distortion) {
        return new UltraEventElement.PortalSurface(key, start, end, 90, ConflictPolicy.ALLOW, null,
                UltraEventElement.PortalMode.DIMENSION_VIEW, position, AdvancedTransformTrack.identity(),
                MotionCurve.none(), size, null, Vec3d.ZERO,
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(100, 1.0, Easing.EASE_OUT_CUBIC)),
                ColorTrack.constant(color), ScalarTrack.constant(distortion), 2, 750,
                Map.of("premium", "render-target-eligible"));
    }

    private static UltraEventElement.LightRig light(String key, double start, double end, String parent,
                                                    int color, double intensity, double radius, double volume) {
        return new UltraEventElement.LightRig(key, start, end, 120, ConflictPolicy.ALLOW, parent, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), MotionCurve.none(), List.of(
                new UltraEventElement.RigLight(UltraEventElement.LightKind.POINT, Vec3d.ZERO, new Vec3d(0, -1, 0),
                        ColorTrack.constant(color), ScalarTrack.constant(intensity), ScalarTrack.constant(radius),
                        ScalarTrack.constant(0.0), ScalarTrack.constant(180.0), true, ScalarTrack.constant(volume))),
                ScalarTrack.constant(1.0), 1000, Map.of());
    }

    private static UltraEventElement.Fracture fracture(String key, double start, double end, String parent,
                                                       Identifier model, int shards, int color, Vec3d impulse,
                                                       double strength) {
        return new UltraEventElement.Fracture(key, start, end, 170, ConflictPolicy.ALLOW, parent, model,
                UltraEventElement.FractureMode.VORONOI, shards, Vec3d.ZERO, AdvancedTransformTrack.identity(),
                MotionCurve.none(), impulse, ScalarTrack.constant(strength), ScalarTrack.constant(0.045),
                ScalarTrack.constant(0.978), ScalarTrack.constant(8.0), ColorTrack.constant(color),
                ScalarTrack.constant(1.0), true, false, 1200, Map.of("fracture_duration", "650"));
    }

    private static AdvancedEventElement.Attachment attachmentParticles(String key, double start, double end,
                                                                        String parent, String particle,
                                                                        double rate, double spread, double speed) {
        return new AdvancedEventElement.Attachment(key, start, end, 110, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.EmitterPayload(Identifier.ofVanilla(particle), EventElement.EmitterShape.SPHERE,
                        ScalarTrack.constant(rate), ScalarTrack.constant(spread), ScalarTrack.constant(speed),
                        ScalarTrack.constant(1.0), ColorTrack.constant(0xFFFFFFFF), 4096), 1100);
    }

    private static AdvancedEventElement.Attachment beam(String key, double start, double end, String parent,
                                                         Vec3d from, Vec3d to, double width, int color) {
        return new AdvancedEventElement.Attachment(key, start, end, 145, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.BeamPayload(from, to, ScalarTrack.constant(width), ColorTrack.constant(color)), 1200);
    }

    private static SceneElement.Block block(String key, double start, double end, BlockState state, Vec3d offset,
                                            Vec3d scale, double grow) {
        return new SceneElement.Block(key, start, end, 35, ConflictPolicy.ALLOW, state, null, offset,
                TransformTrack.of(Keyframe.at(0, new Transform(Vec3d.ZERO, Vec3d.ZERO,
                                new Vec3d(0.04, 0.04, 0.04)), Easing.EASE_OUT_BACK),
                        Keyframe.at(grow, new Transform(Vec3d.ZERO, Vec3d.ZERO, scale))),
                MotionCurve.none(), false, 0, false);
    }

    private static SceneElement.Ring ring(String key, double start, double end, Vec3d center,
                                          double from, double to, int color) {
        return new SceneElement.Ring(key, start, end, 70, ConflictPolicy.ALLOW, center,
                ScalarTrack.of(Keyframe.at(0, from, Easing.EASE_OUT_CUBIC), Keyframe.at(Math.max(1.0, end - start), to)),
                ScalarTrack.constant(0.30), 80, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(color));
    }

    private static SceneElement.WorldText text(String key, double start, double end, String value, int color) {
        return new SceneElement.WorldText(key, start, end, 190, ConflictPolicy.REPLACE_LOWER, value,
                Identifier.ofVanilla("default"), new Vec3d(0, 9, 14), TransformTrack.constant(Transform.scale(1.55)),
                MotionCurve.none(), ColorTrack.constant(color), 0x48000000, true, true, true);
    }

    private static PathTrack route(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double duration) {
        return PathTrack.catmullRom(PathTrack.Point.at(0, a, Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(duration * 0.33, b, Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(duration * 0.66, c, Easing.EASE_IN_OUT_SINE), PathTrack.Point.at(duration, d));
    }

    private static AdvancedTransformTrack transform(Vec3d scale) {
        return new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                Vec3Track.angles(Keyframe.at(0, Vec3d.ZERO)), Vec3Track.constant(scale), Vec3Track.constant(Vec3d.ZERO));
    }

    private static UltraEventElement.Force force(UltraEventElement.ForceKind kind, double strength,
                                                  double radius, long seed) {
        return new UltraEventElement.Force(kind, Vec3d.ZERO, new Vec3d(0, 1, 0),
                ScalarTrack.constant(strength), ScalarTrack.constant(radius), ScalarTrack.constant(1.2), seed);
    }

    private static UltraEventElement.Force directional(Vec3d direction, double strength, double radius, long seed) {
        return new UltraEventElement.Force(UltraEventElement.ForceKind.DIRECTIONAL, Vec3d.ZERO, direction,
                ScalarTrack.constant(strength), ScalarTrack.constant(radius), ScalarTrack.constant(1.0), seed);
    }

    private static SceneBuilder base(Identifier id, double duration, String name) {
        return SceneBuilder.create(id).duration(duration).priority(180)
                .meta("showcase", "mega-event").meta("preset", name)
                .meta("duration_ticks", Double.toString(duration));
    }

    private static void register(String name, SceneDefinition scene) {
        CineFxApi.register(scene);
        CATALOG.put(name, scene.id());
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
