package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.Motions;
import dev.garfield.cinefx.api.Transform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Editor-side mirror of CineFxSceneGraphBridge's transform composition. It exists so viewport
 * markers, focus and animation paths match the actual parent/child runtime graph instead of only
 * showing an element's local offset.
 */
public final class HierarchyJson {
    private static volatile EditorModel.Project currentProject;

    private HierarchyJson() { }

    public static void setProject(EditorModel.Project project) { currentProject = project; }
    public static EditorModel.Project project() { return currentProject; }

    /** Returns the runtime-equivalent world position relative to the scene anchor. */
    public static Vec3d worldRelativePivot(EditorModel.Project project, EditorModel.Element element, double sceneTick) {
        if (project == null || element == null || !isTransformable(element)) return null;
        Map<String, EditorModel.Element> graph = activeGraph(project, sceneTick);
        EditorModel.Element active = graph.get(element.key());
        if (active == null) return null;
        Matrix4f matrix = resolve(project, active, graph, new HashMap<>(), new HashSet<>(), sceneTick);
        Vec3d world = point(matrix, Vec3d.ZERO);
        return world.subtract(project.anchor());
    }

    /** Samples the full resolved hierarchy, so a child path also follows an animated parent. */
    public static List<Vec3d> resolvedAnimationPath(EditorModel.Project project, EditorModel.Element element) {
        if (project == null || element == null || !isTransformable(element)) return List.of();
        double start = Math.max(0.0, element.startTick());
        double end = Math.max(start, element.endTick());
        double duration = end - start;
        if (duration <= 0.0001) return List.of();
        int samples = Math.max(2, Math.min(80, (int)Math.ceil(duration / 5.0) + 1));
        ArrayList<Vec3d> out = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            double sceneTick = start + duration * i / (samples - 1.0);
            Vec3d point = worldRelativePivot(project, element, sceneTick);
            if (point != null) out.add(point);
        }
        return List.copyOf(out);
    }

    private static Map<String, EditorModel.Element> activeGraph(EditorModel.Project project, double sceneTick) {
        HashMap<String, EditorModel.Element> graph = new HashMap<>();
        for (EditorModel.Element candidate : project.elements) {
            if (candidate == null || !candidate.enabled || !isTransformable(candidate)) continue;
            if (sceneTick < candidate.startTick() || sceneTick > candidate.endTick()) continue;
            String key = candidate.key();
            if (key != null && !key.isBlank()) graph.put(key, candidate);
        }
        return graph;
    }

    private static Matrix4f resolve(EditorModel.Project project, EditorModel.Element element,
                                    Map<String, EditorModel.Element> graph, Map<String, Matrix4f> cache,
                                    Set<String> resolving, double sceneTick) {
        Matrix4f cached = cache.get(element.key());
        if (cached != null) return new Matrix4f(cached);
        if (!resolving.add(element.key())) return root(project);

        String parentKey = string(element.data, "parentKey", "").trim();
        EditorModel.Element parent = parentKey.isBlank() ? null : graph.get(parentKey);
        Matrix4f matrix = parent == null
                ? root(project)
                : resolve(project, parent, graph, cache, resolving, sceneTick);

        double localTick = Math.max(0.0, sceneTick - element.startTick());
        Vec3d base = vector(element.data.get("baseOffset"), Vec3d.ZERO);
        JsonObject advanced = object(element.data.get("transform"));
        Vec3d translation = sampleChannel(advanced, "translation", localTick, Vec3d.ZERO);
        Vec3d rotation = sampleChannel(advanced, "rotationDegrees", localTick, Vec3d.ZERO);
        Vec3d scale = sampleChannel(advanced, "scale", localTick, new Vec3d(1, 1, 1));
        Vec3d pivot = sampleChannel(advanced, "pivot", localTick, Vec3d.ZERO);

        Transform motion = sampleMotion(object(element.data.get("motion")), localTick, project.seed);
        translation = base.add(translation).add(motion.translation());
        rotation = rotation.add(motion.rotationDegrees());
        scale = new Vec3d(scale.x * motion.scale().x, scale.y * motion.scale().y, scale.z * motion.scale().z);

        matrix.translate((float)translation.x, (float)translation.y, (float)translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)pivot.x, (float)pivot.y, (float)pivot.z);
        if (rotation.x != 0.0) matrix.rotateX((float)Math.toRadians(rotation.x));
        if (rotation.y != 0.0) matrix.rotateY((float)Math.toRadians(rotation.y));
        if (rotation.z != 0.0) matrix.rotateZ((float)Math.toRadians(rotation.z));
        matrix.scale((float)scale.x, (float)scale.y, (float)scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);

        resolving.remove(element.key());
        cache.put(element.key(), new Matrix4f(matrix));
        return matrix;
    }

    private static Matrix4f root(EditorModel.Project project) {
        Vec3d anchor = project.anchor();
        return new Matrix4f().translation((float)anchor.x, (float)anchor.y, (float)anchor.z);
    }

    private static Vec3d sampleChannel(JsonObject advanced, String name, double tick, Vec3d fallback) {
        if (advanced == null) return fallback;
        JsonObject track = object(advanced.get(name));
        if (track == null) return fallback;
        JsonObject sampled = AnimationJson.sampleVec3(track, tick);
        return vector(sampled, fallback);
    }

    private static Transform sampleMotion(JsonObject motion, double tick, long seed) {
        if (motion == null) return Transform.IDENTITY;
        String preset = string(motion, "preset", "NONE").toUpperCase(Locale.ROOT);
        JsonObject p = object(motion.get("parameters"));
        if (p == null) p = new JsonObject();
        try {
            MotionCurve curve = switch (preset) {
                case "SPIN" -> Motions.spin(number(p, "x", 0), number(p, "y", 1), number(p, "z", 0));
                case "LEVITATE" -> Motions.levitate(number(p, "amplitude", 1), positive(p, "periodTicks", 40), number(p, "phaseRadians", 0));
                case "ORBIT" -> Motions.orbit(number(p, "radius", 3), positive(p, "periodTicks", 80), number(p, "verticalOffset", 0), bool(p, "faceTangent", true));
                case "GRAVITY" -> Motions.gravity(vector(p.get("launch"), new Vec3d(0, .4, 0)), positive(p, "gravity", .04));
                case "BALLISTIC" -> Motions.ballistic(
                        vector(p.get("velocity"), Vec3d.ZERO),
                        vector(p.get("acceleration"), Vec3d.ZERO),
                        vector(p.get("angularVelocity"), Vec3d.ZERO),
                        vector(p.get("angularAcceleration"), Vec3d.ZERO));
                case "JITTER" -> Motions.jitter(number(p, "amplitude", .05), number(p, "frequency", .25));
                default -> null;
            };
            if (curve != null) {
                Transform result = curve.sample(tick, seed);
                return result == null ? Transform.IDENTITY : result;
            }
            if ("BAKED".equals(preset)) {
                JsonObject sampled = sampleBakedMotion(motion, tick);
                if (sampled != null) return transform(sampled);
            }
        } catch (RuntimeException ignored) { }
        return Transform.IDENTITY;
    }

    private static JsonObject sampleBakedMotion(JsonObject motion, double tick) {
        if (!motion.has("keys") || !motion.get("keys").isJsonArray()) return null;
        JsonObject synthetic = new JsonObject();
        synthetic.addProperty("$kind", "TransformTrack");
        JsonArray keys = motion.getAsJsonArray("keys").deepCopy();
        for (JsonElement raw : keys) {
            if (raw.isJsonObject() && !raw.getAsJsonObject().has("easing")) raw.getAsJsonObject().addProperty("easing", "LINEAR");
        }
        synthetic.add("keys", keys);
        return AnimationJson.sampleTransform(synthetic, tick);
    }

    private static Transform transform(JsonObject value) {
        return new Transform(
                vector(value.get("translation"), Vec3d.ZERO),
                vector(value.get("rotationDegrees"), Vec3d.ZERO),
                vector(value.get("scale"), new Vec3d(1, 1, 1)));
    }

    private static boolean isTransformable(EditorModel.Element element) {
        if (element.apiClass == null || element.apiClass.isBlank()) return false;
        try { return ComplexElement.Transformable.class.isAssignableFrom(Class.forName(element.apiClass)); }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d local) {
        double x = matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30();
        double y = matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31();
        double z = matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32();
        return new Vec3d(x, y, z);
    }

    private static JsonObject object(JsonElement value) { return value != null && value.isJsonObject() ? value.getAsJsonObject() : null; }
    private static Vec3d vector(JsonElement value, Vec3d fallback) { return value != null && value.isJsonObject() ? vector(value.getAsJsonObject(), fallback) : fallback; }
    private static Vec3d vector(JsonObject value, Vec3d fallback) {
        if (value == null) return fallback;
        try { return new Vec3d(value.get("x").getAsDouble(), value.get("y").getAsDouble(), value.get("z").getAsDouble()); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonObject object, String name, String fallback) {
        try { return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double number(JsonObject object, String name, double fallback) {
        try { return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double positive(JsonObject object, String name, double fallback) { double value = number(object, name, fallback); return value > 0 ? value : fallback; }
    private static boolean bool(JsonObject object, String name, boolean fallback) {
        try { return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
