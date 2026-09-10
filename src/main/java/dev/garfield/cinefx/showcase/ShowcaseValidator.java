package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural QA for the built-in showcase and long-form preset catalogs. */
public final class ShowcaseValidator {
    private ShowcaseValidator() { }

    public static Report validateAll() {
        ArrayList<String> errors = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        int elements = 0;
        LinkedHashMap<String, net.minecraft.util.Identifier> catalog = new LinkedHashMap<>();
        catalog.putAll(ShowcaseScenes.catalog());
        catalog.putAll(MegaEventPresets.catalog());

        for (Map.Entry<String, net.minecraft.util.Identifier> entry : catalog.entrySet()) {
            SceneDefinition scene = CineFxApi.find(entry.getValue()).orElse(null);
            if (scene == null) {
                errors.add(entry.getKey() + ": scene is not registered");
                continue;
            }
            elements += scene.elements().size();
            HashMap<String, SceneElement> byKey = new HashMap<>();
            HashMap<String, ComplexElement.Transformable> graph = new HashMap<>();
            for (SceneElement element : scene.elements()) {
                SceneElement previous = byKey.put(element.key(), element);
                if (previous != null) errors.add(scene.id() + ": duplicate element key " + element.key());
                if (element.startTick() < 0.0) errors.add(scene.id() + ": negative start tick on " + element.key());
                if (element.endTick() > scene.durationTicks() + 0.0001 && Double.isFinite(element.endTick())) {
                    warnings.add(scene.id() + ": " + element.key() + " ends after scene duration");
                }
                if (element instanceof ComplexElement.Transformable transformable) graph.put(transformable.key(), transformable);
            }

            for (ComplexElement.Transformable transformable : graph.values()) {
                String parent = transformable.parentKey();
                if (parent != null && !graph.containsKey(parent)) {
                    errors.add(scene.id() + ": missing parent " + parent + " for " + transformable.key());
                }
            }

            Set<String> done = new HashSet<>();
            Set<String> visiting = new HashSet<>();
            for (String key : graph.keySet()) detectCycle(scene, key, graph, done, visiting, errors);
        }

        return new Report(errors.isEmpty(), catalog.size(), elements,
                List.copyOf(errors), List.copyOf(warnings));
    }

    public static void requireValid() {
        Report report = validateAll();
        if (!report.ok()) throw new IllegalStateException("CineFX showcase validation failed: " + String.join("; ", report.errors()));
    }

    private static void detectCycle(SceneDefinition scene, String key,
                                    Map<String, ComplexElement.Transformable> graph,
                                    Set<String> done, Set<String> visiting, List<String> errors) {
        if (done.contains(key)) return;
        if (!visiting.add(key)) {
            errors.add(scene.id() + ": scene graph cycle at " + key);
            return;
        }
        ComplexElement.Transformable node = graph.get(key);
        if (node != null && node.parentKey() != null && graph.containsKey(node.parentKey())) {
            detectCycle(scene, node.parentKey(), graph, done, visiting, errors);
        }
        visiting.remove(key);
        done.add(key);
    }

    public record Report(boolean ok, int scenes, int elements, List<String> errors, List<String> warnings) { }
}
