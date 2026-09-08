package dev.garfield.cinefx.client;

import dev.garfield.cinefx.CineFx;
import dev.garfield.cinefx.client.api.ClientCineFx;
import dev.garfield.cinefx.network.PlayScenePayload;
import dev.garfield.cinefx.network.StopScenePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Installs CineFX render, camera/event/scene-graph bridges and small scene-control receivers. */
public final class CineFxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Lowest-priority fallback: real vanilla player/entity models without world/tracker entities.
        CineFxRuntime.INSTANCE.cinematicBackends().register(
                Identifier.of(CineFx.MOD_ID, "vanilla_actors"),
                -1000,
                new CineFxBuiltInCinematicBackend());

        WorldRenderEvents.END_MAIN.register(CineFxWorldRenderer::render);
        WorldRenderEvents.END_MAIN.register(CineFxSceneGraphBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxLightingBridge::render);
        WorldRenderEvents.END_MAIN.register(CineFxEventBridge::render);
        ClientTickEvents.END_CLIENT_TICK.register(CineFxEventBridge::tick);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "hud"), CineFxHudRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(PlayScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.playNetworked(
                        parse(payload.sceneId()),
                        new Vec3d(payload.x(), payload.y(), payload.z()),
                        payload.startGameTime(), payload.seed(), payload.variables())));

        ClientPlayNetworking.registerGlobalReceiver(StopScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.stop(parse(payload.sceneId()))));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CineFxRuntime.INSTANCE.clear();
            CineFxSceneGraphBridge.clear();
            CineFxVanillaActorRenderer.clear();
        });
    }

    private static Identifier parse(String raw) {
        int separator = raw.indexOf(':');
        return separator < 0
                ? Identifier.ofVanilla(raw)
                : Identifier.of(raw.substring(0, separator), raw.substring(separator + 1));
    }
}
