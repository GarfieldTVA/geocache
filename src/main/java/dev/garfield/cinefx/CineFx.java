package dev.garfield.cinefx;

import dev.garfield.cinefx.api.EventAuthoringServer;
import dev.garfield.cinefx.api.EventDirector;
import dev.garfield.cinefx.api.EventDirectorCommands;
import dev.garfield.cinefx.network.CineFxNetworking;
import dev.garfield.cinefx.showcase.MegaEventPresets;
import dev.garfield.cinefx.showcase.ShowcaseCommands;
import dev.garfield.cinefx.showcase.ShowcaseScenes;
import dev.garfield.cinefx.showcase.ShowcaseValidator;
import net.fabricmc.api.ModInitializer;

/** Common bootstrap. CineFX keeps scene definitions side-neutral; rendering lives in the client source set. */
public final class CineFx implements ModInitializer {
    public static final String MOD_ID = "cinefx";

    @Override
    public void onInitialize() {
        CineFxNetworking.initialize();
        EventDirector.INSTANCE.initialize();
        EventAuthoringServer.initialize();
        EventDirectorCommands.initialize();
        ShowcaseScenes.registerAll();
        MegaEventPresets.register();
        ShowcaseValidator.requireValid();
        ShowcaseCommands.initialize();
    }
}
