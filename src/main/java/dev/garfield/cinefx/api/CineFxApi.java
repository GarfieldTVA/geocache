package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Side-neutral registry used by dependent mods to publish named scenes. */
public final class CineFxApi {
    private static final Map<Identifier, SceneDefinition> SCENES = new ConcurrentHashMap<>();

    private CineFxApi() { }

    public static SceneBuilder scene(Identifier id) { return SceneBuilder.create(id); }

    public static void register(SceneDefinition scene) {
        SceneDefinition previous = SCENES.putIfAbsent(scene.id(), scene);
        if (previous != null) throw new IllegalStateException("CineFX scene already registered: " + scene.id());
    }

    /** Useful for hot-reload/dev tools. Production mods should normally use register. */
    public static void replace(SceneDefinition scene) { SCENES.put(scene.id(), scene); }

    public static Optional<SceneDefinition> find(Identifier id) { return Optional.ofNullable(SCENES.get(id)); }

    public static Collection<SceneDefinition> scenes() { return ListView.copyOf(SCENES.values()); }

    private static final class ListView {
        private static <T> Collection<T> copyOf(Collection<T> values) { return java.util.List.copyOf(values); }
    }
}
