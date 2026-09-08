package dev.garfield.cinefx;

import dev.garfield.cinefx.api.EventDirector;
import dev.garfield.cinefx.network.CineFxNetworking;
import dev.garfield.cinefx.showcase.ShowcaseCommands;
import dev.garfield.cinefx.showcase.ShowcaseScenes;
import net.fabricmc.api.ModInitializer;

/** Common bootstrap. CineFX keeps scene definitions side-neutral; rendering lives in the client source set. */
public final class CineFx implements ModInitializer {
    public static final String MOD_ID = "cinefx";

    @Override
    public void onInitialize() {
        CineFxNetworking.initialize();
        EventDirector.INSTANCE.initialize();
        ShowcaseScenes.registerAll();
        ShowcaseCommands.initialize();
    }
}
