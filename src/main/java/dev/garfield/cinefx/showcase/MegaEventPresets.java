package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
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
        register("event_zeropoint", augmentZeroPoint(ZeroPointNarrative.build(ZEROPOINT)));
        register("event_invasion", StarfallNarrative.build(INVASION));
        register("event_titan", TitanNarrative.build(TITAN));
        register("event_nexus", ChronoNarrative.build(NEXUS));
    }

    public static Map<String, Identifier> catalog() { return Map.copyOf(CATALOG); }

    /** Signature Zero Point beat: thousands of terrain fragments are ripped into a tightening helix around the escaped core. */
    private static SceneDefinition augmentZeroPoint(SceneDefinition source) {
        ArrayList<SceneElement> elements = new ArrayList<>(source.elements());
        elements.add(NarrativeSetPieces.singularityLift("zp_environment_singularity", 4380, 5580,
                new Vec3d(0, 0, 18), 2200, 34.0, 30.0, 0xFF93A9B7));
        elements.add(NarrativeSetPieces.orbitalDebris("zp_suspended_reality_chunks", 5200, 6120,
                new Vec3d(0, 17, 18), 1200, 27.0, 0xFFB5C9D3));
        Map<String, String> metadata = new java.util.HashMap<>(source.metadata());
        metadata.put("signature_set_piece", "2200 terrain blocks ripped into the Zero Point plus 1200 suspended chunks");
        return new SceneDefinition(source.id(), source.durationTicks(), source.priority(), source.looping(), elements, metadata);
    }

    private static void register(String commandName, SceneDefinition scene) {
        CineFxApi.register(scene);
        CATALOG.put(commandName, scene.id());
    }

    private static Identifier id(String path) { return Identifier.of("cinefx", path); }
}
