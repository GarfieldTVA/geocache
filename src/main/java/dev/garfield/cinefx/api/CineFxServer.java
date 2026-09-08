package dev.garfield.cinefx.api;

import dev.garfield.cinefx.network.PlayScenePayload;
import dev.garfield.cinefx.network.StopScenePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Server-side synchronization helpers. Animation data is never spammed every tick. */
public final class CineFxServer {
    private CineFxServer() { }

    public static boolean play(ServerPlayerEntity player, Identifier sceneId, SceneOptions options) {
        long start = options.startGameTime() < 0L ? player.getEntityWorld().getTime() : options.startGameTime();
        return play(player, sceneId, options.anchor(), start, options.seed(), options.variables());
    }

    public static boolean play(ServerPlayerEntity player, Identifier sceneId, Vec3d anchor,
                               long startGameTime, long seed) {
        return play(player, sceneId, anchor, startGameTime, seed, Map.of());
    }

    public static boolean play(ServerPlayerEntity player, Identifier sceneId, Vec3d anchor,
                               long startGameTime, long seed, Map<String, String> variables) {
        if (!ServerPlayNetworking.canSend(player, PlayScenePayload.ID)) return false;
        ServerPlayNetworking.send(player, new PlayScenePayload(sceneId.toString(), anchor.x, anchor.y, anchor.z,
                startGameTime, seed, variables));
        return true;
    }

    public static int play(ServerWorld world, Identifier sceneId, SceneOptions options) {
        long start = options.startGameTime() < 0L ? world.getTime() : options.startGameTime();
        int sent = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (play(player, sceneId, options.anchor(), start, options.seed(), options.variables())) sent++;
        }
        return sent;
    }

    public static int play(ServerWorld world, Identifier sceneId, Vec3d anchor, long startGameTime, long seed) {
        return play(world, sceneId, new SceneOptions(anchor, startGameTime, seed, Map.of()));
    }

    /** Recommended for large servers: only notify clients close enough to actually see the event. */
    public static int playAround(ServerWorld world, Identifier sceneId, SceneOptions options, double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) throw new IllegalArgumentException("radius must be >= 0");
        long start = options.startGameTime() < 0L ? world.getTime() : options.startGameTime();
        double radiusSquared = radius * radius;
        int sent = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getEntityPos().squaredDistanceTo(options.anchor()) <= radiusSquared
                    && play(player, sceneId, options.anchor(), start, options.seed(), options.variables())) {
                sent++;
            }
        }
        return sent;
    }

    public static int play(MinecraftServer server, Identifier sceneId, SceneOptions options) {
        int sent = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (play(player, sceneId, options)) sent++;
        }
        return sent;
    }

    public static int play(MinecraftServer server, Identifier sceneId, Vec3d anchor, long startGameTime, long seed) {
        return play(server, sceneId, new SceneOptions(anchor, startGameTime, seed, Map.of()));
    }

    public static boolean stop(ServerPlayerEntity player, Identifier sceneId) {
        if (!ServerPlayNetworking.canSend(player, StopScenePayload.ID)) return false;
        ServerPlayNetworking.send(player, new StopScenePayload(sceneId.toString()));
        return true;
    }
}
