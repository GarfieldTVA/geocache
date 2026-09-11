package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.CubicBezier;
import dev.garfield.cinefx.api.Easing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Discovers numeric animation channels inside an editor element and edits their live JSON keys. */
public final class CurveChannels {
    public static final class Channel {
        private final String label;
        private final JsonArray keys;
        private final String[] valuePath;
        private final boolean angular;
        private final int colorShift;

        private Channel(String label, JsonArray keys, boolean angular, int colorShift, String... valuePath) {
            this.label = label;
            this.keys = keys;
            this.angular = angular;
            this.colorShift = colorShift;
            this.valuePath = valuePath;
        }

        public String label() { return label; }
        public JsonArray keys() { return keys; }
        public int size() { return keys.size(); }
        public JsonObject key(int index) { return keys.get(index).getAsJsonObject(); }
        public double tick(int index) { return number(key(index), "tick", 0.0); }
        public double value(int index) { return value(key(index)); }
        public boolean angular() { return angular; }

        public double value(JsonObject key) {
            if (colorShift >= 0) return (parseColor(key.get("value")) >>> colorShift) & 255;
            JsonElement value = navigate(key, valuePath);
            try { return value == null || value.isJsonNull() ? 0.0 : value.getAsDouble(); }
            catch (RuntimeException ignored) { return 0.0; }
        }

        public void setValue(JsonObject key, double value) {
            if (colorShift >= 0) {
                int color = parseColor(key.get("value"));
                int component = Math.max(0, Math.min(255, (int)Math.round(value)));
                color = (color & ~(255 << colorShift)) | (component << colorShift);
                key.addProperty("value", String.format(Locale.ROOT, "#%08X", color));
                return;
            }
            setNumber(key, valuePath, value);
        }

        public void setTick(JsonObject key, double tick) { key.addProperty("tick", Math.max(0.0, tick)); }

        public CubicBezier bezier(JsonObject key) {
            if (key == null || !key.has("bezier") || !key.get("bezier").isJsonObject()) return null;
            JsonObject b = key.getAsJsonObject("bezier");
            try { return new CubicBezier(number(b,"x1",.33), number(b,"y1",.33), number(b,"x2",.67), number(b,"y2",.67)); }
            catch (RuntimeException ignored) { return null; }
        }

        public CubicBezier ensureBezier(JsonObject key) {
            CubicBezier existing = bezier(key);
            if (existing != null) return existing;
            CubicBezier curve = defaultBezier(easing(key));
            setBezier(key, curve);
            return curve;
        }

        public void setBezier(JsonObject key, CubicBezier curve) {
            if (curve == null) { key.remove("bezier"); return; }
            JsonObject value = new JsonObject();
            value.addProperty("x1", curve.x1()); value.addProperty("y1", curve.y1());
            value.addProperty("x2", curve.x2()); value.addProperty("y2", curve.y2());
            key.add("bezier", value);
        }

        public double sample(double tick) {
            List<JsonObject> ordered = orderedKeys();
            if (ordered.isEmpty()) return 0.0;
            if (ordered.size() == 1 || tick <= number(ordered.getFirst(), "tick", 0)) return value(ordered.getFirst());
            if (tick >= number(ordered.getLast(), "tick", 0)) return value(ordered.getLast());
            JsonObject a = ordered.getFirst(), b = ordered.getLast();
            for (int i = 0; i < ordered.size() - 1; i++) {
                JsonObject left = ordered.get(i), right = ordered.get(i + 1);
                if (tick >= number(left,"tick",0) && tick <= number(right,"tick",0)) { a = left; b = right; break; }
            }
            double ta = number(a,"tick",0), tb = number(b,"tick",ta);
            double raw = tb <= ta ? 1.0 : Math.max(0.0, Math.min(1.0, (tick-ta)/(tb-ta)));
            CubicBezier bezier = bezier(a);
            double t = bezier == null ? easing(a).apply(raw) : bezier.apply(raw);
            double av = value(a), bv = value(b);
            double delta = angular ? shortestDegrees(bv - av) : bv - av;
            double sampled = av + delta * t;
            return colorShift >= 0 ? clamp(sampled, 0.0, 255.0) : sampled;
        }

