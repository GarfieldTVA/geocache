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
    }
}
