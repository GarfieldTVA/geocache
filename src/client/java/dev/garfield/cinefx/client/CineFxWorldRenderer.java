package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CustomRenderContext;
import dev.garfield.cinefx.client.api.CustomWorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** One renderer for every virtual CineFX world object. No display entities are created. */
public final class CineFxWorldRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double DEFAULT_CULL_DISTANCE_SQUARED = 512.0 * 512.0;

    private CineFxWorldRenderer() { }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        var scenes = CineFxRuntime.INSTANCE.snapshot(absoluteTick);
        if (scenes.isEmpty()) return;

        Camera camera = context.gameRenderer().getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        MatrixStack matrices = context.matrices();
        OrderedRenderCommandQueue queue = context.commandQueue();
        if (matrices == null || queue == null) return;

        VisualClaims claims = new VisualClaims();
        for (ActiveScene scene : scenes) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            for (SceneElement element : scene.elementsByPriority()) {
                if (!element.activeAt(sceneTick)) continue;
                double elementTick = sceneTick - element.startTick();
                if (element instanceof SceneElement.Block block) {
                    renderBlock(matrices, queue, cameraPos, claims, scene, block, elementTick);
                } else if (element instanceof SceneElement.BlockSkin skin) {
                    renderSkin(matrices, queue, cameraPos, claims, scene, skin);
                } else if (element instanceof SceneElement.Beam beam) {
                    renderBeam(matrices, queue, cameraPos, claims, scene, beam, elementTick);
                } else if (element instanceof SceneElement.Ring ring) {
                    renderRing(matrices, queue, cameraPos, claims, scene, ring, elementTick);
                } else if (element instanceof SceneElement.WorldText text) {
                    renderWorldText(client, matrices, queue, camera, cameraPos, claims, scene, text, sceneTick, elementTick);
                } else if (element instanceof SceneElement.Custom custom) {
                    renderCustom(client, context, matrices, queue, camera, cameraPos, claims, scene, custom, sceneTick, elementTick);
                }
            }
        }
    }

    private static void renderBlock(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                    Vec3d cameraPos, VisualClaims claims, ActiveScene scene,
                                    SceneElement.Block block, double elementTick) {
        BlockState state;
        Vec3d base;
        if (block.samplesWorld()) {
            BlockPos source = BlockPos.ofFloored(scene.options().anchor()).add(block.sampleOffset());
            state = scene.sampledBlock(block.key());
            if (state == null || state.isAir()) return;
            base = Vec3d.of(source);
        } else {
            state = block.fixedState();
            if (state.isAir()) return;
            base = scene.options().anchor().add(block.baseOffset());
        }

        Transform transform = sample(block.transform().sample(elementTick), block.motion().sample(elementTick, scene.options().seed()));
        Vec3d worldPos = base.add(transform.translation());
        if (worldPos.squaredDistanceTo(cameraPos) > DEFAULT_CULL_DISTANCE_SQUARED) return;
        BlockPos occupied = BlockPos.ofFloored(worldPos);
        if (block.claimOccupiedCell() && !claims.block(occupied, block.conflictPolicy())) return;

        matrices.push();
        matrices.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y, worldPos.z - cameraPos.z);
        matrices.translate(0.5, 0.5, 0.5);
        applyRotation(matrices, transform.rotationDegrees());
        Vec3d scale = transform.scale();
        matrices.scale((float)scale.x, (float)scale.y, (float)scale.z);
        matrices.translate(-0.5, -0.5, -0.5);
        queue.submitBlock(matrices, state, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, block.outlineColor());
        matrices.pop();
    }

    private static void renderSkin(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                   Vec3d cameraPos, VisualClaims claims, ActiveScene scene,
                                   SceneElement.BlockSkin skin) {
        BlockPos pos = BlockPos.ofFloored(scene.options().anchor()).add(skin.offset());
        if (!claims.block(pos, skin.conflictPolicy())) return;
        Vec3d base = Vec3d.of(pos);
        if (base.squaredDistanceTo(cameraPos) > DEFAULT_CULL_DISTANCE_SQUARED) return;

        matrices.push();
        matrices.translate(base.x - cameraPos.x, base.y - cameraPos.y, base.z - cameraPos.z);
        double inflate = skin.inflate();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.scale((float)inflate, (float)inflate, (float)inflate);
        matrices.translate(-0.5, -0.5, -0.5);
        queue.submitBlock(matrices, skin.replacement(), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
        matrices.pop();
    }

    private static void renderBeam(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                   Vec3d cameraPos, VisualClaims claims, ActiveScene scene,
                                   SceneElement.Beam beam, double elementTick) {
        if (!claims.claim("beam:" + beam.key(), beam.conflictPolicy())) return;
        Transform transform = sample(beam.transform().sample(elementTick), beam.motion().sample(elementTick, scene.options().seed()));
        Vec3d anchor = scene.options().anchor().add(transform.translation());
        if (anchor.squaredDistanceTo(cameraPos) > DEFAULT_CULL_DISTANCE_SQUARED) return;
        int color = beam.color().sample(elementTick);

        matrices.push();
        matrices.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
        applyRotation(matrices, transform.rotationDegrees());
        Vec3d scale = transform.scale();
        matrices.scale((float)scale.x, (float)scale.y, (float)scale.z);
        queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) ->
                drawBeam(vertices, entry.getPositionMatrix(), beam.fromOffset(), beam.toOffset(), beam.width(), color));
        matrices.pop();
    }

    private static void renderRing(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                   Vec3d cameraPos, VisualClaims claims, ActiveScene scene,
                                   SceneElement.Ring ring, double elementTick) {
        if (!claims.claim("ring:" + ring.key(), ring.conflictPolicy())) return;
        Transform transform = sample(ring.transform().sample(elementTick), ring.motion().sample(elementTick, scene.options().seed()));
        Vec3d anchor = scene.options().anchor().add(ring.baseOffset()).add(transform.translation());
        if (anchor.squaredDistanceTo(cameraPos) > DEFAULT_CULL_DISTANCE_SQUARED) return;
        double radius = Math.max(0.0, ring.radius().sample(elementTick));
        double thickness = Math.max(0.001, ring.thickness().sample(elementTick));
        int color = ring.color().sample(elementTick);

        matrices.push();
        matrices.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
        applyRotation(matrices, transform.rotationDegrees());
        Vec3d scale = transform.scale();
        matrices.scale((float)scale.x, (float)scale.y, (float)scale.z);
        queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) ->
                drawRing(vertices, entry.getPositionMatrix(), radius, thickness, ring.segments(), color));
        matrices.pop();
    }

    private static void renderWorldText(MinecraftClient client, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                        Camera camera, Vec3d cameraPos, VisualClaims claims, ActiveScene scene,
                                        SceneElement.WorldText element, double sceneTick, double elementTick) {
        Transform transform = sample(element.transform().sample(elementTick), element.motion().sample(elementTick, scene.options().seed()));
        Vec3d worldPos = scene.options().anchor().add(element.baseOffset()).add(transform.translation());
        if (worldPos.squaredDistanceTo(cameraPos) > DEFAULT_CULL_DISTANCE_SQUARED) return;
        if (!claims.claim("world-text:" + BlockPos.ofFloored(worldPos).asLong(), element.conflictPolicy())) return;

        String value = TemplateEngine.render(element.textTemplate(), sceneTick, scene.definition().durationTicks(), scene.options());
        StyleSpriteSource.Font font = new StyleSpriteSource.Font(element.fontId());
        Text text = Text.literal(value).styled(style -> style.withFont(font));
        int width = client.textRenderer.getWidth(text);
        float x = -width / 2.0f;
        int color = element.color().sample(elementTick);

        matrices.push();
        matrices.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y, worldPos.z - cameraPos.z);
        if (element.billboard()) matrices.multiply(camera.getRotation());
        applyRotation(matrices, transform.rotationDegrees());
        Vec3d scale = transform.scale();
        float base = 0.025f;
        matrices.scale((float)(base * scale.x), (float)(-base * scale.y), (float)(base * scale.z));

        RenderCommandQueue panelQueue = queue.getBatchingQueue(0);
        RenderCommandQueue textQueue = queue.getBatchingQueue(1);
        if ((element.backgroundArgb() >>> 24) != 0) {
            panelQueue.submitCustom(matrices, RenderLayers.textBackgroundSeeThrough(),
                    (entry, vertices) -> drawQuad(vertices, entry.getPositionMatrix(),
                            x - 3.0f, -3.0f, x + width + 3.0f, client.textRenderer.fontHeight + 2.0f,
                            element.backgroundArgb()));
        }
        textQueue.submitText(matrices, x, 0.0f, text.asOrderedText(), false,
                element.seeThrough() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                FULL_BRIGHT, color, 0, 0);
        matrices.pop();
    }

    private static void renderCustom(MinecraftClient client, WorldRenderContext context, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue, Camera camera, Vec3d cameraPos,
                                     VisualClaims claims, ActiveScene scene, SceneElement.Custom element,
                                     double sceneTick, double elementTick) {
        if (!claims.claim("custom:" + element.rendererType() + ':' + element.key(), element.conflictPolicy())) return;
        CustomWorldRenderer renderer = CineFxRuntime.INSTANCE.customRenderers().find(element.rendererType());
        if (renderer == null) return;
        Transform transform = sample(element.transform().sample(elementTick), element.motion().sample(elementTick, scene.options().seed()));
        try {
            renderer.render(new CustomRenderContext(client, context, matrices, queue, camera, cameraPos,
                    scene.options().anchor(), sceneTick, scene.options().seed(), element, transform));
        } catch (RuntimeException exception) {
            System.err.println("[CineFX] Custom renderer failed for " + element.rendererType() + ": " + exception.getMessage());
        }
    }

    private static Transform sample(Transform track, Transform motion) { return track.combine(motion); }

    private static void applyRotation(MatrixStack matrices, Vec3d degrees) {
        if (degrees.x != 0.0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)degrees.x));
        if (degrees.y != 0.0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)degrees.y));
        if (degrees.z != 0.0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)degrees.z));
    }

    private static void drawBeam(VertexConsumer vertices, Matrix4f matrix, Vec3d from, Vec3d to, double width, int color) {
        Vec3d delta = to.subtract(from);
        if (delta.lengthSquared() < 1.0e-8) return;
        Vec3d dir = delta.normalize();
        Vec3d reference = Math.abs(dir.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d side = dir.crossProduct(reference).normalize().multiply(width * 0.5);
        Vec3d up = dir.crossProduct(side).normalize().multiply(width * 0.5);
        coloredQuad(vertices, matrix, from.subtract(side), from.add(side), to.add(side), to.subtract(side), color);
        coloredQuad(vertices, matrix, from.subtract(up), from.add(up), to.add(up), to.subtract(up), color);
    }

    private static void drawRing(VertexConsumer vertices, Matrix4f matrix, double radius, double thickness, int segments, int color) {
        double inner = Math.max(0.0, radius - thickness * 0.5);
        double outer = radius + thickness * 0.5;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0 * i) / segments;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segments;
            Vec3d p0 = new Vec3d(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
            Vec3d p1 = new Vec3d(Math.cos(a0) * outer, 0, Math.sin(a0) * outer);
            Vec3d p2 = new Vec3d(Math.cos(a1) * outer, 0, Math.sin(a1) * outer);
            Vec3d p3 = new Vec3d(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
            coloredQuad(vertices, matrix, p0, p1, p2, p3, color);
        }
    }

    private static void coloredQuad(VertexConsumer vertices, Matrix4f matrix, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        vertices.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(color);
        vertices.vertex(matrix, (float)b.x, (float)b.y, (float)b.z).color(color);
        vertices.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(color);
        vertices.vertex(matrix, (float)d.x, (float)d.y, (float)d.z).color(color);
    }

    private static void drawQuad(VertexConsumer vertices, Matrix4f matrix, float left, float top,
                                 float right, float bottom, int color) {
        vertices.vertex(matrix, left, top, 0.01f).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, left, bottom, 0.01f).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, right, bottom, 0.01f).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, right, top, 0.01f).color(color).light(FULL_BRIGHT);
    }
}
