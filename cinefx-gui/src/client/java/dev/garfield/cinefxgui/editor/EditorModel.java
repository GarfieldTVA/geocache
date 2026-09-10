package dev.garfield.cinefxgui.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EditorModel {
    public static final int CURRENT_FORMAT_VERSION = 2;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private EditorModel() { }

    public static final class Project {
        public int formatVersion = CURRENT_FORMAT_VERSION;
        public String name = "Untitled";
        public String sceneId = "cinefx_gui:untitled";
        public double durationTicks = 200.0;
        public int priority = 0;
        public boolean looping = false;
        public double anchorX;
        public double anchorY;
        public double anchorZ;
        public long seed = 1L;
        public Map<String, String> variables = new LinkedHashMap<>();
        public Map<String, String> metadata = new LinkedHashMap<>();
        public List<Element> elements = new ArrayList<>();

        public transient boolean dirty;
        public transient String sourcePreset;

        public static Project fresh(MinecraftClient client) {
            Project project = new Project();
            if (client != null && client.player != null) {
                project.anchorX = client.player.getX();
                project.anchorY = client.player.getY();
                project.anchorZ = client.player.getZ();
            }
            project.seed = System.nanoTime();
            return project;
        }

        public Identifier identifier() {
            Identifier id = Identifier.tryParse(sceneId);
            return id == null ? Identifier.of("cinefx_gui", "untitled") : id;
        }

        public Vec3d anchor() { return new Vec3d(anchorX, anchorY, anchorZ); }

        public Project deepCopy() {
            Project copy = GSON.fromJson(GSON.toJson(this), Project.class);
            copy.dirty = dirty;
            copy.sourcePreset = sourcePreset;
            return copy;
        }

        public Element find(String editorId) {
            if (editorId == null) return null;
            for (Element element : elements) if (editorId.equals(element.editorId)) return element;
            return null;
        }
    }

    public static final class Element {
        public String editorId = UUID.randomUUID().toString();
        public String apiClass = "";
        public String label = "Element";
        public boolean enabled = true;
        public boolean hiddenInEditor = false;
        public boolean locked = false;
        public int lane = 0;
        public JsonObject data = new JsonObject();

        public double startTick() { return number("startTick", 0.0); }
        public double endTick() { return number("endTick", startTick()); }
        public int priority() { return (int)Math.round(number("priority", 0.0)); }

        public void setStartTick(double value) { data.addProperty("startTick", value); }
        public void setEndTick(double value) { data.addProperty("endTick", value); }
        public void setPriority(int value) { data.addProperty("priority", value); }

        public String key() {
            return data.has("key") && !data.get("key").isJsonNull() ? data.get("key").getAsString() : label;
        }

        public void setKey(String key) { data.addProperty("key", key); }

        public Element duplicate() {
            Element copy = new Element();
            copy.apiClass = apiClass;
            copy.label = label + " Copy";
            copy.enabled = enabled;
            copy.hiddenInEditor = hiddenInEditor;
            copy.locked = false;
            copy.lane = lane + 1;
            copy.data = data.deepCopy();
            String oldKey = key();
            if (oldKey != null && !oldKey.isBlank()) copy.setKey(oldKey + "_copy");
            return copy;
        }

        private double number(String name, double fallback) {
            try {
                return data.has(name) && !data.get(name).isJsonNull() ? data.get(name).getAsDouble() : fallback;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    public static final class History {
        private final int maxEntries;
        private final Deque<String> undo = new ArrayDeque<>();
        private final Deque<String> redo = new ArrayDeque<>();

        public History(int maxEntries) { this.maxEntries = Math.max(8, maxEntries); }

        public void checkpoint(Project project) {
            String json = GSON.toJson(project);
            if (!undo.isEmpty() && undo.peekLast().equals(json)) return;
            undo.addLast(json);
            while (undo.size() > maxEntries) undo.removeFirst();
            redo.clear();
        }

        public Project undo(Project current) {
            if (undo.isEmpty()) return current;
            redo.addLast(GSON.toJson(current));
            Project project = GSON.fromJson(undo.removeLast(), Project.class);
            project.dirty = true;
            return project;
        }

        public Project redo(Project current) {
            if (redo.isEmpty()) return current;
            undo.addLast(GSON.toJson(current));
            Project project = GSON.fromJson(redo.removeLast(), Project.class);
            project.dirty = true;
            return project;
        }

        public boolean canUndo() { return !undo.isEmpty(); }
        public boolean canRedo() { return !redo.isEmpty(); }
        public void clear() { undo.clear(); redo.clear(); }
    }

    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
