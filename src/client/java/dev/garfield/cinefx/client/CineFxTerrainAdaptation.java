package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.InstanceBatchFrame;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Lightweight client-side terrain fitting for story visuals that explicitly opt in. */
final class CineFxTerrainAdaptation {
    private CineFxTerrainAdaptation() { }

    static ActorFrame actor(ActorFrame frame) {
        if (!flag(frame.appearance(), "ground_snap")) return frame;
        Vec3d snapped = snapped(frame.worldPosition(), 12.0);
        if (snapped == null) return frame;
        Matrix4f matrix = translated(frame.worldMatrix(), snapped.y - frame.worldPosition().y);
        return new ActorFrame(frame.sceneInstanceId(), frame.elementKey(), frame.kind(), frame.resourceId(),
                frame.profileName(), frame.skinTexture(), frame.appearance(), matrix, snapped, frame.lookAt(),
                frame.tintArgb(), frame.opacity(), frame.emissive(), frame.animations(), frame.boneOverrides(),
                frame.morphs(), frame.castShadow(), frame.localTick());
    }

    static InstanceBatchFrame batch(InstanceBatchFrame frame) {
        if (!flag(frame.parameters(), "terrain_snap")) return frame;
        Vec3d snapped = snapped(frame.worldPosition(), 16.0);
        if (snapped == null) return frame;
        Matrix4f matrix = translated(frame.rootMatrix(), snapped.y - frame.worldPosition().y);
        return new InstanceBatchFrame(frame.sceneInstanceId(), frame.elementKey(), frame.modelId(), frame.materialId(),
                matrix, snapped, frame.instances(), frame.castShadow(), frame.lodGroup(), frame.parameters(),
                frame.seed(), frame.localTick());
    }

    private static Vec3d snapped(Vec3d position, double maxCorrection) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        int x = (int)Math.floor(position.x);
        int z = (int)Math.floor(position.z);
        int start = (int)Math.floor(position.y) + 8;
        int end = start - 36;
        for (int y = start; y >= end; y--) {
            BlockPos floor = new BlockPos(x, y, z);
            BlockState state = client.world.getBlockState(floor);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            BlockState above = client.world.getBlockState(floor.up());
            if (!above.isAir()) continue;
            double targetY = y + 1.01;
            double correction = targetY - position.y;
            if (Math.abs(correction) > maxCorrection) return null;
            return new Vec3d(position.x, targetY, position.z);
        }
        return null;
    }

    private static Matrix4f translated(org.joml.Matrix4fc source, double dy) {
        Matrix4f out = new Matrix4f(source);
        out.m31(out.m31() + (float)dy);
        return out;
    }

    private static boolean flag(java.util.Map<String, String> values, String key) {
        if (values == null) return false;
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }
}
