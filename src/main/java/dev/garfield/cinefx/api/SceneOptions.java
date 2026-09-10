package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime parameters for one scene instance. startGameTime < 0 means "start now" on the client. */
public record SceneOptions(Vec3d anchor, long startGameTime, long seed, Map<String, String> variables) {
    public SceneOptions {
        anchor = anchor == null ? Vec3d.ZERO : anchor;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static SceneOptions at(Vec3d anchor) {
        return new SceneOptions(anchor, -1L, System.nanoTime(), Map.of());
    }

    public SceneOptions startingAt(long gameTime) {
        return new SceneOptions(anchor, gameTime, seed, variables);
    }

    public SceneOptions seeded(long seed) {
        return new SceneOptions(anchor, startGameTime, seed, variables);
    }

    public SceneOptions with(String key, String value) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(variables);
        map.put(key, value);
        return new SceneOptions(anchor, startGameTime, seed, map);
    }
}
