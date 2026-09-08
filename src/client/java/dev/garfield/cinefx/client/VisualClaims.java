package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.ConflictPolicy;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Frame-local reservation table. Render order is high-priority scene, then high-priority element. */
final class VisualClaims {
    private final Set<String> claims = new HashSet<>();

    boolean claim(String key, ConflictPolicy policy) {
        if (policy == ConflictPolicy.ALLOW || policy == ConflictPolicy.FORCE) return true;
        if (claims.contains(key)) return false;
        claims.add(key);
        return true;
    }

    boolean block(BlockPos pos, ConflictPolicy policy) {
        return claim("block:" + pos.asLong(), policy);
    }

    boolean screen(String channel, ConflictPolicy policy) {
        return claim("screen:" + channel, policy);
    }
}
