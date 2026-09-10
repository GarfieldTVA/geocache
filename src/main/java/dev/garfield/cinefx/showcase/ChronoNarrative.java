package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.UltraEventElement;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static dev.garfield.cinefx.showcase.NarrativeEventKit.*;
import static dev.garfield.cinefx.showcase.NarrativeSetPieces.*;

/** Chrono Nexus: One Minute Too Many. The same world state starts repeating until four timelines collide. */
final class ChronoNarrative {
    private static final Identifier DRONE = id("model/chrono_drone");
    private static final Identifier CORE = id("model/zeropoint_core");
    private static final Identifier PYLON = id("model/stabilizer_pylon");

    private ChronoNarrative() { }

    static SceneDefinition build(Identifier sceneId) {
        double d = 7800.0;
        SceneBuilder b = base(sceneId, d, "chrono-nexus-one-minute-too-many");
        ambience(b, "nx", d, 0xFF7DA6C7, 0xFF253F5B, 0x081569A8);
        b.add(loop("nx_clock_hum", 0, 7450, Identifier.ofVanilla("block.beacon.ambient"), 0.20, 0.88));

        // 0:00–0:30 a harmless demonstration with one drone, one beacon and calm technicians.
        b.add(crowd("nx_technicians_original", 0, 1280, Identifier.ofVanilla("villager"), 16, 11, 18,
                "wander", 1.8, 0.014, "walk"));
        movingMesh(b, "nx_primary_drone", 0, 6900, DRONE,
                route(6900, new Vec3d(-8, 4, 18), new Vec3d(7, 5, 22), new Vec3d(9, 6, 14), new Vec3d(-6, 4, 12), new Vec3d(-8, 4, 18)),
                new Vec3d(0.95, 0.95, 0.95), 0xFFC2FBFF, 0.62, "crystal", 8);
        b.add(mesh("nx_demo_beacon", 0, 6900, null, PYLON, new Vec3d(0, 0, 18),
                new Vec3d(1.0, 1.0, 1.0), 0xFF8CF3FF, 0.7, "crystal"));
        b.add(text("nx_intro", 70, 470, "CHRONO TEST // 60 SECOND LOCAL REWIND", new Vec3d(0, 8.5, 14), 0xFFC9F8FF));
        b.add(camera("nx_establish", 0, 250,
                route(250, new Vec3d(-15, 7, 3), new Vec3d(-8, 10, 12), new Vec3d(8, 10, 20), new Vec3d(16, 7, 25)),
                new Vec3d(0, 3, 18), 69, 59, 0.008, 0.04));
        b.add(control("nx_establish_lock", 0, 250));

        // 0:30–1:00 planned rewind: a 1400-block test structure explodes, freezes, then rebuilds exactly backwards.
        b.add(gravityExplosion("nx_test_explosion", 560, 900, new Vec3d(11, 0, 19), 1400,
                new Vec3d(10, 8, 10), 6.0, 8.5, 0xFFB8CDD8, true));
        b.add(sound("nx_test_boom", 580, Identifier.ofVanilla("entity.generic.explode"), new Vec3d(11, 2, 19), 1.2f, 0.85f));
        b.add(text("nx_rewind_count", 760, 1040, "REWIND // ARMED", new Vec3d(0, 9, 14), 0xFFA6F7FF));
        b.add(rewindExplosion("nx_test_rewind", 900, 1220, new Vec3d(11, 0, 19), 1400,
                new Vec3d(10, 8, 10), 6.0, 8.5, 0xFFB8CDD8));
        b.add(field("nx_reverse_particles", 880, 1250, Identifier.ofVanilla("end_rod"), new Vec3d(11, 5, 19),
                1700, 0xFFC6FCFF, 38000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 3.8, 25, 10))));
        b.add(sound("nx_rewind_chime", 1180, Identifier.ofVanilla("block.amethyst_block.chime"), new Vec3d(0, 2, 18), 1.0f, 1.3f));
        b.add(text("nx_success", 1190, 1450, "TEST COMPLETE // SUCCESS", new Vec3d(0, 9, 14), 0xFFA8FFD2));

        // 1:00–1:25 the reset happens by itself; people are duplicated at different temporal offsets.
        b.add(rewindExplosion("nx_unauthorized_rewind", 1310, 1550, new Vec3d(11, 0, 19), 900,
                new Vec3d(8, 6, 8), 5.0, 7.5, 0xFF9EBCCA));
        b.add(crowd("nx_technicians_echo", 1350, 2920, Identifier.ofVanilla("villager"), 14, 10, 18,
                "orbit", 3.1, -0.028, "walk"));
        b.add(text("nx_uncommanded", 1360, 1700, "UNCOMMANDED RESET", new Vec3d(0, 9.5, 14), 0xFFFFA7D9));
        movingMesh(b, "nx_echo_drone", 1420, 6800, DRONE,
                route(5380, new Vec3d(8, 5, 18), new Vec3d(-7, 6, 22), new Vec3d(-10, 5, 14), new Vec3d(7, 4, 12), new Vec3d(8, 5, 18)),
                new Vec3d(0.9, 0.9, 0.9), 0xFFFF92E5, 0.55, "crystal", -9);

        // 1:25–2:00 void echo overwrites only one quadrant.
        sector(b, "nx_void_sector", 1700, 6000, Blocks.OBSIDIAN.getDefaultState(), new Vec3d(-17, 0, 31), 18, 0xFFFF61E8);
        b.add(portal("nx_void_echo", 1700, 6000, new Vec3d(-18, 8, 32), new Vec3d(8, 11, 0.2), 0xFFFF61E8, 0.8));
        b.add(field("nx_void_drift", 1760, 6050, Identifier.ofVanilla("portal"), new Vec3d(-17, 5, 31),
                1100, 0xFFFF63E6, 28000, List.of(force(UltraEventElement.ForceKind.VORTEX, 1.5, 18, 20))));
        b.add(text("nx_void_label", 1820, 2160, "TIMELINE ECHO // VOID", new Vec3d(-8, 10, 20), 0xFFFFB2EF));

        // 2:00–2:35 frozen echo: same logic but a visibly different material/motion language.
        sector(b, "nx_ice_sector", 2400, 6150, Blocks.PACKED_ICE.getDefaultState(), new Vec3d(17, 0, 31), 20, 0xFFA8F2FF);
        b.add(field("nx_snow_spiral", 2440, 6200, Identifier.ofVanilla("snowflake"), new Vec3d(17, 7, 31),
                1200, 0xFFD6FAFF, 30000, List.of(force(UltraEventElement.ForceKind.VORTEX, 1.1, 20, 31),
                        directional(new Vec3d(0.3, -0.3, 0.1), 0.5, 24, 32))));
        b.add(text("nx_ice_label", 2500, 2840, "TIMELINE ECHO // FROZEN", new Vec3d(8, 10, 20), 0xFFC9F8FF));

        // 2:35–3:10 desert echo sends actual chunks across the area instead of another portal-only beat.
        sector(b, "nx_desert_sector", 3100, 6300, Blocks.CUT_SANDSTONE.getDefaultState(), new Vec3d(-17, 0, 5), 20, 0xFFFFC375);
        b.add(gravityExplosion("nx_desert_burst", 3180, 3660, new Vec3d(-17, 0, 5), 1150,
                new Vec3d(16, 7, 16), 4.0, 5.6, 0xFFD9A866, true));
        b.add(text("nx_desert_label", 3200, 3540, "TIMELINE ECHO // DESERT", new Vec3d(-8, 10, 13), 0xFFFFD3A0));

        // 3:10–3:45 neon future brings a fleet of drones and geometry, not just a tint swap.
        sector(b, "nx_neon_sector", 3800, 6500, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), new Vec3d(17, 0, 5), 22, 0xFF65F4FF);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6.0;
            movingMesh(b, "nx_future_drone_" + i, 3820 + i * 20, 6250, DRONE,
                    route(2400, new Vec3d(17 + Math.cos(a) * 18, 5 + (i % 3) * 2, 5 + Math.sin(a) * 18),
                            new Vec3d(Math.cos(a + 1.2) * 14, 8, 18 + Math.sin(a + 1.2) * 14),
                            new Vec3d(Math.cos(a + 2.5) * 9, 9, 18 + Math.sin(a + 2.5) * 9)),
                    new Vec3d(0.78, 0.78, 0.78), 0xFF71F7FF, 0.72, "crystal", 14);
        }
        b.add(text("nx_neon_label", 3920, 4260, "TIMELINE ECHO // FUTURE", new Vec3d(8, 10, 13), 0xFF9CFAFF));

        // 3:45–4:20 timeline collision: 1800 fragments orbit while duplicate actors cross paths.
        b.add(orbitalDebris("nx_collision_debris", 4500, 5400, new Vec3d(0, 11, 18), 1800, 28, 0xFF9DDDEC));
        b.add(crowd("nx_loop_a", 4480, 5320, Identifier.ofVanilla("villager"), 20, 15, 18,
                "march", 8, 0.060, "run"));
        b.add(crowd("nx_loop_b", 4480, 5320, Identifier.ofVanilla("villager"), 20, 15, 18,
                "march", 8, -0.055, "run"));
        b.add(camera("nx_collision_camera", 4620, 4910,
                route(290, new Vec3d(-22, 12, 5), new Vec3d(-7, 18, 14), new Vec3d(9, 20, 21), new Vec3d(23, 11, 31)),
                new Vec3d(0, 11, 18), 66, 76, 0.055, 0.36));
        b.add(control("nx_collision_lock", 4620, 4910));

        // 4:20–4:55 failed merge: all four sectors are ripped into a single singularity.
        b.add(mesh("nx_merge_core", 5050, 6000, null, CORE, new Vec3d(0, 12, 18),
                new Vec3d(3.2, 3.2, 3.2), 0xFFE5FDFF, 1.0, "crystal"));
        b.add(light("nx_merge_light", 5050, 6000, "nx_merge_core", Vec3d.ZERO, 0xFFB8FBFF, 11, 48, 1.0));
        b.add(portal("nx_merge_portal", 5080, 6000, new Vec3d(0, 13, 18), new Vec3d(17, 19, 0.3), 0xFFC25FFF, 1.0));
        b.add(singularityLift("nx_world_pull", 5150, 5850, new Vec3d(0, 0, 18), 2600, 35, 28, 0xFF9CAEB9));
        b.add(field("nx_merge_pull", 5120, 5900, Identifier.ofVanilla("end_rod"), new Vec3d(0, 13, 18),
                2800, 0xFFE1FDFF, 76000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 5.5, 52, 60),
                        force(UltraEventElement.ForceKind.VORTEX, 3.5, 45, 61))));

        // 4:55–5:35 manual rewind: one giant deterministic set piece plays backwards.
        b.add(text("nx_manual_rewind", 5940, 6220, "MANUAL REWIND // ALL LOOPS", new Vec3d(0, 10, 14), 0xFFFFFFFF));
        b.add(rewindExplosion("nx_world_rewind", 5960, 6760, new Vec3d(0, 0, 18), 3000,
                new Vec3d(40, 15, 40), 9.0, 8.0, 0xFFADC5D0));
        b.add(field("nx_rewind_stream", 5960, 6760, Identifier.ofVanilla("electric_spark"), new Vec3d(0, 10, 18),
                3200, 0xFFC6FCFF, 80000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 6.2, 55, 70))));
        b.add(camera("nx_rewind_camera", 6120, 6450,
                route(330, new Vec3d(24, 12, 4), new Vec3d(10, 19, 12), new Vec3d(-9, 22, 22), new Vec3d(-24, 11, 31)),
                new Vec3d(0, 9, 18), 64, 82, 0.08, 0.50));
        b.add(control("nx_rewind_lock", 6120, 6450));

        // 5:35–6:05 original minute is restored except one extra drone.
        b.add(crowd("nx_technicians_restored", 6760, d, Identifier.ofVanilla("villager"), 16, 11, 18,
                "wander", 1.8, 0.014, "walk"));
        movingMesh(b, "nx_extra_drone", 6740, 7420, DRONE,
                route(680, new Vec3d(10, 5, 20), new Vec3d(7, 7, 24), new Vec3d(3, 9, 20), new Vec3d(0, 12, 18)),
                new Vec3d(0.85, 0.85, 0.85), 0xFFFF8EE2, 0.62, "crystal", -7);
        b.add(text("nx_anomaly", 6890, 7180, "ONE OBJECT DOES NOT BELONG", new Vec3d(0, 9.5, 14), 0xFFFFB4EB));
        b.add(portal("nx_cleanup_gate", 7040, 7480, new Vec3d(0, 12, 18), new Vec3d(6, 8, 0.2), 0xFFFF75E2, 0.72));

        // 6:05–6:30 clean timeline. The anomaly flies away, portal closes, normal route resumes.
        movingMesh(b, "nx_drone_exit", 7240, 7550, DRONE,
                route(310, new Vec3d(3, 9, 20), new Vec3d(1, 11, 19), new Vec3d(0, 12, 18), new Vec3d(0, 12, 18)),
                new Vec3d(0.75, 0.75, 0.75), 0xFFFF94E5, 0.72, "crystal", 0);
        b.add(material("nx_gate_dissolve", 7440, 7700, "nx_cleanup_gate", UltraEventElement.MaterialMode.DISSOLVE, 0xFFFFFFFF));
        b.add(text("nx_final", 7520, d, "TIMELINE 01 // STABLE", new Vec3d(0, 9, 14), 0xFFAFFFF0));
        b.add(sound("nx_final_chime", 7560, Identifier.ofVanilla("block.amethyst_block.chime"), new Vec3d(0, 2, 18), 1.0f, 1.25f));
        return b.build();
    }
}
