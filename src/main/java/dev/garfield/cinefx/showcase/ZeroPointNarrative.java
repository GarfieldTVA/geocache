package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.SceneBuilder;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.UltraEventElement;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static dev.garfield.cinefx.showcase.NarrativeEventKit.*;

/** Zero Point: The Broken Protocol. Story beats are documented in MEGA_EVENT_SCENARIOS.md. */
final class ZeroPointNarrative {
    private static final Identifier CORE = id("model/zeropoint_core");
    private static final Identifier DRONE = id("model/chrono_drone");
    private static final Identifier PYLON = id("model/stabilizer_pylon");

    private ZeroPointNarrative() { }

    static SceneDefinition build(Identifier sceneId) {
        double d = 7200.0;
        SceneBuilder b = base(sceneId, d, "zero-point-the-broken-protocol");
        ambience(b, "zp", d, 0xFF75A7C8, 0xFF294B68, 0x080076A8);
        b.add(loop("zp_low_hum", 0, d, Identifier.ofVanilla("block.beacon.ambient"), 0.26, 0.82));

        // 0:00–0:25 Survey: workers and scanners establish what the audience is looking at.
        b.add(crowd("zp_engineers_work", 0, 1950, Identifier.ofVanilla("villager"), 20, 13, 18,
                "wander", 2.8, 0.021, "walk"));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2.0 / 3.0;
            movingMesh(b, "zp_scan_drone_" + i, 0, 2300, DRONE,
                    route(2300, new Vec3d(Math.cos(a) * 8, 4.5, 18 + Math.sin(a) * 8),
                            new Vec3d(Math.cos(a + 1.2) * 10, 6.0, 18 + Math.sin(a + 1.2) * 10),
                            new Vec3d(Math.cos(a + 2.5) * 7, 4.0, 18 + Math.sin(a + 2.5) * 7),
                            new Vec3d(Math.cos(a + 4.5) * 9, 5.5, 18 + Math.sin(a + 4.5) * 9)),
                    new Vec3d(0.8, 0.8, 0.8), 0xFFC8FBFF, 0.45, "crystal", 8.0);
        }
        b.add(text("zp_scan_label", 80, 470, "SUBSURFACE SIGNAL // 18m", new Vec3d(0, 7.5, 15), 0xFFBDF7FF));
        b.add(camera("zp_establish", 0, 280,
                route(280, new Vec3d(-15, 6, 2), new Vec3d(-10, 10, 10), new Vec3d(8, 9, 15), new Vec3d(15, 6, 22)),
                new Vec3d(0, 3, 18), 69, 58, 0.01, 0.06));
        b.add(control("zp_establish_lock", 0, 280));

        // 0:25–0:50 First pulse.
        b.add(sound("zp_first_pulse_sound", 500, Identifier.ofVanilla("block.respawn_anchor.charge"), new Vec3d(0, 0, 18), 1.1f, 0.72f));
        b.add(deform("zp_first_pulse", 500, 1120, UltraEventElement.DeformMode.PULSE,
                new Vec3d(0, 0.02, 18), 31.0, 2.1, 0xFF62E8FF));
        b.add(ring("zp_first_wave", 500, 940, new Vec3d(0, 0.1, 18), 0.4, 33, 0.35, 0xFF84F4FF));
        b.add(field("zp_probe_sparks", 500, 1250, Identifier.ofVanilla("electric_spark"), new Vec3d(0, 1.2, 18),
                780, 0xFF75EFFF, 16000, List.of(force(UltraEventElement.ForceKind.VORTEX, 1.2, 8, 10))));

        // 0:50–1:20 Four stabilizers deploy and lift the core.
        Vec3d[] pylons = {new Vec3d(-12, 0, 18), new Vec3d(12, 0, 18), new Vec3d(0, 0, 6), new Vec3d(0, 0, 30)};
        for (int i = 0; i < pylons.length; i++) {
            double start = 980 + i * 150.0;
            String key = "zp_pylon_" + i;
            b.add(mesh(key, start, 5650 + i * 70, null, PYLON, pylons[i], new Vec3d(1.15, 1.15, 1.15),
                    0xFFB7F5FF, 0.42, "crystal"));
            Vec3d delta = new Vec3d(-pylons[i].x, 4.8, 18 - pylons[i].z);
            b.add(beam(key + "_link", start + 80, 4300 + i * 90, key, new Vec3d(0, 4.3, 0), delta, 0.18, 0xFF65E9FF));
            b.add(sound(key + "_lock", start + 100, Identifier.ofVanilla("block.beacon.activate"), pylons[i], 0.72f, 1.0f + i * 0.06f));
        }
        b.add(node("zp_core_lift", 760, 4300,
                route(1280, new Vec3d(0, -7.5, 18), new Vec3d(0, -4, 18), new Vec3d(0, 1.5, 18), new Vec3d(0, 4.5, 18)), 0));
        b.add(mesh("zp_core_contained", 760, 4300, "zp_core_lift", CORE, Vec3d.ZERO,
                new Vec3d(2.7, 2.7, 2.7), 0xFFD2FCFF, 0.82, "crystal"));
        b.add(light("zp_core_light", 760, 4300, "zp_core_lift", Vec3d.ZERO, 0xFF5CEBFF, 7.5, 34, 0.82));
        b.add(text("zp_stable_label", 1450, 1950, "STABILIZATION 100%", new Vec3d(0, 9, 14), 0xFFA9FFD8));

