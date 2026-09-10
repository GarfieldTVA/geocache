package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PresetStore {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("cinefx-gui");
    private static final Path PRESETS = ROOT.resolve("presets");
    private static final Path AUTOSAVE = ROOT.resolve("autosave.json");

    private PresetStore() { }

    public static Path root() { return ROOT; }

    public static List<String> list() {
        ensureDirectories();
        try (var stream = Files.list(PRESETS)) {
            ArrayList<String> names = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> names.add(path.getFileName().toString().replaceFirst("\\.json$", "")));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return List.copyOf(names);
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static Path save(EditorModel.Project project, String requestedName) throws IOException {
        ensureDirectories();
        normalize(project);
        String fileName = sanitize(requestedName == null || requestedName.isBlank() ? project.name : requestedName);
        if (fileName.isBlank()) fileName = "untitled";
        Path target = PRESETS.resolve(fileName + ".json");
        atomicWrite(target, EditorModel.GSON.toJson(project));
        project.sourcePreset = fileName;
        project.dirty = false;
        return target;
    }

    public static EditorModel.Project load(String name) throws IOException {
        ensureDirectories();
        String safe = sanitize(name);
        Path path = PRESETS.resolve(safe + ".json");
        EditorModel.Project project = EditorModel.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), EditorModel.Project.class);
        normalize(project);
        project.sourcePreset = safe;
        project.dirty = false;
        return project;
    }

    public static void autosave(EditorModel.Project project) {
        ensureDirectories();
        try {
            normalize(project);
            atomicWrite(AUTOSAVE, EditorModel.GSON.toJson(project));
        } catch (Exception ignored) { }
    }

    public static EditorModel.Project loadAutosave() {
        if (!Files.isRegularFile(AUTOSAVE)) return null;
        try {
            EditorModel.Project project = EditorModel.GSON.fromJson(Files.readString(AUTOSAVE, StandardCharsets.UTF_8), EditorModel.Project.class);
            normalize(project);
            project.sourcePreset = null;
            project.dirty = true;
            return project;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(PRESETS.resolve(sanitize(name) + ".json"));
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Normalizes and migrates any preset supported by this editor build. */
    static void normalize(EditorModel.Project project) {
        if (project == null) throw new IllegalArgumentException("Invalid CineFX GUI preset");
        if (project.formatVersion <= 0) project.formatVersion = 1;
        if (project.formatVersion > EditorModel.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Preset format " + project.formatVersion + " is newer than this CineFX GUI build (max " + EditorModel.CURRENT_FORMAT_VERSION + ")");
        }
        migrate(project);

        if (project.name == null || project.name.isBlank()) project.name = "Untitled";
        if (project.sceneId == null || project.sceneId.isBlank()) project.sceneId = "cinefx_gui:untitled";
        if (!Double.isFinite(project.durationTicks) || project.durationTicks <= 0) project.durationTicks = 200.0;
        if (!Double.isFinite(project.anchorX)) project.anchorX = 0;
        if (!Double.isFinite(project.anchorY)) project.anchorY = 0;
        if (!Double.isFinite(project.anchorZ)) project.anchorZ = 0;
        if (project.variables == null) project.variables = new java.util.LinkedHashMap<>();
        if (project.metadata == null) project.metadata = new java.util.LinkedHashMap<>();
        if (project.elements == null) project.elements = new ArrayList<>();
        project.elements.removeIf(java.util.Objects::isNull);

        Set<String> editorIds = new HashSet<>();
        int lane = 0;
        for (EditorModel.Element element : project.elements) {
            if (element.editorId == null || element.editorId.isBlank() || !editorIds.add(element.editorId)) {
                do { element.editorId = UUID.randomUUID().toString(); } while (!editorIds.add(element.editorId));
            }
            if (element.data == null) element.data = new JsonObject();
            if (element.label == null || element.label.isBlank()) element.label = "Element";
            if (element.apiClass == null) element.apiClass = "";
            if (!element.apiClass.isBlank() && !element.data.has("$type")) element.data.addProperty("$type", element.apiClass);
            element.lane = lane++;
        }
        project.formatVersion = EditorModel.CURRENT_FORMAT_VERSION;
    }

    private static void migrate(EditorModel.Project project) {
        // v1 -> v2: editor identity became strict, autosaves became atomic and every element
        // keeps its API class marker in JSON so recovery is possible after partial/manual edits.
        if (project.formatVersion < 2) {
            if (project.elements != null) {
                for (EditorModel.Element element : project.elements) {
                    if (element == null) continue;
                    if (element.data == null) element.data = new JsonObject();
                    if (element.apiClass != null && !element.apiClass.isBlank() && !element.data.has("$type")) {
                        element.data.addProperty("$type", element.apiClass);
                    }
                }
            }
            project.formatVersion = 2;
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureDirectories() {
        try { Files.createDirectories(PRESETS); }
        catch (IOException ignored) { }
    }

    private static String sanitize(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        while (safe.startsWith(".")) safe = safe.substring(1);
        return safe.length() > 96 ? safe.substring(0, 96) : safe;
    }
}
