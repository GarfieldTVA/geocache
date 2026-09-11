package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.Easing;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Easing-aware sampling helpers for the editor-side JSON model.
 * Keeping this here avoids introducing tiny discontinuities when an editor action inserts
 * a keyframe between two existing CineFX keys.
 */
public final class AnimationJson {
    private AnimationJson() { }

    public static JsonObject duplicateTrackKey(JsonObject track, double tick) {
        JsonObject key = new JsonObject();
        key.addProperty("tick", tick);
        key.addProperty("easing", Easing.LINEAR.name());
        String kind = string(track, "$kind", "");
        JsonElement sampled = switch (kind) {
            case "ScalarTrack" -> sampleScalar(track, tick);
            case "ColorTrack" -> sampleColor(track, tick);
            case "Vec3Track" -> sampleVec3(track, tick);
            case "TransformTrack" -> sampleTransform(track, tick);
            default -> sampleLoose(track, tick);
        };
        key.add("value", sampled == null ? new JsonPrimitive(0) : sampled);
        return key;
    }

    public static JsonObject duplicatePathPoint(JsonObject path, double tick) {
        JsonObject point = new JsonObject();
        point.addProperty("tick", tick);
        point.add("position", samplePathPosition(path, tick));
        point.add("inHandle", JsonNull.INSTANCE);
        point.add("outHandle", JsonNull.INSTANCE);
        point.addProperty("easing", Easing.LINEAR.name());
        return point;
    }

    public static JsonObject sampleVec3(JsonObject track, double tick) {
        List<JsonObject> keys = sorted(track, "keys");
        if (keys.isEmpty()) return null;
        JsonObject a = floor(keys, tick), b = ceil(keys, tick);
        JsonObject av = object(a.get("value")), bv = object(b.get("value"));
        if (av == null) return bv == null ? null : bv.deepCopy();
        if (bv == null || a == b) return av.deepCopy();
        double t = easedFraction(a, b, tick);
        boolean angular = bool(track, "angularDegrees", false);
        Vec3d va = vec(av, Vec3d.ZERO), vb = vec(bv, Vec3d.ZERO);
        Vec3d out = angular ? lerpAngles(va, vb, t) : lerp(va, vb, t);
        return vec(out);
    }

    public static JsonObject sampleTransform(JsonObject track, double tick) {
        List<JsonObject> keys = sorted(track, "keys");
        if (keys.isEmpty()) return null;
        JsonObject a = floor(keys, tick), b = ceil(keys, tick);
        JsonObject av = object(a.get("value")), bv = object(b.get("value"));
        if (av == null) return bv == null ? null : normalizeTransform(bv.deepCopy());
        if (bv == null || a == b) return normalizeTransform(av.deepCopy());
        av = normalizeTransform(av.deepCopy());
        bv = normalizeTransform(bv.deepCopy());
        double t = easedFraction(a, b, tick);
        JsonObject out = av.deepCopy();
        out.add("translation", vec(lerp(vec(av.getAsJsonObject("translation"), Vec3d.ZERO), vec(bv.getAsJsonObject("translation"), Vec3d.ZERO), t)));
        // Legacy TransformTrack delegates to Transform.lerp(), which interpolates Euler components
        // directly. Keep editor-side insertion/preview identical; shortest-angle semantics belong
        // only to Vec3Track.angularDegrees (used by AdvancedTransformTrack rotation channels).
        out.add("rotationDegrees", vec(lerp(vec(av.getAsJsonObject("rotationDegrees"), Vec3d.ZERO), vec(bv.getAsJsonObject("rotationDegrees"), Vec3d.ZERO), t)));
        out.add("scale", vec(lerp(vec(av.getAsJsonObject("scale"), new Vec3d(1, 1, 1)), vec(bv.getAsJsonObject("scale"), new Vec3d(1, 1, 1)), t)));
        return out;
    }

    public static JsonElement sampleScalar(JsonObject track, double tick) {
        List<JsonObject> keys = sorted(track, "keys");
        if (keys.isEmpty()) return new JsonPrimitive(0.0);
        JsonObject a = floor(keys, tick), b = ceil(keys, tick);
        double av = number(a, "value", 0), bv = number(b, "value", av);
        if (a == b) return new JsonPrimitive(av);
        return new JsonPrimitive(av + (bv - av) * easedFraction(a, b, tick));
    }

