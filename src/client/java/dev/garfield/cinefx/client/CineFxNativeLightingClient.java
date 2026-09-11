package dev.garfield.cinefx.client;

import net.fabricmc.api.ClientModInitializer;

/** Registers the low-priority vanilla-safe lighting preview backend. */
public final class CineFxNativeLightingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CineFxNativeLightingFallback.register();
    }
}
