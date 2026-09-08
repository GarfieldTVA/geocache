package dev.garfield.cinefx;

import dev.garfield.cinefx.network.CineFxNetworking;
import net.fabricmc.api.ModInitializer;

/** Common bootstrap. CineFX keeps scene definitions side-neutral; rendering lives in the client source set. */
public final class CineFx implements ModInitializer {
    public static final String MOD_ID = "cinefx";

    @Override
    public void onInitialize() {
        CineFxNetworking.initialize();
    }
}