    public static JsonElement sampleColor(JsonObject track, double tick) {
        List<JsonObject> keys = sorted(track, "keys");
        if (keys.isEmpty()) return new JsonPrimitive("#FFFFFFFF");
        JsonObject a = floor(keys, tick), b = ceil(keys, tick);
        int ca = parseColor(a.get("value")), cb = parseColor(b.get("value"));
        if (a == b) return new JsonPrimitive(color(ca));
        double t = easedFraction(a, b, tick);
        int aa = (ca >>> 24) & 255, ar = (ca >>> 16) & 255, ag = (ca >>> 8) & 255, ab = ca & 255;
        int ba = (cb >>> 24) & 255, br = (cb >>> 16) & 255, bg = (cb >>> 8) & 255, bb = cb & 255;
        int out = (lerp8(aa, ba, t) << 24) | (lerp8(ar, br, t) << 16) | (lerp8(ag, bg, t) << 8) | lerp8(ab, bb, t);
        return new JsonPrimitive(color(out));
    }

    public static JsonObject samplePathPosition(JsonObject path, double tick) {
        List<JsonObject> points = sorted(path, "points");
        if (points.isEmpty()) return vec(Vec3d.ZERO);
        if (points.size() == 1) return position(points.getFirst());
        JsonObject a = floor(points, tick), b = ceil(points, tick);
        if (a == b) return position(a);
        int ia = points.indexOf(a);
        double t = easedFraction(a, b, tick);
        Vec3d p1 = vec(position(a), Vec3d.ZERO), p2 = vec(position(b), Vec3d.ZERO);
        String mode = string(path, "interpolation", "CATMULL_ROM").toUpperCase(Locale.ROOT);
        Vec3d sampled;
        switch (mode) {
            case "BEZIER" -> {
                JsonObject outHandle = object(a.get("outHandle"));
                JsonObject inHandle = object(b.get("inHandle"));
                Vec3d c1 = outHandle == null ? lerp(p1, p2, 1.0 / 3.0) : p1.add(vec(outHandle, Vec3d.ZERO));
                Vec3d c2 = inHandle == null ? lerp(p1, p2, 2.0 / 3.0) : p2.add(vec(inHandle, Vec3d.ZERO));
                sampled = bezier(p1, c1, c2, p2, t);
            }
            case "CATMULL_ROM" -> {
                Vec3d p0 = vec(position(points.get(Math.max(0, ia - 1))), Vec3d.ZERO);
                Vec3d p3 = vec(position(points.get(Math.min(points.size() - 1, ia + 2))), Vec3d.ZERO);
                sampled = catmull(p0, p1, p2, p3, t);
            }
            default -> sampled = lerp(p1, p2, t);
        }
        return vec(sampled);
    }

    private static JsonElement sampleLoose(JsonObject track, double tick) {
        List<JsonObject> keys = sorted(track, "keys");
        if (keys.isEmpty()) return new JsonPrimitive(0);
        JsonObject nearest = floor(keys, tick);
        JsonElement value = nearest.get("value");
        return value == null ? new JsonPrimitive(0) : value.deepCopy();
    }

    private static List<JsonObject> sorted(JsonObject owner, String field) {
        ArrayList<JsonObject> out = new ArrayList<>();
        if (owner != null && owner.has(field) && owner.get(field).isJsonArray()) {
            for (JsonElement raw : owner.getAsJsonArray(field)) if (raw.isJsonObject()) out.add(raw.getAsJsonObject());
        }
        out.sort(Comparator.comparingDouble(v -> number(v, "tick", 0)));
        return out;
    }

    private static JsonObject floor(List<JsonObject> keys, double tick) {
        JsonObject best = keys.getFirst();
        for (JsonObject key : keys) {
            if (number(key, "tick", 0) > tick) break;
            best = key;
        }
        return best;
    }

