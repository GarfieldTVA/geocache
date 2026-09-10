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
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Built-in visual showcase and stress-test catalog for CineFX. */
public final class ShowcaseScenes {
    public static final Identifier BUNDLE = id("showcase/core");
    public static final Identifier SKYFALL = id("showcase/skyfall_armada");
    public static final Identifier TITAN = id("showcase/ancient_titan");
    public static final Identifier VOID = id("showcase/void_cataclysm");
    public static final Identifier CYBER = id("showcase/cyber_city");
    public static final Identifier FROZEN = id("showcase/frozen_collapse");
    public static final Identifier DESERT = id("showcase/desert_relic");
    public static final Identifier OCEAN = id("showcase/underwater_leviathan");
    public static final Identifier NATURE = id("showcase/nature_reclamation");
    public static final Identifier MOONBASE = id("showcase/moonbase_evacuation");
    public static final Identifier REALITY = id("showcase/reality_fracture");
    public static final Identifier STRESS_TIMELINE = id("stress/long_timeline");
    public static final Identifier STRESS_INSTANCES = id("stress/instance_storm");
    public static final Identifier STRESS_CROWD = id("stress/actor_crowd");
    public static final Identifier STRESS_ATTACHMENTS = id("stress/attachment_hell");

    private static final LinkedHashMap<String, Identifier> CATALOG = new LinkedHashMap<>();
    private static boolean registered;

