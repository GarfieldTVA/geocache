package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CinematicBackend;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AnimationSample;
import dev.garfield.cinefx.client.api.CinematicBackend.BoneSample;
import dev.garfield.cinefx.client.api.CinematicBackend.DecalFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.InstanceBatchFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MorphSample;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.CinematicBackend.ShadowFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.TrailFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.TrailPoint;
import dev.garfield.cinefx.client.api.CinematicBackend.VolumeFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the high-complexity parent/child scene graph once per frame and submits compact
 * batches to cinematic backends. No graph node becomes a Minecraft entity.
 */
public final class CineFxSceneGraphBridge {
    private static final Map<String, TrailHistory> TRAILS = new HashMap<>();
    private static final Set<String> REPORTED_CYCLES = new HashSet<>();

    private CineFxSceneGraphBridge() { }

    public static void clear() {
        TRAILS.clear();
        REPORTED_CYCLES.clear();
    }

    public static void render(WorldRenderContext worldContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            clear();
            return;
        }

        MatrixStack matrices = worldContext.matrices();
        OrderedRenderCommandQueue queue = worldContext.commandQueue();
        if (matrices == null || queue == null) return;

        Camera camera = worldContext.gameRenderer().getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        SceneRenderContext renderContext = new SceneRenderContext(
                worldContext, matrices, queue, camera, cameraPos, absoluteTick);
        HashSet<String> seenTrails = new HashSet<>();

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;

            List<SceneElement> active = scene.activeElementsAt(sceneTick);
            if (active.isEmpty()) continue;

            HashMap<String, ComplexElement.Transformable> graph = new HashMap<>();
            for (SceneElement raw : active) {
                if (raw instanceof ComplexElement.Transformable transformable) {
                    graph.put(transformable.key(), transformable);
                }
            }
            if (graph.isEmpty()) continue;

            HashMap<String, Matrix4f> resolved = new HashMap<>();
            HashMap<String, Boolean> visibility = new HashMap<>();
            VisualClaims claims = new VisualClaims();
            ArrayList<ActorFrame> actors = new ArrayList<>();
            ArrayList<MeshFrame> meshes = new ArrayList<>();
            ArrayList<InstanceBatchFrame> instances = new ArrayList<>();
            ArrayList<ShadowFrame> shadows = new ArrayList<>();
            ArrayList<TrailFrame> trails = new ArrayList<>();
            ArrayList<DecalFrame> decals = new ArrayList<>();
            ArrayList<VolumeFrame> volumes = new ArrayList<>();

