package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.client.api.SceneHandle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Client-thread scene runtime. No individual visual object is ticked. */
public final class CineFxRuntime {
    public static final CineFxRuntime INSTANCE = new CineFxRuntime();

    private final AtomicLong ids = new AtomicLong(1L);
    private final ArrayList<ActiveScene> active = new ArrayList<>();
    private final CustomRendererRegistry customRenderers = new CustomRendererRegistry();
    private final PostFxBackendRegistry postFxBackends = new PostFxBackendRegistry();

    private CineFxRuntime() { }

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
        active.sort(Comparator.comparingInt((ActiveScene value) -> value.definition().priority()).reversed());
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

    public static double absoluteGameTick(MinecraftClient client) {
        if (client.world == null) return 0.0;
        return client.world.getTime() + client.getRenderTickCounter().getTickProgress(false);
    }

    private static void requireClientThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) throw new IllegalStateException("CineFX client API must run on the Minecraft client thread");
    }
}
