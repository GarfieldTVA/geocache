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

/** Titan: Heart of the Mountain. The Titan is first mistaken for the threat, then becomes the only thing able to seal the rift. */
final class TitanNarrative {
    private static final Identifier HEART = id("model/titan_heart");
    private static final Identifier DRONE = id("model/chrono_drone");
    private static final Identifier PYLON = id("model/stabilizer_pylon");

    private TitanNarrative() { }

    static SceneDefinition build(Identifier sceneId) {
        double d = 7000.0;
        SceneBuilder b = base(sceneId, d, "titan-heart-of-the-mountain");
        ambience(b, "tr", d, 0xFFD2AF7D, 0xFF694C35, 0x087C431A);
        b.add(loop("tr_low_rumble", 0, 6500, Identifier.ofVanilla("block.beacon.ambient"), 0.18, 0.55));

        // 0:00–0:30 excavation site. Workers circle one object with an obvious purpose.
        b.add(crowd("tr_workers", 0, 1050, Identifier.ofVanilla("villager"), 24, 15, 17,
                "wander", 2.2, 0.017, "walk"));
        b.add(mesh("tr_heart_excavated", 0, 1880, null, HEART, new Vec3d(0, 1.2, 19),
                new Vec3d(1.6, 1.6, 1.6), 0xFFFFA34E, 0.78, "crystal"));
        b.add(light("tr_heart_excavated_light", 0, 1880, "tr_heart_excavated", Vec3d.ZERO,
                0xFFFF9844, 4.5, 22, 0.38));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3.0;
            movingMesh(b, "tr_scan_drone_" + i, 0, 1350, DRONE,
                    route(1350, new Vec3d(Math.cos(a) * 9, 4.2, 19 + Math.sin(a) * 9),
                            new Vec3d(Math.cos(a + 1.3) * 6, 5.0, 19 + Math.sin(a + 1.3) * 6),
                            new Vec3d(Math.cos(a + 2.5) * 8, 4.5, 19 + Math.sin(a + 2.5) * 8)),
                    new Vec3d(0.72, 0.72, 0.72), 0xFFFFD09A, 0.32, "crystal", 5));
        }
        b.add(text("tr_excavation_label", 70, 430, "EXCAVATION 07 // UNKNOWN POWER SOURCE", new Vec3d(0, 8.5, 14), 0xFFFFE3C1));
        b.add(camera("tr_establish", 0, 260,
                route(260, new Vec3d(-15, 7, 4), new Vec3d(-8, 11, 13), new Vec3d(9, 10, 20), new Vec3d(16, 7, 25)),
                new Vec3d(0, 2.5, 19), 70, 59, 0.01, 0.04));
        b.add(control("tr_establish_lock", 0, 260));

        // 0:30–1:00 activation. Stone plates rise because the heart is waking the buried body.
        b.add(sound("tr_activation", 620, Identifier.ofVanilla("block.respawn_anchor.charge"), new Vec3d(0, 1, 19), 1.3f, 0.62f));
        b.add(deform("tr_activation_fissure", 620, 1260, UltraEventElement.DeformMode.FISSURE,
                new Vec3d(0, 0.02, 19), 32, 5.5, 0xFFFF9D54));
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18.0;
            double r = 5.0 + (i % 4) * 2.1;
            b.add(block("tr_plate_" + i, 650 + i * 16.0, 2400, Blocks.DEEPSLATE_BRICKS.getDefaultState(),
                    new Vec3d(Math.cos(a) * r, 0, 19 + Math.sin(a) * r),
                    new Vec3d(1.3, 1.0 + (i % 5) * 0.8, 1.3)));
        }
        b.add(ring("tr_heart_pulse", 700, 1140, new Vec3d(0, 0.1, 19), 0.5, 30, 0.34, 0xFFFFAB5F));

        // 1:00–1:35 Titan emerges in a moving hierarchy; no static giant prop.
        b.add(node("tr_titan_root", 1140, 6600,
                route(5460, new Vec3d(0, -14, 39), new Vec3d(0, -3, 34),
                        new Vec3d(-7, 0, 27), new Vec3d(7, 0, 20), new Vec3d(0, 0, 14)), 0));
        b.add(mob("tr_titan", 1140, 6600, "tr_titan_root", Identifier.ofVanilla("iron_golem"),
                Vec3d.ZERO, new Vec3d(8.0, 8.0, 8.0), "walk"));
        b.add(mesh("tr_titan_heart", 1220, 6600, "tr_titan_root", HEART, new Vec3d(0, 12.5, 0.6),
                new Vec3d(2.2, 2.2, 2.2), 0xFFFFAA51, 0.86, "crystal"));
        b.add(light("tr_titan_heart_light", 1220, 6600, "tr_titan_root", new Vec3d(0, 12.5, 0.6),
                0xFFFF9A43, 7.0, 34, 0.54));
        b.add(field("tr_rising_dust", 1120, 1750, Identifier.ofVanilla("ash"), new Vec3d(0, 3, 35),
                1500, 0xFFC29F7C, 32000, List.of(directional(new Vec3d(0, 1, 0), 1.8, 24, 12),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.8, 30, 13))));
        b.add(camera("tr_awaken_camera", 1180, 1500,
                route(320, new Vec3d(-22, 5, 22), new Vec3d(-15, 13, 31), new Vec3d(6, 20, 35), new Vec3d(22, 10, 24)),
                new Vec3d(0, 15, 35), 72, 55, 0.04, 0.28));
        b.add(control("tr_awaken_lock", 1180, 1500));
        b.add(text("tr_titan_warning", 1460, 1900, "SEISMIC ENTITY // ACTIVE", new Vec3d(0, 10, 14), 0xFFFFC48D));

        // 1:35–2:05 humans misread the Titan as the threat and fire on it.
        Vec3d[] defense = {new Vec3d(-16, 0, 8), new Vec3d(16, 0, 8), new Vec3d(-17, 0, 30), new Vec3d(17, 0, 30)};
        for (int i = 0; i < defense.length; i++) {
            double start = 1850 + i * 100.0;
            String key = "tr_defense_" + i;
            b.add(mesh(key, start, 6000, null, PYLON, defense[i], new Vec3d(1.0, 1.0, 1.0),
                    0xFFFFB06A, 0.58, "crystal"));
            b.add(beam(key + "_anti_titan", 2150 + i * 65, 2750 + i * 60, key,
                    new Vec3d(0, 4.2, 0), new Vec3d(-defense[i].x * 0.6, 12, 19 - defense[i].z), 0.13, 0xFFFF9A55));
        }
        b.add(crowd("tr_workers_flee", 1800, 4100, Identifier.ofVanilla("villager"), 34, 18, 18,
                "panic", 10, 0.078, "panic"));
        b.add(material("tr_titan_damage", 2250, 3100, "tr_titan", UltraEventElement.MaterialMode.BURN, 0xFFFF6A42));

        // 2:05–2:40 the real threat reveals itself behind everyone. Titan turns toward it.
        b.add(portal("tr_true_rift", 2500, 5600, new Vec3d(0, 24, -12), new Vec3d(17, 13, 0.25), 0xFF62DAFF, 0.95));
        b.add(field("tr_rift_pull", 2560, 5500, Identifier.ofVanilla("portal"), new Vec3d(0, 22, -10),
                1750, 0xFF65DDFF, 48000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 2.5, 34, 31),
                        force(UltraEventElement.ForceKind.VORTEX, 1.8, 30, 32))));
        b.add(text("tr_reassessment", 2680, 3140, "NEW THREAT // TITAN RETARGETING", new Vec3d(0, 10, 14), 0xFF9EEFFF));
        b.add(material("tr_heart_shift", 2660, 3400, "tr_titan_heart", UltraEventElement.MaterialMode.HOLOGRAM, 0xFF6BE7FF));

        // 2:40–3:15 Rift Sentinel enters and closes distance. Drones retarget too.
        b.add(node("tr_sentinel_root", 3150, 5100,
                route(1950, new Vec3d(0, 5, -9), new Vec3d(-8, 1, 0), new Vec3d(10, 0, 9), new Vec3d(2, 0, 15)), 0));
        b.add(sentinel("tr_rift_sentinel", 3150, 5100, "tr_sentinel_root", new Vec3d(4.2, 4.2, 4.2), 0xFF9AEFFF));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3.0;
            movingMesh(b, "tr_combat_drone_" + i, 3200, 5050, DRONE,
                    route(1850, new Vec3d(Math.cos(a) * 19, 7, 18 + Math.sin(a) * 19),
                            new Vec3d(Math.cos(a + 1.1) * 12, 8, 8 + Math.sin(a + 1.1) * 12),
                            new Vec3d(Math.cos(a + 2.3) * 9, 6, 13 + Math.sin(a + 2.3) * 9)),
                    new Vec3d(0.85, 0.85, 0.85), 0xFF9DF4FF, 0.52, "crystal", 9));
        }

        // 3:15–3:55 battle: Titan does something only this event does — it rips a whole structure out of the ground.
        b.add(beam("tr_rift_beam", 3750, 4180, "tr_sentinel_root", new Vec3d(0, 2.6, 0),
                new Vec3d(0, 9, 27), 0.75, 0xFF6CE7FF));
        b.add(beam("tr_titan_beam", 3950, 4380, "tr_titan_root", new Vec3d(0, 13, 1),
                new Vec3d(0, 8, -28), 1.2, 0xFFFFA857));
        b.add(node("tr_ripped_tower_root", 4010, 4850,
                route(840, new Vec3d(-18, 0, 20), new Vec3d(-12, 12, 18),
                        new Vec3d(-4, 20, 14), new Vec3d(0, 24, -7)), 22));
        b.add(rigidStructure("tr_ripped_tower", 4010, 4850, "tr_ripped_tower_root", Vec3d.ZERO,
                1250, new Vec3d(9, 14, 9), 0xFF77736C));
        b.add(deform("tr_tower_socket", 4000, 4450, UltraEventElement.DeformMode.SINK,
                new Vec3d(-18, 0.02, 20), 13, 4.2, 0xFF9D7658));
        b.add(field("tr_throw_dust", 4010, 4470, Identifier.ofVanilla("ash"), new Vec3d(-18, 2, 20),
                1800, 0xFFC19B77, 36000, List.of(directional(new Vec3d(0, 1, 0), 2.6, 22, 51))));
        b.add(gravityExplosion("tr_tower_impact", 4800, 5300, new Vec3d(0, 23, -7), 1900,
                new Vec3d(10, 15, 10), 8.2, 9.8, 0xFF77736C, false));
        b.add(sound("tr_throw_impact", 4820, Identifier.ofVanilla("entity.generic.explode"), new Vec3d(0, 8, -7), 1.7f, 0.72f));
        b.add(camera("tr_throw_camera", 4110, 4430,
                route(320, new Vec3d(-28, 8, 12), new Vec3d(-15, 16, 23), new Vec3d(4, 21, 16), new Vec3d(19, 12, 2)),
                new Vec3d(-5, 13, 12), 67, 74, 0.06, 0.42));
        b.add(control("tr_throw_lock", 4110, 4430));

        // 3:55–4:30 Sentinel starts pulling the Titan's heart out of its chest.
        b.add(field("tr_heart_pull", 4740, 5480, Identifier.ofVanilla("electric_spark"), new Vec3d(0, 13, 12),
                2100, 0xFF6BE9FF, 52000, List.of(directional(new Vec3d(0, 0.35, -1), 3.1, 32, 61),
                        force(UltraEventElement.ForceKind.TURBULENCE, 0.75, 24, 62))));
        b.add(material("tr_heart_exposed", 4760, 5480, "tr_titan_heart", UltraEventElement.MaterialMode.SCAN, 0xFFB9F6FF));
        b.add(text("tr_heart_warning", 4860, 5260, "GUARDIAN HEART // UNSTABLE", new Vec3d(0, 10, 14), 0xFFFFB78A));

        // 4:30–5:05 defense pylons change allegiance and stabilize the heart.
        for (int i = 0; i < defense.length; i++) {
            b.add(beam("tr_stabilize_" + i, 5360 + i * 70, 6040, "tr_defense_" + i,
                    new Vec3d(0, 4.2, 0), new Vec3d(-defense[i].x, 11, 18 - defense[i].z), 0.14, 0xFF75EFFF));
        }
        b.add(light("tr_heart_stable", 5450, 6100, "tr_titan_root", new Vec3d(0, 12.5, 0.6),
                0xFF74EFFF, 11.0, 42, 0.95));
        b.add(beam("tr_seal_beam", 5720, 6120, "tr_titan_root", new Vec3d(0, 13, 0),
                new Vec3d(0, 14, -40), 1.7, 0xFF9FF9FF));
        b.add(field("tr_rift_reverse", 5740, 6200, Identifier.ofVanilla("end_rod"), new Vec3d(0, 23, -11),
                2600, 0xFFC8FFFF, 65000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 5.0, 46, 71),
                        force(UltraEventElement.ForceKind.VORTEX, -2.0, 35, 72))));
        b.add(camera("tr_seal_camera", 5700, 5980,
                route(280, new Vec3d(-20, 10, 10), new Vec3d(-7, 20, 4), new Vec3d(8, 23, -3), new Vec3d(20, 12, 5)),
                new Vec3d(0, 18, -4), 63, 78, 0.08, 0.52));
        b.add(control("tr_seal_lock", 5700, 5980));

        // 5:05–5:35 price of sealing the rift: petrification climbs upward while Titan keeps moving.
        b.add(material("tr_petrify", 6060, 6660, "tr_titan", UltraEventElement.MaterialMode.FREEZE, 0xFFB9B6AE));
        b.add(text("tr_last_walk", 6150, 6490, "GUARDIAN MOVING AWAY FROM SETTLEMENT", new Vec3d(0, 10, 14), 0xFFD8E9E7));
        b.add(field("tr_stone_dust", 6100, 6700, Identifier.ofVanilla("ash"), new Vec3d(0, 8, 14),
                1200, 0xFFB7ADA1, 28000, List.of(directional(new Vec3d(0.1, -1, 0.1), 0.8, 24, 81))));

        // 5:35–5:50 collapse, not disappearance: thousands of stone chunks fall with gravity.
        b.add(gravityExplosion("tr_titan_collapse_blocks", 6580, 6960, new Vec3d(0, 9, 14), 2800,
                new Vec3d(15, 22, 12), 3.7, 12.0, 0xFF9F9A91, true));
        b.add(fracture("tr_titan_shell_break", 6580, 6940, "tr_titan_root", id("model/titan_proxy"), 4800,
                0xFFB7B2AA, new Vec3d(0.2, 0.1, 0.25), 5.3));
        b.add(camera("tr_collapse_camera", 6560, 6860,
                route(300, new Vec3d(-22, 7, 2), new Vec3d(-9, 14, 10), new Vec3d(10, 18, 18), new Vec3d(24, 8, 12)),
                new Vec3d(0, 10, 14), 65, 80, 0.11, 0.72));
        b.add(control("tr_collapse_lock", 6560, 6860));

        // Memorial beat.
        b.add(mesh("tr_heart_memorial", 6730, d, null, HEART, new Vec3d(0, 1.4, 14),
                new Vec3d(1.2, 1.2, 1.2), 0xFF8CEFFF, 0.42, "crystal"));
        b.add(crowd("tr_workers_return", 6780, d, Identifier.ofVanilla("villager"), 12, 11, 14,
                "wander", 1.5, 0.012, "walk"));
        b.add(text("tr_final", 6790, d, "GUARDIAN STATUS // DORMANT", new Vec3d(0, 8.5, 11), 0xFFD4F4F0));
        return b.build();
    }
}
