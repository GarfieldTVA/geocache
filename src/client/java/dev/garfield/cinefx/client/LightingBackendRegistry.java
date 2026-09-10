package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.LightFrame;
import dev.garfield.cinefx.client.api.LightingBackend;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LightingBackendRegistry {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void register(Identifier id, int priority, LightingBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id and backend are required");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX lighting backend already registered: " + id);
        }
        entries.add(new Entry(id, priority, backend));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public synchronized boolean apply(List<LightFrame> lights) {
        for (Entry entry : entries) {
            try {
                if (entry.backend.apply(lights)) return true;
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Lighting backend failed: " + entry.id + " - " + exception.getMessage());
            }
        }
        return false;
    }

    private record Entry(Identifier id, int priority, LightingBackend backend) { }
}
