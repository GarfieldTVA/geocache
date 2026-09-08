package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.GradeFrame;
import dev.garfield.cinefx.client.api.PostFxBackend;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PostFxBackendRegistry {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void register(Identifier id, int priority, PostFxBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id and backend are required");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX post-FX backend already registered: " + id);
        }
        entries.add(new Entry(id, priority, backend));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public synchronized boolean render(DrawContext context, GradeFrame frame) {
        for (Entry entry : entries) {
            try {
                if (entry.backend.render(context, frame)) return true;
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Post-FX backend failed: " + entry.id + " - " + exception.getMessage());
            }
        }
        return false;
    }

    private record Entry(Identifier id, int priority, PostFxBackend backend) { }
}
