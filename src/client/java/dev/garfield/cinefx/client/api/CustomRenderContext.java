package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** Client extension context. The matrix stack is the shared world stack: always push/pop around your changes. */
public record CustomRenderContext(
        MinecraftClient client,
        WorldRenderContext worldContext,
        MatrixStack matrices,
        OrderedRenderCommandQueue commandQueue,
        Camera camera,
        Vec3d cameraPosition,
        Vec3d sceneAnchor,
        double sceneTick,
        long sceneSeed,
        SceneElement.Custom element,
        Transform sampledTransform
) { }
