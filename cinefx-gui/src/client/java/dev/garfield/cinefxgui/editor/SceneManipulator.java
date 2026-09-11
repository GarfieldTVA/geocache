package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON-side transform adapter for the editor. It intentionally manipulates the data model instead
 * of depending on CineFX renderer internals. The resulting objects are still decoded and executed by
 * the separately installed CineFX API mod.
 */
public final class SceneManipulator {
    public enum Tool { MOVE, ROTATE, SCALE }
    public enum Axis { X, Y, Z, CENTER }

    public static final class Session {
        private final Tool tool;
        private final Axis axis;
        private final JsonObject vector;
        private final Vec3d original;

        private Session(Tool tool, Axis axis, JsonObject vector) {
            this.tool = tool;
            this.axis = axis;
            this.vector = vector;
            this.original = readVec(vector, tool == Tool.SCALE ? new Vec3d(1, 1, 1) : Vec3d.ZERO);
        }

        public Tool tool() { return tool; }
        public Axis axis() { return axis; }
        public Vec3d original() { return original; }

        public void applyAxis(double amount) {
            Vec3d out;
            if (tool == Tool.SCALE) {
                if (axis == Axis.CENTER) {
                    double factor = Math.max(0.001, 1.0 + amount);
                    out = new Vec3d(original.x * factor, original.y * factor, original.z * factor);
                } else {
                    double x = original.x, y = original.y, z = original.z;
                    if (axis == Axis.X) x = Math.max(0.001, x + amount);
                    if (axis == Axis.Y) y = Math.max(0.001, y + amount);
                    if (axis == Axis.Z) z = Math.max(0.001, z + amount);
                    out = new Vec3d(x, y, z);
                }
            } else {
                double x = original.x, y = original.y, z = original.z;
                if (axis == Axis.X) x += amount;
                if (axis == Axis.Y) y += amount;
                if (axis == Axis.Z) z += amount;
                out = new Vec3d(x, y, z);
            }
            writeVec(vector, out);
        }

        public void applyPlane(Vec3d delta) {
            if (tool == Tool.MOVE && delta != null) writeVec(vector, original.add(delta));
        }
    }

    private record NamedVector(String name, JsonObject value) { }

    private static final String[] POSITION_NAMES = {
            "baseOffset", "centerOffset", "offset", "fromOffset", "originOffset", "position",
            "emitterOffset", "sourceOffset", "startOffset", "anchorOffset", "targetOffset",
            "destinationOffset", "toOffset", "endOffset", "lookAtOffset"
    };
    private static final String[] POSITION_TRACK_NAMES = {
            "translation", "position", "positionTrack", "offset", "offsetTrack", "center", "centerTrack",
            "origin", "originTrack", "anchor", "anchorTrack", "source", "sourceTrack", "target", "targetTrack"
    };

    private SceneManipulator() { }

    public static Vec3d pivotLocal(EditorModel.Element element, double localTick) {
        if (element == null || element.data == null) return null;
        NamedVector base = findPreferredVector(element.data, POSITION_NAMES);
        Vec3d baseValue = base == null ? Vec3d.ZERO : readVec(base.value, Vec3d.ZERO);

        JsonObject transform = findTrack(element.data, "TransformTrack");
        if (transform != null) {
            JsonObject value = sampleTransformValue(transform, localTick);
            if (value != null && value.has("translation") && value.get("translation").isJsonObject()) {
                return baseValue.add(readVec(value.getAsJsonObject("translation"), Vec3d.ZERO));
            }
        }

        JsonObject advanced = findAdvancedTransform(element.data);
        JsonObject advancedTranslation = advancedChannel(advanced, "translation");
        if (advancedTranslation != null) {
            JsonObject sampled = sampleVec3TrackValue(advancedTranslation, localTick);
            if (sampled != null) return baseValue.add(readVec(sampled, Vec3d.ZERO));
        }

        if (base != null) return baseValue;

        JsonObject positionalTrack = findVec3TrackByNames(element.data, POSITION_TRACK_NAMES);
        if (positionalTrack != null) {
            JsonObject sampled = sampleVec3TrackValue(positionalTrack, localTick);
            if (sampled != null) return readVec(sampled, Vec3d.ZERO);
        }

        JsonObject path = findTrack(element.data, "PathTrack");
        if (path != null) {
            JsonObject point = nearestTimedObject(path.getAsJsonArray("points"), localTick);
            if (point != null && point.has("position") && point.get("position").isJsonObject())
                return readVec(point.getAsJsonObject("position"), Vec3d.ZERO);
        }
        return findAnyVector(element.data);
    }

