package dev.garfield.cinefx.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AnimationSample;
import dev.garfield.cinefx.client.api.CinematicBackend.AttachmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.BoneSample;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MorphSample;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free premium glTF 2.0 renderer layered in front of the simpler reference renderer.
 * It supports textured materials, CPU PBR-style lighting, node animations, morph targets,
 * CPU skinning and named-node sockets while staying inside the 1.21.11 ordered render queue.
 */
final class CineFxPremiumGltfRenderer {
    private static final int MAGIC_GLTF = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final int MAX_TRIANGLES = 1_500_000;
    private static final Map<Identifier, Model> CACHE = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static final Map<Identifier, NativeImageBackedTexture> DYNAMIC_TEXTURES = new HashMap<>();
    private static final Map<String, SocketPose> SOCKETS = new HashMap<>();
    private static Identifier whiteTexture;

    private CineFxPremiumGltfRenderer() { }

    static boolean preload(Identifier logicalId) {
        load(logicalId);
        return true;
    }

    static List<MeshFrame> renderMeshes(SceneRenderContext context, List<MeshFrame> frames) {
        ArrayList<MeshFrame> fallback = new ArrayList<>();
        for (MeshFrame frame : frames) {
            if (frame.opacity() <= 0.001) continue;
            Model model = load(frame.modelId());
            if (model == null) {
                fallback.add(frame);
                continue;
            }
            PoseRequest request = PoseRequest.forMesh(frame);
            try {
                renderModel(context, model, frame.worldMatrix(), frame.tintArgb(), frame.opacity(), frame.emissive(),
                        frame.castShadow(), request);
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Premium glTF mesh renderer fell back for " + frame.modelId() + ": " + exception.getMessage());
                fallback.add(frame);
            }
        }
        cleanupSockets(context.absoluteGameTick());
        return List.copyOf(fallback);
    }

    static List<ActorFrame> renderActors(SceneRenderContext context, List<ActorFrame> frames) {
        ArrayList<ActorFrame> fallback = new ArrayList<>();
        for (ActorFrame frame : frames) {
            if (frame.kind() != ComplexElement.ActorKind.CUSTOM_MODEL || frame.opacity() <= 0.001) continue;
            Model model = load(frame.resourceId());
            if (model == null) {
                fallback.add(frame);
                continue;
            }
            try {
                PoseRequest request = PoseRequest.forActor(frame);
                Pose pose = pose(model, request);
                renderModel(context, model, frame.worldMatrix(), frame.tintArgb(), frame.opacity(), frame.emissive(),
                        frame.castShadow(), request, pose);
                recordSockets(context, frame, model, pose);
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Premium glTF actor renderer fell back for " + frame.resourceId() + ": " + exception.getMessage());
                fallback.add(frame);
            }
        }
        cleanupSockets(context.absoluteGameTick());
        return List.copyOf(fallback);
    }

    static List<AttachmentFrame> refineAttachments(List<AttachmentFrame> frames) {
        ArrayList<AttachmentFrame> result = new ArrayList<>(frames.size());
        for (AttachmentFrame frame : frames) {
            if (frame.boneName() == null) {
                result.add(frame);
                continue;
            }
            SocketPose socketPose = SOCKETS.get(socketKey(frame.sceneInstanceId(), frame.parentKey()));
            Matrix4f socket = socketPose == null ? null : socketPose.namedWorld.get(frame.boneName().toLowerCase(Locale.ROOT));
            if (socketPose == null || socket == null) {
                result.add(frame);
                continue;
            }
            try {
                Matrix4f rootInverse = new Matrix4f(socketPose.rootWorld).invert();
                Matrix4f localAttachment = rootInverse.mul(new Matrix4f(frame.worldMatrix()));
                Matrix4f refined = new Matrix4f(socket).mul(localAttachment);
                result.add(new AttachmentFrame(frame.sceneInstanceId(), frame.elementKey(), frame.parentKey(), frame.boneName(),
                        frame.inheritMode(), refined, point(refined, Vec3d.ZERO), frame.payload(), frame.seed(), frame.localTick()));
            } catch (RuntimeException exception) {
                result.add(frame);
            }
        }
        return List.copyOf(result);
    }

