package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assets that should be warmed before a live event reaches an expensive phase.
 * resources are real ResourceManager ids; logicalAssets are renderer-owned model/animation ids.
 */
public record AssetBundle(
        Identifier id,
        List<Identifier> resources,
        List<Identifier> logicalAssets,
        Map<String, String> metadata
) {
    public AssetBundle {
        if (id == null) throw new IllegalArgumentException("id is required");
        resources = resources == null ? List.of() : List.copyOf(resources);
        logicalAssets = logicalAssets == null ? List.of() : List.copyOf(logicalAssets);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static final class Registry {
        private static final ConcurrentHashMap<Identifier, AssetBundle> BUNDLES = new ConcurrentHashMap<>();
        private Registry() { }

        public static void register(AssetBundle bundle) {
            if (bundle == null) throw new IllegalArgumentException("bundle is required");
            AssetBundle previous = BUNDLES.putIfAbsent(bundle.id(), bundle);
            if (previous != null) throw new IllegalStateException("CineFX asset bundle already registered: " + bundle.id());
        }

        /** Tooling/hot-reload path. Runtime users that need duplicate protection should keep using register(). */
        public static void replace(AssetBundle bundle) {
            if (bundle == null) throw new IllegalArgumentException("bundle is required");
            BUNDLES.put(bundle.id(), bundle);
        }

        public static boolean remove(Identifier id) { return id != null && BUNDLES.remove(id) != null; }
        public static Optional<AssetBundle> find(Identifier id) { return Optional.ofNullable(BUNDLES.get(id)); }
        public static Map<Identifier, AssetBundle> snapshot() { return Map.copyOf(BUNDLES); }
    }
}
