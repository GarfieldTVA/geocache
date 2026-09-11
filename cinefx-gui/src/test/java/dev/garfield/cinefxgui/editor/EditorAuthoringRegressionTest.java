package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class EditorAuthoringRegressionTest {
    @Test
    void hierarchySearchKeepsAncestorsAndRejectsCycles() {
        EditorModel.Project project = new EditorModel.Project();
        EditorModel.Element root = element("root");
        EditorModel.Element child = element("child");
        EditorModel.Element leaf = element("leaf_target");
        HierarchyModel.setParent(child, root);
        HierarchyModel.setParent(leaf, child);
        project.elements.addAll(List.of(root, child, leaf));

        HierarchyModel.Tree tree = HierarchyModel.build(project, "target");
        assertEquals(List.of(root), tree.roots());
        assertEquals(List.of(child), tree.children().get("root"));
        assertEquals(List.of(leaf), tree.children().get("child"));
        assertTrue(tree.visible().containsAll(List.of(root, child, leaf)), "search should retain ancestor context");
        assertTrue(HierarchyModel.wouldCycle(root, leaf, project), "root cannot be parented below its own descendant");
        assertFalse(HierarchyModel.wouldCycle(leaf, root, project));
    }

    @Test
    void hierarchyMarksBrokenParentsAndCanReturnElementToRoot() {
        EditorModel.Project project = new EditorModel.Project();
        EditorModel.Element orphan = element("orphan");
        orphan.data.addProperty("parentKey", "missing");
        project.elements.add(orphan);

        HierarchyModel.Tree tree = HierarchyModel.build(project, "");
        assertEquals(List.of(orphan), tree.roots());
        assertTrue(tree.brokenParents().contains(orphan));
        HierarchyModel.setParent(orphan, null);
        assertEquals("", HierarchyModel.parentKey(orphan));
    }

    @Test
    void preExistingHierarchyCycleRemainsVisibleAndRepairable() {
        EditorModel.Project project = new EditorModel.Project();
        EditorModel.Element a = element("a");
        EditorModel.Element b = element("b");
        EditorModel.Element child = element("child");
        HierarchyModel.setParent(a, b);
        HierarchyModel.setParent(b, a);
        HierarchyModel.setParent(child, a);
        project.elements.addAll(List.of(a, b, child));

        HierarchyModel.Tree tree = HierarchyModel.build(project, "");
        assertTrue(tree.roots().containsAll(List.of(a, b)), "cycle members must be promoted so the outliner never becomes empty");
        assertTrue(tree.brokenParents().containsAll(List.of(a, b)));
        assertEquals(List.of(child), tree.children().get("a"), "non-cyclic descendants remain attached to a promoted cycle member");

        HierarchyModel.setParent(a, null);
        HierarchyModel.Tree repaired = HierarchyModel.build(project, "");
        assertFalse(repaired.brokenParents().contains(a));
        assertFalse(repaired.brokenParents().contains(b));
        assertEquals(List.of(b, child), repaired.children().get("a"),
                "repairing the cycle must keep unrelated descendants attached to their original parent");
    }

    @Test
    void transformTrackDiscoversNineSharedNumericChannels() {
        EditorModel.Element element = element("animated");
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "TransformTrack");
        JsonArray keys = new JsonArray();
        keys.add(transformKey(0, 1, 2, 3, 10, 20, 30, 1, 1, 1));
        keys.add(transformKey(20, 4, 5, 6, 40, 50, 60, 2, 2, 2));
        track.add("keys", keys);
        element.data.add("transform", track);

        List<CurveChannels.Channel> channels = CurveChannels.discover(element);
        assertEquals(9, channels.size());
        CurveChannels.Channel x = channels.stream().filter(c -> c.label().equals("transform.translation.X")).findFirst().orElseThrow();
        CurveChannels.Channel ry = channels.stream().filter(c -> c.label().equals("transform.rotation.Y")).findFirst().orElseThrow();
        assertSame(x.keys(), ry.keys(), "TransformTrack component rows must edit the same structural keys");
        assertEquals(1.0, x.value(0), 1.0e-9);
        assertEquals(50.0, ry.value(1), 1.0e-9);

        JsonObject second = x.key(1);
        x.setTick(second, 5.0);
        x.sort();
        assertEquals(5.0, x.tick(1), 1.0e-9);
        assertEquals(5.0, ry.tick(1), 1.0e-9, "moving a structural transform key must move every component row");
    }

    @Test
    void assetPickerDiscoversTypedNestedFieldsButIgnoresOrdinaryLabels() {
        EditorModel.Element element = element("assets");
        element.data.addProperty("soundId", "minecraft:block.amethyst_block.chime");
        element.data.addProperty("label", "not an asset");
        JsonObject visual = new JsonObject();
        visual.addProperty("texture", "minecraft:textures/block/stone.png");
        visual.addProperty("modelId", "cinefx:model/example.glb");
        visual.addProperty("particle", "minecraft:cloud");
        element.data.add("visual", visual);

        List<AssetPickerScreen.Binding> bindings = AssetPickerScreen.discover(element);
        assertEquals(4, bindings.size());
        assertEquals(AssetPickerScreen.Kind.SOUND, kind(bindings, "soundId"));
        assertEquals(AssetPickerScreen.Kind.TEXTURE, kind(bindings, "visual.texture"));
        assertEquals(AssetPickerScreen.Kind.MODEL, kind(bindings, "visual.modelId"));
        assertEquals(AssetPickerScreen.Kind.PARTICLE, kind(bindings, "visual.particle"));
        assertTrue(bindings.stream().noneMatch(binding -> binding.path().equals("label")));
    }

    private static AssetPickerScreen.Kind kind(List<AssetPickerScreen.Binding> bindings, String path) {
        return bindings.stream().filter(binding -> binding.path().equals(path)).findFirst().orElseThrow().kind();
    }

    private static EditorModel.Element element(String key) {
        EditorModel.Element element = new EditorModel.Element();
        element.label = key;
        element.setKey(key);
        return element;
    }

    private static JsonObject transformKey(double tick,
                                           double tx, double ty, double tz,
                                           double rx, double ry, double rz,
                                           double sx, double sy, double sz) {
        JsonObject key = new JsonObject();
        key.addProperty("tick", tick);
        key.addProperty("easing", "LINEAR");
        JsonObject value = new JsonObject();
        value.add("translation", vec(tx, ty, tz));
        value.add("rotationDegrees", vec(rx, ry, rz));
        value.add("scale", vec(sx, sy, sz));
        key.add("value", value);
        return key;
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject value = new JsonObject();
        value.addProperty("x", x);
        value.addProperty("y", y);
        value.addProperty("z", z);
        return value;
    }
}