    public static Session begin(EditorModel.Element element, Tool tool, Axis axis, double localTick) {
        if (element == null || element.locked || element.data == null) return null;
        JsonObject vector = switch (tool) {
            case MOVE -> positionVectorForEdit(element.data, localTick);
            case ROTATE -> rotationVectorForEdit(element.data, localTick);
            case SCALE -> scaleVectorForEdit(element.data, localTick);
        };
        return vector == null ? null : new Session(tool, axis, vector);
    }

    public static boolean translate(EditorModel.Element element, Vec3d delta, double localTick) {
        Session session = begin(element, Tool.MOVE, Axis.CENTER, localTick);
        if (session == null) return false;
        session.applyPlane(delta);
        return true;
    }

    public static boolean setBlock(EditorModel.Element element, String blockId) {
        if (element == null || element.data == null || blockId == null || blockId.isBlank()) return false;
        JsonObject state = findBlockState(element.data);
        if (state == null) return false;
        state.addProperty("block", blockId);
        if (!state.has("properties") || !state.get("properties").isJsonObject()) state.add("properties", new JsonObject());
        return true;
    }

    public static String blockId(EditorModel.Element element) {
        if (element == null || element.data == null) return null;
        JsonObject state = findBlockState(element.data);
        if (state == null || !state.has("block") || !state.get("block").isJsonPrimitive()) return null;
        return state.get("block").getAsString();
    }

    public static boolean setLookAt(EditorModel.Element camera, Vec3d localTarget) {
        if (camera == null || camera.data == null || localTarget == null || !camera.data.has("lookAtOffset")) return false;
        camera.data.add("lookAtOffset", vec(localTarget));
        return true;
    }

    public static void setBaseOffset(EditorModel.Element element, Vec3d localPosition) {
        if (element == null || element.data == null || localPosition == null) return;
        if (element.data.has("baseOffset")) element.data.add("baseOffset", vec(localPosition));
        else if (element.data.has("centerOffset")) element.data.add("centerOffset", vec(localPosition));
        else if (element.data.has("offset")) element.data.add("offset", vec(localPosition));
        else if (element.data.has("position")) element.data.add("position", vec(localPosition));
    }

    public static List<Vec3d> animationPathLocal(EditorModel.Element element) {
        if (element == null || element.data == null) return List.of();
        ArrayList<Vec3d> result = new ArrayList<>();
        NamedVector base = findPreferredVector(element.data, POSITION_NAMES);
        Vec3d baseValue = base == null ? Vec3d.ZERO : readVec(base.value, Vec3d.ZERO);

        JsonObject transform = findTrack(element.data, "TransformTrack");
        if (transform != null && transform.has("keys") && transform.get("keys").isJsonArray()) {
            appendTransformPath(result, transform.getAsJsonArray("keys"), baseValue);
        }
        if (result.size() > 1) return List.copyOf(result);

        JsonObject advanced = findAdvancedTransform(element.data);
        JsonObject advancedTranslation = advancedChannel(advanced, "translation");
        if (advancedTranslation != null && advancedTranslation.has("keys") && advancedTranslation.get("keys").isJsonArray()) {
            result.clear();
            appendVec3TrackPath(result, advancedTranslation.getAsJsonArray("keys"), baseValue);
        }
        if (result.size() > 1) return List.copyOf(result);

        JsonObject positionalTrack = findVec3TrackByNames(element.data, POSITION_TRACK_NAMES);
        if (positionalTrack != null && positionalTrack.has("keys") && positionalTrack.get("keys").isJsonArray()) {
            result.clear();
            appendVec3TrackPath(result, positionalTrack.getAsJsonArray("keys"), baseValue);
        }
        if (result.size() > 1) return List.copyOf(result);

        JsonObject path = findTrack(element.data, "PathTrack");
        if (path != null && path.has("points") && path.get("points").isJsonArray()) {
            result.clear();
            for (JsonElement raw : path.getAsJsonArray("points")) {
                if (raw.isJsonObject() && raw.getAsJsonObject().has("position") && raw.getAsJsonObject().get("position").isJsonObject())
                    result.add(baseValue.add(readVec(raw.getAsJsonObject().getAsJsonObject("position"), Vec3d.ZERO)));
            }
        }
        return List.copyOf(result);
    }

