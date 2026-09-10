package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CustomWorldRenderer;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomRendererRegistry {
    private final Map<Identifier, CustomWorldRenderer> renderers = new ConcurrentHashMap<>();

    public void register(Identifier type, CustomWorldRenderer renderer) {
        if (type == null || renderer == null) throw new IllegalArgumentException("type and renderer are required");
        CustomWorldRenderer old = renderers.putIfAbsent(type, renderer);
        if (old != null) throw new IllegalStateException("CineFX custom renderer already registered: " + type);
    }

    public CustomWorldRenderer find(Identifier type) { return renderers.get(type); }
}