    private ShowcaseScenes() { }

    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;
        AssetBundle.Registry.register(new AssetBundle(BUNDLE, List.of(), List.of(
                id("model/mothership"), id("model/reality_core"), id("sky/reality_fracture")),
                Map.of("kind", "optional-showcase-assets")));
        register("skyfall", skyfall());
        register("titan", titan());
        register("void", voidCataclysm());
        register("cyber", cyberCity());
        register("frozen", frozenCollapse());
        register("desert", desertRelic());
        register("ocean", underwaterLeviathan());
        register("nature", natureReclamation());
        register("moonbase", moonbaseEvacuation());
        register("reality", realityFracture());
        register("stress_timeline", stressTimeline());
        register("stress_instances", stressInstances());
        register("stress_crowd", stressCrowd());
        register("stress_attachments", stressAttachments());
    }

    public static Map<String, Identifier> catalog() { return Map.copyOf(CATALOG); }

    /** A multi-universe directed event that automatically walks through the ten hero scenes. */
    public static EventProgram masterProgram() {
        EventProgram.Builder builder = EventProgram.builder(id("program/showcase_marathon"), "preload");
        builder.phase(phase("preload", List.of(EventProgram.Action.preload(BUNDLE)),
                transition("skyfall", EventProgram.Condition.any(
                        EventProgram.Condition.assetsReady(BUNDLE), EventProgram.Condition.after(100)))));
        builder.phase(phase("skyfall", List.of(EventProgram.Action.play(SKYFALL)), transition("titan", EventProgram.Condition.after(900))));
        builder.phase(phase("titan", swap(SKYFALL, TITAN), transition("void", EventProgram.Condition.after(700))));
        builder.phase(phase("void", swap(TITAN, VOID), transition("cyber", EventProgram.Condition.after(700))));
        builder.phase(phase("cyber", swap(VOID, CYBER), transition("frozen", EventProgram.Condition.after(700))));
        builder.phase(phase("frozen", swap(CYBER, FROZEN), transition("desert", EventProgram.Condition.after(700))));
        builder.phase(phase("desert", swap(FROZEN, DESERT), transition("ocean", EventProgram.Condition.after(700))));
        builder.phase(phase("ocean", swap(DESERT, OCEAN), transition("nature", EventProgram.Condition.after(700))));
        builder.phase(phase("nature", swap(OCEAN, NATURE), transition("moonbase", EventProgram.Condition.after(700))));
        builder.phase(phase("moonbase", swap(NATURE, MOONBASE), transition("reality", EventProgram.Condition.after(700))));
        builder.phase(phase("reality", swap(MOONBASE, REALITY), transition("done", EventProgram.Condition.after(1200))));
        builder.phase(phase("done", List.of(EventProgram.Action.stop(REALITY))));
        return builder.meta("showcase", "multi-universe-marathon").build();
    }

    private static SceneDefinition skyfall() {
        double d = 900;
        SceneBuilder b = base(SKYFALL, d);
        b.add(atmosphere("skyfall_atmosphere", 0, d, 0xFF111A36, 0xFF354765, 0.012, 0.2));
        b.add(sky("skyfall_sky", 0, d, 0xFF151D38, 0xFF02040A, 0.25, 0.15));
        PathTrack flight = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-85, 65, -150), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(260, new Vec3d(-30, 50, -70), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(590, new Vec3d(20, 39, -28), Easing.EASE_OUT_CUBIC),
                PathTrack.Point.at(d, new Vec3d(5, 34, 8)));
        b.add(node("mothership", 0, d, flight.asMotionCurve(true, -8)));
        b.add(mesh("mothership_mesh", 0, d, "mothership", id("model/mothership"), new Vec3d(12, 4, 24)));
        b.add(attachLight("left_engine", 0, d, "mothership", new Vec3d(-5, 0, -8), 0xFF45C8FF, 5.0, 28));
        b.add(attachLight("right_engine", 0, d, "mothership", new Vec3d(5, 0, -8), 0xFF45C8FF, 5.0, 28));
        b.add(attachEmitter("engine_exhaust", 0, d, "mothership", new Vec3d(0, 0, -9), "end_rod", 220, 1.5, 0.45));
        b.add(attachBeam("orbital_beam", 560, 760, "mothership", new Vec3d(0, -2, 1), new Vec3d(0, -80, 55), 0.8, 0xFF62E7FF));
        b.add(ring("orbital_impact", 650, 840, new Vec3d(22, 0.12, 28), 0.5, 55, 0xFF8FEAFF));
        b.add(emitter("impact_sparks", 650, 790, "electric_spark", new Vec3d(22, 1, 28), 360, 5.5, 1.25));
        b.add(crowd("ground_squad", 260, d, Identifier.ofVanilla("villager"), 42, 15, 15));
        b.add(cameraShake("impact_camera", 645, 800, 0.22, 1.8));
        return b.build();
    }

    private static SceneDefinition titan() {
        double d = 700;
        SceneBuilder b = base(TITAN, d);
        b.add(atmosphere("titan_dust", 0, d, 0xFFD6A765, 0xFF8A6541, 0.045, 0.7));
        b.add(actor("stone_titan", 80, d, Identifier.ofVanilla("iron_golem"), new Vec3d(0, -9, 18),
                new Vec3d(7.0, 7.0, 7.0), rise(0, 180, 9)));
        b.add(new ComplexElement.Shadow("titan_shadow", 80, d, 5, ConflictPolicy.ALLOW, null,
                ComplexElement.ShadowMode.BLOB, null, new Vec3d(0, 0.03, 18), AdvancedTransformTrack.identity(),
                MotionCurve.none(), ScalarTrack.constant(0.6), ScalarTrack.constant(0.7),
                ScalarTrack.of(Keyframe.at(0, 2.0), Keyframe.at(180, 11.0)), 260));
        b.add(emitter("titan_dust_cloud", 60, 360, "ash", new Vec3d(0, 1, 18), 520, 9, 0.65));
        b.add(ring("quake_a", 220, 390, new Vec3d(0, 0.1, 18), 1, 34, 0xB0E3B87C));
        b.add(ring("quake_b", 300, 500, new Vec3d(0, 0.11, 18), 2, 55, 0x80C98C52));
        b.add(cameraShake("titan_rumble", 200, 470, 0.3, 2.6));
        return b.build();
    }

    private static SceneDefinition voidCataclysm() {
        double d = 700;
        SceneBuilder b = base(VOID, d);
        b.add(atmosphere("void_fog", 0, d, 0xFF24113B, 0xFF160824, 0.065, 0.05));
        b.add(sky("void_sky", 0, d, 0xFF260840, 0xFF020005, 0.95, 0.45));
        b.add(portalRing("void_outer", 20, d, 15.0, 1.1, 0xFFC34EFF));
        b.add(portalRing("void_inner", 60, d, 10.5, 0.42, 0xFF57EAFF));
        b.add(emitter("portal_stream", 30, d, "portal", new Vec3d(0, 22, 28), 560, 13, 0.35));
        b.add(actor("void_watcher", 300, d, Identifier.ofVanilla("enderman"), new Vec3d(0, 1, 18),
                new Vec3d(2.4, 2.4, 2.4), MotionCurve.none()));
        b.add(worldText("void_warning", 120, 460, "REALITY BREACH // {time}", new Vec3d(0, 11, 9), 1.8, 0xFFFFA8FF));
        b.add(cameraShake("void_rumble", 170, 530, 0.12, 1.0));
        return b.build();
    }

    private static SceneDefinition cyberCity() {
        double d = 700;
        SceneBuilder b = base(CYBER, d);
        b.add(atmosphere("cyber_haze", 0, d, 0xFF092432, 0xFF06151D, 0.028, 0.15));
        int index = 0;
        for (int x = -4; x <= 4; x++) {
            for (int z = 8; z <= 18; z += 2) {
                double start = 30 + index * 2.4;
                BlockState state = ((x + z) & 1) == 0
                        ? Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState()
                        : Blocks.SEA_LANTERN.getDefaultState();
                b.add(growingBlock("tower_" + index, start, d, state, new Vec3d(x * 2.0, 0, z),
                        new Vec3d(1.1, 3.0 + Math.abs(x) * 0.7, 1.1), 55));
                index++;
            }
        }
        b.add(emitter("neon_sparks", 0, d, "electric_spark", new Vec3d(0, 6, 14), 190, 13, 0.16));
        b.add(worldText("city_online", 110, 520, "CITY NODE // ONLINE", new Vec3d(0, 9, 7), 1.5, 0xFF67F8FF));
        b.add(new EventElement.Overlay("cyber_glitch", 0, d, 5, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(0xFF00D9FF), ScalarTrack.constant(0.025), ScalarTrack.constant(0.04),
                ScalarTrack.constant(0.04), ScalarTrack.constant(0.18)));
        return b.build();
    }

    private static SceneDefinition frozenCollapse() {
        double d = 700;
        SceneBuilder b = base(FROZEN, d);
        b.add(atmosphere("blizzard", 0, d, 0xFFB2E0FF, 0xFFA5C8DC, 0.075, 0.95));
        b.add(emitter("snowstorm", 0, d, "snowflake", new Vec3d(0, 9, 14), 720, 20, 0.25));
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2.0 / 28.0;
            Vec3d pos = new Vec3d(Math.cos(angle) * 8.5, 0, 14 + Math.sin(angle) * 8.5);
            b.add(growingBlock("ice_spire_" + i, 120 + i * 2.0, d, Blocks.PACKED_ICE.getDefaultState(), pos,
                    new Vec3d(0.9, 3.0 + (i % 5) * 0.55, 0.9), 48));
        }
        b.add(actor("frost_guardian", 260, d, Identifier.ofVanilla("stray"), new Vec3d(0, -4, 14),
                new Vec3d(4, 4, 4), rise(0, 120, 4)));
        b.add(ring("ice_fracture", 190, 430, new Vec3d(0, 0.08, 14), 1, 24, 0xFFC3F4FF));
        b.add(cameraShake("ice_quake", 195, 410, 0.16, 1.7));
        return b.build();
    }

    private static SceneDefinition desertRelic() {
        double d = 700;
        SceneBuilder b = base(DESERT, d);
        b.add(atmosphere("sandstorm", 0, d, 0xFFE0A455, 0xFFAD7437, 0.09, 1.0));
        b.add(emitter("sand_particles", 0, d, "ash", new Vec3d(0, 5, 18), 760, 22, 0.8));
        int n = 0;
        for (int y = 0; y < 7; y++) {
            int half = 7 - y;
            for (int x = -half; x <= half; x += 2) {
                b.add(growingBlock("relic_" + n++, 30 + y * 18 + (x + half) * 2, d,
                        Blocks.CUT_SANDSTONE.getDefaultState(), new Vec3d(x, y, 19 + y),
                        new Vec3d(1, 1, 1), 44));
            }
        }
        b.add(actor("relic_guardian", 280, d, Identifier.ofVanilla("husk"), new Vec3d(0, 1, 14),
                new Vec3d(2.8, 2.8, 2.8), MotionCurve.none()));
        b.add(new SceneElement.Beam("sun_column", 220, d, 30, ConflictPolicy.ALLOW,
                new Vec3d(0, 65, 25), new Vec3d(0, 6, 25), 0.75, TransformTrack.identity(),
                MotionCurve.none(), ColorTrack.constant(0xFFFFE0A0)));
        return b.build();
    }

    private static SceneDefinition underwaterLeviathan() {
        double d = 700;
        SceneBuilder b = base(OCEAN, d);
        b.add(atmosphere("deep_ocean", 0, d, 0xFF145171, 0xFF082C41, 0.085, 0.08));
        b.add(emitter("bubbles", 0, d, "bubble", new Vec3d(0, 7, 15), 390, 18, 0.2));
        PathTrack swim = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-40, 14, 42), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(190, new Vec3d(-10, 10, 22), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(430, new Vec3d(18, 12, 8), Easing.EASE_IN_OUT_SINE),
                PathTrack.Point.at(d, new Vec3d(42, 18, 32)));
        b.add(actor("leviathan", 0, d, Identifier.ofVanilla("guardian"), Vec3d.ZERO,
                new Vec3d(6.0, 6.0, 6.0), swim.asMotionCurve(true, 8)));
        b.add(new ComplexElement.Shadow("leviathan_shadow", 0, d, 2, ConflictPolicy.ALLOW, null,
                ComplexElement.ShadowMode.BLOB, null, new Vec3d(0, 0.03, 14), AdvancedTransformTrack.identity(),
                MotionCurve.none(), ScalarTrack.constant(0.3), ScalarTrack.constant(0.9), ScalarTrack.constant(10), 320));
        return b.build();
    }

    private static SceneDefinition natureReclamation() {
        double d = 700;
        SceneBuilder b = base(NATURE, d);
        b.add(atmosphere("spring_air", 0, d, 0xFF92D7A5, 0xFFB8DDB6, 0.018, 0.15));
        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2.0 / 18.0;
            double radius = 5 + (i % 4) * 2.2;
            Vec3d trunk = new Vec3d(Math.cos(angle) * radius, 0, 14 + Math.sin(angle) * radius);
            double start = 20 + i * 11;
            b.add(growingBlock("trunk_" + i, start, d, Blocks.OAK_LOG.getDefaultState(), trunk,
                    new Vec3d(0.65, 5.0, 0.65), 75));
            b.add(growingBlock("leaves_" + i, start + 38, d, Blocks.OAK_LEAVES.getDefaultState(), trunk.add(0, 5, 0),
                    new Vec3d(3.2, 2.0, 3.2), 62));
        }
        b.add(emitter("life_spores", 90, d, "happy_villager", new Vec3d(0, 5, 14), 210, 14, 0.08));
        b.add(ring("life_wave", 55, 310, new Vec3d(0, 0.1, 14), 1, 29, 0xFF82FF95));
        return b.build();
    }

    private static SceneDefinition moonbaseEvacuation() {
        double d = 700;
        SceneBuilder b = base(MOONBASE, d);
        b.add(atmosphere("moon_haze", 0, d, 0xFF1B2231, 0xFF252A36, 0.012, 0));
        for (int x = -8; x <= 8; x += 2) {
            b.add(growingBlock("base_tile_" + x, 0, d, Blocks.IRON_BLOCK.getDefaultState(), new Vec3d(x, 0, 16),
                    new Vec3d(1.8, 0.3, 4.0), 15));
        }
        b.add(crowd("evacuees", 0, 520, Identifier.ofVanilla("villager"), 80, 18, 15));
        b.add(new SceneElement.HudText("evac_countdown", 0, d, 60, ConflictPolicy.REPLACE_LOWER,
                "EVACUATION // T-{time}", Identifier.ofVanilla("default"), 0.5, 0.14, 0, 0,
                SceneElement.HorizontalAlign.CENTER, ScalarTrack.constant(1.8), ColorTrack.constant(0xFFFF6262), true));
        b.add(emitter("dome_blast", 500, 650, "poof", new Vec3d(0, 3, 16), 950, 12, 2.2));
        b.add(ring("dome_shockwave", 510, 690, new Vec3d(0, 0.15, 16), 1, 48, 0xFFFFC075));
        b.add(cameraShake("moonbase_blast", 500, 650, 0.34, 3.0));
        return b.build();
    }

    private static SceneDefinition realityFracture() {
        double d = 1200;
        SceneBuilder b = base(REALITY, d);
        b.add(atmosphere("fracture_fog", 0, d, 0xFF41205F, 0xFF1A0E2A, 0.048, 0.4));
        b.add(new AdvancedEventElement.Sky("fracture_sky", 0, d, 70, ConflictPolicy.REPLACE_LOWER,
                id("sky/reality_fracture"), ColorTrack.constant(0xFFFF4F94), ColorTrack.constant(0xFF120020),
                ScalarTrack.constant(0.1), ScalarTrack.constant(1.5), ScalarTrack.constant(0.8),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(500, 1.0)),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(d, 180.0)), Map.of("fractures", "procedural")));
        b.add(new AdvancedEventElement.PlayerControl("director_control", 0, d, 80, ConflictPolicy.REPLACE_LOWER,
                false, false, false, false, ScalarTrack.constant(1), ScalarTrack.constant(1),
                ScalarTrack.of(Keyframe.at(0, 70.0), Keyframe.at(260, 88.0), Keyframe.at(560, 62.0), Keyframe.at(d, 70.0)),
                true, true));
        PathTrack corePath = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-12, 14, 30)),
                PathTrack.Point.at(400, new Vec3d(0, 18, 18)),
                PathTrack.Point.at(800, new Vec3d(12, 12, 26)),
                PathTrack.Point.at(d, new Vec3d(0, 22, 16)));
        b.add(node("fracture_core", 0, d, corePath.asMotionCurve(true, 0)));
        b.add(mesh("fracture_core_mesh", 0, d, "fracture_core", id("model/reality_core"), new Vec3d(5, 5, 5)));
        b.add(attachEmitter("core_particles", 0, d, "fracture_core", Vec3d.ZERO, "end_rod", 760, 5.5, 0.9));
        b.add(attachLight("core_light", 0, d, "fracture_core", Vec3d.ZERO, 0xFFFF4FD8, 9, 44));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 2.0 / 8.0;
            b.add(attachBeam("fracture_ray_" + i, 160 + i * 22, d, "fracture_core", Vec3d.ZERO,
                    new Vec3d(Math.cos(angle) * 36, (i % 3 - 1) * 8, Math.sin(angle) * 36), 0.22, 0xFFFF72EF));
        }
        b.add(crowd("witnesses", 120, 900, Identifier.ofVanilla("villager"), 96, 20, 18));
        b.add(new ComplexElement.Volume("reality_volume", 100, d, 30, ConflictPolicy.ALLOW, null,
                ComplexElement.VolumeShape.SPHERE, id("volume/reality"), new Vec3d(0, 14, 20),
                new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                        Vec3Track.angles(Keyframe.at(0, Vec3d.ZERO), Keyframe.at(d, new Vec3d(0, 360, 0))),
                        Vec3Track.of(Keyframe.at(0, new Vec3d(1, 1, 1)), Keyframe.at(600, new Vec3d(18, 18, 18))),
                        Vec3Track.constant(Vec3d.ZERO)), MotionCurve.none(), ColorTrack.constant(0x88D957FF),
                ScalarTrack.constant(0.35), ScalarTrack.constant(2.8), ScalarTrack.constant(0.9),
                ScalarTrack.constant(1.4), 500, Map.of("raymarch_steps", "64")));
        b.add(new AdvancedEventElement.AudioLayer("reality_music", 0, d, 5, ConflictPolicy.ALLOW,
                Identifier.ofVanilla("music_disc_otherside"), ScalarTrack.constant(0.8), ScalarTrack.constant(1),
                ScalarTrack.constant(0), true, true, 80, 100, Map.of("stem", "main")));
        b.add(new EventElement.Overlay("fracture_grade", 0, d, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(0xFF7F225F), ScalarTrack.constant(0.05), ScalarTrack.constant(0.08),
                ScalarTrack.constant(0.12), ScalarTrack.constant(0.22)));
        return b.build();
    }

    private static SceneDefinition stressTimeline() {
        SceneBuilder b = base(STRESS_TIMELINE, 12000);
        for (int i = 0; i < 8000; i++) {
            double start = (i * 1.47) % 11980.0;
            b.add(new EventElement.Marker("cue_" + i, start, start + 2, i & 3, ConflictPolicy.ALLOW,
                    "stress_cue", Map.of("index", Integer.toString(i))));
        }
        for (int i = 0; i < 200; i++) {
            double start = i * 58.0;
            b.add(ring("timeline_ring_" + i, start, Math.min(12000, start + 70),
                    new Vec3d(0, 0.1, 12), 0.5, 12 + (i % 5) * 3, 0x8090D7FF));
        }
        return b.build();
    }

    private static SceneDefinition stressInstances() {
        SceneBuilder b = base(STRESS_INSTANCES, 1200);
        ArrayList<ComplexElement.InstanceSpec> values = new ArrayList<>(20000);
        for (int i = 0; i < 20000; i++) {
            final int index = i;
            double angle = index * 0.61803398875;
            double radius = 5 + (index % 320) * 0.16;
            Vec3d pos = new Vec3d(Math.cos(angle) * radius, (index % 80) * 0.12 + 3, 20 + Math.sin(angle) * radius);
            values.add(new ComplexElement.InstanceSpec("debris_" + index, pos, AdvancedTransformTrack.identity(),
                    (tick, seed) -> new Transform(
                            new Vec3d(0, Math.sin((tick + index) * 0.03) * 0.8, 0),
                            new Vec3d(tick * (index % 7 + 1) * 0.12, tick * 0.2, 0),
                            new Vec3d(1, 1, 1)), ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1),
                    index % 60, 0.8 + (index % 5) * 0.1, index % 8));
        }
        b.add(new ComplexElement.InstanceBatch("debris_storm", 0, 1200, 20, ConflictPolicy.ALLOW, null,
                id("model/stress_debris"), id("material/debris"), Vec3d.ZERO, AdvancedTransformTrack.identity(),
                MotionCurve.none(), values, true, 800, 0, Map.of("instances", "20000")));
        b.add(emitter("debris_dust", 0, 1200, "ash", new Vec3d(0, 8, 20), 900, 42, 0.4));
        return b.build();
    }

    private static SceneDefinition stressCrowd() {
        SceneBuilder b = base(STRESS_CROWD, 1200);
        b.add(crowd("crowd_500", 0, 1200, Identifier.ofVanilla("villager"), 500, 35, 18));
        b.add(new SceneElement.HudText("crowd_label", 0, 1200, 50, ConflictPolicy.REPLACE_LOWER,
                "CROWD STRESS // 500 agents", Identifier.ofVanilla("default"), 0.5, 0.1, 0, 0,
                SceneElement.HorizontalAlign.CENTER, ScalarTrack.constant(1.3), ColorTrack.constant(0xFFFFFFFF), true));
        return b.build();
    }

    private static SceneDefinition stressAttachments() {
        SceneBuilder b = base(STRESS_ATTACHMENTS, 1200);
        PathTrack path = PathTrack.catmullRom(
                PathTrack.Point.at(0, new Vec3d(-10, 12, 22)), PathTrack.Point.at(400, new Vec3d(8, 18, 15)),
                PathTrack.Point.at(800, new Vec3d(-5, 10, 8)), PathTrack.Point.at(1200, new Vec3d(0, 16, 18)));
        b.add(node("attachment_root", 0, 1200, path.asMotionCurve(true, 0)));
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2.0 / 64.0;
            Vec3d endpoint = new Vec3d(Math.cos(angle) * (8 + i % 5), (i % 7 - 3) * 1.5,
                    Math.sin(angle) * (8 + i % 5));
            b.add(attachBeam("attached_beam_" + i, 0, 1200, "attachment_root", Vec3d.ZERO, endpoint,
                    0.045 + (i % 3) * 0.018, 0xFF80D8FF));
        }
        b.add(attachEmitter("attached_emitter", 0, 1200, "attachment_root", Vec3d.ZERO, "electric_spark", 1000, 7, 1));
        b.add(attachLight("attached_light", 0, 1200, "attachment_root", Vec3d.ZERO, 0xFF80D8FF, 10, 48));
        return b.build();
    }

    private static SceneBuilder base(Identifier id, double duration) {
        return SceneBuilder.create(id).duration(duration).priority(100).meta("showcase", "true");
    }

    private static EventProgram.Phase phase(String id, List<EventProgram.Action> enter, EventProgram.Transition... transitions) {
        return new EventProgram.Phase(id, enter, List.of(), List.of(transitions));
    }

    private static EventProgram.Transition transition(String target, EventProgram.Condition condition) {
        return new EventProgram.Transition(target, 0, condition, List.of());
    }

    private static List<EventProgram.Action> swap(Identifier previous, Identifier next) {
        return List.of(EventProgram.Action.stop(previous), EventProgram.Action.play(next));
    }

    private static void register(String name, SceneDefinition definition) {
        CineFxApi.register(definition);
        CATALOG.put(name, definition.id());
    }

    private static EventElement.Atmosphere atmosphere(String key, double start, double end, int sky, int fog,
                                                      double density, double wind) {
        return new EventElement.Atmosphere(key, start, end, 10, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(sky), ColorTrack.constant(fog), ScalarTrack.constant(density),
                ScalarTrack.constant(0), ScalarTrack.constant(220), ScalarTrack.constant(0.5),
                ScalarTrack.constant(1), ScalarTrack.constant(wind));
    }

    private static AdvancedEventElement.Sky sky(String key, double start, double end, int horizon, int zenith,
                                                double eclipse, double aurora) {
        return new AdvancedEventElement.Sky(key, start, end, 20, ConflictPolicy.REPLACE_LOWER, null,
                ColorTrack.constant(horizon), ColorTrack.constant(zenith), ScalarTrack.constant(0.6),
                ScalarTrack.constant(1.2), ScalarTrack.constant(eclipse), ScalarTrack.constant(aurora),
                ScalarTrack.of(Keyframe.at(0, 0.0), Keyframe.at(Math.max(1, end - start), 25.0)), Map.of());
    }

    private static EventElement.Emitter emitter(String key, double start, double end, String particle, Vec3d offset,
                                                double rate, double spread, double speed) {
        return new EventElement.Emitter(key, start, end, 20, ConflictPolicy.ALLOW, Identifier.ofVanilla(particle),
                EventElement.EmitterShape.SPHERE, offset, TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(rate), ScalarTrack.constant(spread), ScalarTrack.constant(speed),
                ScalarTrack.constant(1), ColorTrack.constant(0xFFFFFFFF), 2048);
    }

    private static SceneElement.Ring ring(String key, double start, double end, Vec3d center,
                                          double fromRadius, double toRadius, int color) {
        return new SceneElement.Ring(key, start, end, 30, ConflictPolicy.ALLOW, center,
                ScalarTrack.of(Keyframe.at(0, fromRadius, Easing.EASE_OUT_CUBIC),
                        Keyframe.at(Math.max(1, end - start), toRadius)), ScalarTrack.constant(0.35), 64,
                TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(color));
    }

    private static SceneElement.Ring portalRing(String key, double start, double end, double radius,
                                                double thickness, int color) {
        return new SceneElement.Ring(key, start, end, 30, ConflictPolicy.ALLOW, new Vec3d(0, 22, 28),
                ScalarTrack.of(Keyframe.at(0, 0.1, Easing.EASE_OUT_BACK), Keyframe.at(150, radius)),
                ScalarTrack.constant(thickness), 96,
                TransformTrack.constant(new Transform(Vec3d.ZERO, new Vec3d(90, 0, 0), new Vec3d(1, 1, 1))),
                MotionCurve.none(), ColorTrack.constant(color));
    }

    private static SceneElement.Block growingBlock(String key, double start, double end, BlockState state,
                                                   Vec3d offset, Vec3d finalScale, double growTicks) {
        TransformTrack track = TransformTrack.of(
                Keyframe.at(0, new Transform(Vec3d.ZERO, Vec3d.ZERO, new Vec3d(0.05, 0.05, 0.05)), Easing.EASE_OUT_BACK),
                Keyframe.at(growTicks, new Transform(Vec3d.ZERO, Vec3d.ZERO, finalScale)));
        return new SceneElement.Block(key, start, end, 10, ConflictPolicy.ALLOW, state, null, offset,
                track, MotionCurve.none(), false, 0, false);
    }

    private static ComplexElement.Node node(String key, double start, double end, MotionCurve motion) {
        return new ComplexElement.Node(key, start, end, 30, ConflictPolicy.ALLOW, null, Vec3d.ZERO,
                AdvancedTransformTrack.identity(), motion, true);
    }

    private static ComplexElement.Mesh mesh(String key, double start, double end, String parent,
                                            Identifier model, Vec3d scale) {
        return new ComplexElement.Mesh(key, start, end, 40, ConflictPolicy.ALLOW, parent, model, null, Vec3d.ZERO,
                new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO),
                        Vec3Track.angles(Keyframe.at(0, Vec3d.ZERO)), Vec3Track.constant(scale), Vec3Track.constant(Vec3d.ZERO)),
                MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF), ScalarTrack.constant(1), ScalarTrack.constant(0.2),
                true, 900, Map.of());
    }

    private static ComplexElement.Actor actor(String key, double start, double end, Identifier type, Vec3d offset,
                                              Vec3d scale, MotionCurve motion) {
        return new ComplexElement.Actor(key, start, end, 40, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.ENTITY, type, null, null, Map.of(), offset,
                new AdvancedTransformTrack(Vec3Track.constant(Vec3d.ZERO), Vec3Track.angles(Keyframe.at(0, Vec3d.ZERO)),
                        Vec3Track.constant(scale), Vec3Track.constant(Vec3d.ZERO)), motion,
                List.of(walkAnimation(0.65)), List.of(), List.of(), null, ColorTrack.constant(0xFFFFFFFF),
                ScalarTrack.constant(1), ScalarTrack.constant(0), true, 600);
    }

    private static ComplexElement.AnimationLayer walkAnimation(double speed) {
        return new ComplexElement.AnimationLayer(Identifier.ofVanilla("walk"), ScalarTrack.constant(1),
                ScalarTrack.constant(speed), 0, true, ComplexElement.BlendMode.OVERRIDE, Map.of());
    }

    private static AdvancedEventElement.Crowd crowd(String key, double start, double end, Identifier entityType,
                                                     int count, double radius, double zCenter) {
        ArrayList<AdvancedEventElement.CrowdAgent> agents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297;
            double r = 2.5 + radius * Math.sqrt((i + 0.5) / count);
            agents.add(new AdvancedEventElement.CrowdAgent(
                    new Vec3d(Math.cos(angle) * r, 0, zCenter + Math.sin(angle) * r),
                    Math.toDegrees(-angle), i % 30, i % 6));
        }
        return new AdvancedEventElement.Crowd(key, start, end, 15, ConflictPolicy.ALLOW, null,
                ComplexElement.ActorKind.ENTITY, entityType, "Crowd", agents, List.of(walkAnimation(0.6)),
                Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF),
                ScalarTrack.constant(1), true, 600, Map.of());
    }

    private static AdvancedEventElement.Attachment attachLight(String key, double start, double end, String parent,
                                                                Vec3d offset, int color, double intensity, double radius) {
        return new AdvancedEventElement.Attachment(key, start, end, 40, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, offset, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.LightPayload(SceneLight.Kind.POINT, new Vec3d(0, -1, 0),
                        ColorTrack.constant(color), ScalarTrack.constant(intensity), ScalarTrack.constant(radius), 0, 180), 900);
    }

    private static AdvancedEventElement.Attachment attachEmitter(String key, double start, double end, String parent,
                                                                  Vec3d offset, String particle, double rate,
                                                                  double spread, double speed) {
        return new AdvancedEventElement.Attachment(key, start, end, 40, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, offset, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.EmitterPayload(Identifier.ofVanilla(particle), EventElement.EmitterShape.SPHERE,
                        ScalarTrack.constant(rate), ScalarTrack.constant(spread), ScalarTrack.constant(speed),
                        ScalarTrack.constant(1), ColorTrack.constant(0xFFFFFFFF), 2048), 900);
    }

    private static AdvancedEventElement.Attachment attachBeam(String key, double start, double end, String parent,
                                                               Vec3d from, Vec3d to, double width, int color) {
        return new AdvancedEventElement.Attachment(key, start, end, 50, ConflictPolicy.ALLOW, parent, null,
                AdvancedEventElement.InheritMode.FULL, Vec3d.ZERO, AdvancedTransformTrack.identity(), MotionCurve.none(),
                new AdvancedEventElement.BeamPayload(from, to, ScalarTrack.constant(width), ColorTrack.constant(color)), 1000);
    }

    private static EventElement.Camera cameraShake(String key, double start, double end,
                                                   double translation, double rotation) {
        return new EventElement.Camera(key, start, end, 60, ConflictPolicy.REPLACE_LOWER,
                EventElement.CameraMode.ADDITIVE, Vec3d.ZERO, TransformTrack.identity(), MotionCurve.none(),
                ScalarTrack.constant(translation), ScalarTrack.constant(rotation), ScalarTrack.constant(1.8), null);
    }

    private static SceneElement.WorldText worldText(String key, double start, double end, String text,
                                                    Vec3d offset, double scale, int color) {
        return new SceneElement.WorldText(key, start, end, 50, ConflictPolicy.REPLACE_LOWER, text,
                Identifier.ofVanilla("default"), offset, TransformTrack.constant(Transform.scale(scale)), MotionCurve.none(),
                ColorTrack.constant(color), 0x60000000, true, true, true);
    }

    private static MotionCurve rise(double startTick, double endTick, double height) {
        return (tick, seed) -> {
            double t = Math.max(0.0, Math.min(1.0, (tick - startTick) / Math.max(1.0, endTick - startTick)));
            double eased = Easing.EASE_OUT_CUBIC.apply(t);
            return Transform.translation(0, height * eased, 0);
        };
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
