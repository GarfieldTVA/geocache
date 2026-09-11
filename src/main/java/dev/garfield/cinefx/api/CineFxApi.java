package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Side-neutral registry used by dependent mods to publish named scenes. */
public final class CineFxApi {
    private static final Map<Identifier, SceneDefinition> SCENES = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Consumer<SceneDefinition>> REPLACE_LISTENERS = new CopyOnWriteArrayList<>();

    private CineFxApi() { }

    public static SceneBuilder scene(Identifier id) { return SceneBuilder.create(id); }

    public static void register(SceneDefinition scene) {
        if (scene == null) throw new IllegalArgumentException("scene is required");
        SceneDefinition previous = SCENES.putIfAbsent(scene.id(), scene);
        if (previous != null) throw new IllegalStateException("CineFX scene already registered: " + scene.id());
    }

    /**
     * Hot-reload/dev-tools path. Existing client scene instances keep their instance id, start time
     * and SceneOptions; listeners can swap only the immutable SceneDefinition backing them.
     */
    public static void replace(SceneDefinition scene) {
        if (scene == null) throw new IllegalArgumentException("scene is required");
        SCENES.put(scene.id(), scene);
        for (Consumer<SceneDefinition> listener : REPLACE_LISTENERS) {
            try {
                listener.accept(scene);
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Scene hot-reload listener failed for " + scene.id() + ": " + exception.getMessage());
            }
        }
    }

    /** Registers a lightweight registry replacement listener. The returned Runnable unsubscribes it. */
    public static Runnable onReplace(Consumer<SceneDefinition> listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        REPLACE_LISTENERS.add(listener);
        return () -> REPLACE_LISTENERS.remove(listener);
    }

    public static Optional<SceneDefinition> find(Identifier id) { return Optional.ofNullable(SCENES.get(id)); }

    public static Collection<SceneDefinition> scenes() { return ListView.copyOf(SCENES.values()); }

    private static final class ListView {
        private static <T> Collection<T> copyOf(Collection<T> values) { return java.util.List.copyOf(values); }
    }
}
