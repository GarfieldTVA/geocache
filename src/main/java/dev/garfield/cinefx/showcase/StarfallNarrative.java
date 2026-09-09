package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.UltraEventElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static dev.garfield.cinefx.showcase.NarrativeEventKit.*;
import static dev.garfield.cinefx.showcase.NarrativeSetPieces.*;

/** Starfall: Last Evacuation. An invasion with readable causes, counter-actions and a chain-destruction payoff. */
final class StarfallNarrative {
    private static final Identifier FIGHTER = id("model/alien_fighter");
    private static final Identifier DRONE = id("model/chrono_drone");
    private static final Identifier PYLON = id("model/stabilizer_pylon");
    private static final Identifier MOTHERSHIP = id("model/mothership");

    private StarfallNarrative() { }

    static SceneDefinition build(Identifier sceneId) {
        double d = 6600.0;
        SceneBuilder b = base(sceneId, d, "starfall-last-evacuation");
        ambience(b, "inv", d, 0xFF7489A6, 0xFF26344A, 0x08193C72);
        b.add(loop("inv_distant_hum", 0, 6200, Identifier.ofVanilla("block.beacon.ambient"), 0.22, 0.68));

        // 0:00–0:25 false calm.
        b.add(crowd("inv_civilians_normal", 0, 620, Identifier.ofVanilla("villager"), 30, 17, 18,
                "wander", 2.6, 0.018, "walk"));
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1 : 1;
            movingMesh(b, "inv_patrol_drone_" + i, 0, 1350, DRONE,
                    route(1350, new Vec3d(side * 12, 5, 8), new Vec3d(side * 8, 6, 20),
                            new Vec3d(-side * 5, 5, 25), new Vec3d(side * 13, 6, 14)),
                    new Vec3d(0.8, 0.8, 0.8), 0xFFBDF8FF, 0.38, "crystal", side * 5));
        }
        b.add(text("inv_day_label", 60, 420, "STARFALL OUTPOST // ALL CLEAR", new Vec3d(0, 8, 14), 0xFFCFE9FF));

        // 0:25–0:55 three scouts make separate low passes.
        Vec3d[][] passes = {
                {new Vec3d(-70, 18, -40), new Vec3d(-24, 12, 5), new Vec3d(20, 9, 23), new Vec3d(70, 17, 48)},
                {new Vec3d(65, 24, -28), new Vec3d(22, 15, 2), new Vec3d(-18, 12, 28), new Vec3d(-72, 22, 55)},
                {new Vec3d(-58, 30, 65), new Vec3d(-18, 16, 35), new Vec3d(24, 11, 8), new Vec3d(66, 26, -35)}
        };
        for (int i = 0; i < passes.length; i++) {
            double start = 430 + i * 170.0;
            movingMesh(b, "inv_scout_" + i, start, start + 760, FIGHTER,
                    route(760, passes[i]), new Vec3d(1.6, 1.6, 1.6), 0xFF9CB8FF, 0.28, "ship", i % 2 == 0 ? -14 : 14));
            b.add(attachedParticles("inv_scout_trail_" + i, start, start + 760, "inv_scout_" + i + "_root",
                    new Vec3d(0, 0, -2.2), Identifier.ofVanilla("end_rod"), 120, 0.18, 0.18));
        }
        b.add(sound("inv_first_alarm", 520, Identifier.ofVanilla("block.note_block.bass"), new Vec3d(0, 2, 18), 1.2f, 0.52f));
        b.add(text("inv_contact", 520, 970, "MULTIPLE AIR CONTACTS", new Vec3d(0, 9.5, 14), 0xFFFFB6A3));
        b.add(crowd("inv_evacuation", 620, 4700, Identifier.ofVanilla("villager"), 44, 20, 18,
                "panic", 9.0, 0.072, "panic"));

        // 0:55–1:30 scouts seed three invasion beacons.
        Vec3d[] beaconSites = {new Vec3d(-22, 0, 27), new Vec3d(23, 0, 28), new Vec3d(0, 0, -3)};
        for (int i = 0; i < beaconSites.length; i++) {
            double start = 1080 + i * 210.0;
            b.add(mesh("inv_beacon_" + i, start, 4500, null, PYLON, beaconSites[i],
                    new Vec3d(0.85, 0.85, 0.85), 0xFFA58CFF, 0.65, "crystal"));
            b.add(portal("inv_drop_gate_" + i, start + 140, 4300, beaconSites[i].add(0, 5.5, 0),
                    new Vec3d(5.5, 8, 0.2), 0xFF8269FF, 0.75));
            b.add(ring("inv_beacon_lock_" + i, start, start + 350, beaconSites[i].add(0, 0.08, 0),
                    0.3, 10.0, 0.2, 0xFF9B83FF));
        }
        b.add(crowd("inv_raiders_wave1", 1450, 3900, Identifier.ofVanilla("pillager"), 26, 20, 18,
                "march", 6.0, 0.046, "run"));

        // 1:30–2:00 mothership arrives and is actually moving through the sky.
        b.add(node("inv_mothership_root", 1750, 5750,
                route(4000, new Vec3d(-150, 78, -190), new Vec3d(-70, 62, -85),
                        new Vec3d(-18, 48, -12), new Vec3d(8, 38, 25)), -8));
        b.add(mesh("inv_mothership", 1750, 5750, "inv_mothership_root", MOTHERSHIP, Vec3d.ZERO,
                new Vec3d(14, 5, 28), 0xFFD5E5F5, 0.24, "ship"));
        b.add(light("inv_mothership_engine", 1750, 5750, "inv_mothership_root", new Vec3d(0, 0, -9),
                0xFF62DFFF, 8.0, 42, 0.62));
        b.add(attachedParticles("inv_ship_exhaust", 1750, 5750, "inv_mothership_root", new Vec3d(0, 0, -10),
                Identifier.ofVanilla("end_rod"), 620, 2.0, 0.65));
        b.add(camera("inv_scale_reveal", 1760, 2100,
                route(340, new Vec3d(-18, 8, 4), new Vec3d(-10, 15, 16), new Vec3d(8, 19, 22), new Vec3d(18, 10, 16)),
                new Vec3d(-14, 34, -8), 72, 54, 0.02, 0.13));
        b.add(control("inv_scale_reveal_lock", 1760, 2100));
        b.add(text("inv_mothership_warning", 1950, 2380, "CAPITAL SHIP // DESCENDING", new Vec3d(0, 10, 13), 0xFFFFB6A3));

        // 2:00–2:35 defense pylons deploy, acquire targets and fire in staggered rhythm.
        Vec3d[] defense = {new Vec3d(-14, 0, 8), new Vec3d(14, 0, 8), new Vec3d(-15, 0, 30), new Vec3d(15, 0, 30)};
        for (int i = 0; i < defense.length; i++) {
            double start = 2350 + i * 115.0;
            String key = "inv_defense_" + i;
            b.add(mesh(key, start, 5700, null, PYLON, defense[i], new Vec3d(1.0, 1.0, 1.0),
                    0xFF75EFFF, 0.7, "crystal"));
            b.add(beam(key + "_fire", 2750 + i * 80, 3650 + i * 80, key,
                    new Vec3d(0, 4.3, 0), new Vec3d((i % 2 == 0 ? -18 : 18), 22 + i * 3, -35), 0.13, 0xFF6CEFFF));
        }
        b.add(text("inv_defense_online", 2500, 2920, "DEFENSE GRID // ONLINE", new Vec3d(0, 9, 14), 0xFF9FFFF2));

        // 2:35–3:10 one scout is hit and crashes through a defense node.
        movingMesh(b, "inv_damaged_fighter", 3050, 3500, FIGHTER,
                route(450, new Vec3d(38, 16, -20), new Vec3d(18, 10, 4), new Vec3d(13, 5, 9), new Vec3d(14, 1, 8)),
                new Vec3d(1.8, 1.8, 1.8), 0xFFFFB08A, 0.5, "ship", 24));
        b.add(fracture("inv_fighter_crash", 3460, 3820, "inv_damaged_fighter_root", FIGHTER, 1450,
                0xFFFFC29A, new Vec3d(0.5, 0.2, 0.2), 4.0));
        b.add(gravityExplosion("inv_crash_blocks", 3460, 4020, defense[1], 1050,
                new Vec3d(10, 5, 10), 5.3, 7.4, 0xFF65717A, true));
        b.add(deform("inv_crash_crater", 3460, 4250, UltraEventElement.DeformMode.CRACK,
                defense[1], 25, 3.6, 0xFFFF7A52));
        b.add(sound("inv_crash_boom", 3480, Identifier.ofVanilla("entity.generic.explode"), defense[1], 1.6f, 0.78f));
        b.add(text("inv_grid_failure", 3540, 3940, "DEFENSE NODE B // LOST", new Vec3d(0, 9, 14), 0xFFFF9B82));

        // 3:10–3:45 boarding wave; patrol drones switch role and intercept raiders.
        b.add(crowd("inv_raiders_wave2", 3720, 5100, Identifier.ofVanilla("pillager"), 42, 23, 18,
                "march", 9.0, 0.061, "run"));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5;
            movingMesh(b, "inv_interceptor_" + i, 3750, 5000, DRONE,
                    route(1250, new Vec3d(Math.cos(a) * 18, 5, 18 + Math.sin(a) * 18),
                            new Vec3d(Math.cos(a + 0.9) * 9, 6, 18 + Math.sin(a + 0.9) * 9),
                            new Vec3d(Math.cos(a + 1.8) * 16, 7, 18 + Math.sin(a + 1.8) * 16)),
                    new Vec3d(0.9, 0.9, 0.9), 0xFF9EFFFF, 0.55, "crystal", i % 2 == 0 ? 12 : -12));
        }

        // 3:45–4:20 mothership beam: charge, fire, then a moving ground-level destruction line.
        b.add(light("inv_beam_charge", 4300, 4680, "inv_mothership_root", new Vec3d(0, -3, 4),
                0xFF8AF8FF, 15.0, 55, 1.0));
        b.add(text("inv_beam_charge_text", 4320, 4580, "ORBITAL BEAM // CHARGING", new Vec3d(0, 10, 14), 0xFFFFD0B8));
        b.add(beam("inv_orbital_beam", 4610, 5060, "inv_mothership_root", new Vec3d(0, -3, 4),
                new Vec3d(0, -72, 43), 1.45, 0xFFBFFBFF));
        b.add(field("inv_beam_impact", 4620, 5120, Identifier.ofVanilla("flame"), new Vec3d(0, 1.0, 36),
                2200, 0xFFFFA071, 48000, List.of(directional(new Vec3d(0.0, 1.0, 0.0), 2.2, 28, 71),
                        force(UltraEventElement.ForceKind.TURBULENCE, 1.1, 35, 72))));
        b.add(gravityExplosion("inv_beam_ground_blast", 4680, 5220, new Vec3d(0, 0, 36), 1700,
                new Vec3d(30, 4, 16), 7.0, 8.4, 0xFF8C766C, true));
        b.add(camera("inv_beam_camera", 4590, 4870,
                route(280, new Vec3d(-24, 9, 32), new Vec3d(-10, 15, 26), new Vec3d(8, 17, 34), new Vec3d(21, 10, 39)),
                new Vec3d(0, 20, 30), 64, 58, 0.07, 0.42));
        b.add(control("inv_beam_camera_lock", 4590, 4870));

        // 4:20–4:50 captured fighter counterattack: three converging strike paths, each hits a different engine.
        for (int i = 0; i < 3; i++) {
            double start = 5050 + i * 120.0;
            double side = i - 1;
            movingMesh(b, "inv_counter_fighter_" + i, start, start + 700, FIGHTER,
                    route(700, new Vec3d(side * 45, 12 + i * 4, 62), new Vec3d(side * 28, 20 + i * 5, 42),
                            new Vec3d(side * 12, 29 + i * 3, 31), new Vec3d(side * 4, 36, 24)),
                    new Vec3d(1.5, 1.5, 1.5), 0xFF7DF6FF, 0.72, "ship", side * 18));
            b.add(attachedParticles("inv_counter_trail_" + i, start, start + 700, "inv_counter_fighter_" + i + "_root",
                    new Vec3d(0, 0, -2.2), Identifier.ofVanilla("electric_spark"), 260, 0.25, 0.45));
            b.add(sound("inv_engine_hit_" + i, start + 610, Identifier.ofVanilla("entity.generic.explode"),
                    new Vec3d(side * 6, 34, 24), 1.3f, 0.86f + i * 0.05f));
        }

        // 4:50–5:15 chain destruction. The hull fails in stages and then rains thousands of chunks.
        b.add(material("inv_engine_failure", 5620, 5960, "inv_mothership", UltraEventElement.MaterialMode.BURN, 0xFFFF7E45));
        b.add(fracture("inv_mothership_break", 5840, 6200, "inv_mothership_root", MOTHERSHIP, 6200,
                0xFFE0E5E8, new Vec3d(0.35, 0.15, -0.55), 9.5));
        b.add(gravityExplosion("inv_hull_debris_rain", 5880, 6420, new Vec3d(5, 34, 25), 2600,
                new Vec3d(34, 10, 58), 4.6, 10.5, 0xFF9DA7B0, true));
        b.add(field("inv_fire_rain", 5900, 6400, Identifier.ofVanilla("flame"), new Vec3d(5, 28, 25),
                2600, 0xFFFF7F4F, 70000, List.of(directional(new Vec3d(0.2, -1.0, 0.1), 2.8, 60, 91),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.7, 48, 92))));
        b.add(camera("inv_chain_destruction", 5810, 6200,
                route(390, new Vec3d(-28, 12, 3), new Vec3d(-10, 26, 10), new Vec3d(12, 31, 26), new Vec3d(30, 18, 35)),
                new Vec3d(4, 33, 24), 62, 79, 0.12, 0.82));
        b.add(control("inv_chain_destruction_lock", 5810, 6200));

        // 5:15–5:30 aftermath: gates collapse and civilians return cautiously.
        for (int i = 0; i < beaconSites.length; i++) {
            b.add(material("inv_beacon_shutdown_" + i, 6200 + i * 55, 6460, "inv_beacon_" + i,
                    UltraEventElement.MaterialMode.DISSOLVE, 0xFF7DF6FF));
        }
        b.add(crowd("inv_civilians_return", 6320, d, Identifier.ofVanilla("villager"), 22, 16, 18,
                "wander", 2.2, 0.014, "walk"));
        b.add(text("inv_final", 6380, d, "EVACUATION CANCELLED // SKY CLEAR", new Vec3d(0, 9, 14), 0xFFC9FFE6));
        b.add(sound("inv_final_chime", 6410, Identifier.ofVanilla("block.amethyst_block.chime"), new Vec3d(0, 3, 18), 1.0f, 1.1f));
        return b.build();
    }
}
