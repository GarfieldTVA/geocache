package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend;
import dev.garfield.cinefx.client.api.CinematicBackend.AtmosphereFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.CameraFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.ParticleFrame;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Priority-ordered integration registry for high-end cinematic renderer channels. */
public final class CinematicBackendRegistry {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void register(Identifier id, int priority, CinematicBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id and backend are required");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX cinematic backend already registered: " + id);
        }
        entries.add(new Entry(id, priority, backend));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public synchronized boolean camera(CameraFrame frame) {
        for (Entry entry : entries) {
            try {
                if (entry.backend.applyCamera(frame)) return true;
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Camera backend failed: " + entry.id + " - " + exception.getMessage());
            }
        }
        return false;
    }

    public synchronized boolean atmosphere(AtmosphereFrame frame) {
        for (Entry entry : entries) {
            try {
                if (entry.backend.applyAtmosphere(frame)) return true;
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Atmosphere backend failed: " + entry.id + " - " + exception.getMessage());
            }
        }
        return false;
    }

    public synchronized boolean particles(List<ParticleFrame> frames) {
        for (Entry entry : entries) {
            try {
                if (entry.backend.emitParticles(frames)) return true;
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Particle backend failed: " + entry.id + " - " + exception.getMessage());
            }
        }
        return false;
    }

    private record Entry(Identifier id, int priority, CinematicBackend backend) { }
}
