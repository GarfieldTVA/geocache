package dev.garfield.cinefx.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class CineFxNetworking {
    private static boolean initialized;

    private CineFxNetworking() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.playS2C().register(PlayScenePayload.ID, PlayScenePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StopScenePayload.ID, StopScenePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PreloadAssetsPayload.ID, PreloadAssetsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PublishEventPublicationResultPayload.ID, PublishEventPublicationResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PreloadAckPayload.ID, PreloadAckPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PublishEventPublicationPayload.ID, PublishEventPublicationPayload.CODEC);
    }
}
