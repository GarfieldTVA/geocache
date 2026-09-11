package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.CubicBezier;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.PathTrack;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.TransformTrack;
import dev.garfield.cinefx.api.Vec3Track;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(value = CineFxBridge.class, remap = false)
public abstract class CineFxBridgeBezierMixin {
    @Inject(method = "encodeKeyframes", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$encodeBezierKeys(List<?> keys, double durationTicks, boolean color,
                                                    CallbackInfoReturnable<JsonArray> cir) {
        JsonArray array = new JsonArray();
        for (Object raw : keys) {
            if (!(raw instanceof Keyframe<?> key)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("tick", key.tick());
            item.add("value", encodeValue(key.value(), color));
            item.addProperty("easing", key.easingToNext().name());
            addBezier(item, key.bezierToNext());
            array.add(item);
        }
        cir.setReturnValue(array);
    }

    @Inject(method = "encodePathTrack", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$encodeBezierPath(PathTrack track, double durationTicks,
                                                    CallbackInfoReturnable<JsonObject> cir) {
        JsonObject out = new JsonObject();
        out.addProperty("$kind", "PathTrack");
        out.addProperty("interpolation", track.interpolation().name());
        JsonArray points = new JsonArray();
        for (PathTrack.Point point : track.points()) {
            JsonObject item = new JsonObject();
            item.addProperty("tick", point.tick());
            item.add("position", vec(point.position()));
            item.add("inHandle", point.inHandle() == null ? JsonNull.INSTANCE : vec(point.inHandle()));
            item.add("outHandle", point.outHandle() == null ? JsonNull.INSTANCE : vec(point.outHandle()));
            item.addProperty("easing", point.easingToNext().name());
            addBezier(item, point.bezierToNext());
            points.add(item);
        }
        out.add("points", points);
        cir.setReturnValue(out);
    }

    @Inject(method = "decodeScalarTrack", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cinefxGui$decodeBezierScalar(JsonElement json, CallbackInfoReturnable<ScalarTrack> cir) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Double>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(number(key, "tick", 0), number(key, "value", 0), easing(key), bezier(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, 0.0));
        cir.setReturnValue(ScalarTrack.of(keys.toArray(Keyframe[]::new)));
    }

    @Inject(method = "decodeColorTrack", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cinefxGui$decodeBezierColor(JsonElement json, CallbackInfoReturnable<ColorTrack> cir) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Integer>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(number(key, "tick", 0), parseColor(key.get("value")), easing(key), bezier(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, 0xFFFFFFFF));
        cir.setReturnValue(ColorTrack.of(keys.toArray(Keyframe[]::new)));
    }

    @Inject(method = "decodeVec3Track", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cinefxGui$decodeBezierVec3(JsonElement json, CallbackInfoReturnable<Vec3Track> cir) {
        JsonObject object = json.getAsJsonObject();
        JsonArray array = object.getAsJsonArray("keys");
        ArrayList<Keyframe<Vec3d>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(number(key, "tick", 0), vector(key.get("value"), Vec3d.ZERO), easing(key), bezier(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, Vec3d.ZERO));
        boolean angular = bool(object, "angularDegrees", false);
        cir.setReturnValue(angular ? Vec3Track.angles(keys.toArray(Keyframe[]::new)) : Vec3Track.of(keys.toArray(Keyframe[]::new)));
    }

    @Inject(method = "decodeTransformTrack", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cinefxGui$decodeBezierTransform(JsonElement json, CallbackInfoReturnable<TransformTrack> cir) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("keys");
        ArrayList<Keyframe<Transform>> keys = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject key = raw.getAsJsonObject();
            keys.add(new Keyframe<>(number(key, "tick", 0), transform(key.get("value")), easing(key), bezier(key)));
        }
        if (keys.isEmpty()) keys.add(Keyframe.at(0.0, Transform.IDENTITY));
        cir.setReturnValue(TransformTrack.of(keys.toArray(Keyframe[]::new)));
    }

    @Inject(method = "decodePathTrack", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$decodeBezierPath(JsonElement json, CallbackInfoReturnable<PathTrack> cir) {
        JsonObject object = json.getAsJsonObject();
        PathTrack.Interpolation interpolation;
        try { interpolation = PathTrack.Interpolation.valueOf(text(object, "interpolation", "LINEAR").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { interpolation = PathTrack.Interpolation.LINEAR; }
        JsonArray array = object.getAsJsonArray("points");
        ArrayList<PathTrack.Point> points = new ArrayList<>();
        if (array != null) for (JsonElement raw : array) {
            JsonObject point = raw.getAsJsonObject();
            points.add(new PathTrack.Point(
                    number(point, "tick", points.size() * 20.0),
                    vector(point.get("position"), Vec3d.ZERO),
                    point.has("inHandle") && point.get("inHandle").isJsonObject() ? vector(point.get("inHandle"), Vec3d.ZERO) : null,
                    point.has("outHandle") && point.get("outHandle").isJsonObject() ? vector(point.get("outHandle"), Vec3d.ZERO) : null,
                    easing(point), bezier(point)));
        }
        if (points.size() < 2) {
            points.clear();
            points.add(PathTrack.Point.at(0.0, Vec3d.ZERO));
            points.add(PathTrack.Point.at(20.0, new Vec3d(0, 0, 1)));
        }
        cir.setReturnValue(new PathTrack(points, interpolation));
    }

    private static JsonElement encodeValue(Object value, boolean color) {
        if (value == null) return JsonNull.INSTANCE;
        if (color && value instanceof Number number) return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", number.intValue()));
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Vec3d vector) return vec(vector);
        if (value instanceof Transform transform) {
            JsonObject object = new JsonObject();
            object.addProperty("$type", Transform.class.getName());
            object.add("translation", vec(transform.translation()));
            object.add("rotationDegrees", vec(transform.rotationDegrees()));
            object.add("scale", vec(transform.scale()));
            return object;
        }
        return new JsonPrimitive(value.toString());
    }

    private static void addBezier(JsonObject key, CubicBezier curve) {
        if (curve == null) return;
        JsonObject object = new JsonObject();
        object.addProperty("x1", curve.x1()); object.addProperty("y1", curve.y1());
        object.addProperty("x2", curve.x2()); object.addProperty("y2", curve.y2());
        key.add("bezier", object);
    }

    private static CubicBezier bezier(JsonObject key) {
        if (key == null || !key.has("bezier") || !key.get("bezier").isJsonObject()) return null;
        JsonObject value = key.getAsJsonObject("bezier");
        try { return new CubicBezier(number(value,"x1",.33), number(value,"y1",.33), number(value,"x2",.67), number(value,"y2",.67)); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Easing easing(JsonObject key) {
        try { return Easing.valueOf(text(key, "easing", "LINEAR").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return Easing.LINEAR; }
    }

    private static Transform transform(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) return Transform.IDENTITY;
        JsonObject value = raw.getAsJsonObject();
        return new Transform(vector(value.get("translation"), Vec3d.ZERO),
                vector(value.get("rotationDegrees"), Vec3d.ZERO),
                vector(value.get("scale"), new Vec3d(1,1,1)));
    }

    private static Vec3d vector(JsonElement raw, Vec3d fallback) {
        if (raw == null || !raw.isJsonObject()) return fallback;
        JsonObject object = raw.getAsJsonObject();
        try { return new Vec3d(object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static JsonObject vec(Vec3d value) {
        JsonObject out = new JsonObject();
        out.addProperty("x", value.x); out.addProperty("y", value.y); out.addProperty("z", value.z);
        return out;
    }

    private static double number(JsonObject object, String key, double fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String text(JsonObject object, String key, String fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int parseColor(JsonElement value) {
        if (value == null || value.isJsonNull()) return 0xFFFFFFFF;
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
            String text = value.getAsString().trim();
            if (text.startsWith("#")) return (int)Long.parseLong(text.substring(1), 16);
            if (text.startsWith("0x") || text.startsWith("0X")) return (int)Long.parseLong(text.substring(2), 16);
            return Integer.parseInt(text);
        } catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }
}