    static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : List.copyOf(DYNAMIC_TEXTURES.keySet())) {
            try {
                client.getTextureManager().destroyTexture(id);
            } catch (RuntimeException ignored) { }
        }
        DYNAMIC_TEXTURES.clear();
        CACHE.clear();
        FAILED.clear();
        SOCKETS.clear();
        whiteTexture = null;
    }

    private static void renderModel(SceneRenderContext context, Model model, Matrix4fc rootWorld,
                                    int tint, double opacity, double emissive, boolean castShadow,
                                    PoseRequest request) {
        renderModel(context, model, rootWorld, tint, opacity, emissive, castShadow, request, pose(model, request));
    }

    private static void renderModel(SceneRenderContext context, Model model, Matrix4fc rootWorld,
                                    int tint, double opacity, double emissive, boolean castShadow,
                                    PoseRequest request, Pose pose) {
        for (int nodeIndex = 0; nodeIndex < model.nodes.size(); nodeIndex++) {
            NodeDef node = model.nodes.get(nodeIndex);
            if (node.mesh < 0 || node.mesh >= model.meshes.size()) continue;
            MeshDef mesh = model.meshes.get(node.mesh);
            double[] morphWeights = morphWeights(model, pose, nodeIndex, mesh, request);
            SkinDef skin = node.skin >= 0 && node.skin < model.skins.size() ? model.skins.get(node.skin) : null;
            for (PrimitiveDef primitive : mesh.primitives) {
                submitPrimitive(context, model, primitive, nodeIndex, rootWorld, pose, skin, morphWeights,
                        tint, opacity, emissive);
                if (castShadow && request.geometryShadow) {
                    submitProjectedGeometryShadow(context, model, primitive, nodeIndex, rootWorld, pose, skin,
                            morphWeights, request.shadowPlaneY);
                }
            }
        }
    }

    private static void submitPrimitive(SceneRenderContext context, Model model, PrimitiveDef primitive, int nodeIndex,
                                        Matrix4fc rootWorld, Pose pose, SkinDef skin, double[] morphWeights,
                                        int tint, double opacity, double emissive) {
        if (primitive.indices.length < 3 || primitive.vertices.isEmpty()) return;
        MaterialDef material = model.materials.get(primitive.materialIndex);
        Identifier texture = material.baseColorTexture == null ? whiteTexture() : material.baseColorTexture;
        RenderLayer layer = material.renderLayer(texture, emissive);
        final Vec3d camera = context.cameraPosition();
        final Matrix4f nodeWorld = pose.world[nodeIndex];
        final double frameOpacity = opacity;
        final double frameEmissive = emissive;

        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), layer, (entry, vertices) -> {
            Matrix4f queueMatrix = entry.getPositionMatrix();
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                int ia = indices[index], ib = indices[index + 1], ic = indices[index + 2];
                if (!validVertex(primitive, ia) || !validVertex(primitive, ib) || !validVertex(primitive, ic)) continue;
                RuntimeVertex a = runtimeVertex(model, primitive, ia, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                RuntimeVertex b = runtimeVertex(model, primitive, ib, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                RuntimeVertex c = runtimeVertex(model, primitive, ic, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                emitTriangle(vertices, queueMatrix, camera, a, b, c, material, tint, frameOpacity, frameEmissive);
            }
        });
        context.matrices().pop();
    }

    private static void submitProjectedGeometryShadow(SceneRenderContext context, Model model, PrimitiveDef primitive,
                                                       int nodeIndex, Matrix4fc rootWorld, Pose pose, SkinDef skin,
                                                       double[] morphWeights, double planeY) {
        if (primitive.indices.length < 3) return;
        final Vec3d camera = context.cameraPosition();
        final Matrix4f nodeWorld = pose.world[nodeIndex];
        final Vec3d light = new Vec3d(0.42, -1.0, 0.28).normalize();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            for (int index = 0; index + 2 < primitive.indices.length; index += 3) {
                int ia = primitive.indices[index], ib = primitive.indices[index + 1], ic = primitive.indices[index + 2];
                if (!validVertex(primitive, ia) || !validVertex(primitive, ib) || !validVertex(primitive, ic)) continue;
                RuntimeVertex a = runtimeVertex(model, primitive, ia, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                RuntimeVertex b = runtimeVertex(model, primitive, ib, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                RuntimeVertex c = runtimeVertex(model, primitive, ic, nodeIndex, rootWorld, nodeWorld, pose, skin, morphWeights);
                Vec3d pa = projectToPlane(a.position, planeY, light).subtract(camera);
                Vec3d pb = projectToPlane(b.position, planeY, light).subtract(camera);
                Vec3d pc = projectToPlane(c.position, planeY, light).subtract(camera);
                int color = 0x43000000;
                vertices.vertex(matrix, (float)pa.x, (float)pa.y, (float)pa.z).color(color);
                vertices.vertex(matrix, (float)pb.x, (float)pb.y, (float)pb.z).color(color);
                vertices.vertex(matrix, (float)pc.x, (float)pc.y, (float)pc.z).color(color);
                vertices.vertex(matrix, (float)pc.x, (float)pc.y, (float)pc.z).color(color);
            }
        });
        context.matrices().pop();
    }

    private static RuntimeVertex runtimeVertex(Model model, PrimitiveDef primitive, int vertexIndex, int nodeIndex,
                                               Matrix4fc rootWorld, Matrix4fc nodeWorld, Pose pose, SkinDef skin,
                                               double[] morphWeights) {
        VertexDef source = primitive.vertices.get(vertexIndex);
        Vec3d position = source.position;
        Vec3d normal = source.normal;
        if (morphWeights.length > 0 && !source.morphPositions.isEmpty()) {
            int count = Math.min(morphWeights.length, source.morphPositions.size());
            for (int i = 0; i < count; i++) {
                double weight = morphWeights[i];
                if (Math.abs(weight) < 1.0e-7) continue;
                position = position.add(source.morphPositions.get(i).multiply(weight));
                if (i < source.morphNormals.size()) normal = normal.add(source.morphNormals.get(i).multiply(weight));
            }
        }

        Vec3d worldPosition;
        Vec3d worldNormal;
        if (skin != null && source.joints.length == 4 && source.weights.length == 4) {
            Vec3d accumulatedPosition = Vec3d.ZERO;
            Vec3d accumulatedNormal = Vec3d.ZERO;
            double sum = 0.0;
            for (int i = 0; i < 4; i++) {
                double weight = source.weights[i];
                int jointSlot = source.joints[i];
                if (weight <= 0.000001 || jointSlot < 0 || jointSlot >= skin.joints.length) continue;
                int jointNode = skin.joints[jointSlot];
                if (jointNode < 0 || jointNode >= pose.world.length) continue;
                Matrix4f jointMatrix = new Matrix4f(pose.world[jointNode]).mul(skin.inverseBind[jointSlot]);
                accumulatedPosition = accumulatedPosition.add(point(jointMatrix, position).multiply(weight));
                accumulatedNormal = accumulatedNormal.add(direction(jointMatrix, normal).multiply(weight));
                sum += weight;
            }
            if (sum > 0.000001) {
                worldPosition = point(rootWorld, accumulatedPosition.multiply(1.0 / sum));
                worldNormal = direction(rootWorld, accumulatedNormal.multiply(1.0 / sum)).normalize();
            } else {
                Matrix4f combined = new Matrix4f(rootWorld).mul(nodeWorld);
                worldPosition = point(combined, position);
                worldNormal = direction(combined, normal).normalize();
            }
        } else {
            Matrix4f combined = new Matrix4f(rootWorld).mul(nodeWorld);
            worldPosition = point(combined, position);
            worldNormal = direction(combined, normal).normalize();
        }
        return new RuntimeVertex(worldPosition, worldNormal, source.u, source.v,
                source.r, source.g, source.b, source.a);
    }

    private static void emitTriangle(VertexConsumer vertices, Matrix4f matrix, Vec3d camera,
                                     RuntimeVertex a, RuntimeVertex b, RuntimeVertex c,
                                     MaterialDef material, int tint, double opacity, double emissive) {
        emitVertex(vertices, matrix, camera, a, material, tint, opacity, emissive);
        emitVertex(vertices, matrix, camera, b, material, tint, opacity, emissive);
        emitVertex(vertices, matrix, camera, c, material, tint, opacity, emissive);
        emitVertex(vertices, matrix, camera, c, material, tint, opacity, emissive);
    }

    private static void emitVertex(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, RuntimeVertex vertex,
                                   MaterialDef material, int tint, double opacity, double emissive) {
        Vec3d p = vertex.position.subtract(camera);
        float[] color = shade(vertex, material, tint, opacity, emissive, camera);
        vertices.vertex(matrix, (float)p.x, (float)p.y, (float)p.z)
                .color(color[0], color[1], color[2], color[3])
                .texture(vertex.u, vertex.v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal((float)vertex.normal.x, (float)vertex.normal.y, (float)vertex.normal.z);
    }

    private static float[] shade(RuntimeVertex vertex, MaterialDef material, int tint, double opacity,
                                 double frameEmissive, Vec3d camera) {
        double tr = ((tint >>> 16) & 255) / 255.0;
        double tg = ((tint >>> 8) & 255) / 255.0;
        double tb = (tint & 255) / 255.0;
        double ta = ((tint >>> 24) & 255) / 255.0;
        double mr = ((material.baseColorArgb >>> 16) & 255) / 255.0;
        double mg = ((material.baseColorArgb >>> 8) & 255) / 255.0;
        double mb = (material.baseColorArgb & 255) / 255.0;
        double ma = ((material.baseColorArgb >>> 24) & 255) / 255.0;

        Vec3d n = vertex.normal.lengthSquared() < 1.0e-8 ? new Vec3d(0, 1, 0) : vertex.normal.normalize();
        Vec3d light = new Vec3d(0.38, 0.86, 0.34).normalize();
        Vec3d view = camera.subtract(vertex.position);
        if (view.lengthSquared() < 1.0e-8) view = new Vec3d(0, 0, 1);
        else view = view.normalize();
        Vec3d half = light.add(view);
        if (half.lengthSquared() < 1.0e-8) half = light;
        else half = half.normalize();

        double ndl = Math.max(0.0, n.dotProduct(light));
        double ndh = Math.max(0.0, n.dotProduct(half));
        double metallic = clamp01(material.metallic);
        double roughness = Math.max(0.04, clamp01(material.roughness));
        double ambient = 0.24 + 0.18 * (1.0 - roughness);
        double diffuse = ndl * (0.78 - 0.36 * metallic);
        double exponent = 4.0 + 124.0 * Math.pow(1.0 - roughness, 2.0);
        double specular = Math.pow(ndh, exponent) * (0.08 + 0.82 * metallic);
        double emission = clamp01(material.emissive + frameEmissive);
        double lighting = Math.min(1.8, ambient + diffuse + specular + emission);

        float r = (float)clamp01(mr * tr * vertex.r * lighting);
        float g = (float)clamp01(mg * tg * vertex.g * lighting);
        float b = (float)clamp01(mb * tb * vertex.b * lighting);
        float a = (float)clamp01(ma * ta * vertex.a * opacity);
        return new float[]{r, g, b, a};
    }

    private static Pose pose(Model model, PoseRequest request) {
        int count = model.nodes.size();
        Vec3d[] translations = new Vec3d[count];
        Quaternionf[] rotations = new Quaternionf[count];
        Vec3d[] scales = new Vec3d[count];
        boolean[] changed = new boolean[count];
        double[][] animatedWeights = new double[count][];
        for (int i = 0; i < count; i++) {
            NodeDef node = model.nodes.get(i);
            translations[i] = node.translation;
            rotations[i] = new Quaternionf(node.rotation);
            scales[i] = node.scale;
        }

        for (AnimationSample layer : request.animations) {
            if (layer.weight() <= 0.0001) continue;
            AnimationDef animation = model.animation(layer.clipId());
            if (animation == null) continue;
            double seconds = layer.timeTicks() / 20.0;
            double duration = animation.duration;
            if (layer.looping() && duration > 0.000001) seconds = positiveModulo(seconds, duration);
            else seconds = Math.max(0.0, Math.min(duration, seconds));
            double weight = clamp01(layer.weight());
            for (AnimationChannel channel : animation.channels) {
                if (channel.node < 0 || channel.node >= count) continue;
                switch (channel.path) {
                    case TRANSLATION -> {
                        Vec3d sample = channel.sampleVec3(seconds);
                        translations[channel.node] = blendVec(translations[channel.node], sample,
                                model.nodes.get(channel.node).translation, weight, layer.blendMode());
                        changed[channel.node] = true;
                    }
                    case SCALE -> {
                        Vec3d sample = channel.sampleVec3(seconds);
                        scales[channel.node] = blendScale(scales[channel.node], sample,
                                model.nodes.get(channel.node).scale, weight, layer.blendMode());
                        changed[channel.node] = true;
                    }
                    case ROTATION -> {
                        Quaternionf sample = channel.sampleQuat(seconds);
                        rotations[channel.node] = blendQuat(rotations[channel.node], sample,
                                model.nodes.get(channel.node).rotation, weight, layer.blendMode());
                        changed[channel.node] = true;
                    }
                    case WEIGHTS -> {
                        double[] sample = channel.sampleWeights(seconds);
                        double[] current = animatedWeights[channel.node];
                        if (current == null || current.length != sample.length) current = new double[sample.length];
                        for (int i = 0; i < sample.length; i++) {
                            current[i] = current[i] * (1.0 - weight) + sample[i] * weight;
                        }
                        animatedWeights[channel.node] = current;
                    }
                }
            }
        }

        Map<String, BoneSample> bones = new HashMap<>();
        for (BoneSample bone : request.bones) {
            if (bone.weight() > 0.0001) bones.put(bone.bone().toLowerCase(Locale.ROOT), bone);
        }

        Matrix4f[] local = new Matrix4f[count];
        for (int i = 0; i < count; i++) {
            NodeDef node = model.nodes.get(i);
            Matrix4f matrix;
            if (node.matrix != null && !changed[i]) matrix = new Matrix4f(node.matrix);
            else matrix = trs(translations[i], rotations[i], scales[i]);
            BoneSample bone = node.name == null ? null : bones.get(node.name.toLowerCase(Locale.ROOT));
            if (bone != null) matrix.mul(weightedBoneMatrix(bone));
            local[i] = matrix;
        }

        Matrix4f[] world = new Matrix4f[count];
        boolean[] resolving = new boolean[count];
        for (int i = 0; i < count; i++) resolveWorld(i, model, local, world, resolving);
        return new Pose(local, world, animatedWeights);
    }

    private static double[] morphWeights(Model model, Pose pose, int nodeIndex, MeshDef mesh, PoseRequest request) {
        double[] weights;
        if (pose.animatedWeights[nodeIndex] != null) weights = pose.animatedWeights[nodeIndex].clone();
        else if (model.nodes.get(nodeIndex).weights.length > 0) weights = model.nodes.get(nodeIndex).weights.clone();
        else weights = mesh.weights.clone();
        if (weights.length == 0 && !mesh.targetNames.isEmpty()) weights = new double[mesh.targetNames.size()];
        if (!request.morphs.isEmpty() && !mesh.targetNames.isEmpty()) {
            Map<String, Double> overrides = new HashMap<>();
            for (MorphSample morph : request.morphs) overrides.put(morph.name().toLowerCase(Locale.ROOT), morph.weight());
            for (int i = 0; i < mesh.targetNames.size() && i < weights.length; i++) {
                Double value = overrides.get(mesh.targetNames.get(i).toLowerCase(Locale.ROOT));
                if (value != null) weights[i] = value;
            }
        }
        return weights;
    }

    private static Matrix4f resolveWorld(int index, Model model, Matrix4f[] local, Matrix4f[] world, boolean[] resolving) {
        if (world[index] != null) return world[index];
        if (resolving[index]) return world[index] = new Matrix4f(local[index]);
        resolving[index] = true;
        NodeDef node = model.nodes.get(index);
        world[index] = node.parent < 0
                ? new Matrix4f(local[index])
                : new Matrix4f(resolveWorld(node.parent, model, local, world, resolving)).mul(local[index]);
        resolving[index] = false;
        return world[index];
    }

    private static Matrix4f weightedBoneMatrix(BoneSample bone) {
        double weight = clamp01(bone.weight());
        var t = bone.transform();
        Vec3d translation = t.translation().multiply(weight);
        Vec3d rotation = t.rotationDegrees().multiply(weight);
        Vec3d scale = new Vec3d(1.0 + (t.scale().x - 1.0) * weight,
                1.0 + (t.scale().y - 1.0) * weight,
                1.0 + (t.scale().z - 1.0) * weight);
        Vec3d pivot = t.pivot();
        Matrix4f matrix = new Matrix4f().translation((float)translation.x, (float)translation.y, (float)translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)pivot.x, (float)pivot.y, (float)pivot.z);
        matrix.rotateXYZ((float)Math.toRadians(rotation.x), (float)Math.toRadians(rotation.y), (float)Math.toRadians(rotation.z));
        matrix.scale((float)scale.x, (float)scale.y, (float)scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);
        return matrix;
    }

    private static Vec3d blendVec(Vec3d current, Vec3d sampled, Vec3d base, double weight, ComplexElement.BlendMode mode) {
        return switch (mode) {
            case OVERRIDE -> lerp(current, sampled, weight);
            case ADDITIVE -> current.add(sampled.subtract(base).multiply(weight));
            case MULTIPLY -> new Vec3d(current.x * lerp(1.0, sampled.x, weight),
                    current.y * lerp(1.0, sampled.y, weight), current.z * lerp(1.0, sampled.z, weight));
        };
    }

    private static Vec3d blendScale(Vec3d current, Vec3d sampled, Vec3d base, double weight, ComplexElement.BlendMode mode) {
        return switch (mode) {
            case OVERRIDE -> lerp(current, sampled, weight);
            case ADDITIVE -> current.add(sampled.subtract(base).multiply(weight));
            case MULTIPLY -> new Vec3d(current.x * lerp(1.0, sampled.x, weight),
                    current.y * lerp(1.0, sampled.y, weight), current.z * lerp(1.0, sampled.z, weight));
        };
    }

    private static Quaternionf blendQuat(Quaternionf current, Quaternionf sampled, Quaternionf base,
                                         double weight, ComplexElement.BlendMode mode) {
        float w = (float)weight;
        if (mode == ComplexElement.BlendMode.OVERRIDE) return new Quaternionf(current).slerp(sampled, w).normalize();
        Quaternionf delta = new Quaternionf(base).conjugate().mul(sampled).normalize();
        Quaternionf weighted = new Quaternionf().identity().slerp(delta, w).normalize();
        return new Quaternionf(current).mul(weighted).normalize();
    }

    private static Matrix4f trs(Vec3d translation, Quaternionf rotation, Vec3d scale) {
        return new Matrix4f().translation((float)translation.x, (float)translation.y, (float)translation.z)
                .rotate(rotation).scale((float)scale.x, (float)scale.y, (float)scale.z);
    }

    private static void recordSockets(SceneRenderContext context, ActorFrame frame, Model model, Pose pose) {
        LinkedHashMap<String, Matrix4f> named = new LinkedHashMap<>();
        for (int i = 0; i < model.nodes.size(); i++) {
            String name = model.nodes.get(i).name;
            if (name == null || name.isBlank()) continue;
            named.put(name.toLowerCase(Locale.ROOT), new Matrix4f(frame.worldMatrix()).mul(pose.world[i]));
        }
        SOCKETS.put(socketKey(frame.sceneInstanceId(), frame.elementKey()),
                new SocketPose(new Matrix4f(frame.worldMatrix()), Map.copyOf(named), context.absoluteGameTick()));
    }

    private static void cleanupSockets(double now) {
        SOCKETS.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > 8.0);
    }

    private static synchronized Model load(Identifier logicalId) {
        if (logicalId == null) return null;
        Model cached = CACHE.get(logicalId);
        if (cached != null) return cached;
        if (FAILED.contains(logicalId)) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager manager = client.getResourceManager();
        for (Identifier resourceId : candidates(logicalId)) {
            Resource resource = manager.getResource(resourceId).orElse(null);
            if (resource == null) continue;
            try (InputStream input = resource.getInputStream()) {
                byte[] bytes = readAll(input);
                ParsedDocument document = resourceId.getPath().toLowerCase(Locale.ROOT).endsWith(".glb")
                        ? parseGlb(bytes)
                        : new ParsedDocument(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(), null);
                Model model = parseModel(manager, resourceId, document.json, document.binaryChunk);
                if (model != null && model.triangleCount > 0) {
                    CACHE.put(logicalId, model);
                    return model;
                }
            } catch (RuntimeException | IOException exception) {
                System.err.println("[CineFX] Premium glTF load failed for " + resourceId + ": " + exception.getMessage());
            }
        }
        FAILED.add(logicalId);
        return null;
    }

    private static Model parseModel(ResourceManager manager, Identifier source, JsonObject json, byte[] glbBinary) throws IOException {
        List<byte[]> buffers = readBuffers(manager, source, json, glbBinary);
        JsonArray views = array(json, "bufferViews");
        JsonArray accessors = array(json, "accessors");
        JsonArray nodeJson = array(json, "nodes");
        JsonArray meshJson = array(json, "meshes");
        if (views == null || accessors == null || nodeJson == null || meshJson == null) return null;

        List<Identifier> images = readImages(manager, source, json, buffers, views);
        List<Integer> textures = readTextures(json);
        List<MaterialDef> materials = readMaterials(json, images, textures);
        if (materials.isEmpty()) materials = List.of(MaterialDef.defaultMaterial());

        ArrayList<MeshDef> meshes = new ArrayList<>(meshJson.size());
        int triangleCount = 0;
        for (int meshIndex = 0; meshIndex < meshJson.size(); meshIndex++) {
            JsonObject mesh = meshJson.get(meshIndex).getAsJsonObject();
            List<String> targetNames = targetNames(mesh);
            double[] defaultWeights = doubles(array(mesh, "weights"));
            ArrayList<PrimitiveDef> primitives = new ArrayList<>();
            JsonArray primitiveArray = array(mesh, "primitives");
            if (primitiveArray != null) for (JsonElement element : primitiveArray) {
                JsonObject primitive = element.getAsJsonObject();
                if (hasCompressionExtension(primitive)) continue;
                int mode = integer(primitive, "mode", 4);
                JsonObject attributes = primitive.getAsJsonObject("attributes");
                if (attributes == null || !attributes.has("POSITION")) continue;
                float[] positions = readFloatAccessor(attributes.get("POSITION").getAsInt(), accessors, views, buffers);
                int vertexCount = positions.length / 3;
                if (vertexCount <= 0) continue;
                int[] indices = primitive.has("indices")
                        ? readIntAccessor(primitive.get("indices").getAsInt(), accessors, views, buffers)
                        : sequential(vertexCount);
                indices = triangulate(indices, mode);
                triangleCount += indices.length / 3;
                if (triangleCount > MAX_TRIANGLES) throw new IllegalArgumentException("model exceeds " + MAX_TRIANGLES + " triangles");
                float[] normals = attributes.has("NORMAL")
                        ? readFloatAccessor(attributes.get("NORMAL").getAsInt(), accessors, views, buffers)
                        : generatedNormals(positions, indices);
                float[] uvs = attributes.has("TEXCOORD_0")
                        ? readFloatAccessor(attributes.get("TEXCOORD_0").getAsInt(), accessors, views, buffers)
                        : new float[vertexCount * 2];
                float[] colors = attributes.has("COLOR_0")
                        ? expandColors(readFloatAccessor(attributes.get("COLOR_0").getAsInt(), accessors, views, buffers),
                        componentCount(accessors.get(attributes.get("COLOR_0").getAsInt()).getAsJsonObject().get("type").getAsString()), vertexCount)
                        : whiteColors(vertexCount);
                int[] joints = attributes.has("JOINTS_0")
                        ? readIntAccessor(attributes.get("JOINTS_0").getAsInt(), accessors, views, buffers)
                        : new int[0];
                float[] weights = attributes.has("WEIGHTS_0")
                        ? readFloatAccessor(attributes.get("WEIGHTS_0").getAsInt(), accessors, views, buffers)
                        : new float[0];
                normalizeWeights(weights);

                JsonArray targets = array(primitive, "targets");
                List<float[]> morphPositions = new ArrayList<>();
                List<float[]> morphNormals = new ArrayList<>();
                if (targets != null) for (JsonElement targetElement : targets) {
                    JsonObject target = targetElement.getAsJsonObject();
                    morphPositions.add(target.has("POSITION")
                            ? readFloatAccessor(target.get("POSITION").getAsInt(), accessors, views, buffers)
                            : new float[positions.length]);
                    morphNormals.add(target.has("NORMAL")
                            ? readFloatAccessor(target.get("NORMAL").getAsInt(), accessors, views, buffers)
                            : new float[normals.length]);
                }

                ArrayList<VertexDef> vertices = new ArrayList<>(vertexCount);
                for (int i = 0; i < vertexCount; i++) {
                    ArrayList<Vec3d> mp = new ArrayList<>(morphPositions.size());
                    ArrayList<Vec3d> mn = new ArrayList<>(morphNormals.size());
                    for (int t = 0; t < morphPositions.size(); t++) {
                        mp.add(vec3(morphPositions.get(t), i));
                        mn.add(vec3(morphNormals.get(t), i));
                    }
                    int[] vertexJoints = new int[0];
                    double[] vertexWeights = new double[0];
                    if (joints.length >= i * 4 + 4 && weights.length >= i * 4 + 4) {
                        vertexJoints = new int[]{joints[i * 4], joints[i * 4 + 1], joints[i * 4 + 2], joints[i * 4 + 3]};
                        vertexWeights = new double[]{weights[i * 4], weights[i * 4 + 1], weights[i * 4 + 2], weights[i * 4 + 3]};
                    }
                    vertices.add(new VertexDef(vec3(positions, i), safeNormal(vec3(normals, i)),
                            component(uvs, i * 2), component(uvs, i * 2 + 1),
                            component(colors, i * 4, 1), component(colors, i * 4 + 1, 1),
                            component(colors, i * 4 + 2, 1), component(colors, i * 4 + 3, 1),
                            vertexJoints, vertexWeights, List.copyOf(mp), List.copyOf(mn)));
                }
                int material = integer(primitive, "material", 0);
                if (material < 0 || material >= materials.size()) material = 0;
                primitives.add(new PrimitiveDef(List.copyOf(vertices), indices, material));
            }
            meshes.add(new MeshDef(List.copyOf(primitives), defaultWeights, targetNames));
        }

        int[] parents = new int[nodeJson.size()];
        java.util.Arrays.fill(parents, -1);
        for (int i = 0; i < nodeJson.size(); i++) {
            JsonArray children = array(nodeJson.get(i).getAsJsonObject(), "children");
            if (children != null) for (JsonElement child : children) {
                int c = child.getAsInt();
                if (c >= 0 && c < parents.length) parents[c] = i;
            }
        }

        ArrayList<NodeDef> nodes = new ArrayList<>(nodeJson.size());
        for (int i = 0; i < nodeJson.size(); i++) {
            JsonObject node = nodeJson.get(i).getAsJsonObject();
            Matrix4f matrix = matrix(node.getAsJsonArray("matrix"));
            Vec3d translation = vec3(node.getAsJsonArray("translation"), Vec3d.ZERO);
            Quaternionf rotation = quat(node.getAsJsonArray("rotation"));
            Vec3d scale = vec3(node.getAsJsonArray("scale"), new Vec3d(1, 1, 1));
            int[] children = ints(array(node, "children"));
            nodes.add(new NodeDef(node.has("name") ? node.get("name").getAsString() : null,
                    integer(node, "mesh", -1), integer(node, "skin", -1), parents[i], children,
                    translation, rotation, scale, matrix, doubles(array(node, "weights"))));
        }

        List<SkinDef> skins = readSkins(json, accessors, views, buffers);
        List<AnimationDef> animations = readAnimations(json, accessors, views, buffers, meshes, nodes);
        int[] roots = sceneRoots(json, nodes);
        return new Model(List.copyOf(nodes), List.copyOf(meshes), materials, skins, animations, roots, triangleCount);
    }

    private static List<SkinDef> readSkins(JsonObject json, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        JsonArray array = array(json, "skins");
        if (array == null) return List.of();
        ArrayList<SkinDef> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject skin = element.getAsJsonObject();
            int[] joints = ints(array(skin, "joints"));
            Matrix4f[] inverse = new Matrix4f[joints.length];
            for (int i = 0; i < inverse.length; i++) inverse[i] = new Matrix4f();
            if (skin.has("inverseBindMatrices")) {
                float[] values = readFloatAccessor(skin.get("inverseBindMatrices").getAsInt(), accessors, views, buffers);
                for (int i = 0; i < inverse.length && values.length >= (i + 1) * 16; i++) {
                    float[] matrix = new float[16];
                    System.arraycopy(values, i * 16, matrix, 0, 16);
                    inverse[i] = new Matrix4f().set(matrix);
                }
            }
            result.add(new SkinDef(joints, inverse));
        }
        return List.copyOf(result);
    }

    private static List<AnimationDef> readAnimations(JsonObject json, JsonArray accessors, JsonArray views,
                                                     List<byte[]> buffers, List<MeshDef> meshes, List<NodeDef> nodes) {
        JsonArray animations = array(json, "animations");
        if (animations == null) return List.of();
        ArrayList<AnimationDef> result = new ArrayList<>();
        for (int animationIndex = 0; animationIndex < animations.size(); animationIndex++) {
            JsonObject animation = animations.get(animationIndex).getAsJsonObject();
            JsonArray samplers = array(animation, "samplers");
            JsonArray channels = array(animation, "channels");
            if (samplers == null || channels == null) continue;
            ArrayList<AnimationChannel> parsed = new ArrayList<>();
            double duration = 0.0;
            for (JsonElement channelElement : channels) {
                JsonObject channel = channelElement.getAsJsonObject();
                int samplerIndex = integer(channel, "sampler", -1);
                if (samplerIndex < 0 || samplerIndex >= samplers.size()) continue;
                JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
                int input = integer(sampler, "input", -1);
                int output = integer(sampler, "output", -1);
                if (input < 0 || output < 0) continue;
                float[] times = readFloatAccessor(input, accessors, views, buffers);
                float[] values = readFloatAccessor(output, accessors, views, buffers);
                if (times.length == 0 || values.length == 0) continue;
                duration = Math.max(duration, times[times.length - 1]);
                JsonObject target = channel.getAsJsonObject("target");
                if (target == null || !target.has("node") || !target.has("path")) continue;
                int node = target.get("node").getAsInt();
                AnimationPath path;
                try { path = AnimationPath.valueOf(target.get("path").getAsString().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException exception) { continue; }
                Interpolation interpolation;
                try { interpolation = Interpolation.valueOf(string(sampler, "interpolation", "LINEAR").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException exception) { interpolation = Interpolation.LINEAR; }
                int components = switch (path) {
                    case TRANSLATION, SCALE -> 3;
                    case ROTATION -> 4;
                    case WEIGHTS -> inferWeightComponents(node, nodes, meshes, times.length, values.length, interpolation);
                };
                if (components <= 0) continue;
                parsed.add(new AnimationChannel(node, path, interpolation, times, values, components));
            }
            String name = animation.has("name") ? animation.get("name").getAsString() : "animation_" + animationIndex;
            result.add(new AnimationDef(name, List.copyOf(parsed), duration));
        }
        return List.copyOf(result);
    }

    private static int inferWeightComponents(int node, List<NodeDef> nodes, List<MeshDef> meshes,
                                             int keyCount, int valueCount, Interpolation interpolation) {
        if (node >= 0 && node < nodes.size()) {
            NodeDef def = nodes.get(node);
            if (def.weights.length > 0) return def.weights.length;
            if (def.mesh >= 0 && def.mesh < meshes.size()) {
                int count = meshes.get(def.mesh).targetNames.size();
                if (count == 0) count = meshes.get(def.mesh).weights.length;
                if (count > 0) return count;
            }
        }
        int multiplier = interpolation == Interpolation.CUBICSPLINE ? 3 : 1;
        return keyCount <= 0 ? 0 : valueCount / (keyCount * multiplier);
    }

    private static List<Identifier> readImages(ResourceManager manager, Identifier source, JsonObject json,
                                               List<byte[]> buffers, JsonArray views) throws IOException {
        JsonArray images = array(json, "images");
        if (images == null) return List.of();
        ArrayList<Identifier> result = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            JsonObject image = images.get(i).getAsJsonObject();
            Identifier id = null;
            if (image.has("uri")) {
                String uri = image.get("uri").getAsString();
                if (uri.startsWith("data:")) {
                    id = registerDynamicTexture(source, i, decodeDataUri(uri));
                } else {
                    Identifier external = sibling(source, URLDecoder.decode(uri, StandardCharsets.UTF_8));
                    if (manager.getResource(external).isPresent()) id = external;
                }
            } else if (image.has("bufferView")) {
                int viewIndex = image.get("bufferView").getAsInt();
                byte[] encoded = copyView(viewIndex, buffers, views);
                id = registerDynamicTexture(source, i, encoded);
            }
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static List<Integer> readTextures(JsonObject json) {
        JsonArray textures = array(json, "textures");
        if (textures == null) return List.of();
        ArrayList<Integer> result = new ArrayList<>(textures.size());
        for (JsonElement element : textures) result.add(integer(element.getAsJsonObject(), "source", -1));
        return List.copyOf(result);
    }

    private static List<MaterialDef> readMaterials(JsonObject json, List<Identifier> images, List<Integer> textures) {
        JsonArray materials = array(json, "materials");
        if (materials == null || materials.isEmpty()) return List.of(MaterialDef.defaultMaterial());
        ArrayList<MaterialDef> result = new ArrayList<>(materials.size());
        for (JsonElement element : materials) {
            JsonObject material = element.getAsJsonObject();
            JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
            JsonArray factor = pbr == null ? null : array(pbr, "baseColorFactor");
            int color = factor == null ? 0xFFFFFFFF : factorColor(factor);
            double metallic = pbr == null ? 1.0 : number(pbr, "metallicFactor", 1.0);
            double roughness = pbr == null ? 1.0 : number(pbr, "roughnessFactor", 1.0);
            Identifier texture = null;
            if (pbr != null && pbr.has("baseColorTexture")) {
                int textureIndex = integer(pbr.getAsJsonObject("baseColorTexture"), "index", -1);
                if (textureIndex >= 0 && textureIndex < textures.size()) {
                    int imageIndex = textures.get(textureIndex);
                    if (imageIndex >= 0 && imageIndex < images.size()) texture = images.get(imageIndex);
                }
            }
            double emissive = 0.0;
            JsonArray emissiveFactor = array(material, "emissiveFactor");
            if (emissiveFactor != null && emissiveFactor.size() >= 3) {
                emissive = Math.max(emissiveFactor.get(0).getAsDouble(),
                        Math.max(emissiveFactor.get(1).getAsDouble(), emissiveFactor.get(2).getAsDouble()));
            }
            JsonObject extensions = material.getAsJsonObject("extensions");
            if (extensions != null && extensions.has("KHR_materials_emissive_strength")) {
                emissive *= number(extensions.getAsJsonObject("KHR_materials_emissive_strength"), "emissiveStrength", 1.0);
            }
            AlphaMode alphaMode;
            try { alphaMode = AlphaMode.valueOf(string(material, "alphaMode", "OPAQUE").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { alphaMode = AlphaMode.OPAQUE; }
            result.add(new MaterialDef(color, texture, metallic, roughness, emissive, alphaMode,
                    number(material, "alphaCutoff", 0.5), material.has("doubleSided") && material.get("doubleSided").getAsBoolean()));
        }
        return List.copyOf(result);
    }

    private static Identifier registerDynamicTexture(Identifier source, int imageIndex, byte[] bytes) throws IOException {
        Identifier id = Identifier.of("cinefx", "gltf_dynamic/" + Integer.toUnsignedString((source.toString() + ':' + imageIndex).hashCode(), 36));
        if (DYNAMIC_TEXTURES.containsKey(id)) return id;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            NativeImage image = NativeImage.read(input);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture.upload();
            DYNAMIC_TEXTURES.put(id, texture);
            return id;
        }
    }

    private static Identifier whiteTexture() {
        if (whiteTexture != null) return whiteTexture;
        Identifier id = Identifier.of("cinefx", "gltf_dynamic/white");
        NativeImage image = new NativeImage(1, 1, false);
        image.setColorArgb(0, 0, 0xFFFFFFFF);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        DYNAMIC_TEXTURES.put(id, texture);
        whiteTexture = id;
        return id;
    }

    private static List<byte[]> readBuffers(ResourceManager manager, Identifier source, JsonObject json, byte[] glbBinary) throws IOException {
        JsonArray definitions = array(json, "buffers");
        if (definitions == null) return glbBinary == null ? List.of() : List.of(glbBinary);
        ArrayList<byte[]> result = new ArrayList<>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            JsonObject definition = definitions.get(i).getAsJsonObject();
            if (definition.has("uri")) {
                String uri = definition.get("uri").getAsString();
                if (uri.startsWith("data:")) result.add(decodeDataUri(uri));
                else {
                    Identifier external = sibling(source, URLDecoder.decode(uri, StandardCharsets.UTF_8));
                    Resource resource = manager.getResource(external).orElseThrow(() -> new IllegalArgumentException("missing buffer " + external));
                    try (InputStream input = resource.getInputStream()) { result.add(readAll(input)); }
                }
            } else if (i == 0 && glbBinary != null) result.add(glbBinary);
            else result.add(new byte[0]);
        }
        return List.copyOf(result);
    }

    private static float[] readFloatAccessor(int accessorIndex, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        if (accessorIndex < 0 || accessorIndex >= accessors.size()) return new float[0];
        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        int components = componentCount(string(accessor, "type", "SCALAR"));
        int count = integer(accessor, "count", 0);
        float[] result = new float[Math.max(0, count * components)];
        if (accessor.has("bufferView")) {
            Access access = access(accessor, views, buffers, components);
            if (access != null) for (int i = 0; i < count; i++) for (int c = 0; c < components; c++) {
                result[i * components + c] = (float)readComponent(access.buffer, access.start + i * access.stride + c * access.componentSize,
                        access.componentType, access.normalized);
            }
        }
        applySparseFloats(result, accessor, accessors, views, buffers, components);
        return result;
    }

    private static int[] readIntAccessor(int accessorIndex, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        if (accessorIndex < 0 || accessorIndex >= accessors.size()) return new int[0];
        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        int components = componentCount(string(accessor, "type", "SCALAR"));
        int count = integer(accessor, "count", 0);
        int[] result = new int[Math.max(0, count * components)];
        if (accessor.has("bufferView")) {
            Access access = access(accessor, views, buffers, components);
            if (access != null) for (int i = 0; i < count; i++) for (int c = 0; c < components; c++) {
                result[i * components + c] = (int)Math.round(readComponent(access.buffer,
                        access.start + i * access.stride + c * access.componentSize, access.componentType, false));
            }
        }
        return result;
    }

    private static void applySparseFloats(float[] result, JsonObject accessor, JsonArray accessors,
                                          JsonArray views, List<byte[]> buffers, int components) {
        JsonObject sparse = accessor.getAsJsonObject("sparse");
        if (sparse == null) return;
        int count = integer(sparse, "count", 0);
        JsonObject indices = sparse.getAsJsonObject("indices");
        JsonObject values = sparse.getAsJsonObject("values");
        if (count <= 0 || indices == null || values == null) return;
        int indexView = integer(indices, "bufferView", -1);
        int valueView = integer(values, "bufferView", -1);
        if (indexView < 0 || valueView < 0) return;
        int indexType = integer(indices, "componentType", 5123);
        int indexOffset = integer(indices, "byteOffset", 0);
        int valueOffset = integer(values, "byteOffset", 0);
        View iv = view(indexView, views, buffers);
        View vv = view(valueView, views, buffers);
        if (iv == null || vv == null) return;
        int valueComponentType = integer(accessor, "componentType", 5126);
        int valueComponentSize = componentSize(valueComponentType);
        for (int i = 0; i < count; i++) {
            int sparseIndex = readUnsignedIndex(iv.buffer, iv.start + indexOffset + i * componentSize(indexType), indexType);
            if (sparseIndex < 0 || sparseIndex * components + components > result.length) continue;
            for (int c = 0; c < components; c++) {
                result[sparseIndex * components + c] = (float)readComponent(vv.buffer,
                        vv.start + valueOffset + (i * components + c) * valueComponentSize,
                        valueComponentType, accessor.has("normalized") && accessor.get("normalized").getAsBoolean());
            }
        }
    }

    private static Access access(JsonObject accessor, JsonArray views, List<byte[]> buffers, int components) {
        int viewIndex = integer(accessor, "bufferView", -1);
        View view = view(viewIndex, views, buffers);
        if (view == null) return null;
        int componentType = integer(accessor, "componentType", 5126);
        int componentSize = componentSize(componentType);
        int stride = view.stride > 0 ? view.stride : componentSize * components;
        int start = view.start + integer(accessor, "byteOffset", 0);
        int count = integer(accessor, "count", 0);
        long end = (long)start + Math.max(0, count - 1L) * stride + (long)componentSize * components;
        if (start < 0 || end > view.buffer.length) return null;
        return new Access(view.buffer, start, stride, componentType, componentSize,
                accessor.has("normalized") && accessor.get("normalized").getAsBoolean());
    }

    private static View view(int index, JsonArray views, List<byte[]> buffers) {
        if (index < 0 || views == null || index >= views.size()) return null;
        JsonObject view = views.get(index).getAsJsonObject();
        int bufferIndex = integer(view, "buffer", -1);
        if (bufferIndex < 0 || bufferIndex >= buffers.size()) return null;
        byte[] buffer = buffers.get(bufferIndex);
        int start = integer(view, "byteOffset", 0);
        int length = integer(view, "byteLength", 0);
        if (start < 0 || length < 0 || (long)start + length > buffer.length) return null;
        return new View(buffer, start, length, integer(view, "byteStride", 0));
    }

    private static byte[] copyView(int index, List<byte[]> buffers, JsonArray views) {
        View view = view(index, views, buffers);
        if (view == null) return new byte[0];
        byte[] result = new byte[view.length];
        System.arraycopy(view.buffer, view.start, result, 0, view.length);
        return result;
    }

    private static double readComponent(byte[] bytes, int offset, int type, boolean normalized) {
        if (offset < 0 || offset >= bytes.length) return 0.0;
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case 5120 -> normalized ? Math.max(-1.0, bytes[offset] / 127.0) : bytes[offset];
            case 5121 -> normalized ? (bytes[offset] & 255) / 255.0 : bytes[offset] & 255;
            case 5122 -> normalized ? Math.max(-1.0, data.getShort(offset) / 32767.0) : data.getShort(offset);
            case 5123 -> normalized ? (data.getShort(offset) & 65535) / 65535.0 : data.getShort(offset) & 65535;
            case 5125 -> data.getInt(offset) & 0xFFFFFFFFL;
            case 5126 -> data.getFloat(offset);
            default -> 0.0;
        };
    }

    private static int readUnsignedIndex(byte[] bytes, int offset, int type) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case 5121 -> bytes[offset] & 255;
            case 5123 -> data.getShort(offset) & 65535;
            case 5125 -> (int)(data.getInt(offset) & 0xFFFFFFFFL);
            default -> -1;
        };
    }

    private static float[] generatedNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int ia = indices[i], ib = indices[i + 1], ic = indices[i + 2];
            if (ia * 3 + 2 >= positions.length || ib * 3 + 2 >= positions.length || ic * 3 + 2 >= positions.length) continue;
            Vec3d a = vec3(positions, ia), b = vec3(positions, ib), c = vec3(positions, ic);
            Vec3d n = b.subtract(a).crossProduct(c.subtract(a));
            if (n.lengthSquared() < 1.0e-12) continue;
            addNormal(normals, ia, n); addNormal(normals, ib, n); addNormal(normals, ic, n);
        }
        for (int i = 0; i < normals.length / 3; i++) {
            Vec3d n = safeNormal(vec3(normals, i));
            normals[i * 3] = (float)n.x; normals[i * 3 + 1] = (float)n.y; normals[i * 3 + 2] = (float)n.z;
        }
        return normals;
    }

    private static int[] triangulate(int[] source, int mode) {
        if (mode == 4) return source;
        if (mode == 5) {
            if (source.length < 3) return new int[0];
            int[] result = new int[(source.length - 2) * 3];
            int out = 0;
            for (int i = 0; i + 2 < source.length; i++) {
                if ((i & 1) == 0) { result[out++] = source[i]; result[out++] = source[i + 1]; result[out++] = source[i + 2]; }
                else { result[out++] = source[i + 1]; result[out++] = source[i]; result[out++] = source[i + 2]; }
            }
            return result;
        }
        if (mode == 6) {
            if (source.length < 3) return new int[0];
            int[] result = new int[(source.length - 2) * 3];
            int out = 0;
            for (int i = 1; i + 1 < source.length; i++) {
                result[out++] = source[0]; result[out++] = source[i]; result[out++] = source[i + 1];
            }
            return result;
        }
        return new int[0];
    }

    private static ParsedDocument parseGlb(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12 || buffer.getInt() != MAGIC_GLTF) throw new IllegalArgumentException("invalid GLB header");
        if (buffer.getInt() != 2) throw new IllegalArgumentException("only glTF 2.0 is supported");
        int declaredLength = buffer.getInt();
        if (declaredLength > bytes.length) throw new IllegalArgumentException("truncated GLB");
        JsonObject json = null;
        byte[] binary = null;
        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            int type = buffer.getInt();
            if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException("invalid GLB chunk");
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            if (type == CHUNK_JSON) json = JsonParser.parseString(new String(chunk, StandardCharsets.UTF_8).replace("\0", "").trim()).getAsJsonObject();
            else if (type == CHUNK_BIN && binary == null) binary = chunk;
        }
        if (json == null) throw new IllegalArgumentException("GLB has no JSON chunk");
        return new ParsedDocument(json, binary);
    }

    private static List<Identifier> candidates(Identifier logical) {
        String namespace = logical.getNamespace();
        String path = logical.getPath();
        ArrayList<Identifier> result = new ArrayList<>();
        if (path.endsWith(".gltf") || path.endsWith(".glb")) result.add(logical);
        String clean = path;
        if (clean.startsWith("models/")) clean = clean.substring(7);
        if (clean.startsWith("model/")) clean = clean.substring(6);
        result.add(Identifier.of(namespace, "cinefx/models/" + clean + ".glb"));
        result.add(Identifier.of(namespace, "cinefx/models/" + clean + ".gltf"));
        result.add(Identifier.of(namespace, "models/" + clean + ".glb"));
        result.add(Identifier.of(namespace, "models/" + clean + ".gltf"));
        return List.copyOf(result);
    }

    private static Identifier sibling(Identifier source, String relative) {
        String path = source.getPath();
        int slash = path.lastIndexOf('/');
        String base = slash < 0 ? "" : path.substring(0, slash + 1);
        String safe = relative.replace('\\', '/');
        while (safe.startsWith("./")) safe = safe.substring(2);
        while (safe.contains("../")) safe = safe.replace("../", "");
        return Identifier.of(source.getNamespace(), base + safe);
    }

    private static byte[] decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) throw new IllegalArgumentException("invalid data URI");
        String header = uri.substring(0, comma);
        String payload = uri.substring(comma + 1);
        return header.contains(";base64")
                ? Base64.getDecoder().decode(payload)
                : URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static int[] sceneRoots(JsonObject json, List<NodeDef> nodes) {
        JsonArray scenes = array(json, "scenes");
        if (scenes != null && !scenes.isEmpty()) {
            int index = integer(json, "scene", 0);
            if (index < 0 || index >= scenes.size()) index = 0;
            int[] roots = ints(array(scenes.get(index).getAsJsonObject(), "nodes"));
            if (roots.length > 0) return roots;
        }
        return java.util.stream.IntStream.range(0, nodes.size()).filter(i -> nodes.get(i).parent < 0).toArray();
    }

    private static List<String> targetNames(JsonObject mesh) {
        JsonObject extras = mesh.getAsJsonObject("extras");
        JsonArray names = extras == null ? null : array(extras, "targetNames");
        if (names == null) return List.of();
        ArrayList<String> result = new ArrayList<>();
        for (JsonElement element : names) result.add(element.getAsString());
        return List.copyOf(result);
    }

    private static boolean hasCompressionExtension(JsonObject primitive) {
        JsonObject extensions = primitive.getAsJsonObject("extensions");
        return extensions != null && (extensions.has("KHR_draco_mesh_compression") || extensions.has("EXT_meshopt_compression"));
    }

    private static int factorColor(JsonArray values) {
        double r = values.size() > 0 ? values.get(0).getAsDouble() : 1;
        double g = values.size() > 1 ? values.get(1).getAsDouble() : 1;
        double b = values.size() > 2 ? values.get(2).getAsDouble() : 1;
        double a = values.size() > 3 ? values.get(3).getAsDouble() : 1;
        return (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static float[] expandColors(float[] source, int components, int vertexCount) {
        float[] result = new float[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            result[i * 4] = component(source, i * components, 1);
            result[i * 4 + 1] = component(source, i * components + 1, 1);
            result[i * 4 + 2] = component(source, i * components + 2, 1);
            result[i * 4 + 3] = components >= 4 ? component(source, i * components + 3, 1) : 1;
        }
        return result;
    }

    private static float[] whiteColors(int count) {
        float[] values = new float[count * 4];
        java.util.Arrays.fill(values, 1.0f);
        return values;
    }

    private static void normalizeWeights(float[] weights) {
        for (int i = 0; i + 3 < weights.length; i += 4) {
            float sum = weights[i] + weights[i + 1] + weights[i + 2] + weights[i + 3];
            if (sum <= 0.000001f) { weights[i] = 1; weights[i + 1] = weights[i + 2] = weights[i + 3] = 0; }
            else for (int c = 0; c < 4; c++) weights[i + c] /= sum;
        }
    }

    private static void addNormal(float[] normals, int vertex, Vec3d normal) {
        int offset = vertex * 3;
        normals[offset] += (float)normal.x; normals[offset + 1] += (float)normal.y; normals[offset + 2] += (float)normal.z;
    }

    private static Vec3d projectToPlane(Vec3d point, double planeY, Vec3d direction) {
        if (Math.abs(direction.y) < 1.0e-6) return new Vec3d(point.x, planeY + 0.01, point.z);
        double t = (planeY - point.y) / direction.y;
        if (t < 0.0) t = 0.0;
        Vec3d projected = point.add(direction.multiply(t));
        return new Vec3d(projected.x, planeY + 0.012, projected.z);
    }

    private static boolean validVertex(PrimitiveDef primitive, int index) { return index >= 0 && index < primitive.vertices.size(); }
    private static Vec3d safeNormal(Vec3d value) { return value.lengthSquared() < 1.0e-12 ? new Vec3d(0, 1, 0) : value.normalize(); }
    private static Vec3d vec3(float[] values, int vertex) {
        int o = vertex * 3;
        return new Vec3d(component(values, o), component(values, o + 1), component(values, o + 2));
    }
    private static Vec3d vec3(JsonArray values, Vec3d fallback) {
        return values == null || values.size() < 3 ? fallback : new Vec3d(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }
    private static Quaternionf quat(JsonArray values) {
        if (values == null || values.size() < 4) return new Quaternionf();
        return new Quaternionf(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat(), values.get(3).getAsFloat()).normalize();
    }
    private static Matrix4f matrix(JsonArray values) {
        if (values == null || values.size() != 16) return null;
        float[] raw = new float[16];
        for (int i = 0; i < 16; i++) raw[i] = values.get(i).getAsFloat();
        return new Matrix4f().set(raw);
    }
    private static double[] doubles(JsonArray values) {
        if (values == null) return new double[0];
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsDouble();
        return result;
    }
    private static int[] ints(JsonArray values) {
        if (values == null) return new int[0];
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsInt();
        return result;
    }
    private static int[] sequential(int count) { int[] values = new int[count]; for (int i = 0; i < count; i++) values[i] = i; return values; }
    private static float component(float[] values, int index) { return component(values, index, 0); }
    private static float component(float[] values, int index, float fallback) { return index >= 0 && index < values.length ? values[index] : fallback; }
    private static int componentCount(String type) { return switch (type) { case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4", "MAT2" -> 4; case "MAT3" -> 9; case "MAT4" -> 16; default -> 1; }; }
    private static int componentSize(int type) { return switch (type) { case 5120, 5121 -> 1; case 5122, 5123 -> 2; case 5125, 5126 -> 4; default -> 4; }; }
    private static int integer(JsonObject object, String name, int fallback) { return object != null && object.has(name) ? object.get(name).getAsInt() : fallback; }
    private static double number(JsonObject object, String name, double fallback) { return object != null && object.has(name) ? object.get(name).getAsDouble() : fallback; }
    private static String string(JsonObject object, String name, String fallback) { return object != null && object.has(name) ? object.get(name).getAsString() : fallback; }
    private static JsonArray array(JsonObject object, String name) { JsonElement value = object == null ? null : object.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : null; }
    private static int channel(double value) { return (int)Math.round(clamp01(value) * 255.0); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static Vec3d lerp(Vec3d a, Vec3d b, double t) { return new Vec3d(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t)); }
    private static double positiveModulo(double value, double modulus) { double result = value % modulus; return result < 0 ? result + modulus : result; }
    private static Vec3d point(Matrix4fc matrix, Vec3d local) { return new Vec3d(matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30(), matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31(), matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32()); }
    private static Vec3d direction(Matrix4fc matrix, Vec3d local) { return new Vec3d(matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z, matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z, matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z); }
    private static byte[] readAll(InputStream input) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); input.transferTo(out); return out.toByteArray(); }
    private static String socketKey(long scene, String element) { return scene + ":" + element; }

    private enum AlphaMode { OPAQUE, MASK, BLEND }
    private enum AnimationPath { TRANSLATION, ROTATION, SCALE, WEIGHTS }
    private enum Interpolation { LINEAR, STEP, CUBICSPLINE }

    private record ParsedDocument(JsonObject json, byte[] binaryChunk) { }
    private record Access(byte[] buffer, int start, int stride, int componentType, int componentSize, boolean normalized) { }
    private record View(byte[] buffer, int start, int length, int stride) { }
    private record SkinDef(int[] joints, Matrix4f[] inverseBind) { }
    private record VertexDef(Vec3d position, Vec3d normal, float u, float v, float r, float g, float b, float a,
                             int[] joints, double[] weights, List<Vec3d> morphPositions, List<Vec3d> morphNormals) { }
    private record PrimitiveDef(List<VertexDef> vertices, int[] indices, int materialIndex) { }
    private record MeshDef(List<PrimitiveDef> primitives, double[] weights, List<String> targetNames) { }
    private record NodeDef(String name, int mesh, int skin, int parent, int[] children, Vec3d translation,
                           Quaternionf rotation, Vec3d scale, Matrix4f matrix, double[] weights) { }
    private record RuntimeVertex(Vec3d position, Vec3d normal, float u, float v, float r, float g, float b, float a) { }
    private record Pose(Matrix4f[] local, Matrix4f[] world, double[][] animatedWeights) { }
    private record SocketPose(Matrix4f rootWorld, Map<String, Matrix4f> namedWorld, double lastSeenTick) { }

    private record MaterialDef(int baseColorArgb, Identifier baseColorTexture, double metallic, double roughness,
                               double emissive, AlphaMode alphaMode, double alphaCutoff, boolean doubleSided) {
        static MaterialDef defaultMaterial() { return new MaterialDef(0xFFFFFFFF, null, 0.0, 0.85, 0.0, AlphaMode.OPAQUE, 0.5, true); }
        RenderLayer renderLayer(Identifier texture, double frameEmissive) {
            if (emissive + frameEmissive > 0.25) return RenderLayers.entityTranslucentEmissive(texture);
            if (alphaMode == AlphaMode.BLEND) return RenderLayers.entityTranslucent(texture);
            if (alphaMode == AlphaMode.MASK) return doubleSided ? RenderLayers.entityCutoutNoCull(texture) : RenderLayers.entityCutout(texture);
            return doubleSided ? RenderLayers.entityCutoutNoCull(texture) : RenderLayers.entityCutout(texture);
        }
    }

    private record AnimationDef(String name, List<AnimationChannel> channels, double duration) { }

    private record AnimationChannel(int node, AnimationPath path, Interpolation interpolation,
                                    float[] times, float[] values, int components) {
        int keyIndex(double time) {
            if (times.length <= 1 || time <= times[0]) return 0;
            int low = 0, high = times.length - 1;
            while (low + 1 < high) {
                int mid = (low + high) >>> 1;
                if (times[mid] <= time) low = mid; else high = mid;
            }
            return low;
        }
        double fraction(int key, double time) {
            if (interpolation == Interpolation.STEP || key + 1 >= times.length) return 0.0;
            double span = times[key + 1] - times[key];
            return span <= 1.0e-8 ? 0.0 : clamp01((time - times[key]) / span);
        }
        int valueOffset(int key, int component) {
            int stride = interpolation == Interpolation.CUBICSPLINE ? components * 3 : components;
            return key * stride + (interpolation == Interpolation.CUBICSPLINE ? components : 0) + component;
        }
        float value(int key, int component) { int index = valueOffset(key, component); return index >= 0 && index < values.length ? values[index] : 0; }
        Vec3d sampleVec3(double time) {
            int key = keyIndex(time); double t = fraction(key, time);
            Vec3d a = new Vec3d(value(key, 0), value(key, 1), value(key, 2));
            if (t == 0.0 || key + 1 >= times.length) return a;
            Vec3d b = new Vec3d(value(key + 1, 0), value(key + 1, 1), value(key + 1, 2));
            if (interpolation != Interpolation.CUBICSPLINE) return lerp(a, b, t);
            double dt = times[key + 1] - times[key];
            Vec3d out = tangent(key, 2).multiply(dt);
            Vec3d in = tangent(key + 1, 0).multiply(dt);
            return hermite(a, out, b, in, t);
        }
        Quaternionf sampleQuat(double time) {
            int key = keyIndex(time); double t = fraction(key, time);
            Quaternionf a = new Quaternionf(value(key, 0), value(key, 1), value(key, 2), value(key, 3)).normalize();
            if (t == 0.0 || key + 1 >= times.length) return a;
            Quaternionf b = new Quaternionf(value(key + 1, 0), value(key + 1, 1), value(key + 1, 2), value(key + 1, 3)).normalize();
            return new Quaternionf(a).slerp(b, (float)t).normalize();
        }
        double[] sampleWeights(double time) {
            int key = keyIndex(time); double t = fraction(key, time);
            double[] result = new double[components];
            for (int i = 0; i < components; i++) {
                double a = value(key, i);
                double b = key + 1 < times.length ? value(key + 1, i) : a;
                result[i] = interpolation == Interpolation.STEP ? a : lerp(a, b, t);
            }
            return result;
        }
        Vec3d tangent(int key, int tangentBlock) {
            int stride = components * 3;
            int base = key * stride + tangentBlock * components;
            return new Vec3d(base < values.length ? values[base] : 0,
                    base + 1 < values.length ? values[base + 1] : 0,
                    base + 2 < values.length ? values[base + 2] : 0);
        }
        static Vec3d hermite(Vec3d p0, Vec3d m0, Vec3d p1, Vec3d m1, double t) {
            double t2 = t * t, t3 = t2 * t;
            double h00 = 2 * t3 - 3 * t2 + 1;
            double h10 = t3 - 2 * t2 + t;
            double h01 = -2 * t3 + 3 * t2;
            double h11 = t3 - t2;
            return p0.multiply(h00).add(m0.multiply(h10)).add(p1.multiply(h01)).add(m1.multiply(h11));
        }
    }

    private record Model(List<NodeDef> nodes, List<MeshDef> meshes, List<MaterialDef> materials,
                         List<SkinDef> skins, List<AnimationDef> animations, int[] roots, int triangleCount) {
        AnimationDef animation(Identifier clipId) {
            if (animations.isEmpty() || clipId == null) return null;
            String path = clipId.getPath();
            String full = clipId.toString();
            for (AnimationDef animation : animations) {
                if (animation.name.equalsIgnoreCase(path) || animation.name.equalsIgnoreCase(full)) return animation;
            }
            if (animations.size() == 1 && ("default".equalsIgnoreCase(path) || "idle".equalsIgnoreCase(path))) return animations.get(0);
            return null;
        }
    }

    private record PoseRequest(List<AnimationSample> animations, List<BoneSample> bones, List<MorphSample> morphs,
                               boolean geometryShadow, double shadowPlaneY) {
        static PoseRequest forActor(ActorFrame frame) {
            double shadowPlane = frame.worldPosition().y;
            String raw = frame.appearance().get("shadow_plane_y");
            if (raw != null) try { shadowPlane = Double.parseDouble(raw); } catch (NumberFormatException ignored) { }
            return new PoseRequest(frame.animations(), frame.boneOverrides(), frame.morphs(), frame.castShadow(), shadowPlane);
        }
        static PoseRequest forMesh(MeshFrame frame) {
            ArrayList<AnimationSample> animations = new ArrayList<>();
            String clip = frame.parameters().get("gltf.animation");
            if (clip != null && !clip.isBlank()) {
                double speed = parse(frame.parameters().get("gltf.animation_speed"), 1.0);
                boolean loop = !"false".equalsIgnoreCase(frame.parameters().getOrDefault("gltf.loop", "true"));
                animations.add(new AnimationSample(Identifier.of("cinefx", clip), 1.0, speed,
                        frame.localTick() * speed, loop, ComplexElement.BlendMode.OVERRIDE, Map.of()));
            }
            ArrayList<MorphSample> morphs = new ArrayList<>();
            for (Map.Entry<String, String> entry : frame.parameters().entrySet()) {
                if (!entry.getKey().startsWith("gltf.morph.")) continue;
                try { morphs.add(new MorphSample(entry.getKey().substring("gltf.morph.".length()), Double.parseDouble(entry.getValue()))); }
                catch (NumberFormatException ignored) { }
            }
            double shadowPlane = parse(frame.parameters().get("shadow_plane_y"), frame.worldPosition().y);
            return new PoseRequest(List.copyOf(animations), List.of(), List.copyOf(morphs), frame.castShadow(), shadowPlane);
        }
        static double parse(String value, double fallback) { if (value == null) return fallback; try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; } }
    }
}