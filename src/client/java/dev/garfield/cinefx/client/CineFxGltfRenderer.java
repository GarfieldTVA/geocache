package dev.garfield.cinefx.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AttachmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.BoneSample;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Lightweight built-in glTF 2.0 / GLB reference renderer.
 *
 * Supported baseline: scene/node transforms, triangle primitives, external/data/GLB buffers,
 * POSITION, indices and material baseColorFactor. The renderer is intentionally untextured and
 * uses Minecraft's ordered debug-quad layer so it remains dependency-free and renderer-safe.
 * Higher-priority backends can replace it with fully textured/GPU-skinned rendering.
 */
final class CineFxGltfRenderer {
    private static final int MAGIC_GLTF = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final Map<Identifier, Model> CACHE = new HashMap<>();
    private static final Set<Identifier> MISSING = new HashSet<>();
    private static final Map<String, BonePose> BONES = new HashMap<>();

    private CineFxGltfRenderer() { }

    static boolean preload(Identifier logicalId) {
        // A missing glTF is still "ready": the native procedural proxy is the declared fallback.
        load(logicalId);
        return true;
    }

    static List<MeshFrame> renderMeshes(SceneRenderContext context, List<MeshFrame> frames) {
        ArrayList<RenderJob> jobs = new ArrayList<>();
        ArrayList<MeshFrame> missing = new ArrayList<>();
        for (MeshFrame frame : frames) {
            if (frame.opacity() <= 0.001) continue;
            Model model = load(frame.modelId());
            if (model == null) missing.add(frame);
            else jobs.add(new RenderJob(model, frame.worldMatrix(), frame.tintArgb(), frame.opacity()));
        }
        submit(context, jobs);
        return List.copyOf(missing);
    }