        public void sort() {
            ArrayList<JsonElement> copy = new ArrayList<>();
            for (JsonElement raw : keys) copy.add(raw);
            copy.sort(Comparator.comparingDouble(raw -> number(raw.getAsJsonObject(), "tick", 0)));
            while (keys.size() > 0) keys.remove(keys.size() - 1);
            for (JsonElement raw : copy) keys.add(raw);
        }

        public double[] bounds() {
            List<JsonObject> ordered = orderedKeys();
            if (ordered.isEmpty()) return new double[]{0, 20, -1, 1};

            double minTick = number(ordered.getFirst(), "tick", 0.0);
            double maxTick = number(ordered.getLast(), "tick", minTick);
            double minValue = Double.POSITIVE_INFINITY, maxValue = Double.NEGATIVE_INFINITY;

            for (JsonObject key : ordered) {
                double value = value(key);
                minValue = Math.min(minValue, value);
                maxValue = Math.max(maxValue, value);
            }

            // Include the actual eased curve between keys, not just endpoint values. This keeps
            // Fit truthful for EASE_OUT_BACK and editable Bezier overshoot while matching color clamp.
            for (int i = 0; i < ordered.size() - 1; i++) {
                double a = number(ordered.get(i), "tick", 0.0);
                double b = number(ordered.get(i + 1), "tick", a);
                if (b <= a) continue;
                for (int step = 1; step < 32; step++) {
                    double tick = a + (b - a) * step / 32.0;
                    double value = sample(tick);
                    minValue = Math.min(minValue, value);
                    maxValue = Math.max(maxValue, value);
                }
            }

            if (!Double.isFinite(minValue) || !Double.isFinite(maxValue)) return new double[]{minTick, Math.max(minTick + 20, maxTick), -1, 1};
            if (maxTick - minTick < 1.0) { minTick -= 10; maxTick += 10; }
            if (maxValue - minValue < 1.0e-6) {
                double pad = Math.max(1.0, Math.abs(minValue) * .15);
                minValue -= pad; maxValue += pad;
            }
            return new double[]{minTick, maxTick, minValue, maxValue};
        }

        private List<JsonObject> orderedKeys() {
            ArrayList<JsonObject> out = new ArrayList<>();
            for (JsonElement raw : keys) if (raw.isJsonObject()) out.add(raw.getAsJsonObject());
            out.sort(Comparator.comparingDouble(v -> number(v,"tick",0)));
            return out;
        }
    }

    private CurveChannels() { }

    public static List<Channel> discover(EditorModel.Element element) {
        if (element == null || element.data == null) return List.of();
        ArrayList<Channel> out = new ArrayList<>();
        discover(element.data, "", out);
        return List.copyOf(out);
    }