            for (SceneElement raw : active) {
                if (!(raw instanceof ComplexElement.Transformable element)) continue;
                if (!isVisible(element, graph, visibility, new HashSet<>())) continue;
                if (!claims.claim("complex:" + element.key(), element.conflictPolicy())) continue;

                double local = sceneTick - element.startTick();
                Matrix4f matrix = resolve(scene, element, graph, resolved, new HashSet<>(), local);
                Vec3d worldPos = point(matrix, Vec3d.ZERO);

                if (raw instanceof ComplexElement.Actor actor) {
                    if (culled(worldPos, cameraPos, actor.cullDistance())) continue;
                    ArrayList<AnimationSample> animationSamples = new ArrayList<>(actor.animations().size());
                    for (ComplexElement.AnimationLayer layer : actor.animations()) {
                        double weight = clamp01(layer.weight().sample(local));
                        if (weight <= 0.0001) continue;
                        double speed = layer.speed().sample(local);
                        double time = (local + layer.timeOffsetTicks()) * speed;
                        animationSamples.add(new AnimationSample(layer.clipId(), weight, speed, time,
                                layer.looping(), layer.blendMode(), layer.parameters()));
                    }
                    ArrayList<BoneSample> boneSamples = new ArrayList<>(actor.boneOverrides().size());
                    for (ComplexElement.BoneTrack bone : actor.boneOverrides()) {
                        double weight = clamp01(bone.weight().sample(local));
                        if (weight <= 0.0001) continue;
                        boneSamples.add(new BoneSample(bone.bone(), bone.transform().sample(local), weight, bone.blendMode()));
                    }
                    ArrayList<MorphSample> morphSamples = new ArrayList<>(actor.morphs().size());
                    for (ComplexElement.MorphTrack morph : actor.morphs()) {
                        morphSamples.add(new MorphSample(morph.name(), morph.weight().sample(local)));
                    }
                    Vec3d lookAt = actor.lookAtOffset() == null ? null : scene.options().anchor().add(actor.lookAtOffset());
                    actors.add(new ActorFrame(scene.instanceId(), actor.key(), actor.kind(), actor.resourceId(),
                            actor.profileName(), actor.skinTexture(), actor.appearance(), new Matrix4f(matrix), worldPos,
                            lookAt, actor.tint().sample(local), clamp01(actor.opacity().sample(local)),
                            Math.max(0.0, actor.emissive().sample(local)), List.copyOf(animationSamples),
                            List.copyOf(boneSamples), List.copyOf(morphSamples), actor.castShadow(), local));
                } else if (raw instanceof ComplexElement.Mesh mesh) {
                    if (culled(worldPos, cameraPos, mesh.cullDistance())) continue;
                    meshes.add(new MeshFrame(scene.instanceId(), mesh.key(), mesh.modelId(), mesh.materialId(),
                            new Matrix4f(matrix), worldPos, mesh.tint().sample(local), clamp01(mesh.opacity().sample(local)),
                            Math.max(0.0, mesh.emissive().sample(local)), mesh.castShadow(), mesh.parameters(), local));
                } else if (raw instanceof ComplexElement.InstanceBatch batch) {
                    if (culled(worldPos, cameraPos, batch.cullDistance())) continue;
                    instances.add(new InstanceBatchFrame(scene.instanceId(), batch.key(), batch.modelId(), batch.materialId(),
                            new Matrix4f(matrix), worldPos, batch.instances(), batch.castShadow(), batch.lodGroup(),
                            batch.parameters(), scene.options().seed(), local));
                } else if (raw instanceof ComplexElement.Shadow shadow) {
                    if (culled(worldPos, cameraPos, shadow.cullDistance())) continue;
                    shadows.add(new ShadowFrame(scene.instanceId(), shadow.key(), shadow.mode(), shadow.texture(),
                            new Matrix4f(matrix), worldPos, clamp01(shadow.opacity().sample(local)),
                            clamp01(shadow.softness().sample(local)), Math.max(0.0, shadow.radius().sample(local)), local));
                } else if (raw instanceof ComplexElement.Trail trail) {
                    if (culled(worldPos, cameraPos, trail.cullDistance())) continue;
                    String historyKey = scene.instanceId() + ":" + trail.key();
                    seenTrails.add(historyKey);
                    Vec3d source = point(matrix, trail.sourceOffset());
                    TrailHistory history = TRAILS.computeIfAbsent(historyKey, ignored -> new TrailHistory());
                    history.sample(source, absoluteTick, trail);
                    trails.add(new TrailFrame(scene.instanceId(), trail.key(), trail.mode(),
                            history.points(absoluteTick, trail.lifetimeTicks()), Math.max(0.001, trail.width().sample(local)),
                            trail.color().sample(local), clamp01(trail.opacity().sample(local)), local));
                } else if (raw instanceof ComplexElement.Decal decal) {
                    if (culled(worldPos, cameraPos, decal.cullDistance())) continue;
                    decals.add(new DecalFrame(scene.instanceId(), decal.key(), decal.texture(), new Matrix4f(matrix), worldPos,
                            decal.size().sample(local), decal.tint().sample(local), clamp01(decal.opacity().sample(local)),
                            Math.max(0.0, decal.projectionDepth().sample(local)), decal.conformToSurface(), local));
                } else if (raw instanceof ComplexElement.Volume volume) {
                    if (culled(worldPos, cameraPos, volume.cullDistance())) continue;
                    volumes.add(new VolumeFrame(scene.instanceId(), volume.key(), volume.shape(), volume.materialId(),
                            new Matrix4f(matrix), worldPos, volume.color().sample(local),
                            Math.max(0.0, volume.density().sample(local)), Math.max(0.0001, volume.noiseScale().sample(local)),
                            Math.max(0.0, volume.distortion().sample(local)), Math.max(0.0, volume.emissive().sample(local)),
                            volume.parameters(), local));
                }
            }