    public static JsonObject ensureTransformKey(EditorModel.Element element, double localTick) {
        if (element == null || element.data == null) return null;
        JsonObject track = findTrack(element.data, "TransformTrack");
        return track == null ? null : ensureTransformValue(track, localTick);
    }

    private static JsonObject positionVectorForEdit(JsonObject root, double tick) {
        JsonObject transform = findTrack(root, "TransformTrack");
        if (transform != null) {
            JsonObject value = ensureTransformValue(transform, tick);
            if (value != null) return ensureVec(value, "translation", Vec3d.ZERO);
        }

        JsonObject advanced = findAdvancedTransform(root);
        JsonObject advancedTranslation = advancedChannel(advanced, "translation");
        if (advancedTranslation != null) return ensureVec3TrackValue(advancedTranslation, tick, Vec3d.ZERO);

        NamedVector base = findPreferredVector(root, POSITION_NAMES);
        if (base != null) return base.value;

        JsonObject positionalTrack = findVec3TrackByNames(root, POSITION_TRACK_NAMES);
        if (positionalTrack != null) return ensureVec3TrackValue(positionalTrack, tick, Vec3d.ZERO);

        JsonObject path = findTrack(root, "PathTrack");
        if (path != null && path.has("points") && path.get("points").isJsonArray()) {
            JsonObject point = ensureTimedObject(path.getAsJsonArray("points"), tick, "position", Vec3d.ZERO);
            return point == null ? null : ensureVec(point, "position", Vec3d.ZERO);
        }
        return null;
    }

    private static JsonObject rotationVectorForEdit(JsonObject root, double tick) {
        JsonObject transform = findTrack(root, "TransformTrack");
        if (transform != null) {
            JsonObject value = ensureTransformValue(transform, tick);
            if (value != null) return ensureVec(value, "rotationDegrees", Vec3d.ZERO);
        }

        JsonObject advanced = findAdvancedTransform(root);
        JsonObject advancedRotation = advancedChannel(advanced, "rotationDegrees");
        if (advancedRotation != null) return ensureVec3TrackValue(advancedRotation, tick, Vec3d.ZERO);

        JsonObject direct = findNamedVector(root, "rotationDegrees", "rotation", "eulerDegrees");
        if (direct != null) return direct;
        JsonObject track = findVec3TrackByNames(root, "rotationDegrees", "rotation", "rotationTrack", "eulerDegrees", "angles");
        return track == null ? null : ensureVec3TrackValue(track, tick, Vec3d.ZERO);
    }

    private static JsonObject scaleVectorForEdit(JsonObject root, double tick) {
        JsonObject transform = findTrack(root, "TransformTrack");
        if (transform != null) {
            JsonObject value = ensureTransformValue(transform, tick);
            if (value != null) return ensureVec(value, "scale", new Vec3d(1, 1, 1));
        }

        JsonObject advanced = findAdvancedTransform(root);
        JsonObject advancedScale = advancedChannel(advanced, "scale");
        if (advancedScale != null) return ensureVec3TrackValue(advancedScale, tick, new Vec3d(1, 1, 1));

        JsonObject direct = findNamedVector(root, "scale", "scale3d");
        if (direct != null) return direct;
        JsonObject track = findVec3TrackByNames(root, "scale", "scale3d", "scaleTrack");
        return track == null ? null : ensureVec3TrackValue(track, tick, new Vec3d(1, 1, 1));
    }

