package dev.garfield.cinefx.client;

import dev.garfield.cinefx.CineFx;
import dev.garfield.cinefx.client.api.ClientCineFx;
import dev.garfield.cinefx.network.PlayScenePayload;
import dev.garfield.cinefx.network.StopScenePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Installs one world renderer, one HUD renderer and two tiny scene-control receivers. */
public final class CineFxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldRenderEvents.END_MAIN.register(CineFxWorldRenderer::render);
        HudElementRegistry.addLast(Identifier.of(CineFx.MOD_ID, "hud"), CineFxHudRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(PlayScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.playNetworked(
                        parse(payload.sceneId()),
                        new Vec3d(payload.x(), payload.y(), payload.z()),
                        payload.startGameTime(), payload.seed(), payload.variables())));

        ClientPlayNetworking.registerGlobalReceiver(StopScenePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCineFx.stop(parse(payload.sceneId()))));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CineFxRuntime.INSTANCE.clear());
    }

    private static Identifier parse(String raw) {
        int separator = raw.indexOf(':');
        return separator < 0
                ? Identifier.ofVanilla(raw)
                : Identifier.of(raw.substring(0, separator), raw.substring(separator + 1));
    }
}