    private static void discover(JsonElement raw, String path, List<Channel> out) {
        if (raw == null || raw.isJsonNull()) return;
        if (raw.isJsonArray()) {
            int i = 0;
            for (JsonElement child : raw.getAsJsonArray()) discover(child, path + "[" + i++ + "]", out);
            return;
        }
        if (!raw.isJsonObject()) return;
        JsonObject object = raw.getAsJsonObject();
        String kind = text(object, "$kind", "");
        String label = path.isBlank() ? "track" : path;
        if (object.has("keys") && object.get("keys").isJsonArray()) {
            JsonArray keys = object.getAsJsonArray("keys");
            if ("ScalarTrack".equals(kind)) {
                out.add(new Channel(label, keys, false, -1, "value")); return;
            }
            if ("ColorTrack".equals(kind)) {
                out.add(new Channel(label + ".A", keys, false, 24));
                out.add(new Channel(label + ".R", keys, false, 16));
                out.add(new Channel(label + ".G", keys, false, 8));
                out.add(new Channel(label + ".B", keys, false, 0));
                return;
            }
            if ("Vec3Track".equals(kind)) {
                boolean angular = bool(object, "angularDegrees", false);
                addVecChannels(out, label, keys, angular, "value"); return;
            }
            if ("TransformTrack".equals(kind) || "BAKED".equalsIgnoreCase(text(object, "preset", ""))) {
                addTransformChannels(out, label, keys); return;
            }
        }
        if ("PathTrack".equals(kind) && object.has("points") && object.get("points").isJsonArray()) {
            JsonArray points = object.getAsJsonArray("points");
            addVecChannels(out, label + ".position", points, false, "position");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("$")) continue;
            discover(entry.getValue(), path.isBlank() ? name : path + "." + name, out);
        }
    }

    private static void addTransformChannels(List<Channel> out, String label, JsonArray keys) {
        addVecChannels(out, label + ".translation", keys, false, "value", "translation");
        // TransformTrack uses Transform.lerp(), so Euler components interpolate directly and may
        // intentionally encode full turns such as 0 -> 360. AdvancedTransformTrack rotations are
        // separate Vec3Track.angularDegrees channels and still use shortest-arc interpolation.
        addVecChannels(out, label + ".rotation", keys, false, "value", "rotationDegrees");
        addVecChannels(out, label + ".scale", keys, false, "value", "scale");
    }

    private static void addVecChannels(List<Channel> out, String label, JsonArray keys, boolean angular, String... base) {
        out.add(new Channel(label + ".X", keys, angular, -1, append(base,"x")));
        out.add(new Channel(label + ".Y", keys, angular, -1, append(base,"y")));
        out.add(new Channel(label + ".Z", keys, angular, -1, append(base,"z")));
    }

    private static String[] append(String[] base, String value) {
        String[] out = new String[base.length + 1];
        System.arraycopy(base,0,out,0,base.length); out[base.length] = value; return out;
    }

    private static JsonElement navigate(JsonObject root, String[] path) {
        JsonElement current = root;
        for (String part : path) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static void setNumber(JsonObject root, String[] path, double value) {
        JsonObject current = root;
        for (int i = 0; i < path.length - 1; i++) {
            String part = path[i];
            if (!current.has(part) || !current.get(part).isJsonObject()) current.add(part, new JsonObject());
            current = current.getAsJsonObject(part);
        }
        current.addProperty(path[path.length - 1], Double.isFinite(value) ? value : 0.0);
    }

    private static CubicBezier defaultBezier(Easing easing) {
        return switch (easing) {
            case LINEAR -> CubicBezier.linear();
            case EASE_IN_QUAD -> new CubicBezier(.55,.085,.68,.53);
            case EASE_OUT_QUAD -> new CubicBezier(.25,.46,.45,.94);
            case EASE_IN_OUT_QUAD -> new CubicBezier(.455,.03,.515,.955);
            case EASE_IN_CUBIC -> new CubicBezier(.55,.055,.675,.19);
            case EASE_OUT_CUBIC -> new CubicBezier(.215,.61,.355,1);
            case EASE_IN_OUT_CUBIC -> new CubicBezier(.645,.045,.355,1);
            case EASE_IN_SINE -> new CubicBezier(.47,0,.745,.715);
            case EASE_OUT_SINE -> new CubicBezier(.39,.575,.565,1);
            case EASE_IN_OUT_SINE -> new CubicBezier(.445,.05,.55,.95);
            case EASE_OUT_BACK -> new CubicBezier(.175,.885,.32,1.275);
            case SMOOTH_STEP -> new CubicBezier(.42,0,.58,1);
            case SMOOTHER_STEP -> new CubicBezier(.5,0,.5,1);
        };
    }

    private static Easing easing(JsonObject key) {
        try { return Easing.valueOf(text(key,"easing","LINEAR").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return Easing.LINEAR; }
    }
    private static double shortestDegrees(double value) { double v=value%360.0; if(v>=180)v-=360; if(v< -180)v+=360; return v; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double number(JsonObject object,String key,double fallback){try{return object!=null&&object.has(key)?object.get(key).getAsDouble():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static boolean bool(JsonObject object,String key,boolean fallback){try{return object!=null&&object.has(key)?object.get(key).getAsBoolean():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static String text(JsonObject object,String key,String fallback){try{return object!=null&&object.has(key)?object.get(key).getAsString():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static int parseColor(JsonElement value){
        if(value==null||value.isJsonNull())return 0xFFFFFFFF;
        try{if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber())return value.getAsInt();String s=value.getAsString().trim();if(s.startsWith("#"))return(int)Long.parseLong(s.substring(1),16);if(s.startsWith("0x")||s.startsWith("0X"))return(int)Long.parseLong(s.substring(2),16);return Integer.parseInt(s);}catch(RuntimeException ignored){return 0xFFFFFFFF;}
    }
}