    static List<ActorFrame> renderCustomActors(SceneRenderContext context, List<ActorFrame> frames) {
        ArrayList<RenderJob> jobs = new ArrayList<>();
        ArrayList<ActorFrame> missing = new ArrayList<>();
        for (ActorFrame frame : frames) {
            if (frame.kind() != ComplexElement.ActorKind.CUSTOM_MODEL || frame.opacity() <= 0.001) continue;
            Model model = load(frame.resourceId());
            if (model == null) {
                missing.add(frame);
                continue;
            }
            jobs.add(new RenderJob(model, frame.worldMatrix(), frame.tintArgb(), frame.opacity()));
            recordBonePose(context, frame, model);
        }
        submit(context, jobs);
        double now = context.absoluteGameTick();
        BONES.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > 6.0);
        return List.copyOf(missing);
    }

    static List<AttachmentFrame> refineAttachments(List<AttachmentFrame> frames) {
        ArrayList<AttachmentFrame> out = new ArrayList<>(frames.size());
        for (AttachmentFrame frame : frames) {
            if (frame.boneName() == null) {
                out.add(frame);
                continue;
            }
            BonePose pose = BONES.get(key(frame.sceneInstanceId(), frame.parentKey()));
            Matrix4f bone = pose == null ? null : pose.bones.get(frame.boneName().toLowerCase(Locale.ROOT));
            if (pose == null || bone == null) {
                out.add(frame);
                continue;
            }
            try {
                Matrix4f inverseRoot = new Matrix4f(pose.root).invert();
                Matrix4f local = inverseRoot.mul(new Matrix4f(frame.worldMatrix()));
                Matrix4f refined = new Matrix4f(bone).mul(local);
                Vec3d position = point(refined, Vec3d.ZERO);
                out.add(new AttachmentFrame(frame.sceneInstanceId(), frame.elementKey(), frame.parentKey(),
                        frame.boneName(), frame.inheritMode(), refined, position, frame.payload(), frame.seed(), frame.localTick()));
            } catch (RuntimeException exception) {
                out.add(frame);
            }
        }
        return List.copyOf(out);
    }

    static void clear() {
        CACHE.clear();
        MISSING.clear();
        BONES.clear();
    }

    private static void submit(SceneRenderContext context, List<RenderJob> jobs) {
        if (jobs.isEmpty()) return;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            Vec3d camera = context.cameraPosition();
            for (RenderJob job : jobs) {
                for (Triangle triangle : job.model.triangles) {
                    int color = multiply(triangle.colorArgb, job.tintArgb, job.opacity);
                    Vec3d a = point(job.worldMatrix, triangle.a).subtract(camera);
                    Vec3d b = point(job.worldMatrix, triangle.b).subtract(camera);
                    Vec3d c = point(job.worldMatrix, triangle.c).subtract(camera);
                    triangle(vertices, base, a, b, c, color);
                }
            }
        });
        context.matrices().pop();
    }

    private static void recordBonePose(SceneRenderContext context, ActorFrame frame, Model model) {
        LinkedHashMap<String, Matrix4f> resolved = new LinkedHashMap<>();
        Map<String, BoneSample> overrides = new HashMap<>();
        for (BoneSample sample : frame.boneOverrides()) {
            if (sample.weight() > 0.0001) overrides.put(sample.bone().toLowerCase(Locale.ROOT), sample);
        }
        for (Map.Entry<String, Matrix4f> entry : model.namedNodes.entrySet()) {
            Matrix4f local = new Matrix4f(entry.getValue());
            BoneSample override = overrides.get(entry.getKey());
            if (override != null) applyBoneOverride(local, override);
            resolved.put(entry.getKey(), new Matrix4f(frame.worldMatrix()).mul(local));
        }
        BONES.put(key(frame.sceneInstanceId(), frame.elementKey()),
                new BonePose(new Matrix4f(frame.worldMatrix()), Map.copyOf(resolved), context.absoluteGameTick()));
    }

    private static void applyBoneOverride(Matrix4f matrix, BoneSample sample) {
        AdvancedTransform t = sample.transform();
        float weight = (float)Math.max(0.0, Math.min(1.0, sample.weight()));
        Vec3d translation = t.translation().multiply(weight);
        Vec3d rotation = t.rotationDegrees().multiply(weight);
        Vec3d scale = new Vec3d(1.0 + (t.scale().x - 1.0) * weight,
                1.0 + (t.scale().y - 1.0) * weight,
                1.0 + (t.scale().z - 1.0) * weight);
        Vec3d pivot = t.pivot();
        matrix.translate((float)translation.x, (float)translation.y, (float)translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)pivot.x, (float)pivot.y, (float)pivot.z);
        if (rotation.x != 0.0) matrix.rotateX((float)Math.toRadians(rotation.x));
        if (rotation.y != 0.0) matrix.rotateY((float)Math.toRadians(rotation.y));
        if (rotation.z != 0.0) matrix.rotateZ((float)Math.toRadians(rotation.z));
        matrix.scale((float)scale.x, (float)scale.y, (float)scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);
    }

    private static synchronized Model load(Identifier logicalId) {
        if (logicalId == null) return null;
        Model cached = CACHE.get(logicalId);
        if (cached != null) return cached;
        if (MISSING.contains(logicalId)) return null;

        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager manager = client.getResourceManager();
        for (Identifier resourceId : candidates(logicalId)) {
            Resource resource = manager.getResource(resourceId).orElse(null);
            if (resource == null) continue;
            try (InputStream stream = resource.getInputStream()) {
                byte[] bytes = readAll(stream);
                ParsedDocument parsed = resourceId.getPath().toLowerCase(Locale.ROOT).endsWith(".glb")
                        ? parseGlb(bytes)
                        : new ParsedDocument(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(), null);
                Model model = parseModel(manager, resourceId, parsed.json, parsed.binaryChunk);
                if (model != null && !model.triangles.isEmpty()) {
                    CACHE.put(logicalId, model);
                    return model;
                }
            } catch (RuntimeException | IOException exception) {
                System.err.println("[CineFX] glTF load failed for " + resourceId + ": " + exception.getMessage());
            }
        }
        MISSING.add(logicalId);
        return null;
    }

    private static List<Identifier> candidates(Identifier logical) {
        String namespace = logical.getNamespace();
        String path = logical.getPath();
        ArrayList<Identifier> ids = new ArrayList<>();
        if (path.endsWith(".gltf") || path.endsWith(".glb")) ids.add(logical);
        String clean = path;
        if (clean.startsWith("models/")) clean = clean.substring("models/".length());
        if (clean.startsWith("model/")) clean = clean.substring("model/".length());
        ids.add(Identifier.of(namespace, "cinefx/models/" + clean + ".glb"));
        ids.add(Identifier.of(namespace, "cinefx/models/" + clean + ".gltf"));
        ids.add(Identifier.of(namespace, "models/" + clean + ".glb"));
        ids.add(Identifier.of(namespace, "models/" + clean + ".gltf"));
        return List.copyOf(ids);
    }

    private static ParsedDocument parseGlb(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12 || buffer.getInt() != MAGIC_GLTF) throw new IllegalArgumentException("invalid GLB header");
        int version = buffer.getInt();
        if (version != 2) throw new IllegalArgumentException("only glTF 2.0 is supported");
        int declaredLength = buffer.getInt();
        if (declaredLength > bytes.length) throw new IllegalArgumentException("truncated GLB");
        JsonObject json = null;
        byte[] bin = null;
        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            int type = buffer.getInt();
            if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException("invalid GLB chunk length");
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            if (type == CHUNK_JSON) {
                String text = new String(chunk, StandardCharsets.UTF_8).replace("\0", "").trim();
                json = JsonParser.parseString(text).getAsJsonObject();
            } else if (type == CHUNK_BIN && bin == null) {
                bin = chunk;
            }
        }
        if (json == null) throw new IllegalArgumentException("GLB has no JSON chunk");
        return new ParsedDocument(json, bin);
    }

    private static Model parseModel(ResourceManager manager, Identifier source, JsonObject json, byte[] glbBinary) throws IOException {
        List<byte[]> buffers = readBuffers(manager, source, json, glbBinary);
        JsonArray bufferViews = array(json, "bufferViews");
        JsonArray accessors = array(json, "accessors");
        JsonArray materials = array(json, "materials");
        JsonArray meshes = array(json, "meshes");
        JsonArray nodes = array(json, "nodes");
        if (meshes == null || nodes == null || accessors == null || bufferViews == null) return null;

        ArrayList<Triangle> triangles = new ArrayList<>();
        LinkedHashMap<String, Matrix4f> namedNodes = new LinkedHashMap<>();
        Set<Integer> visited = new HashSet<>();
        JsonArray roots = sceneRoots(json);
        if (roots != null) {
            for (JsonElement root : roots) traverseNode(root.getAsInt(), new Matrix4f(), nodes, meshes,
                    accessors, bufferViews, materials, buffers, triangles, namedNodes, visited);
        } else {
            Set<Integer> children = new HashSet<>();
            for (JsonElement element : nodes) {
                JsonObject node = element.getAsJsonObject();
                JsonArray c = array(node, "children");
                if (c != null) for (JsonElement child : c) children.add(child.getAsInt());
            }
            for (int i = 0; i < nodes.size(); i++) if (!children.contains(i)) {
                traverseNode(i, new Matrix4f(), nodes, meshes, accessors, bufferViews, materials,
                        buffers, triangles, namedNodes, visited);
            }
        }
        return new Model(List.copyOf(triangles), Map.copyOf(namedNodes));
    }

    private static void traverseNode(int index, Matrix4f parent, JsonArray nodes, JsonArray meshes,
                                     JsonArray accessors, JsonArray views, JsonArray materials, List<byte[]> buffers,
                                     List<Triangle> triangles, Map<String, Matrix4f> namedNodes, Set<Integer> path) {
        if (index < 0 || index >= nodes.size() || !path.add(index)) return;
        JsonObject node = nodes.get(index).getAsJsonObject();
        Matrix4f world = new Matrix4f(parent).mul(nodeTransform(node));
        if (node.has("name")) namedNodes.put(node.get("name").getAsString().toLowerCase(Locale.ROOT), new Matrix4f(world));
        if (node.has("mesh")) {
            int meshIndex = node.get("mesh").getAsInt();
            if (meshIndex >= 0 && meshIndex < meshes.size()) {
                appendMesh(meshes.get(meshIndex).getAsJsonObject(), world, accessors, views, materials, buffers, triangles);
            }
        }
        JsonArray children = array(node, "children");
        if (children != null) for (JsonElement child : children) {
            traverseNode(child.getAsInt(), world, nodes, meshes, accessors, views, materials, buffers,
                    triangles, namedNodes, path);
        }
        path.remove(index);
    }

    private static void appendMesh(JsonObject mesh, Matrix4fc nodeMatrix, JsonArray accessors, JsonArray views,
                                   JsonArray materials, List<byte[]> buffers, List<Triangle> out) {
        JsonArray primitives = array(mesh, "primitives");
        if (primitives == null) return;
        for (JsonElement element : primitives) {
            JsonObject primitive = element.getAsJsonObject();
            int mode = primitive.has("mode") ? primitive.get("mode").getAsInt() : 4;
            if (mode != 4) continue;
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            if (attributes == null || !attributes.has("POSITION")) continue;
            int positionAccessor = attributes.get("POSITION").getAsInt();
            List<Vec3d> positions = readVec3(positionAccessor, accessors, views, buffers);
            if (positions.isEmpty()) continue;
            int[] indices = primitive.has("indices")
                    ? readIndices(primitive.get("indices").getAsInt(), accessors, views, buffers)
                    : sequential(positions.size());
            int color = materialColor(primitive, materials);
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int ia = indices[i], ib = indices[i + 1], ic = indices[i + 2];
                if (ia < 0 || ib < 0 || ic < 0 || ia >= positions.size() || ib >= positions.size() || ic >= positions.size()) continue;
                out.add(new Triangle(point(nodeMatrix, positions.get(ia)), point(nodeMatrix, positions.get(ib)),
                        point(nodeMatrix, positions.get(ic)), color));
            }
        }
    }

    private static List<byte[]> readBuffers(ResourceManager manager, Identifier source, JsonObject json, byte[] glbBinary) throws IOException {
        JsonArray definitions = array(json, "buffers");
        if (definitions == null) return glbBinary == null ? List.of() : List.of(glbBinary);
        ArrayList<byte[]> result = new ArrayList<>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            JsonObject definition = definitions.get(i).getAsJsonObject();
            if (definition.has("uri")) {
                String uri = definition.get("uri").getAsString();
                if (uri.startsWith("data:")) {
                    int comma = uri.indexOf(',');
                    if (comma < 0) throw new IllegalArgumentException("invalid data URI");
                    result.add(Base64.getDecoder().decode(uri.substring(comma + 1)));
                } else {
                    Identifier external = sibling(source, uri);
                    Resource resource = manager.getResource(external).orElseThrow(() -> new IllegalArgumentException("missing buffer " + external));
                    try (InputStream input = resource.getInputStream()) { result.add(readAll(input)); }
                }
            } else if (i == 0 && glbBinary != null) {
                result.add(glbBinary);
            } else {
                result.add(new byte[0]);
            }
        }
        return List.copyOf(result);
    }

    private static Identifier sibling(Identifier source, String relative) {
        String path = source.getPath();
        int slash = path.lastIndexOf('/');
        String base = slash < 0 ? "" : path.substring(0, slash + 1);
        String safe = relative.replace('\\', '/');
        while (safe.startsWith("./")) safe = safe.substring(2);
        return Identifier.of(source.getNamespace(), base + safe);
    }

    private static List<Vec3d> readVec3(int accessorIndex, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        Access access = access(accessorIndex, accessors, views, buffers);
        if (access == null || access.components != 3) return List.of();
        ArrayList<Vec3d> values = new ArrayList<>(access.count);
        for (int i = 0; i < access.count; i++) {
            int offset = access.start + i * access.stride;
            values.add(new Vec3d(readComponent(access.buffer, offset, access.componentType, access.normalized),
                    readComponent(access.buffer, offset + access.componentSize, access.componentType, access.normalized),
                    readComponent(access.buffer, offset + access.componentSize * 2, access.componentType, access.normalized)));
        }
        return List.copyOf(values);
    }

    private static int[] readIndices(int accessorIndex, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        Access access = access(accessorIndex, accessors, views, buffers);
        if (access == null || access.components != 1) return new int[0];
        int[] values = new int[access.count];
        ByteBuffer data = ByteBuffer.wrap(access.buffer).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < access.count; i++) {
            int offset = access.start + i * access.stride;
            values[i] = switch (access.componentType) {
                case 5121 -> access.buffer[offset] & 0xFF;
                case 5123 -> data.getShort(offset) & 0xFFFF;
                case 5125 -> (int)(data.getInt(offset) & 0xFFFFFFFFL);
                default -> i;
            };
        }
        return values;
    }

    private static Access access(int index, JsonArray accessors, JsonArray views, List<byte[]> buffers) {
        if (index < 0 || index >= accessors.size()) return null;
        JsonObject accessor = accessors.get(index).getAsJsonObject();
        if (!accessor.has("bufferView")) return null;
        int viewIndex = accessor.get("bufferView").getAsInt();
        if (viewIndex < 0 || viewIndex >= views.size()) return null;
        JsonObject view = views.get(viewIndex).getAsJsonObject();
        int bufferIndex = view.get("buffer").getAsInt();
        if (bufferIndex < 0 || bufferIndex >= buffers.size()) return null;
        int componentType = accessor.get("componentType").getAsInt();
        int componentSize = componentSize(componentType);
        int components = components(accessor.get("type").getAsString());
        int viewOffset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int accessorOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : componentSize * components;
        int count = accessor.get("count").getAsInt();
        boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
        byte[] buffer = buffers.get(bufferIndex);
        int start = viewOffset + accessorOffset;
        long end = (long)start + Math.max(0, count - 1L) * stride + (long)componentSize * components;
        if (start < 0 || end > buffer.length) return null;
        return new Access(buffer, start, stride, count, componentType, componentSize, components, normalized);
    }

    private static double readComponent(byte[] bytes, int offset, int type, boolean normalized) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case 5120 -> normalized ? Math.max(-1.0, bytes[offset] / 127.0) : bytes[offset];
            case 5121 -> normalized ? (bytes[offset] & 0xFF) / 255.0 : bytes[offset] & 0xFF;
            case 5122 -> normalized ? Math.max(-1.0, data.getShort(offset) / 32767.0) : data.getShort(offset);
            case 5123 -> normalized ? (data.getShort(offset) & 0xFFFF) / 65535.0 : data.getShort(offset) & 0xFFFF;
            case 5125 -> data.getInt(offset) & 0xFFFFFFFFL;
            case 5126 -> data.getFloat(offset);
            default -> 0.0;
        };
    }

    private static int materialColor(JsonObject primitive, JsonArray materials) {
        if (materials == null || !primitive.has("material")) return 0xFFFFFFFF;
        int index = primitive.get("material").getAsInt();
        if (index < 0 || index >= materials.size()) return 0xFFFFFFFF;
        JsonObject material = materials.get(index).getAsJsonObject();
        JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
        JsonArray factor = pbr == null ? null : array(pbr, "baseColorFactor");
        if (factor == null || factor.size() < 4) return 0xFFFFFFFF;
        int r = channel(factor.get(0).getAsDouble()), g = channel(factor.get(1).getAsDouble());
        int b = channel(factor.get(2).getAsDouble()), a = channel(factor.get(3).getAsDouble());
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static Matrix4f nodeTransform(JsonObject node) {
        if (node.has("matrix")) {
            JsonArray values = node.getAsJsonArray("matrix");
            if (values.size() == 16) {
                float[] matrix = new float[16];
                for (int i = 0; i < 16; i++) matrix[i] = values.get(i).getAsFloat();
                return new Matrix4f().set(matrix);
            }
        }
        Vec3d translation = vec3(node.getAsJsonArray("translation"), Vec3d.ZERO);
        Vec3d scale = vec3(node.getAsJsonArray("scale"), new Vec3d(1, 1, 1));
        Quaternionf rotation = new Quaternionf();
        JsonArray q = node.getAsJsonArray("rotation");
        if (q != null && q.size() >= 4) rotation.set(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat());
        return new Matrix4f().translation((float)translation.x, (float)translation.y, (float)translation.z)
                .rotate(rotation).scale((float)scale.x, (float)scale.y, (float)scale.z);
    }

    private static JsonArray sceneRoots(JsonObject json) {
        JsonArray scenes = array(json, "scenes");
        if (scenes == null || scenes.isEmpty()) return null;
        int scene = json.has("scene") ? json.get("scene").getAsInt() : 0;
        if (scene < 0 || scene >= scenes.size()) scene = 0;
        return array(scenes.get(scene).getAsJsonObject(), "nodes");
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static Vec3d vec3(JsonArray values, Vec3d fallback) {
        if (values == null || values.size() < 3) return fallback;
        return new Vec3d(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }

    private static int components(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private static int componentSize(int type) {
        return switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> 4;
        };
    }

    private static int[] sequential(int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = i;
        return values;
    }

    private static int channel(double value) { return (int)Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0); }

    private static int multiply(int material, int tint, double opacity) {
        int ma = (material >>> 24) & 255, mr = (material >>> 16) & 255, mg = (material >>> 8) & 255, mb = material & 255;
        int ta = (tint >>> 24) & 255, tr = (tint >>> 16) & 255, tg = (tint >>> 8) & 255, tb = tint & 255;
        int a = (int)Math.round(ma * (ta / 255.0) * Math.max(0.0, Math.min(1.0, opacity)));
        int r = mr * tr / 255, g = mg * tg / 255, b = mb * tb / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d local) {
        return new Vec3d(matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30(),
                matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31(),
                matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32());
    }

    private static void triangle(VertexConsumer vertices, Matrix4f matrix, Vec3d a, Vec3d b, Vec3d c, int color) {
        vertices.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(color);
        vertices.vertex(matrix, (float)b.x, (float)b.y, (float)b.z).color(color);
        vertices.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(color);
        vertices.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(color);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        input.transferTo(out);
        return out.toByteArray();
    }

    private static String key(long scene, String element) { return scene + ":" + element; }

    private record ParsedDocument(JsonObject json, byte[] binaryChunk) { }
    private record Triangle(Vec3d a, Vec3d b, Vec3d c, int colorArgb) { }
    private record Model(List<Triangle> triangles, Map<String, Matrix4f> namedNodes) { }
    private record RenderJob(Model model, Matrix4fc worldMatrix, int tintArgb, double opacity) { }
    private record Access(byte[] buffer, int start, int stride, int count, int componentType,
                          int componentSize, int components, boolean normalized) { }
    private record BonePose(Matrix4f root, Map<String, Matrix4f> bones, double lastSeenTick) { }
}
