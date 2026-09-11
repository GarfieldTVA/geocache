package dev.garfield.cinefxgui.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.garfield.cinefx.api.AssetBundle;
import dev.garfield.cinefx.api.EventProgramSpec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Disk store for visual EventProgramSpec + AssetBundle workspaces. */
public final class EventWorkspaceStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("cinefx-gui").resolve("events");
    private static final Path AUTOSAVE = ROOT.resolve("autosave.json");

    private EventWorkspaceStore() { }

    public static List<String> list() {
        ensureRoot();
        try (var stream = Files.list(ROOT)) {
            ArrayList<String> names = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equals("autosave.json"))
                    .forEach(path -> names.add(path.getFileName().toString().replaceFirst("\\.json$", "")));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return List.copyOf(names);
        } catch (IOException ignored) { return List.of(); }
    }

    public static Path save(EventAuthoringModel.Workspace workspace, String requestedName) throws IOException {
        normalize(workspace);
        validateAndPublish(workspace);
        String safe = sanitize(requestedName == null || requestedName.isBlank() ? workspace.name : requestedName);
        if (safe.isBlank()) safe = "event";
        Path target = ROOT.resolve(safe + ".json");
        atomicWrite(target, GSON.toJson(workspace));
        workspace.sourceName = safe;
        workspace.dirty = false;
        return target;
    }

    public static EventAuthoringModel.Workspace load(String name) throws IOException {
        Path target = ROOT.resolve(sanitize(name) + ".json");
        EventAuthoringModel.Workspace workspace = GSON.fromJson(Files.readString(target, StandardCharsets.UTF_8), EventAuthoringModel.Workspace.class);
        normalize(workspace);
        workspace.sourceName = sanitize(name);
        workspace.dirty = false;
        return workspace;
    }

    public static void autosave(EventAuthoringModel.Workspace workspace) {
        try {
            normalize(workspace);
            atomicWrite(AUTOSAVE, GSON.toJson(workspace));
        } catch (Exception ignored) { }
    }

    public static EventAuthoringModel.Workspace loadAutosave() {
        if (!Files.isRegularFile(AUTOSAVE)) return null;
        try {
            EventAuthoringModel.Workspace workspace = GSON.fromJson(Files.readString(AUTOSAVE, StandardCharsets.UTF_8), EventAuthoringModel.Workspace.class);
            normalize(workspace);
            workspace.sourceName = null;
            workspace.dirty = true;
            return workspace;
        } catch (Exception ignored) { return null; }
    }

    public static void validateAndPublish(EventAuthoringModel.Workspace workspace) {
        normalize(workspace);
        EventProgramSpec spec = workspace.program.compile();
        spec.compile();
        EventProgramSpec.Registry.replace(spec);
        for (EventAuthoringModel.Bundle draft : workspace.bundles) {
            if (draft == null || draft.id == null || draft.id.isBlank()) continue;
            AssetBundle.Registry.replace(draft.compile());
        }
    }

    public static List<String> validate(EventAuthoringModel.Workspace workspace) {
        ArrayList<String> errors = new ArrayList<>();
        if (workspace == null) return List.of("Workspace is null");
        try { workspace.program.compile().compile(); }
        catch (RuntimeException exception) { errors.add("Program: " + compact(exception.getMessage())); }
        if (workspace.bundles != null) {
            for (int i = 0; i < workspace.bundles.size(); i++) {
                EventAuthoringModel.Bundle bundle = workspace.bundles.get(i);
                if (bundle == null) { errors.add("Bundle " + i + ": null"); continue; }
                try { bundle.compile(); }
                catch (RuntimeException exception) { errors.add("Bundle " + (i + 1) + ": " + compact(exception.getMessage())); }
            }
        }
        return List.copyOf(errors);
    }

    static void normalize(EventAuthoringModel.Workspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("Invalid CineFX event workspace");
        if (workspace.formatVersion <= 0) workspace.formatVersion = 1;
        if (workspace.name == null || workspace.name.isBlank()) workspace.name = "Untitled Event";
        if (workspace.program == null) workspace.program = EventAuthoringModel.Program.starter();
        if (workspace.program.id == null || workspace.program.id.isBlank()) workspace.program.id = "cinefx_gui:event";
        if (workspace.program.initialPhase == null || workspace.program.initialPhase.isBlank()) workspace.program.initialPhase = "intro";
        if (workspace.program.phases == null) workspace.program.phases = new ArrayList<>();
        if (workspace.program.phases.isEmpty()) workspace.program = EventAuthoringModel.Program.starter();
        if (workspace.program.metadata == null) workspace.program.metadata = new java.util.LinkedHashMap<>();
        for (EventAuthoringModel.Phase phase : workspace.program.phases) {
            if (phase == null) continue;
            if (phase.id == null || phase.id.isBlank()) phase.id = "phase";
            if (phase.onEnter == null) phase.onEnter = new ArrayList<>();
            if (phase.onExit == null) phase.onExit = new ArrayList<>();
            if (phase.transitions == null) phase.transitions = new ArrayList<>();
            for (EventAuthoringModel.Transition transition : phase.transitions) normalize(transition);
            phase.onEnter.removeIf(java.util.Objects::isNull);
            phase.onExit.removeIf(java.util.Objects::isNull);
        }
        workspace.program.phases.removeIf(java.util.Objects::isNull);
        if (workspace.bundles == null) workspace.bundles = new ArrayList<>();
        workspace.bundles.removeIf(java.util.Objects::isNull);
        for (EventAuthoringModel.Bundle bundle : workspace.bundles) {
            if (bundle.id == null || bundle.id.isBlank()) bundle.id = "cinefx_gui:assets";
            if (bundle.resources == null) bundle.resources = new ArrayList<>();
            if (bundle.logicalAssets == null) bundle.logicalAssets = new ArrayList<>();
            if (bundle.metadata == null) bundle.metadata = new java.util.LinkedHashMap<>();
        }
    }

    private static void normalize(EventAuthoringModel.Transition transition) {
        if (transition == null) return;
        if (transition.targetPhase == null) transition.targetPhase = "";
        if (transition.condition == null) transition.condition = new EventAuthoringModel.Condition();
        if (transition.actions == null) transition.actions = new ArrayList<>();
        transition.actions.removeIf(java.util.Objects::isNull);
        normalize(transition.condition);
    }

    private static void normalize(EventAuthoringModel.Condition condition) {
        if (condition == null) return;
        if (condition.type == null) condition.type = EventAuthoringModel.ConditionType.ALWAYS;
        if (condition.key == null) condition.key = "variable";
        if (condition.value == null) condition.value = "value";
        if (condition.id == null) condition.id = "cinefx_gui:assets";
        if (condition.children == null) condition.children = new ArrayList<>();
        condition.children.removeIf(java.util.Objects::isNull);
        for (EventAuthoringModel.Condition child : condition.children) normalize(child);
    }

    private static void atomicWrite(Path target, String text) throws IOException {
        ensureRoot();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void ensureRoot() {
        try { Files.createDirectories(ROOT); }
        catch (IOException ignored) { }
    }

    private static String sanitize(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        while (safe.startsWith(".")) safe = safe.substring(1);
        return safe.length() > 96 ? safe.substring(0, 96) : safe;
    }

    private static String compact(String message) { return message == null || message.isBlank() ? "invalid" : message.replace('\n', ' '); }
}
