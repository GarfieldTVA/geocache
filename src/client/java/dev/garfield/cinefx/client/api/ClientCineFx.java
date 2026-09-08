package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.client.CineFxRuntime;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Client-only API for local playback and renderer integrations. */
public final class ClientCineFx {
    private ClientCineFx() { }

    public static SceneHandle play(Identifier sceneId, SceneOptions options) {
        return CineFxRuntime.INSTANCE.play(sceneId, options);
    }

    public static SceneHandle play(Identifier sceneId, Vec3d anchor) {
        return play(sceneId, SceneOptions.at(anchor));
    }

    public static void stop(SceneHandle handle) { CineFxRuntime.INSTANCE.stop(handle.instanceId()); }
    public static void stop(Identifier sceneId) { CineFxRuntime.INSTANCE.stop(sceneId); }
    public static void stopAll() { CineFxRuntime.INSTANCE.clear(); }

    public static void registerRenderer(Identifier type, CustomWorldRenderer renderer) {
        CineFxRuntime.INSTANCE.customRenderers().register(type, renderer);
    }

    public static void registerPostFxBackend(Identifier id, int priority, PostFxBackend backend) {
        CineFxRuntime.INSTANCE.postFxBackends().register(id, priority, backend);
    }

    /** Public so custom network bridges can reuse CineFX's synchronized playback path. */
    public static SceneHandle playNetworked(Identifier sceneId, Vec3d anchor, long startGameTime,
                                            long seed, Map<String, String> variables) {
        return play(sceneId, new SceneOptions(anchor, startGameTime, seed, variables));
    }
}
