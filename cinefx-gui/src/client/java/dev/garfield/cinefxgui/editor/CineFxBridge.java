package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransformTrack;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.ConflictPolicy;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.Motions;
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.api.Vec3Track;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The only place where CineFX GUI translates editor JSON into CineFX API objects.
 * The editor deliberately owns no copy of CineFX model classes.
 */
public final class CineFxBridge {
    public record ElementType(String category, String displayName, Class<? extends SceneElement> apiClass) { }
    public record BuildError(String editorId, String key, String message) { }
    public record BuildResult(SceneDefinition scene, List<BuildError> errors) {
        public boolean clean() { return errors.isEmpty(); }
    }

    private static final List<ElementType> ELEMENT_TYPES = discoverTypes();

    private CineFxBridge() { }

    public static List<ElementType> elementTypes() { return ELEMENT_TYPES; }

    public static EditorModel.Element newDraft(ElementType type, double durationTicks, int serial) {
        EditorModel.Element element = new EditorModel.Element();
        element.apiClass = type.apiClass().getName();
        element.label = type.displayName();
        element.lane = serial;
        element.data = defaultRecord(type.apiClass(), durationTicks, serial);
        element.data.addProperty("$type", type.apiClass().getName());
        return element;
    }

    public static EditorModel.Project importScene(SceneDefinition scene) {
        EditorModel.Project project = new EditorModel.Project();
        project.name = scene.id().getPath();
        project.sceneId = scene.id().toString();
        project.durationTicks = scene.durationTicks();
        project.priority = scene.priority();
        project.looping = scene.looping();
        project.metadata = new LinkedHashMap<>(scene.metadata());
        project.elements = new ArrayList<>();
        int lane = 0;
        for (SceneElement raw : scene.elements()) {
            EditorModel.Element element = new EditorModel.Element();
            element.apiClass = raw.getClass().getName();
            element.label = pretty(raw.getClass().getSimpleName());
            element.lane = lane++;
            JsonElement encoded = encode(raw, scene.durationTicks());
            element.data = encoded != null && encoded.isJsonObject() ? encoded.getAsJsonObject() : new JsonObject();
            project.elements.add(element);
        }
        project.dirty = true;
        return project;
    }

    public static BuildResult build(EditorModel.Project project, Identifier sceneId, boolean includeAudio) {
        ArrayList<SceneElement> built = new ArrayList<>();
        ArrayList<BuildError> errors = new ArrayList<>();
        for (EditorModel.Element element : project.elements) {
            if (element == null || !element.enabled) continue;
            if (!includeAudio && isAudioElement(element.apiClass)) continue;
            try {
                Class<?> rawClass = Class.forName(element.apiClass);
                if (!SceneElement.class.isAssignableFrom(rawClass)) {
                    throw new IllegalArgumentException("Class is not a CineFX SceneElement");
                }
                Object decoded = decode(element.data, rawClass);
                built.add((SceneElement)decoded);
            } catch (Throwable throwable) {
                Throwable root = throwable;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                String message = root.getMessage();
                if (message == null || message.isBlank()) message = root.getClass().getSimpleName();
                errors.add(new BuildError(element.editorId, element.key(), message));
            }
        }
        SceneDefinition scene = new SceneDefinition(
                sceneId,
                Math.max(1.0, project.durationTicks),
                project.priority,
                project.looping,
                built,
                project.metadata == null ? Map.of() : project.metadata);
        return new BuildResult(scene, List.copyOf(errors));
    }

