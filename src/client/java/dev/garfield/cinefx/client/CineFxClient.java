package dev.garfield.cinefx.client;

import dev.garfield.cinefx.CineFx;
import dev.garfield.cinefx.client.api.ClientCineFx;
import dev.garfield.cinefx.network.PlayScenePayload;
import dev.garfield.cinefx.network.PreloadAckPayload;
import dev.garfield.cinefx.network.PreloadAssetsPayload;
import dev.garfield.cinefx.network.StopScenePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Installs CineFX rendering, advanced-event bridges and synchronization receivers. */
public final class CineFxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CineFxAssetPreloader.register(
                Identifier.of(CineFx.MOD_ID, "builtin_gltf"),
                -1000,
                CineFxClient::preloadGltf);

        CineFxRuntime.INSTANCE.cinematicBackends().register(
                Identifier.of(CineFx.MOD_ID, "vanilla_actors"),
                -1000,
                new CineFxBuiltInCinematicBackend());

        UltraBackendRegistry.INSTANCE.register(
                Identifier.of(CineFx.MOD_ID, "native_ultra"),
                -1000,
                new CineFxUltraNativeBackend());
        CineFxUltraClientCommands.register();

        WorldRenderEvents.END_MAIN.register(CineFxWorldRenderer::render);
        // Ultra is sampled before graph rendering so procedural rigs, material effects and light rigs
        // are available to the built-in premium glTF renderer in the same render frame.
        WorldRenderEvents.END_MAIN.register(CineFxUltraBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxSceneGraphBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxAdvancedEventBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxLightingBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxEventBridge::render);

        ClientTickEvents.END_CLIENT_TICK.register(CineFxEventBridge::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CineFxPlayerControlState::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CineFxAudioLayerMixer::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CineFxNativeVisualFallback::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CineFxUltraState.tick();
            CineFxUltraPhysics.tick();
            CineFxUltraSpatialAudio.tick(client);
        });

        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "native_sky_fallback"), CineFxNativeVisualFallback::renderSkyHud);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "ultra_post"), CineFxUltraPostOverlay::render);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "hud"), CineFxHudRenderer::render);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "ultra_editor"), CineFxUltraEditorOverlay::render);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "debug"), CineFxDebugOverlay::render);

        ClientPlayNetworking.registerGlobalReceiver(PlayScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.playNetworked(
                        parse(payload.sceneId()),
                        new Vec3d(payload.x(), payload.y(), payload.z()),
                        payload.startGameTime(), payload.seed(), payload.variables())));

        ClientPlayNetworking.registerGlobalReceiver(StopScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.stop(parse(payload.sceneId()))));

        ClientPlayNetworking.registerGlobalReceiver(PreloadAssetsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CineFxAssetPreloader.Result result = CineFxAssetPreloader.preload(parse(payload.bundleId()));
                    ClientPlayNetworking.send(new PreloadAckPayload(payload.requestId(), result.ready(),
                            result.missingResources() + result.logicalAssetsFailed()));
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CineFxRuntime.INSTANCE.clear();
            CineFxSceneGraphBridge.clear();
            CineFxVanillaActorRenderer.clear();
            CineFxPremiumGltfRenderer.clear();
            CineFxGltfRenderer.clear();
            CineFxPlayerControlState.clear();
            CineFxAudioLayerMixer.clear();
            CineFxNativeVisualFallback.clear();
            CineFxUltraState.clear();
            CineFxUltraPhysics.clear();
            CineFxUltraSpatialAudio.clear();
            CineFxUltraPostOverlay.clear();
            CineFxUltraEditorOverlay.clear();
            AdaptiveQualityController.reset();
        });
    }

    private static boolean preloadGltf(Identifier logical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        ResourceManager manager = client.getResourceManager();
        if (!hasGltfResource(manager, logical)) return false;
        CineFxPremiumGltfRenderer.preload(logical);
        CineFxGltfRenderer.preload(logical);
        return true;
    }

    private static boolean hasGltfResource(ResourceManager manager, Identifier logical) {
        String namespace = logical.getNamespace();
        String path = logical.getPath();
        if ((path.endsWith(".gltf") || path.endsWith(".glb")) && manager.getResource(logical).isPresent()) return true;
        String clean = path;
        if (clean.startsWith("models/")) clean = clean.substring(7);
        if (clean.startsWith("model/")) clean = clean.substring(6);
        return manager.getResource(Identifier.of(namespace, "cinefx/models/" + clean + ".glb")).isPresent()
                || manager.getResource(Identifier.of(namespace, "cinefx/models/" + clean + ".gltf")).isPresent()
                || manager.getResource(Identifier.of(namespace, "models/" + clean + ".glb")).isPresent()
                || manager.getResource(Identifier.of(namespace, "models/" + clean + ".gltf")).isPresent();
    }

    private static Identifier parse(String raw) {
        int separator = raw.indexOf(':');
        return separator < 0
                ? Identifier.ofVanilla(raw)
                : Identifier.of(raw.substring(0, separator), raw.substring(separator + 1));
    }
}