            CinematicBackendRegistry backends = CineFxRuntime.INSTANCE.cinematicBackends();
            if (!actors.isEmpty()) backends.actors(renderContext, List.copyOf(actors));
            if (!meshes.isEmpty()) backends.meshes(renderContext, List.copyOf(meshes));
            if (!instances.isEmpty()) backends.instances(renderContext, List.copyOf(instances));
            if (!shadows.isEmpty() && !backends.shadows(renderContext, List.copyOf(shadows))) {
                fallbackShadows(matrices, queue, cameraPos, shadows);
            }
            if (!trails.isEmpty() && !backends.trails(renderContext, List.copyOf(trails))) {
                fallbackTrails(matrices, queue, cameraPos, trails);
            }
            if (!decals.isEmpty() && !backends.decals(renderContext, List.copyOf(decals))) {
                fallbackDecals(matrices, queue, cameraPos, decals);
            }
            if (!volumes.isEmpty()) backends.volumes(renderContext, List.copyOf(volumes));
        }

        TRAILS.keySet().removeIf(key -> !seenTrails.contains(key));
    }

    private static Matrix4f resolve(ActiveScene scene, ComplexElement.Transformable element,
                                    Map<String, ComplexElement.Transformable> graph,
                                    Map<String, Matrix4f> cache, Set<String> resolving, double localTick) {
        Matrix4f cached = cache.get(element.key());
        if (cached != null) return cached;

        if (!resolving.add(element.key())) {
            String cycleKey = scene.instanceId() + ":" + element.key();
            if (REPORTED_CYCLES.add(cycleKey)) {
                System.err.println("[CineFX] Scene graph cycle detected at " + element.key());
            }
            return root(scene);
        }

        Matrix4f matrix;
        ComplexElement.Transformable parent = element.parentKey() == null ? null : graph.get(element.parentKey());
        if (parent == null) {
            matrix = root(scene);
        } else {
            double parentLocal = scene.localTick(CineFxRuntime.absoluteGameTick(MinecraftClient.getInstance())) - parent.startTick();
            matrix = new Matrix4f(resolve(scene, parent, graph, cache, resolving, parentLocal));
        }

        AdvancedTransform advanced = element.transform().sample(localTick);
        Transform motion = element.motion().sample(localTick, scene.options().seed());
        Vec3d translation = element.baseOffset().add(advanced.translation()).add(motion.translation());
        Vec3d rotation = advanced.rotationDegrees().add(motion.rotationDegrees());
        Vec3d scale = new Vec3d(
                advanced.scale().x * motion.scale().x,
                advanced.scale().y * motion.scale().y,
                advanced.scale().z * motion.scale().z);
        Vec3d pivot = advanced.pivot();

        matrix.translate((float)translation.x, (float)translation.y, (float)translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)pivot.x, (float)pivot.y, (float)pivot.z);
        if (rotation.x != 0.0) matrix.rotateX((float)Math.toRadians(rotation.x));
        if (rotation.y != 0.0) matrix.rotateY((float)Math.toRadians(rotation.y));
        if (rotation.z != 0.0) matrix.rotateZ((float)Math.toRadians(rotation.z));
        matrix.scale((float)scale.x, (float)scale.y, (float)scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);

        resolving.remove(element.key());
        Matrix4f frozen = new Matrix4f(matrix);
        cache.put(element.key(), frozen);
        return frozen;
    }

    private static Matrix4f root(ActiveScene scene) {
        Vec3d anchor = scene.options().anchor();
        return new Matrix4f().translation((float)anchor.x, (float)anchor.y, (float)anchor.z);
    }

    private static boolean isVisible(ComplexElement.Transformable element,
                                     Map<String, ComplexElement.Transformable> graph,
                                     Map<String, Boolean> cache, Set<String> resolving) {
        Boolean cached = cache.get(element.key());
        if (cached != null) return cached;
        if (!resolving.add(element.key())) return true;
        boolean visible = !(element instanceof ComplexElement.Node node) || node.visible();
        if (visible && element.parentKey() != null) {
            ComplexElement.Transformable parent = graph.get(element.parentKey());
            if (parent != null) visible = isVisible(parent, graph, cache, resolving);
        }
        resolving.remove(element.key());
        cache.put(element.key(), visible);
        return visible;
    }

    private static boolean culled(Vec3d worldPos, Vec3d cameraPos, double distance) {
        return worldPos.squaredDistanceTo(cameraPos) > distance * distance;
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d local) {
        double x = matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30();
        double y = matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31();
        double z = matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32();
        return new Vec3d(x, y, z);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void fallbackShadows(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                        Vec3d cameraPos, List<ShadowFrame> frames) {
        for (ShadowFrame frame : frames) {
            if (frame.mode() != ComplexElement.ShadowMode.BLOB || frame.radius() <= 0.0 || frame.opacity() <= 0.0) continue;
            int alpha = (int)Math.round(190.0 * clamp01(frame.opacity()));
            int color = alpha << 24;
            matrices.push();
            Vec3d pos = frame.worldPosition();
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y + 0.015, pos.z - cameraPos.z);
            double radius = frame.radius();
            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) ->
                    drawDisc(vertices, entry.getPositionMatrix(), radius, 24, color));
            matrices.pop();
        }
    }

    private static void fallbackTrails(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                       Vec3d cameraPos, List<TrailFrame> frames) {
        for (TrailFrame frame : frames) {
            List<TrailPoint> points = frame.points();
            if (points.size() < 2 || frame.opacity() <= 0.0) continue;
            matrices.push();
            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) -> {
                Matrix4f matrix = entry.getPositionMatrix();
                for (int i = 1; i < points.size(); i++) {
                    TrailPoint a = points.get(i - 1);
                    TrailPoint b = points.get(i);
                    Vec3d p0 = a.position();
                    Vec3d p1 = b.position();
                    Vec3d direction = p1.subtract(p0);
                    if (direction.lengthSquared() < 1.0e-8) continue;
                    Vec3d mid = p0.add(p1).multiply(0.5);
                    Vec3d toCamera = cameraPos.subtract(mid);
                    Vec3d side = direction.crossProduct(toCamera);
                    if (side.lengthSquared() < 1.0e-8) side = direction.crossProduct(new Vec3d(0, 1, 0));
                    if (side.lengthSquared() < 1.0e-8) continue;
                    side = side.normalize().multiply(frame.width() * 0.5);
                    double fade = 1.0 - Math.max(a.age01(), b.age01());
                    int color = multiplyAlpha(frame.colorArgb(), frame.opacity() * fade);
                    coloredQuad(vertices, matrix,
                            p0.subtract(side).subtract(cameraPos), p0.add(side).subtract(cameraPos),
                            p1.add(side).subtract(cameraPos), p1.subtract(side).subtract(cameraPos), color);
                }
            });
            matrices.pop();
        }
    }

    private static void fallbackDecals(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                       Vec3d cameraPos, List<DecalFrame> frames) {
        for (DecalFrame frame : frames) {
            if (frame.opacity() <= 0.0) continue;
            Vec3d size = frame.size();
            Vec3d pos = frame.worldPosition();
            int color = multiplyAlpha(frame.tintArgb(), frame.opacity());
            matrices.push();
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y + 0.01, pos.z - cameraPos.z);
            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) -> {
                double hx = Math.abs(size.x) * 0.5;
                double hz = Math.abs(size.z == 0.0 ? size.y : size.z) * 0.5;
                coloredQuad(vertices, entry.getPositionMatrix(),
                        new Vec3d(-hx, 0, -hz), new Vec3d(-hx, 0, hz),
                        new Vec3d(hx, 0, hz), new Vec3d(hx, 0, -hz), color);
            });
            matrices.pop();
        }
    }

    private static void drawDisc(VertexConsumer vertices, Matrix4f matrix, double radius, int segments, int color) {
        Vec3d center = Vec3d.ZERO;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3d p0 = new Vec3d(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3d p1 = new Vec3d(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            coloredQuad(vertices, matrix, center, p0, p1, center, color);
        }
    }

    private static void coloredQuad(VertexConsumer vertices, Matrix4f matrix,
                                    Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        vertices.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(color);
        vertices.vertex(matrix, (float)b.x, (float)b.y, (float)b.z).color(color);
        vertices.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(color);
        vertices.vertex(matrix, (float)d.x, (float)d.y, (float)d.z).color(color);
    }

    private static int multiplyAlpha(int argb, double opacity) {
        int source = (argb >>> 24) & 255;
        int alpha = (int)Math.round(source * clamp01(opacity));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static final class TrailHistory {
        private final ArrayDeque<TimedPoint> points = new ArrayDeque<>();

        void sample(Vec3d position, double absoluteTick, ComplexElement.Trail trail) {
            TimedPoint last = points.peekLast();
            double minDistanceSq = trail.minSampleDistance() * trail.minSampleDistance();
            if (last == null || last.position.squaredDistanceTo(position) >= minDistanceSq) {
                points.addLast(new TimedPoint(position, absoluteTick));
            }
            while (points.size() > trail.maxPoints()) points.removeFirst();
            prune(absoluteTick, trail.lifetimeTicks());
        }

        List<TrailPoint> points(double absoluteTick, double lifetimeTicks) {
            prune(absoluteTick, lifetimeTicks);
            ArrayList<TrailPoint> result = new ArrayList<>(points.size());
            for (TimedPoint point : points) {
                double age = clamp01((absoluteTick - point.tick) / lifetimeTicks);
                result.add(new TrailPoint(point.position, age));
            }
            return List.copyOf(result);
        }

        private void prune(double absoluteTick, double lifetimeTicks) {
            while (!points.isEmpty() && absoluteTick - points.peekFirst().tick > lifetimeTicks) points.removeFirst();
        }
    }

    private record TimedPoint(Vec3d position, double tick) { }
}