        // 1:20–1:45 The protocol starts lying.
        b.add(portal("zp_micro_rift_a", 1580, 2650, new Vec3d(-22, 8, 23), new Vec3d(4, 6, 0.2), 0xFFFF58D5, 0.55));
        b.add(portal("zp_micro_rift_b", 1760, 2650, new Vec3d(20, 10, 11), new Vec3d(3.5, 5, 0.2), 0xFF765CFF, 0.58));
        b.add(text("zp_warning", 1740, 2220, "PROTOCOL MISMATCH", new Vec3d(0, 10, 14), 0xFFFF9CE9));
        b.add(sound("zp_alarm", 1780, Identifier.ofVanilla("block.note_block.didgeridoo"), new Vec3d(0, 2, 18), 1.0f, 0.62f));

        // 1:45–2:20 Sentinel arrives, walks through the scene, and corrupts two specific pylons.
        b.add(portal("zp_sentinel_gate", 2080, 5200, new Vec3d(19, 6, 29), new Vec3d(8, 11, 0.25), 0xFFFF4FD7, 0.9));
        PathTrack sentinelRoute = route(2800, new Vec3d(19, 0, 29), new Vec3d(12, 0, 22),
                new Vec3d(-10, 0, 18), new Vec3d(1, 0, 18));
        b.add(node("zp_sentinel_route", 2180, 5100, sentinelRoute, 0));
        b.add(sentinel("zp_sentinel", 2180, 5100, "zp_sentinel_route", new Vec3d(3.8, 3.8, 3.8), 0xFFFFC8F2));
        b.add(crowd("zp_engineers_flee", 2160, 5000, Identifier.ofVanilla("villager"), 24, 15, 18,
                "panic", 8.0, 0.075, "panic"));
        b.add(material("zp_corrupt_pylon_0", 2400, 4200, "zp_pylon_0", UltraEventElement.MaterialMode.CORRUPTION, 0xFFFF3EBF));
        b.add(material("zp_corrupt_pylon_1", 2700, 4200, "zp_pylon_1", UltraEventElement.MaterialMode.CORRUPTION, 0xFFFF3EBF));
        b.add(field("zp_corruption_stream", 2420, 3300, Identifier.ofVanilla("portal"), new Vec3d(0, 5, 18),
                950, 0xFFFF59D9, 24000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 1.8, 15, 20))));

        // 2:20–3:00 Separate reality sectors leak in; each has a readable identity.
        sector(b, "zp_void_sector", 2850, 4550, Blocks.OBSIDIAN.getDefaultState(), new Vec3d(-17, 0, 32), 12, 0xFFFF62E9);
        sector(b, "zp_ice_sector", 3100, 4700, Blocks.PACKED_ICE.getDefaultState(), new Vec3d(17, 0, 31), 12, 0xFFA9F4FF);
        sector(b, "zp_desert_sector", 3350, 4850, Blocks.CUT_SANDSTONE.getDefaultState(), new Vec3d(-18, 0, 5), 12, 0xFFFFC47A);
        sector(b, "zp_neon_sector", 3600, 5000, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), new Vec3d(18, 0, 5), 12, 0xFF62F1FF);
        b.add(camera("zp_reality_reveal", 3520, 3800,
                route(280, new Vec3d(-21, 12, 18), new Vec3d(-8, 17, 34), new Vec3d(11, 15, 31), new Vec3d(23, 9, 17)),
                new Vec3d(0, 5, 18), 66, 57, 0.02, 0.13));
        b.add(control("zp_reality_reveal_lock", 3520, 3800));

        // 3:00–3:35 Drones countermeasure: they converge and fire at the core while Sentinel reaches it.
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5;
            String drone = "zp_counter_drone_" + i;
            movingMesh(b, drone, 3800, 4700, DRONE,
                    route(900, new Vec3d(Math.cos(a) * 24, 9, 18 + Math.sin(a) * 24),
                            new Vec3d(Math.cos(a + 0.7) * 13, 8, 18 + Math.sin(a + 0.7) * 13),
                            new Vec3d(Math.cos(a + 1.4) * 8, 7, 18 + Math.sin(a + 1.4) * 8)),
                    new Vec3d(0.9, 0.9, 0.9), 0xFFAEF8FF, 0.55, "crystal", 10);
            b.add(beam(drone + "_beam", 4140, 4700, drone + "_root", Vec3d.ZERO,
                    new Vec3d(-Math.cos(a) * 8, -2.5, -Math.sin(a) * 8), 0.11, 0xFF6BEFFF));
        }
        b.add(text("zp_countermeasure", 3950, 4380, "DRONE COUNTERMEASURE // ENGAGED", new Vec3d(0, 11, 13), 0xFFAAFAFF));

        // 3:35–4:15 The core breaks free and moves; the scene finally changes topology.
        PathTrack freeCore = route(1350, new Vec3d(0, 4.5, 18), new Vec3d(-8, 12, 24),
                new Vec3d(9, 17, 15), new Vec3d(0, 20, 18));
        b.add(node("zp_core_free_route", 4300, 5680, freeCore, 12));
        b.add(mesh("zp_core_free", 4300, 5680, "zp_core_free_route", CORE, Vec3d.ZERO,
                new Vec3d(3.1, 3.1, 3.1), 0xFFE0FDFF, 1.0, "crystal"));
        b.add(light("zp_core_free_light", 4300, 5680, "zp_core_free_route", Vec3d.ZERO, 0xFF77F5FF, 10.0, 45, 1.0));
        b.add(deform("zp_main_fissure", 4300, 6100, UltraEventElement.DeformMode.FISSURE,
                new Vec3d(0, 0.02, 18), 50, 7.5, 0xFFFF55DD));
        b.add(field("zp_orbit_fragments", 4350, 5750, Identifier.ofVanilla("end_rod"), new Vec3d(0, 18, 18),
                1700, 0xFFD8FCFF, 42000, List.of(force(UltraEventElement.ForceKind.VORTEX, 3.6, 24, 30))));

        // 4:15–4:55 Stabilizers fail one after another; the causal chain is visible.
        for (int i = 0; i < 4; i++) {
            double t = 5060 + i * 220.0;
            b.add(fracture("zp_pylon_break_" + i, t, t + 360, null, PYLON, 950 + i * 120,
                    0xFFB9F5FF, new Vec3d((i - 1.5) * 0.25, 0.55, 0.15), 3.5 + i * 0.6));
            b.add(ring("zp_pylon_shock_" + i, t, t + 260, pylons[i].add(0, 0.1, 0), 0.5, 14 + i * 4, 0.28, 0xFFFF7BDD));
            b.add(sound("zp_pylon_boom_" + i, t + 20, Identifier.ofVanilla("entity.generic.explode"), pylons[i], 1.0f, 0.8f + i * 0.04f));
        }
        b.add(material("zp_sentinel_hit", 5480, 5880, "zp_sentinel", UltraEventElement.MaterialMode.PHASE, 0xFFFFFFFF));
        b.add(camera("zp_sentinel_expulsion", 5480, 5750,
                route(270, new Vec3d(-16, 8, 20), new Vec3d(-5, 12, 26), new Vec3d(8, 10, 23), new Vec3d(16, 7, 29)),
                new Vec3d(10, 4, 25), 62, 72, 0.08, 0.5));
        b.add(control("zp_sentinel_expulsion_lock", 5480, 5750));

        // 4:55–5:25 Fracture, silence, suspended debris.
        b.add(fracture("zp_core_fracture", 5860, 6350, "zp_core_free_route", CORE, 4400,
                0xFFDCF9FF, new Vec3d(0, 0.7, 0), 7.0));
        b.add(field("zp_reverse_shards", 6100, 6600, Identifier.ofVanilla("end_rod"), new Vec3d(0, 18, 18),
                2200, 0xFFE5FDFF, 65000, List.of(force(UltraEventElement.ForceKind.ATTRACTOR, 4.5, 38, 44),
                        force(UltraEventElement.ForceKind.VORTEX, -1.6, 28, 45))));
        b.add(text("zp_time_stop", 6080, 6330, "TEMPORAL LOCK", new Vec3d(0, 13, 18), 0xFFFFFFFF));

        // 5:25–6:00 Rewind and repair, with returning people as the emotional payoff.
        b.add(deform("zp_rebuild", 6380, d, UltraEventElement.DeformMode.REBUILD,
                new Vec3d(0, 0.02, 18), 46, 4.0, 0xFF7CFFAC));
        b.add(mesh("zp_core_restored", 6480, d, null, CORE, new Vec3d(0, 3.2, 18),
                new Vec3d(2.4, 2.4, 2.4), 0xFFC9FFF0, 0.48, "crystal"));
        b.add(light("zp_restored_light", 6480, d, "zp_core_restored", Vec3d.ZERO, 0xFF7CFFB6, 4.0, 24, 0.35));
        b.add(crowd("zp_engineers_return", 6700, d, Identifier.ofVanilla("villager"), 16, 12, 18,
                "wander", 2.0, 0.015, "walk"));
        b.add(text("zp_final", 6800, d, "PROTOCOL RESTORED", new Vec3d(0, 9, 14), 0xFFB9FFD0));
        b.add(sound("zp_final_chime", 6840, Identifier.ofVanilla("block.amethyst_block.chime"), new Vec3d(0, 3, 18), 1.0f, 1.15f));
        return b.build();
    }
}