    private static JsonObject ensureVec3TrackValue(JsonObject track, double tick, Vec3d fallback) {
        JsonArray keys = ensureArray(track, "keys");
        for (JsonElement raw : keys) {
            if (!raw.isJsonObject()) continue;
            JsonObject key = raw.getAsJsonObject();
            if (Math.abs(number(key, "tick", -1e30) - tick) < 0.001) return ensureVec(key, "value", fallback);
        }
        JsonObject sampled = sampleVec3TrackValue(track, tick);
        JsonObject key = new JsonObject();
        key.addProperty("tick", tick);
        key.add("value", sampled == null ? vec(fallback) : sampled.deepCopy());
        key.addProperty("easing", "LINEAR");
        keys.add(key);
        sortTimedArray(keys);
        return ensureVec(key, "value", fallback);
    }

    private static JsonObject ensureTransformValue(JsonObject track, double tick) {
        JsonArray keys = ensureArray(track, "keys");
        for (JsonElement raw : keys) {
            if (raw.isJsonObject() && Math.abs(number(raw.getAsJsonObject(), "tick", -1e30) - tick) < 0.001) {
                JsonObject key = raw.getAsJsonObject();
                if (!key.has("value") || !key.get("value").isJsonObject()) key.add("value", identityTransform());
                return normalizeTransform(key.getAsJsonObject("value"));
            }
        }
        JsonObject sampled = sampleTransformValue(track, tick);
        JsonObject key = new JsonObject();
        key.addProperty("tick", tick);
        key.add("value", sampled == null ? identityTransform() : sampled.deepCopy());
        key.addProperty("easing", "LINEAR");
        keys.add(key);
        sortTimedArray(keys);
        return normalizeTransform(key.getAsJsonObject("value"));
    }

    private static JsonObject sampleTransformValue(JsonObject track, double tick) {
        if (track == null || !track.has("keys") || !track.get("keys").isJsonArray()) return null;
        ArrayList<JsonObject> keys = timedObjects(track.getAsJsonArray("keys"));
        if (keys.isEmpty()) return null;
        if (tick <= number(keys.getFirst(), "tick", 0)) return transformValue(keys.getFirst());
        if (tick >= number(keys.getLast(), "tick", 0)) return transformValue(keys.getLast());
        for (int i = 0; i < keys.size() - 1; i++) {
            JsonObject a = keys.get(i), b = keys.get(i + 1);
            double ta = number(a, "tick", 0), tb = number(b, "tick", ta);
            if (tick < ta || tick > tb) continue;
            JsonObject av = transformValue(a), bv = transformValue(b);
            if (av == null) return bv;
            if (bv == null) return av;
            double t = tb <= ta ? 1 : (tick - ta) / (tb - ta);
            JsonObject out = identityTransform();
            out.add("translation", vec(lerp(readVec(av.getAsJsonObject("translation"), Vec3d.ZERO), readVec(bv.getAsJsonObject("translation"), Vec3d.ZERO), t)));
            out.add("rotationDegrees", vec(lerp(readVec(av.getAsJsonObject("rotationDegrees"), Vec3d.ZERO), readVec(bv.getAsJsonObject("rotationDegrees"), Vec3d.ZERO), t)));
            out.add("scale", vec(lerp(readVec(av.getAsJsonObject("scale"), new Vec3d(1,1,1)), readVec(bv.getAsJsonObject("scale"), new Vec3d(1,1,1)), t)));
            return out;
        }
        return transformValue(keys.getFirst());
    }

    private static JsonObject sampleVec3TrackValue(JsonObject track, double tick) {
        if (track == null || !track.has("keys") || !track.get("keys").isJsonArray()) return null;
        ArrayList<JsonObject> keys = timedObjects(track.getAsJsonArray("keys"));
        if (keys.isEmpty()) return null;
        if (tick <= number(keys.getFirst(), "tick", 0)) return vecValue(keys.getFirst());
        if (tick >= number(keys.getLast(), "tick", 0)) return vecValue(keys.getLast());
        for (int i = 0; i < keys.size() - 1; i++) {
            JsonObject a = keys.get(i), b = keys.get(i + 1);
            double ta = number(a, "tick", 0), tb = number(b, "tick", ta);
            if (tick < ta || tick > tb) continue;
            JsonObject av = vecValue(a), bv = vecValue(b);
            if (av == null) return bv;
            if (bv == null) return av;
            double t = tb <= ta ? 1 : (tick - ta) / (tb - ta);
            return vec(lerp(readVec(av, Vec3d.ZERO), readVec(bv, Vec3d.ZERO), t));
        }
        return vecValue(keys.getFirst());
    }