    private static JsonObject ceil(List<JsonObject> keys, double tick) {
        for (JsonObject key : keys) if (number(key, "tick", 0) >= tick) return key;
        return keys.getLast();
    }

    private static double easedFraction(JsonObject a, JsonObject b, double tick) {
        double ta = number(a, "tick", 0), tb = number(b, "tick", ta);
        double raw = tb <= ta ? 1 : Math.max(0, Math.min(1, (tick - ta) / (tb - ta)));
        try { return Easing.valueOf(string(a, "easing", "LINEAR").toUpperCase(Locale.ROOT)).apply(raw); }
        catch (Exception ignored) { return raw; }
    }

    private static JsonObject normalizeTransform(JsonObject value) {
        if (!value.has("translation") || !value.get("translation").isJsonObject()) value.add("translation", vec(Vec3d.ZERO));
        if (!value.has("rotationDegrees") || !value.get("rotationDegrees").isJsonObject()) value.add("rotationDegrees", vec(Vec3d.ZERO));
        if (!value.has("scale") || !value.get("scale").isJsonObject()) value.add("scale", vec(new Vec3d(1, 1, 1)));
        return value;
    }

    private static JsonObject position(JsonObject point) {
        JsonObject position = object(point == null ? null : point.get("position"));
        return position == null ? vec(Vec3d.ZERO) : position.deepCopy();
    }

    private static JsonObject object(JsonElement value) { return value != null && value.isJsonObject() ? value.getAsJsonObject() : null; }
    private static double number(JsonObject o, String name, double fallback) {
        try { return o != null && o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(JsonObject o, String name, boolean fallback) {
        try { return o != null && o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonObject o, String name, String fallback) {
        try { return o != null && o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Vec3d vec(JsonObject o, Vec3d fallback) {
        if (o == null) return fallback;
        try { return new Vec3d(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static JsonObject vec(Vec3d v) {
        JsonObject o = new JsonObject(); o.addProperty("x", v.x); o.addProperty("y", v.y); o.addProperty("z", v.z); return o;
    }
    private static Vec3d lerp(Vec3d a, Vec3d b, double t) { return new Vec3d(a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t, a.z + (b.z-a.z)*t); }
    private static Vec3d lerpAngles(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + shortest(b.x-a.x)*t, a.y + shortest(b.y-a.y)*t, a.z + shortest(b.z-a.z)*t);
    }
    private static double shortest(double degrees) { double v = degrees % 360.0; if (v >= 180) v -= 360; if (v < -180) v += 360; return v; }
    private static Vec3d bezier(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double u=1-t; return p0.multiply(u*u*u).add(p1.multiply(3*u*u*t)).add(p2.multiply(3*u*t*t)).add(p3.multiply(t*t*t));
    }
    private static Vec3d catmull(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2=t*t,t3=t2*t;
        return new Vec3d(
                .5*((2*p1.x)+(-p0.x+p2.x)*t+(2*p0.x-5*p1.x+4*p2.x-p3.x)*t2+(-p0.x+3*p1.x-3*p2.x+p3.x)*t3),
                .5*((2*p1.y)+(-p0.y+p2.y)*t+(2*p0.y-5*p1.y+4*p2.y-p3.y)*t2+(-p0.y+3*p1.y-3*p2.y+p3.y)*t3),
                .5*((2*p1.z)+(-p0.z+p2.z)*t+(2*p0.z-5*p1.z+4*p2.z-p3.z)*t2+(-p0.z+3*p1.z-3*p2.z+p3.z)*t3));
    }
    private static int parseColor(JsonElement value) {
        if (value == null || value.isJsonNull()) return 0xFFFFFFFF;
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
            String text=value.getAsString().trim();
            if (text.startsWith("#")) return (int)Long.parseLong(text.substring(1),16);
            if (text.startsWith("0x")||text.startsWith("0X")) return (int)Long.parseLong(text.substring(2),16);
            return Integer.parseInt(text);
        } catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }
    private static String color(int value) { return String.format(Locale.ROOT, "#%08X", value); }
    private static int lerp8(int a,int b,double t){ return Math.max(0,Math.min(255,(int)Math.round(a+(b-a)*t))); }
}
