package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.client.api.EventMarkerListener;
import dev.garfield.cinefx.client.api.SceneHandle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Client-thread scene runtime. No individual visual object is ticked. */
public final class CineFxRuntime {
    public static final CineFxRuntime INSTANCE = new CineFxRuntime();

    private final AtomicLong ids = new AtomicLong(1L);
    private final ArrayList<ActiveScene> active = new ArrayList<>();
    private final CustomRendererRegistry customRenderers = new CustomRendererRegistry();
    private final PostFxBackendRegistry postFxBackends = new PostFxBackendRegistry();
    private final LightingBackendRegistry lightingBackends = new LightingBackendRegistry();
    private final CinematicBackendRegistry cinematicBackends = new CinematicBackendRegistry();
    private final ArrayList<EventMarkerListener> markerListeners = new ArrayList<>();

    private CineFxRuntime() {
        CineFxApi.onReplace(this::onSceneReplaced);
    }

    public SceneHandle play(Identifier sceneId, SceneOptions options) {
        requireClientThread();
        SceneDefinition definition = CineFxApi.find(sceneId)
                .orElseThrow(() -> new IllegalStateException("CineFX scene is not registered: " + sceneId));
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? 0L : client.world.getTime();
        long start = options.startGameTime() < 0L ? now : options.startGameTime();
        long id = ids.getAndIncrement();
        ActiveScene scene = new ActiveScene(id, definition, options, start, client);
        active.add(scene);
        sortActive();
        return new SceneHandle(id, sceneId);
    }

    public void stop(long instanceId) {
        requireClientThread();
        active.removeIf(scene -> scene.instanceId() == instanceId);
    }

    public void stop(Identifier sceneId) {
        requireClientThread();
        active.removeIf(scene -> scene.definition().id().equals(sceneId));
    }

    public void clear() { active.clear(); }

    /** Snapshot is tiny (active scenes, not individual blocks) and prevents mutation during callbacks. */
    public List<ActiveScene> snapshot(double absoluteGameTick) {
        active.removeIf(scene -> scene.completed(absoluteGameTick));
        return List.copyOf(active);
    }

    public CustomRendererRegistry customRenderers() { return customRenderers; }
    public PostFxBackendRegistry postFxBackends() { return postFxBackends; }
    public LightingBackendRegistry lightingBackends() { return lightingBackends; }
    public CinematicBackendRegistry cinematicBackends() { return cinematicBackends; }

    public void addMarkerListener(EventMarkerListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        markerListeners.add(listener);
    }

    public void fireMarker(Identifier sceneId, long sceneInstanceId, String name, Map<String, String> parameters) {
        for (EventMarkerListener listener : List.copyOf(markerListeners)) {
            try {
                listener.onMarker(sceneId, sceneInstanceId, name, parameters);
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Marker listener failed for " + name + ": " + exception.getMessage());
            }
        }
    }

    public static double absoluteGameTick(MinecraftClient client) {
        if (client.world == null) return 0.0;
        return client.world.getTime() + client.getRenderTickCounter().getTickProgress(false);
    }

    private void onSceneReplaced(SceneDefinition replacement) {
        MinecraftClient client = MinecraftClient.getInstance();
        Runnable refresh = () -> {
            boolean changed = false;
            for (ActiveScene scene : active) {
                if (!scene.definition().id().equals(replacement.id())) continue;
                scene.refreshDefinition(replacement, client);
                changed = true;
            }
            if (changed) sortActive();
        };
        if (client.isOnThread()) refresh.run(); else client.execute(refresh);
    }

    private void sortActive() {
        active.sort(Comparator.comparingInt((ActiveScene value) -> value.definition().priority()).reversed());
    }

    private static void requireClientThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) throw new IllegalStateException("CineFX client API must run on the Minecraft client thread");
    }
}