    private static ArrayList<JsonObject> timedObjects(JsonArray array) {
        ArrayList<JsonObject> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) if (raw.isJsonObject()) keys.add(raw.getAsJsonObject());
        keys.sort(Comparator.comparingDouble(k -> number(k, "tick", 0)));
        return keys;
    }

    private static JsonObject transformValue(JsonObject key) {
        if (key == null || !key.has("value") || !key.get("value").isJsonObject()) return null;
        return normalizeTransform(key.getAsJsonObject("value").deepCopy());
    }

    private static JsonObject vecValue(JsonObject key) {
        if (key == null || !key.has("value") || !key.get("value").isJsonObject()) return null;
        JsonObject value = key.getAsJsonObject("value");
        return isVec(value) ? value.deepCopy() : null;
    }

    private static JsonObject normalizeTransform(JsonObject value) {
        ensureVec(value, "translation", Vec3d.ZERO);
        ensureVec(value, "rotationDegrees", Vec3d.ZERO);
        ensureVec(value, "scale", new Vec3d(1, 1, 1));
        return value;
    }

    private static JsonObject ensureTimedObject(JsonArray array, double tick, String vectorName, Vec3d fallback) {
        if (array == null) return null;
        for (JsonElement raw : array) {
            if (raw.isJsonObject() && Math.abs(number(raw.getAsJsonObject(), "tick", -1e30) - tick) < 0.001) return raw.getAsJsonObject();
        }
        JsonObject source = nearestTimedObject(array, tick);
        JsonObject key = source == null ? new JsonObject() : source.deepCopy();
        key.addProperty("tick", tick);
        if (!key.has("easing")) key.addProperty("easing", "LINEAR");
        if (!key.has(vectorName) || !key.get(vectorName).isJsonObject()) key.add(vectorName, vec(fallback));
        array.add(key);
        sortTimedArray(array);
        return key;
    }

    private static JsonObject nearestTimedObject(JsonArray array, double tick) {
        if (array == null || array.isEmpty()) return null;
        JsonObject best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (JsonElement raw : array) {
            if (!raw.isJsonObject()) continue;
            double distance = Math.abs(number(raw.getAsJsonObject(), "tick", 0) - tick);
            if (distance < bestDistance) { bestDistance = distance; best = raw.getAsJsonObject(); }
        }
        return best;
    }

    private static void sortTimedArray(JsonArray array) {
        ArrayList<JsonObject> values = new ArrayList<>();
        for (JsonElement raw : array) if (raw.isJsonObject()) values.add(raw.getAsJsonObject());
        if (values.size() != array.size()) return;
        values.sort(Comparator.comparingDouble(v -> number(v, "tick", 0)));
        while (!array.isEmpty()) array.remove(array.size() - 1);
        values.forEach(array::add);
    }

    private static JsonObject findTrack(JsonElement value, String kind) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (kind.equals(string(object, "$kind", ""))) return object;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonObject found = findTrack(entry.getValue(), kind);
                if (found != null) return found;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject found = findTrack(child, kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject findAdvancedTransform(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String type = string(object, "$type", "");
            if (type.endsWith("AdvancedTransformTrack") || hasAdvancedChannels(object)) return object;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonObject found = findAdvancedTransform(entry.getValue());
                if (found != null) return found;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject found = findAdvancedTransform(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasAdvancedChannels(JsonObject object) {
        return isVec3Track(object.get("translation")) && isVec3Track(object.get("rotationDegrees"))
                && isVec3Track(object.get("scale"));
    }

    private static JsonObject advancedChannel(JsonObject advanced, String name) {
        if (advanced == null || !advanced.has(name) || !advanced.get(name).isJsonObject()) return null;
        JsonObject channel = advanced.getAsJsonObject(name);
        return "Vec3Track".equals(string(channel, "$kind", "")) ? channel : null;
    }

    private static boolean isVec3Track(JsonElement value) {
        return value != null && value.isJsonObject() && "Vec3Track".equals(string(value.getAsJsonObject(), "$kind", ""));
    }

    private static JsonObject findVec3TrackByNames(JsonObject root, String... candidates) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            JsonElement value = entry.getValue();
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            if ("Vec3Track".equals(string(object, "$kind", "")) && matchesAnyName(name, candidates)) return object;
            JsonObject nested = findVec3TrackByNames(object, candidates);
            if (nested != null) return nested;
        }
        return null;
    }

    private static boolean matchesAnyName(String value, String... candidates) {
        for (String candidate : candidates) {
            String needle = candidate.toLowerCase(Locale.ROOT);
            if (value.equals(needle) || value.contains(needle)) return true;
        }
        return false;
    }

    private static NamedVector findPreferredVector(JsonObject root, String... names) {
        for (String name : names) {
            JsonObject direct = directVec(root, name);
            if (direct != null) return new NamedVector(name, direct);
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                NamedVector nested = findPreferredVector(entry.getValue().getAsJsonObject(), names);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static JsonObject findNamedVector(JsonObject root, String... names) {
        NamedVector found = findPreferredVector(root, names);
        return found == null ? null : found.value;
    }

    private static Vec3d findAnyVector(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (isVec(object)) return readVec(object, Vec3d.ZERO);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Vec3d found = findAnyVector(entry.getValue());
                if (found != null) return found;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                Vec3d found = findAnyVector(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject findBlockState(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("block") && object.get("block").isJsonPrimitive()) return object;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonObject found = findBlockState(entry.getValue());
                if (found != null) return found;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject found = findBlockState(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject directVec(JsonObject root, String name) {
        if (!root.has(name) || !root.get(name).isJsonObject()) return null;
        JsonObject object = root.getAsJsonObject(name);
        return isVec(object) ? object : null;
    }

    private static boolean isVec(JsonObject object) {
        return object != null && object.has("x") && object.has("y") && object.has("z")
                && object.get("x").isJsonPrimitive() && object.get("y").isJsonPrimitive() && object.get("z").isJsonPrimitive();
    }

    private static JsonObject ensureVec(JsonObject parent, String name, Vec3d fallback) {
        JsonObject current = directVec(parent, name);
        if (current != null) return current;
        JsonObject replacement = vec(fallback);
        parent.add(name, replacement);
        return replacement;
    }

    private static JsonArray ensureArray(JsonObject object, String name) {
        if (object.has(name) && object.get(name).isJsonArray()) return object.getAsJsonArray(name);
        JsonArray array = new JsonArray(); object.add(name, array); return array;
    }

    private static JsonObject identityTransform() {
        JsonObject value = new JsonObject();
        value.add("translation", vec(Vec3d.ZERO));
        value.add("rotationDegrees", vec(Vec3d.ZERO));
        value.add("scale", vec(new Vec3d(1, 1, 1)));
        return value;
    }

    private static void appendTransformPath(List<Vec3d> out, JsonArray keys, Vec3d base) {
        for (JsonElement raw : keys) {
            if (!raw.isJsonObject()) continue;
            JsonObject value = transformValue(raw.getAsJsonObject());
            if (value != null && value.has("translation") && value.get("translation").isJsonObject())
                out.add(base.add(readVec(value.getAsJsonObject("translation"), Vec3d.ZERO)));
        }
    }

    private static void appendVec3TrackPath(List<Vec3d> out, JsonArray keys, Vec3d base) {
        for (JsonElement raw : keys) {
            if (!raw.isJsonObject()) continue;
            JsonObject value = vecValue(raw.getAsJsonObject());
            if (value != null) out.add(base.add(readVec(value, Vec3d.ZERO)));
        }
    }

    private static JsonObject vec(Vec3d value) {
        JsonObject object = new JsonObject();
        object.addProperty("x", value.x); object.addProperty("y", value.y); object.addProperty("z", value.z);
        return object;
    }

    private static Vec3d readVec(JsonObject object, Vec3d fallback) {
        if (!isVec(object)) return fallback;
        try { return new Vec3d(object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static void writeVec(JsonObject object, Vec3d value) {
        object.addProperty("x", value.x); object.addProperty("y", value.y); object.addProperty("z", value.z);
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t, a.z + (b.z-a.z)*t);
    }

    private static double number(JsonObject object, String name, double fallback) {
        try { return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String string(JsonObject object, String name, String fallback) {
        try { return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
