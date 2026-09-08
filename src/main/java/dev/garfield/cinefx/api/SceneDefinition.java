package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/** A reusable timeline registered once by a mod and started locally or from the server by ID. */
public record SceneDefinition(
        Identifier id,
        double durationTicks,
        int priority,
        boolean looping,
        List<SceneElement> elements,
        Map<String, String> metadata
) {
    public SceneDefinition {
        if (id == null) throw new IllegalArgumentException("scene id is required");
        if (!Double.isFinite(durationTicks) || durationTicks <= 0.0) throw new IllegalArgumentException("durationTicks must be > 0");
        elements = elements == null ? List.of() : List.copyOf(elements);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
