package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AssetBundle;
import dev.garfield.cinefx.client.api.AssetPreloadBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Synchronous warmup coordinator. Resource ids are verified by vanilla; logical ids are offered to integrations. */
public final class CineFxAssetPreloader {
    private static final List<Entry> BACKENDS = new ArrayList<>();

    private CineFxAssetPreloader() { }

    public static synchronized void register(Identifier id, int priority, AssetPreloadBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id/backend are required");
        if (BACKENDS.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX asset preloader already registered: " + id);
        }
        BACKENDS.add(new Entry(id, priority, backend));
        BACKENDS.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public static Result preload(Identifier bundleId) {
        MinecraftClient client = MinecraftClient.getInstance();
        AssetBundle bundle = AssetBundle.Registry.find(bundleId).orElse(null);
        if (bundle == null) return new Result(false, 1, 0);

        int missing = 0;
        for (Identifier resource : bundle.resources()) {
            if (client.getResourceManager().getResource(resource).isEmpty()) missing++;
        }

        int logicalFailed = 0;
        for (Identifier asset : bundle.logicalAssets()) {
            boolean handled = false;
            synchronized (CineFxAssetPreloader.class) {
                for (Entry entry : BACKENDS) {
                    try {
                        if (entry.backend.preload(asset)) {
                            handled = true;
                            break;
                        }
                    } catch (RuntimeException exception) {
                        System.err.println("[CineFX] Asset preloader failed: " + entry.id + " - " + exception.getMessage());
                    }
                }
            }
            if (!handled && !BACKENDS.isEmpty()) logicalFailed++;
        }
        return new Result(missing == 0 && logicalFailed == 0, missing, logicalFailed);
    }

    public record Result(boolean ready, int missingResources, int logicalAssetsFailed) { }
    private record Entry(Identifier id, int priority, AssetPreloadBackend backend) { }
}
