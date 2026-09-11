package dev.garfield.cinefxgui.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure hierarchy model shared by the outliner and tests; no rendering or Mixin internals. */
public final class HierarchyModel {
    public record Tree(Map<String, EditorModel.Element> byKey,
                       Map<String, List<EditorModel.Element>> children,
                       List<EditorModel.Element> roots,
                       Set<EditorModel.Element> visible,
                       Set<EditorModel.Element> brokenParents,
                       String search) { }

    private HierarchyModel() { }

    public static Tree build(EditorModel.Project project, String rawSearch) {
        if (project == null || project.elements == null) {
            return new Tree(Map.of(), Map.of(), List.of(), Set.of(), Set.of(), normalize(rawSearch));
        }
        LinkedHashMap<String, EditorModel.Element> byKey = new LinkedHashMap<>();
        for (EditorModel.Element element : project.elements) {
            if (element != null && element.key() != null && !element.key().isBlank()) byKey.putIfAbsent(element.key(), element);
        }
        Map<String, List<EditorModel.Element>> children = new HashMap<>();
        ArrayList<EditorModel.Element> roots = new ArrayList<>();
        HashSet<EditorModel.Element> broken = new HashSet<>();
        for (EditorModel.Element element : project.elements) {
            if (element == null) continue;
            String parent = parentKey(element);
            if (parent.isBlank()) roots.add(element);
            else if (byKey.containsKey(parent) && byKey.get(parent) != element) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(element);
            else { roots.add(element); broken.add(element); }
        }

        String search = normalize(rawSearch);
        HashSet<EditorModel.Element> visible = new HashSet<>();
        if (search.isEmpty()) visible.addAll(project.elements);
        else {
            for (EditorModel.Element element : project.elements) {
                if (element == null) continue;
                String haystack = (element.key() + " " + element.label + " " + element.apiClass).toLowerCase(Locale.ROOT);
                if (!haystack.contains(search)) continue;
                visible.add(element);
                String parent = parentKey(element);
                HashSet<String> guard = new HashSet<>();
                while (!parent.isBlank() && guard.add(parent)) {
                    EditorModel.Element ancestor = byKey.get(parent);
                    if (ancestor == null) break;
                    visible.add(ancestor);
                    parent = parentKey(ancestor);
                }
            }
        }
        LinkedHashMap<String, List<EditorModel.Element>> frozenChildren = new LinkedHashMap<>();
        for (Map.Entry<String, List<EditorModel.Element>> entry : children.entrySet()) frozenChildren.put(entry.getKey(), List.copyOf(entry.getValue()));
        return new Tree(Map.copyOf(byKey), Map.copyOf(frozenChildren), List.copyOf(roots), Set.copyOf(visible), Set.copyOf(broken), search);
    }

    public static boolean wouldCycle(EditorModel.Element moving, EditorModel.Element target, EditorModel.Project project) {
        if (moving == null || target == null) return false;
        if (moving == target || moving.key().equals(target.key())) return true;
        Map<String, EditorModel.Element> byKey = new HashMap<>();
        if (project != null && project.elements != null) for (EditorModel.Element element : project.elements) {
            if (element != null) byKey.putIfAbsent(element.key(), element);
        }
        String cursor = target.key();
        HashSet<String> guard = new HashSet<>();
        while (cursor != null && !cursor.isBlank() && guard.add(cursor)) {
            if (cursor.equals(moving.key())) return true;
            EditorModel.Element element = byKey.get(cursor);
            if (element == null) break;
            cursor = parentKey(element);
        }
        return false;
    }

    public static String parentKey(EditorModel.Element element) {
        try {
            return element != null && element.data != null && element.data.has("parentKey") && element.data.get("parentKey").isJsonPrimitive()
                    ? element.data.get("parentKey").getAsString().trim() : "";
        } catch (RuntimeException ignored) { return ""; }
    }

    public static void setParent(EditorModel.Element element, EditorModel.Element parent) {
        if (element == null || element.data == null) return;
        if (parent == null || parent.key() == null || parent.key().isBlank()) element.data.remove("parentKey");
        else element.data.addProperty("parentKey", parent.key());
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
