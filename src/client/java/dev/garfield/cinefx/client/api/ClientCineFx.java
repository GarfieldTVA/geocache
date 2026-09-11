package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.QualityTier;
import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.client.AdaptiveQualityController;
import dev.garfield.cinefx.client.CineFxAssetPreloader;
import dev.garfield.cinefx.client.CineFxDebugOverlay;
import dev.garfield.cinefx.client.CineFxRuntime;
import dev.garfield.cinefx.client.CineFxUltraEditorOverlay;
import dev.garfield.cinefx.client.UltraBackendRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Client-only API for playback, renderer integrations, preload and developer controls. */
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

    /** Freeze a running scene at its current scene-local tick. */
    public static void pause(SceneHandle handle) {
        if (handle != null) CineFxRuntime.INSTANCE.pause(handle.instanceId());
    }

    /** Freeze a running scene at an explicit scene-local tick without recreating the scene. */
    public static void seek(SceneHandle handle, double localTick) {
        if (handle != null) CineFxRuntime.INSTANCE.seek(handle.instanceId(), localTick);
    }

    /** Resume a paused/seeked scene from its exact frozen position. */
    public static void resume(SceneHandle handle) {
        if (handle != null) CineFxRuntime.INSTANCE.resume(handle.instanceId());
    }

    public static void registerRenderer(Identifier type, CustomWorldRenderer renderer) {
        CineFxRuntime.INSTANCE.customRenderers().register(type, renderer);
    }

    public static void registerPostFxBackend(Identifier id, int priority, PostFxBackend backend) {
        CineFxRuntime.INSTANCE.postFxBackends().register(id, priority, backend);
    }

    public static void registerLightingBackend(Identifier id, int priority, LightingBackend backend) {
        CineFxRuntime.INSTANCE.lightingBackends().register(id, priority, backend);
    }

    public static void registerCinematicBackend(Identifier id, int priority, CinematicBackend backend) {
        CineFxRuntime.INSTANCE.cinematicBackends().register(id, priority, backend);
    }

    public static void registerUltraBackend(Identifier id, int priority, UltraBackend backend) {
        UltraBackendRegistry.INSTANCE.register(id, priority, backend);
    }

    public static void registerAssetPreloader(Identifier id, int priority, AssetPreloadBackend backend) {
        CineFxAssetPreloader.register(id, priority, backend);
    }

    public static CineFxAssetPreloader.Result preload(Identifier bundleId) {
        return CineFxAssetPreloader.preload(bundleId);
    }

    public static QualityTier quality() { return AdaptiveQualityController.current(); }
    public static void forceQuality(QualityTier tier) { AdaptiveQualityController.force(tier); }
    public static void automaticQuality() { AdaptiveQualityController.automatic(); }
    public static void debugOverlay(boolean enabled) { CineFxDebugOverlay.setEnabled(enabled); }
    public static void ultraEditor(boolean enabled) { CineFxUltraEditorOverlay.setEnabled(enabled); }

    public static void onMarker(EventMarkerListener listener) {
        CineFxRuntime.INSTANCE.addMarkerListener(listener);
    }

    /** Public so custom network bridges can reuse CineFX's synchronized playback path. */
    public static SceneHandle playNetworked(Identifier sceneId, Vec3d anchor, long startGameTime,
                                            long seed, Map<String, String> variables) {
        return play(sceneId, new SceneOptions(anchor, startGameTime, seed, variables));
    }
}
