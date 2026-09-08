package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fluent scene definition builder. */
public final class SceneBuilder {
    private final Identifier id;
    private double durationTicks = 200.0;
    private int priority = 0;
    private boolean looping;
    private final List<SceneElement> elements = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private SceneBuilder(Identifier id) {
        if (id == null) throw new IllegalArgumentException("id is required");
        this.id = id;
    }

    public static SceneBuilder create(Identifier id) { return new SceneBuilder(id); }

    public SceneBuilder duration(double ticks) {
        if (!Double.isFinite(ticks) || ticks <= 0.0) throw new IllegalArgumentException("duration must be > 0");
        this.durationTicks = ticks;
        return this;
    }

    public SceneBuilder priority(int priority) { this.priority = priority; return this; }
    public SceneBuilder looping(boolean looping) { this.looping = looping; return this; }
    public SceneBuilder loop() { this.looping = true; return this; }

    public SceneBuilder add(SceneElement element) {
        if (element == null) throw new IllegalArgumentException("element is required");
        elements.add(element);
        return this;
    }

    public SceneBuilder meta(String key, String value) {
        metadata.put(key, value);
        return this;
    }

    public SceneDefinition build() {
        return new SceneDefinition(id, durationTicks, priority, looping, elements, metadata);
    }

    public SceneDefinition register() {
        SceneDefinition scene = build();
        CineFxApi.register(scene);
        return scene;
    }
}
