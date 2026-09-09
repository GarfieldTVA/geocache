package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.SceneDefinition;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registry for the long-form narrative live-event presets. */
public final class MegaEventPresets {
    public static final Identifier ZEROPOINT = id("mega/zeropoint_collapse");
    public static final Identifier INVASION = id("mega/starfall_invasion");
    public static final Identifier TITAN = id("mega/titan_reckoning");
    public static final Identifier NEXUS = id("mega/chrono_nexus");

    private static final LinkedHashMap<String, Identifier> CATALOG = new LinkedHashMap<>();
    private static boolean registered;

    private MegaEventPresets() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PremiumShowcase.register();
        register("event_zeropoint", ZeroPointNarrative.build(ZEROPOINT));
        register("event_invasion", StarfallNarrative.build(INVASION));
        register("event_titan", TitanNarrative.build(TITAN));
        register("event_nexus", ChronoNarrative.build(NEXUS));
    }

    public static Map<String, Identifier> catalog() { return Map.copyOf(CATALOG); }

    private static void register(String commandName, SceneDefinition scene) {
        CineFxApi.register(scene);
        CATALOG.put(commandName, scene.id());
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
