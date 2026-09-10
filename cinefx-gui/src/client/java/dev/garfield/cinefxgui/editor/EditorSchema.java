package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/** Small reflection helper for list/record editing. It never executes CineFX internals. */
public final class EditorSchema {
    private EditorSchema() { }

    public static Type root(EditorModel.Element element) {
        try { return Class.forName(element.apiClass); }
        catch (Exception ignored) { return Object.class; }
    }

    public static Type field(Type parent, String name) {
        Class<?> raw = raw(parent);
        if (raw == null || !raw.isRecord()) return Object.class;
        for (RecordComponent component : raw.getRecordComponents()) {
            if (component.getName().equals(name)) return component.getGenericType();
        }
        return Object.class;
    }

    public static Type item(Type list) {
        if (list instanceof ParameterizedType p && p.getActualTypeArguments().length > 0) return p.getActualTypeArguments()[0];
        return Object.class;
    }

    public static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    public static JsonElement defaultValue(Type type, String name, double duration) {
        Class<?> c = raw(type);
        if (c == null) return new JsonPrimitive("");
        if (c == String.class) return new JsonPrimitive("");
        if (c == boolean.class || c == Boolean.class) return new JsonPrimitive(false);
        if (Number.class.isAssignableFrom(c) || c.isPrimitive() && c != boolean.class && c != char.class)
            return new JsonPrimitive("endTick".equals(name) ? Math.max(1, duration) : 0);
        if (c == Identifier.class) return new JsonPrimitive("");
        if (c == Vec3d.class || c == BlockPos.class) return vec(0, 0, 0);
        if (c.isEnum()) {
            Object[] values = c.getEnumConstants();
            return values.length == 0 ? JsonNull.INSTANCE : new JsonPrimitive(((Enum<?>) values[0]).name());
        }
        if (c == List.class || c == java.util.Collection.class || c == Iterable.class) return new JsonArray();
        if (c == Map.class) return new JsonObject();
        if (c.isRecord()) {
            JsonObject o = new JsonObject();
            o.addProperty("$type", c.getName());
            for (RecordComponent component : c.getRecordComponents()) {
                if ("key".equals(component.getName())) o.addProperty("key", "element");
                else if ("startTick".equals(component.getName())) o.addProperty("startTick", 0);
                else if ("endTick".equals(component.getName())) o.addProperty("endTick", Math.max(1, duration));
                else o.add(component.getName(), defaultValue(component.getGenericType(), component.getName(), duration));
            }
            return o;
        }
        return JsonNull.INSTANCE;
    }

    /** Inserts a key at the exact currently rendered value instead of copying the last key. */
    public static JsonObject duplicateTrackKey(JsonObject track, double tick) {
        return AnimationJson.duplicateTrackKey(track, tick);
    }

    /** Inserts a path point sampled from LINEAR/CATMULL_ROM/BEZIER path interpolation. */
    public static JsonObject duplicatePathPoint(JsonObject path, double tick) {
        return AnimationJson.duplicatePathPoint(path, tick);
    }

    public static String cycleEnum(Type type, String current, int direction) {
        Class<?> c = raw(type);
        if (c == null || !c.isEnum()) return current;
        Object[] values = c.getEnumConstants();
        int index = 0;
        for (int i = 0; i < values.length; i++) if (((Enum<?>) values[i]).name().equalsIgnoreCase(current)) index = i;
        index = Math.floorMod(index + (direction >= 0 ? 1 : -1), values.length);
        return ((Enum<?>) values[index]).name();
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x); o.addProperty("y", y); o.addProperty("z", z);
        return o;
    }
}
