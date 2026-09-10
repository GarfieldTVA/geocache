package dev.garfield.cinefxgui.editor;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        String fileName = sanitize(requestedName == null || requestedName.isBlank() ? project.name : requestedName);
        if (fileName.isBlank()) fileName = "untitled";
        Path target = PRESETS.resolve(fileName + ".json");
        Path temp = PRESETS.resolve(fileName + ".json.tmp");
        Files.writeString(temp, EditorModel.GSON.toJson(project), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
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
            Files.writeString(AUTOSAVE, EditorModel.GSON.toJson(project), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
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

    private static void normalize(EditorModel.Project project) {
        if (project == null) throw new IllegalArgumentException("Invalid CineFX GUI preset");
        if (project.name == null) project.name = "Untitled";
        if (project.sceneId == null) project.sceneId = "cinefx_gui:untitled";
        if (!Double.isFinite(project.durationTicks) || project.durationTicks <= 0) project.durationTicks = 200.0;
        if (project.variables == null) project.variables = new java.util.LinkedHashMap<>();
        if (project.metadata == null) project.metadata = new java.util.LinkedHashMap<>();
        if (project.elements == null) project.elements = new ArrayList<>();
        project.elements.removeIf(java.util.Objects::isNull);
        for (EditorModel.Element element : project.elements) {
            if (element.editorId == null || element.editorId.isBlank()) element.editorId = java.util.UUID.randomUUID().toString();
            if (element.data == null) element.data = new com.google.gson.JsonObject();
            if (element.label == null) element.label = "Element";
            if (element.apiClass == null) element.apiClass = "";
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
