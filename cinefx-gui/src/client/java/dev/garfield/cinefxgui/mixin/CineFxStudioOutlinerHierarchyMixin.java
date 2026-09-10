package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Adds Blender-like parent/child readability without replacing the Studio's existing row hitboxes. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioOutlinerHierarchyMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private String selectedId;
    @Shadow private String searchText;

    @Inject(method = "drawOutliner", at = @At("TAIL"))
    private void cinefxGui$drawHierarchy(DrawContext context, int mouseX, int mouseY, int y, int bottom, CallbackInfo ci) {
        if (project == null || project.elements == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer text = client.textRenderer;

        Map<String, EditorModel.Element> byKey = new HashMap<>();
        for (EditorModel.Element element : project.elements) {
            if (element != null && element.key() != null && !element.key().isBlank()) byKey.putIfAbsent(element.key(), element);
        }

        int rowY = y + 47 + 16;
        for (EditorModel.Element element : project.elements) {
            if (element == null || !matches(element.key() + " " + element.label)) continue;
            int yy = rowY;
            if (yy >= 102 && yy < bottom - 18) {
                int depth = depth(element, byKey);
                boolean selected = element.editorId.equals(selectedId);
                boolean hovered = mouseX >= 5 && mouseX <= 247 && mouseY >= yy - 2 && mouseY <= yy + 18;
                context.fill(5, yy - 2, 247, yy + 18,
                        selected ? 0xFF304C61 : hovered ? 0xFF27323C : 0xFF1C2228);
                context.drawTextWithShadow(text, element.enabled ? "●" : "○", 10, yy + 4,
                        element.enabled ? 0xFF69D59B : 0xFF707981);

                int treeX = 25;
                for (int i = 0; i < depth; i++) {
                    int gx = treeX + i * 11;
                    context.fill(gx, yy - 2, gx + 1, yy + 19, 0x423E5361);
                }
                if (depth > 0) {
                    int branchX = treeX + (depth - 1) * 11;
                    context.fill(branchX, yy + 8, branchX + 8, yy + 9, 0x754C6474);
                    context.drawTextWithShadow(text, "›", branchX + 7, yy + 3, 0xFF708696);
                }

                int labelX = 26 + depth * 11;
                int available = Math.max(24, 219 - labelX);
                String label = text.trimToWidth(element.key(), available);
                context.drawTextWithShadow(text, label, labelX, yy + 4,
                        element.hiddenInEditor ? 0xFF76818A : 0xFFD9E3E9);
                if (element.locked) context.drawTextWithShadow(text, "L", 231, yy + 4, 0xFFCCA55B);

                String parent = parentKey(element);
                if (!parent.isBlank() && depth == 0) {
                    // Broken/missing parent remains obvious even before opening diagnostics.
                    context.drawTextWithShadow(text, "!", 218, yy + 4, 0xFFFF8D7D);
                }
            }
            rowY += 21;
        }
    }

    private boolean matches(String value) {
        return searchText == null || searchText.isBlank()
                || value != null && value.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT).trim());
    }

    private static int depth(EditorModel.Element element, Map<String, EditorModel.Element> byKey) {
        int depth = 0;
        Set<String> seen = new HashSet<>();
        String parent = parentKey(element);
        while (!parent.isBlank() && depth < 12 && seen.add(parent)) {
            EditorModel.Element parentElement = byKey.get(parent);
            if (parentElement == null) break;
            depth++;
            parent = parentKey(parentElement);
        }
        return depth;
    }

    private static String parentKey(EditorModel.Element element) {
        try {
            return element.data != null && element.data.has("parentKey") && element.data.get("parentKey").isJsonPrimitive()
                    ? element.data.get("parentKey").getAsString().trim() : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