    public static JsonElement encode(Object value, double durationTicks) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String string) return new JsonPrimitive(string);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Character character) return new JsonPrimitive(character);
        if (value instanceof Identifier id) return new JsonPrimitive(id.toString());
        if (value instanceof Enum<?> enumeration) return new JsonPrimitive(enumeration.name());
        if (value instanceof Vec3d vec) return vec(vec.x, vec.y, vec.z);
        if (value instanceof BlockPos pos) return blockPos(pos.getX(), pos.getY(), pos.getZ());
        if (value instanceof BlockState state) return encodeBlockState(state);
        if (value instanceof ScalarTrack track) return encodeScalarTrack(track, durationTicks);
        if (value instanceof ColorTrack track) return encodeColorTrack(track, durationTicks);
        if (value instanceof Vec3Track track) return encodeVec3Track(track, durationTicks);
        if (value instanceof TransformTrack track) return encodeTransformTrack(track, durationTicks);
        if (value instanceof PathTrack track) return encodePathTrack(track, durationTicks);
        if (value instanceof MotionCurve curve) return encodeMotionCurve(curve, durationTicks);
        if (value instanceof Map<?, ?> map) {
            JsonObject out = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) out.add(String.valueOf(entry.getKey()), encode(entry.getValue(), durationTicks));
            }
            return out;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray out = new JsonArray();
            for (Object item : iterable) out.add(encode(item, durationTicks));
            return out;
        }
        Class<?> type = value.getClass();
        if (type.isRecord()) {
            JsonObject out = new JsonObject();
            out.addProperty("$type", type.getName());
            for (RecordComponent component : type.getRecordComponents()) {
                try {
                    out.add(component.getName(), encode(component.getAccessor().invoke(value), durationTicks));
                } catch (ReflectiveOperationException exception) {
                    out.add(component.getName(), JsonNull.INSTANCE);
                }
            }
            return out;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    public static Object decode(JsonElement json, Type targetType) throws ReflectiveOperationException {
        if (json == null || json.isJsonNull()) {
            if (targetType instanceof Class<?> primitive && primitive.isPrimitive()) return primitiveDefault(primitive);
            return null;
        }

        if (targetType instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw == List.class || raw == java.util.Collection.class || raw == Iterable.class) {
                Type itemType = parameterized.getActualTypeArguments()[0];
                ArrayList<Object> values = new ArrayList<>();
                if (json.isJsonArray()) for (JsonElement item : json.getAsJsonArray()) values.add(decode(item, itemType));
                return List.copyOf(values);
            }
            if (raw == Map.class) {
                Type keyType = parameterized.getActualTypeArguments()[0];
                Type valueType = parameterized.getActualTypeArguments()[1];
                LinkedHashMap<Object, Object> values = new LinkedHashMap<>();
                if (json.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                        if (entry.getKey().startsWith("$")) continue;
                        values.put(decodeMapKey(entry.getKey(), keyType), decode(entry.getValue(), valueType));
                    }
                }
                return Map.copyOf(values);
            }
            if (raw instanceof Class<?> rawClass) return decode(json, rawClass);
        }

        if (targetType instanceof TypeVariable<?>) return jsonToLooseValue(json);
        if (!(targetType instanceof Class<?> type)) return jsonToLooseValue(json);

        if (type == String.class) return json.getAsString();
        if (type == boolean.class || type == Boolean.class) return json.getAsBoolean();
        if (type == byte.class || type == Byte.class) return json.getAsByte();
        if (type == short.class || type == Short.class) return json.getAsShort();
        if (type == int.class || type == Integer.class) return json.getAsInt();
        if (type == long.class || type == Long.class) return json.getAsLong();
        if (type == float.class || type == Float.class) return json.getAsFloat();
        if (type == double.class || type == Double.class) return json.getAsDouble();
        if (type == char.class || type == Character.class) return json.getAsString().isEmpty() ? '\0' : json.getAsString().charAt(0);
        if (type == Identifier.class) {
            String value = json.getAsString().trim();
            return value.isEmpty() ? null : Identifier.tryParse(value);
        }
        if (type == Vec3d.class) return decodeVec(json);
        if (type == BlockPos.class) {
            JsonObject object = json.getAsJsonObject();
            return new BlockPos(intValue(object, "x", 0), intValue(object, "y", 0), intValue(object, "z", 0));
        }
        if (type == BlockState.class) return decodeBlockState(json);
        if (type == ScalarTrack.class) return decodeScalarTrack(json);
        if (type == ColorTrack.class) return decodeColorTrack(json);
        if (type == Vec3Track.class) return decodeVec3Track(json);
        if (type == TransformTrack.class) return decodeTransformTrack(json);
        if (type == PathTrack.class) return decodePathTrack(json);
        if (type == MotionCurve.class) return decodeMotionCurve(json);
        if (type.isEnum()) return decodeEnum(type, json.getAsString());

        JsonObject object = json.getAsJsonObject();
        if (object.has("$type") && !object.get("$type").isJsonNull()) {
            try {
                Class<?> actual = Class.forName(object.get("$type").getAsString());
                if (type.isAssignableFrom(actual)) type = actual;
            } catch (ClassNotFoundException ignored) { }
        }
        if (!type.isRecord()) throw new IllegalArgumentException("Unsupported CineFX value type: " + type.getName());

        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            parameterTypes[i] = component.getType();
            JsonElement child = object.get(component.getName());
            values[i] = decode(child == null ? JsonNull.INSTANCE : child, component.getGenericType());
        }
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    public static Vec3d tryResolveEditorPosition(EditorModel.Element element) {
        if (element == null || element.data == null) return null;
        String[] preferred = {"baseOffset", "centerOffset", "fromOffset", "offset", "lookAtOffset", "targetOffset"};
        for (String name : preferred) {
            JsonElement value = element.data.get(name);
            if (value != null && value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("x") && object.has("y") && object.has("z")) return decodeVec(object);
            }
        }
        return null;
    }

    private static List<ElementType> discoverTypes() {
        ArrayList<ElementType> result = new ArrayList<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        discoverNested(SceneElement.class, "Basic", result, seen);
        discoverNested(EventElement.class, "Event", result, seen);
        discoverNested(ComplexElement.class, "Complex", result, seen);
        discoverNested(AdvancedEventElement.class, "Advanced", result, seen);
        discoverNested(UltraEventElement.class, "Ultra", result, seen);
        addType(SceneLight.class, "Lighting", result, seen);
        result.sort(Comparator.comparing(ElementType::category).thenComparing(ElementType::displayName));
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static void discoverNested(Class<?> root, String category, List<ElementType> out, Set<Class<?>> seen) {
        for (Class<?> nested : root.getDeclaredClasses()) {
            if (nested.isRecord() && SceneElement.class.isAssignableFrom(nested) && seen.add(nested)) {
                out.add(new ElementType(category, pretty(nested.getSimpleName()), (Class<? extends SceneElement>)nested));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addType(Class<?> type, String category, List<ElementType> out, Set<Class<?>> seen) {
        if (type.isRecord() && SceneElement.class.isAssignableFrom(type) && seen.add(type)) {
            out.add(new ElementType(category, pretty(type.getSimpleName()), (Class<? extends SceneElement>)type));
        }
    }

    private static JsonObject defaultRecord(Class<?> type, double durationTicks, int serial) {
        JsonObject object = new JsonObject();
        object.addProperty("$type", type.getName());
        if (!type.isRecord()) return object;
        for (RecordComponent component : type.getRecordComponents()) {
            object.add(component.getName(), defaultValue(component.getGenericType(), component.getName(), durationTicks, serial));
        }
        return object;
    }

    private static JsonElement defaultValue(Type targetType, String name, double durationTicks, int serial) {
        if (targetType instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw == List.class || raw == java.util.Collection.class || raw == Iterable.class) return new JsonArray();
            if (raw == Map.class) return new JsonObject();
            if (raw instanceof Class<?> rawClass) return defaultValue(rawClass, name, durationTicks, serial);
        }
        if (!(targetType instanceof Class<?> type)) return JsonNull.INSTANCE;

        if (type == String.class) {
            if ("key".equals(name)) return new JsonPrimitive(slug(nameForType(serial)));
            if (name.toLowerCase(Locale.ROOT).contains("label")) return new JsonPrimitive("Marker " + (serial + 1));
            return new JsonPrimitive("");
        }
        if (type == boolean.class || type == Boolean.class) return new JsonPrimitive(false);
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            if (name.toLowerCase(Locale.ROOT).contains("segments")) return new JsonPrimitive(32);
            if (name.toLowerCase(Locale.ROOT).contains("count")) return new JsonPrimitive(1);
            if (name.toLowerCase(Locale.ROOT).contains("iterations")) return new JsonPrimitive(4);
            return new JsonPrimitive(0);
        }
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            if ("startTick".equals(name)) return new JsonPrimitive(0.0);
            if ("endTick".equals(name)) return new JsonPrimitive(Math.max(1.0, durationTicks));
            if (name.toLowerCase(Locale.ROOT).contains("cullDistance".toLowerCase(Locale.ROOT))) return new JsonPrimitive(512.0);
            if (name.toLowerCase(Locale.ROOT).contains("inflate")) return new JsonPrimitive(1.01);
            return new JsonPrimitive(0.0);
        }
        if (type == Identifier.class) return new JsonPrimitive(defaultIdentifier(name));
        if (type == Vec3d.class) return name.toLowerCase(Locale.ROOT).contains("scale") ? vec(1, 1, 1) : vec(0, 0, 0);
        if (type == BlockPos.class) return blockPos(0, 0, 0);
        if (type == BlockState.class) {
            JsonObject state = new JsonObject();
            state.addProperty("block", "minecraft:stone");
            state.add("properties", new JsonObject());
            return state;
        }
        if (type == ScalarTrack.class) return scalarTrackDefault(defaultScalar(name));
        if (type == ColorTrack.class) return colorTrackDefault(name.toLowerCase(Locale.ROOT).contains("tint") ? 0x00000000 : 0xFFFFFFFF);
        if (type == Vec3Track.class) return vec3TrackDefault(name.toLowerCase(Locale.ROOT).contains("rotation"), name.toLowerCase(Locale.ROOT).contains("scale"));
        if (type == TransformTrack.class) return transformTrackDefault();
        if (type == AdvancedTransformTrack.class) return defaultRecord(AdvancedTransformTrack.class, durationTicks, serial);
        if (type == PathTrack.class) return pathTrackDefault();
        if (type == MotionCurve.class) {
            JsonObject motion = new JsonObject();
            motion.addProperty("$kind", "MotionCurve");
            motion.addProperty("preset", "NONE");
            motion.add("parameters", new JsonObject());
            return motion;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (type == ConflictPolicy.class) return new JsonPrimitive(ConflictPolicy.REPLACE_LOWER.name());
            return constants.length == 0 ? JsonNull.INSTANCE : new JsonPrimitive(((Enum<?>)constants[0]).name());
        }
        if (type.isRecord()) return defaultRecord(type, durationTicks, serial);
        return JsonNull.INSTANCE;
    }

    private static JsonObject scalarTrackDefault(double value) {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "ScalarTrack");
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.addProperty("value", value);
        key.addProperty("easing", Easing.LINEAR.name());
        keys.add(key);
        track.add("keys", keys);
        return track;
    }

    private static JsonObject colorTrackDefault(int argb) {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "ColorTrack");
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.addProperty("value", colorString(argb));
        key.addProperty("easing", Easing.LINEAR.name());
        keys.add(key);
        track.add("keys", keys);
        return track;
    }

    private static JsonObject vec3TrackDefault(boolean angular, boolean scale) {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "Vec3Track");
        track.addProperty("angularDegrees", angular);
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.add("value", scale ? vec(1, 1, 1) : vec(0, 0, 0));
        key.addProperty("easing", Easing.LINEAR.name());
        keys.add(key);
        track.add("keys", keys);
        return track;
    }

    private static JsonObject transformTrackDefault() {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "TransformTrack");
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.add("value", encodeTransform(Transform.IDENTITY));
        key.addProperty("easing", Easing.LINEAR.name());
        keys.add(key);
        track.add("keys", keys);
        return track;
    }

    private static JsonObject pathTrackDefault() {
        JsonObject path = new JsonObject();
        path.addProperty("$kind", "PathTrack");
        path.addProperty("interpolation", PathTrack.Interpolation.LINEAR.name());
        JsonArray points = new JsonArray();
        points.add(pathPoint(0.0, new Vec3d(0, 0, 0)));
        points.add(pathPoint(20.0, new Vec3d(0, 0, 1)));
        path.add("points", points);
        return path;
    }

    private static JsonObject pathPoint(double tick, Vec3d position) {
        JsonObject point = new JsonObject();
        point.addProperty("tick", tick);
        point.add("position", vec(position.x, position.y, position.z));
        point.add("inHandle", JsonNull.INSTANCE);
        point.add("outHandle", JsonNull.INSTANCE);
        point.addProperty("easing", Easing.LINEAR.name());
        return point;
    }

    private static JsonObject encodeScalarTrack(ScalarTrack track, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "ScalarTrack");
        object.add("keys", encodeKeyframes(readTrackKeys(track), durationTicks, false));
        return object;
    }

    private static JsonObject encodeColorTrack(ColorTrack track, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "ColorTrack");
        JsonArray array = new JsonArray();
        for (Object raw : readTrackKeys(track)) {
            if (!(raw instanceof Keyframe<?> key)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("tick", key.tick());
            int color = key.value() instanceof Number number ? number.intValue() : 0xFFFFFFFF;
            item.addProperty("value", colorString(color));
            item.addProperty("easing", key.easingToNext().name());
            array.add(item);
        }
        if (array.isEmpty()) array.add(colorKey(0, 0xFFFFFFFF, Easing.LINEAR));
        object.add("keys", array);
        return object;
    }

    private static JsonObject encodeVec3Track(Vec3Track track, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "Vec3Track");
        object.addProperty("angularDegrees", track.angularDegrees());
        object.add("keys", encodeKeyframes(track.keyframes(), durationTicks, false));
        return object;
    }

    private static JsonObject encodeTransformTrack(TransformTrack track, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "TransformTrack");
        object.add("keys", encodeKeyframes(track.keyframes(), durationTicks, false));
        return object;
    }

    private static JsonArray encodeKeyframes(List<?> keys, double durationTicks, boolean color) {
        JsonArray array = new JsonArray();
        for (Object raw : keys) {
            if (!(raw instanceof Keyframe<?> key)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("tick", key.tick());
            item.add("value", encode(key.value(), durationTicks));
            item.addProperty("easing", key.easingToNext().name());
            array.add(item);
        }
        return array;
    }

    private static JsonObject encodePathTrack(PathTrack track, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "PathTrack");
        object.addProperty("interpolation", track.interpolation().name());
        JsonArray points = new JsonArray();
        for (PathTrack.Point point : track.points()) {
            JsonObject item = new JsonObject();
            item.addProperty("tick", point.tick());
            item.add("position", encode(point.position(), durationTicks));
            item.add("inHandle", encode(point.inHandle(), durationTicks));
            item.add("outHandle", encode(point.outHandle(), durationTicks));
            item.addProperty("easing", point.easingToNext().name());
            points.add(item);
        }
        object.add("points", points);
        return object;
    }

    private static JsonObject encodeMotionCurve(MotionCurve curve, double durationTicks) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "MotionCurve");
        double duration = Math.max(1.0, Math.min(durationTicks, 10000.0));
        Transform a = safeSample(curve, 0.0);
        Transform b = safeSample(curve, Math.min(duration, 20.0));
        Transform c = safeSample(curve, Math.min(duration, 80.0));
        if (Transform.IDENTITY.equals(a) && Transform.IDENTITY.equals(b) && Transform.IDENTITY.equals(c)) {
            object.addProperty("preset", "NONE");
            object.add("parameters", new JsonObject());
            return object;
        }
        object.addProperty("preset", "BAKED");
        JsonArray keys = new JsonArray();
        int samples = Math.max(2, Math.min(80, (int)Math.ceil(duration / 5.0) + 1));
        for (int i = 0; i < samples; i++) {
            double tick = duration * i / (samples - 1.0);
            JsonObject key = new JsonObject();
            key.addProperty("tick", tick);
            key.add("value", encodeTransform(safeSample(curve, tick)));
            keys.add(key);
        }
        object.add("keys", keys);
        return object;
    }

    private static Transform safeSample(MotionCurve curve, double tick) {
        try {
            Transform sample = curve.sample(tick, 1L);
            return sample == null ? Transform.IDENTITY : sample;
        } catch (RuntimeException ignored) {
            return Transform.IDENTITY;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> readTrackKeys(Object track) {
        try {
            Field field = track.getClass().getDeclaredField("keys");
            field.setAccessible(true);
            Object value = field.get(track);
            return value instanceof List<?> list ? list : List.of();
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ScalarTrack decodeScalarTrack(JsonElement json) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Double>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(doubleValue(key, "tick", 0.0), doubleValue(key, "value", 0.0), easing(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, 0.0));
        return ScalarTrack.of(keys.toArray(Keyframe[]::new));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ColorTrack decodeColorTrack(JsonElement json) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Integer>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            JsonElement value = key.get("value");
            keys.add(new Keyframe<>(doubleValue(key, "tick", 0.0), parseColor(value), easing(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, 0xFFFFFFFF));
        return ColorTrack.of(keys.toArray(Keyframe[]::new));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Vec3Track decodeVec3Track(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        JsonArray array = object.getAsJsonArray("keys");
        ArrayList<Keyframe<Vec3d>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(doubleValue(key, "tick", 0.0), decodeVec(key.get("value")), easing(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, Vec3d.ZERO));
        return object.has("angularDegrees") && object.get("angularDegrees").getAsBoolean()
                ? Vec3Track.angles(keys.toArray(Keyframe[]::new))
                : Vec3Track.of(keys.toArray(Keyframe[]::new));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TransformTrack decodeTransformTrack(JsonElement json) throws ReflectiveOperationException {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Transform>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            Transform transform = (Transform)decode(key.get("value"), Transform.class);
            keys.add(new Keyframe<>(doubleValue(key, "tick", 0.0), transform, easing(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, Transform.IDENTITY));
        return TransformTrack.of(keys.toArray(Keyframe[]::new));
    }

    private static PathTrack decodePathTrack(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        PathTrack.Interpolation interpolation = enumValue(PathTrack.Interpolation.class, stringValue(object, "interpolation", "LINEAR"), PathTrack.Interpolation.LINEAR);
        JsonArray array = object.getAsJsonArray("points");
        ArrayList<PathTrack.Point> points = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject point = raw.getAsJsonObject();
            points.add(new PathTrack.Point(
                    doubleValue(point, "tick", points.size() * 20.0),
                    decodeVec(point.get("position")),
                    point.has("inHandle") && !point.get("inHandle").isJsonNull() ? decodeVec(point.get("inHandle")) : null,
                    point.has("outHandle") && !point.get("outHandle").isJsonNull() ? decodeVec(point.get("outHandle")) : null,
                    easing(point)));
        }
        if (points.size() < 2) {
            points.clear();
            points.add(PathTrack.Point.at(0.0, Vec3d.ZERO));
            points.add(PathTrack.Point.at(20.0, new Vec3d(0, 0, 1)));
        }
        return new PathTrack(points, interpolation);
    }

    private static MotionCurve decodeMotionCurve(JsonElement json) throws ReflectiveOperationException {
        if (json == null || json.isJsonNull()) return MotionCurve.none();
        JsonObject object = json.getAsJsonObject();
        String preset = stringValue(object, "preset", "NONE").toUpperCase(Locale.ROOT);
        JsonObject p = object.has("parameters") && object.get("parameters").isJsonObject() ? object.getAsJsonObject("parameters") : new JsonObject();
        return switch (preset) {
            case "SPIN" -> Motions.spin(doubleValue(p, "x", 0), doubleValue(p, "y", 1), doubleValue(p, "z", 0));
            case "LEVITATE" -> Motions.levitate(doubleValue(p, "amplitude", 1), positive(p, "periodTicks", 40), doubleValue(p, "phaseRadians", 0));
            case "ORBIT" -> Motions.orbit(doubleValue(p, "radius", 3), positive(p, "periodTicks", 80), doubleValue(p, "verticalOffset", 0), boolValue(p, "faceTangent", true));
            case "GRAVITY" -> Motions.gravity(vectorParam(p, "launch", new Vec3d(0, 0.4, 0)), positive(p, "gravity", 0.04));
            case "BALLISTIC" -> Motions.ballistic(
                    vectorParam(p, "velocity", Vec3d.ZERO),
                    vectorParam(p, "acceleration", Vec3d.ZERO),
                    vectorParam(p, "angularVelocity", Vec3d.ZERO),
                    vectorParam(p, "angularAcceleration", Vec3d.ZERO));
            case "JITTER" -> Motions.jitter(doubleValue(p, "amplitude", 0.05), positive(p, "frequency", 0.25));
            case "BAKED" -> decodeBakedMotion(object);
            default -> MotionCurve.none();
        };
    }

    private static MotionCurve decodeBakedMotion(JsonObject object) throws ReflectiveOperationException {
        JsonArray array = object.getAsJsonArray("keys");
        ArrayList<Keyframe<Transform>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(Keyframe.at(doubleValue(key, "tick", 0.0), (Transform)decode(key.get("value"), Transform.class)));
        }
        if (keys.isEmpty()) return MotionCurve.none();
        keys.sort(Comparator.comparingDouble(Keyframe::tick));
        List<Keyframe<Transform>> fixed = List.copyOf(keys);
        return (tick, seed) -> {
            if (tick <= fixed.getFirst().tick()) return fixed.getFirst().value();
            if (tick >= fixed.getLast().tick()) return fixed.getLast().value();
            int low = 0;
            int high = fixed.size() - 1;
            while (low + 1 < high) {
                int mid = (low + high) >>> 1;
                if (fixed.get(mid).tick() <= tick) low = mid; else high = mid;
            }
            Keyframe<Transform> a = fixed.get(low);
            Keyframe<Transform> b = fixed.get(high);
            double t = (tick - a.tick()) / Math.max(1.0e-9, b.tick() - a.tick());
            return Transform.lerp(a.value(), b.value(), t);
        };
    }

    private static JsonObject encodeTransform(Transform transform) {
        JsonObject object = new JsonObject();
        object.addProperty("$type", Transform.class.getName());
        object.add("translation", vec(transform.translation().x, transform.translation().y, transform.translation().z));
        object.add("rotationDegrees", vec(transform.rotationDegrees().x, transform.rotationDegrees().y, transform.rotationDegrees().z));
        object.add("scale", vec(transform.scale().x, transform.scale().y, transform.scale().z));
        return object;
    }

    private static JsonObject encodeBlockState(BlockState state) {
        JsonObject object = new JsonObject();
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        object.addProperty("block", id == null ? "minecraft:air" : id.toString());
        JsonObject properties = new JsonObject();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            properties.addProperty(entry.getKey().getName(), propertyName(entry.getKey(), entry.getValue()));
        }
        object.add("properties", properties);
        return object;
    }

    private static BlockState decodeBlockState(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        Identifier id = Identifier.tryParse(stringValue(object, "block", "minecraft:stone"));
        Block block = id == null ? null : Registries.BLOCK.get(id);
        if (block == null) block = Registries.BLOCK.get(Identifier.of("minecraft", "stone"));
        BlockState state = block == null ? null : block.getDefaultState();
        if (state == null) throw new IllegalArgumentException("Unknown block state");
        JsonObject properties = object.has("properties") && object.get("properties").isJsonObject() ? object.getAsJsonObject("properties") : null;
        if (properties != null) {
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                Property<?> property = block.getStateManager().getProperty(entry.getKey());
                if (property != null) state = applyProperty(state, property, entry.getValue().getAsString());
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) {
        return property.name(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String value) {
        Optional parsed = property.parse(value);
        return parsed.isPresent() ? state.with(property, (Comparable)parsed.get()) : state;
    }

    private static Object decodeMapKey(String key, Type type) {
        if (type == String.class || type instanceof TypeVariable<?>) return key;
        if (type == Identifier.class) return Identifier.tryParse(key);
        if (type instanceof Class<?> clazz && clazz.isEnum()) return decodeEnum(clazz, key);
        return key;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object decodeEnum(Class<?> type, String value) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum)constant).name().equalsIgnoreCase(value)) return constant;
        }
        Object[] constants = type.getEnumConstants();
        return constants.length == 0 ? null : constants[0];
    }

    private static Object jsonToLooseValue(JsonElement json) {
        if (json == null || json.isJsonNull()) return null;
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsDouble();
            return primitive.getAsString();
        }
        if (json.isJsonArray()) {
            ArrayList<Object> list = new ArrayList<>();
            for (JsonElement value : json.getAsJsonArray()) list.add(jsonToLooseValue(value));
            return List.copyOf(list);
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) map.put(entry.getKey(), jsonToLooseValue(entry.getValue()));
        return Map.copyOf(map);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte)0;
        if (type == short.class) return (short)0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        return object;
    }

    private static JsonObject blockPos(int x, int y, int z) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        return object;
    }

    private static Vec3d decodeVec(JsonElement json) {
        if (json == null || json.isJsonNull()) return Vec3d.ZERO;
        if (json.isJsonArray()) {
            JsonArray a = json.getAsJsonArray();
            return new Vec3d(a.size() > 0 ? a.get(0).getAsDouble() : 0, a.size() > 1 ? a.get(1).getAsDouble() : 0, a.size() > 2 ? a.get(2).getAsDouble() : 0);
        }
        JsonObject object = json.getAsJsonObject();
        return new Vec3d(doubleValue(object, "x", 0), doubleValue(object, "y", 0), doubleValue(object, "z", 0));
    }

    private static Easing easing(JsonObject object) {
        return enumValue(Easing.class, stringValue(object, "easing", Easing.LINEAR.name()), Easing.LINEAR);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return fallback; }
    }

    private static JsonObject colorKey(double tick, int color, Easing easing) {
        JsonObject key = new JsonObject();
        key.addProperty("tick", tick);
        key.addProperty("value", colorString(color));
        key.addProperty("easing", easing.name());
        return key;
    }

    private static int parseColor(JsonElement value) {
        if (value == null || value.isJsonNull()) return 0xFFFFFFFF;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
        String text = value.getAsString().trim();
        try {
            if (text.startsWith("#")) return (int)Long.parseLong(text.substring(1), 16);
            if (text.startsWith("0x") || text.startsWith("0X")) return (int)Long.parseLong(text.substring(2), 16);
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0xFFFFFFFF;
        }
    }

    private static String colorString(int color) { return String.format(Locale.ROOT, "#%08X", color); }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        try { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int intValue(JsonObject object, String name, int fallback) {
        try { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean boolValue(JsonObject object, String name, boolean fallback) {
        try { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String stringValue(JsonObject object, String name, String fallback) {
        try { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double positive(JsonObject object, String name, double fallback) {
        double value = doubleValue(object, name, fallback);
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private static Vec3d vectorParam(JsonObject object, String name, Vec3d fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? decodeVec(value) : fallback;
    }

    private static double defaultScalar(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("scale") || n.contains("opacity") || n.contains("intensity") || n.contains("volume") || n.contains("pitch") || n.contains("weight") || n.contains("saturation") || n.contains("contrast") || n.contains("global")) return 1.0;
        if (n.contains("radius")) return 4.0;
        if (n.contains("fov")) return -1.0;
        if (n.contains("lifetime")) return 40.0;
        return 0.0;
    }

    private static String defaultIdentifier(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("particle")) return "minecraft:cloud";
        if (n.contains("sound")) return "minecraft:entity.experience_orb.pickup";
        if (n.contains("font")) return "minecraft:default";
        if (n.contains("material")) return "minecraft:stone";
        return "";
    }

    private static String nameForType(int serial) { return "element_" + (serial + 1); }
    private static String slug(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_"); }
    private static boolean isAudioElement(String className) { return className != null && className.toLowerCase(Locale.ROOT).contains("audio"); }

    public static String pretty(String simple) {
        if (simple == null || simple.isBlank()) return "Element";
        return simple.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }
}
