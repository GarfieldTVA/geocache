package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural checks that are useful before CineFX record constructors run. */
public final class ProjectDiagnostics {
    private ProjectDiagnostics() { }

    public static List<CineFxBridge.BuildError> validate(EditorModel.Project project) {
        ArrayList<CineFxBridge.BuildError> errors = new ArrayList<>();
        if (project == null) {
            errors.add(new CineFxBridge.BuildError("", "$project", "Project is null"));
            return List.copyOf(errors);
        }
        if (Identifier.tryParse(project.sceneId == null ? "" : project.sceneId) == null) {
            errors.add(new CineFxBridge.BuildError("", "$project", "Invalid sceneId: " + project.sceneId));
        }
        if (!Double.isFinite(project.durationTicks) || project.durationTicks <= 0) {
            errors.add(new CineFxBridge.BuildError("", "$project", "durationTicks must be finite and > 0"));
        }

        HashMap<String, EditorModel.Element> byKey = new HashMap<>();
        HashSet<String> duplicates = new HashSet<>();
        for (EditorModel.Element element : project.elements) {
            if (element == null || !element.enabled) continue;
            String key = safeKey(element);
            if (key.isBlank()) {
                errors.add(error(element, "Element key is blank"));
            } else if (byKey.putIfAbsent(key, element) != null) {
                duplicates.add(key);
            }
        }
        for (String key : duplicates) {
            for (EditorModel.Element element : project.elements) {
                if (element != null && element.enabled && key.equals(safeKey(element))) {
                    errors.add(error(element, "Duplicate scene element key: " + key));
                }
            }
        }

        Set<String> validKeys = byKey.keySet();
        for (EditorModel.Element element : project.elements) {
            if (element == null || !element.enabled) continue;
            double start = element.startTick(), end = element.endTick();
            if (!Double.isFinite(start) || !Double.isFinite(end)) errors.add(error(element, "Element range must be finite"));
            else {
                if (start < 0) errors.add(error(element, "startTick must be >= 0"));
                if (end < start) errors.add(error(element, "endTick must be >= startTick"));
            }
            if (element.apiClass == null || element.apiClass.isBlank()) errors.add(error(element, "Missing CineFX API class"));

            String parent = string(element.data, "parentKey");
            if (parent != null && !parent.isBlank() && !validKeys.contains(parent)) {
                errors.add(error(element, "Unknown parentKey: " + parent));
            }
            inspectJson(element, element.data, "$", errors);
        }
        return List.copyOf(errors);
    }

    private static void inspectJson(EditorModel.Element element, JsonElement value, String path,
                                    List<CineFxBridge.BuildError> errors) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String childPath = path + "." + entry.getKey();
                JsonElement child = entry.getValue();
                if ((entry.getKey().equals("keys") || entry.getKey().equals("points")) && child.isJsonArray()) {
                    inspectTimedArray(element, child.getAsJsonArray(), childPath, entry.getKey().equals("points"), errors);
                }
                inspectJson(element, child, childPath, errors);
            }
        } else if (value.isJsonArray()) {
            int i = 0;
            for (JsonElement child : value.getAsJsonArray()) inspectJson(element, child, path + "[" + i++ + "]", errors);
        }
    }

    private static void inspectTimedArray(EditorModel.Element element, JsonArray array, String path, boolean pathPoints,
                                          List<CineFxBridge.BuildError> errors) {
        if (pathPoints && array.size() < 2) errors.add(error(element, path + " needs at least 2 points"));
        ArrayList<Double> ticks = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement raw = array.get(i);
            if (!raw.isJsonObject()) {
                errors.add(error(element, path + "[" + i + "] must be an object"));
                continue;
            }
            JsonObject object = raw.getAsJsonObject();
            if (!object.has("tick") || !object.get("tick").isJsonPrimitive()) {
                errors.add(error(element, path + "[" + i + "] is missing tick"));
                continue;
            }
            double tick;
            try { tick = object.get("tick").getAsDouble(); }
            catch (RuntimeException ex) { tick = Double.NaN; }
            if (!Double.isFinite(tick)) errors.add(error(element, path + "[" + i + "] tick must be finite"));
            else {
                if (tick < 0) errors.add(error(element, path + "[" + i + "] tick must be >= 0"));
                for (double existing : ticks) {
                    if (Math.abs(existing - tick) < 1.0e-6) {
                        errors.add(error(element, path + " contains duplicate tick " + tick));
                        break;
                    }
                }
                ticks.add(tick);
            }
        }
    }

    private static CineFxBridge.BuildError error(EditorModel.Element element, String message) {
        return new CineFxBridge.BuildError(element == null ? "" : element.editorId,
                element == null ? "$project" : safeKey(element), message);
    }

    private static String safeKey(EditorModel.Element element) {
        try { return element.key() == null ? "" : element.key().trim(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static String string(JsonObject object, String key) {
        try { return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null; }
        catch (RuntimeException ignored) { return null; }
    }
}
