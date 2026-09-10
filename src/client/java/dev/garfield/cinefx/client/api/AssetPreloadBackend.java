package dev.garfield.cinefx.client.api;

import net.minecraft.util.Identifier;

/** Optional client integration used to warm renderer-owned meshes, rigs, animations or materials. */
@FunctionalInterface
public interface AssetPreloadBackend {
    /** Return true when the logical asset is ready for immediate use. */
    boolean preload(Identifier assetId);
}
