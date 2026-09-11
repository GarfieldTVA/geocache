package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyModel;
import dev.garfield.cinefxgui.editor.PresetStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real hierarchy outliner: collapse/expand, hierarchy-aware search, keyboard navigation and drag/drop
 * re-parenting. Structural decisions are delegated to HierarchyModel so the same code is unit-tested.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioOutlinerHierarchyMixin {
    @Unique private final Set<String> cinefxGui$collapsedNodes = new HashSet<>();
    @Unique private final List<TreeRow> cinefxGui$rows = new ArrayList<>();
    @Unique private final List<PresetRow> cinefxGui$presetRows = new ArrayList<>();
    @Unique private boolean cinefxGui$outlinerActive;
    @Unique private int cinefxGui$outlinerHeaderY;
    @Unique private int cinefxGui$outlinerBottom;
    @Unique private String cinefxGui$dragCandidate;
    @Unique private String cinefxGui$dragging;
    @Unique private String cinefxGui$dropTarget;
    @Unique private double cinefxGui$dragStartX;
    @Unique private double cinefxGui$dragStartY;

    @Inject(method = "drawLeftPanel", at = @At("HEAD"))
    private void cinefxGui$resetOutlinerFrame(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        cinefxGui$outlinerActive = false;
        cinefxGui$rows.clear();
        cinefxGui$presetRows.clear();
    }

    @Inject(method = "drawOutliner", at = @At("TAIL"))
    private void cinefxGui$drawHierarchy(DrawContext context, int mouseX, int mouseY, int y, int bottom, CallbackInfo ci) {
        CineFxStudioAccessMixin access = cinefxGui$access();
        EditorModel.Project project = access.cinefxGui$getProject();
        if (project == null || project.elements == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer text = client.textRenderer;

        cinefxGui$outlinerActive = true;
        cinefxGui$outlinerHeaderY = y + 47;
        cinefxGui$outlinerBottom = bottom;
        int firstRowY = cinefxGui$outlinerHeaderY + 16;
        context.fill(4, cinefxGui$outlinerHeaderY - 3, 248,
                Math.max(cinefxGui$outlinerHeaderY, bottom - 18), 0xFF171B21);

        HierarchyModel.Tree tree = HierarchyModel.build(project, access.cinefxGui$getSearchText());
        context.drawTextWithShadow(text, "ELEMENTS · " + project.elements.size(), 8, cinefxGui$outlinerHeaderY, 0xFF748695);
        int rowY = firstRowY;
        for (EditorModel.Element root : tree.roots()) {
            rowY = cinefxGui$appendRows(context, text, tree, root, 0, rowY, mouseX, mouseY);
        }

        rowY += 5;
        if (rowY < bottom - 18) context.drawTextWithShadow(text, "PRESETS", 8, rowY, 0xFF748695);
        rowY += 16;
        String search = normalize(access.cinefxGui$getSearchText());
        for (String preset : PresetStore.list()) {
            if (!search.isEmpty() && !preset.toLowerCase(Locale.ROOT).contains(search)) continue;
            int yy = rowY;
            if (yy >= cinefxGui$outlinerHeaderY && yy < bottom - 18) {
                boolean hover = mouseX >= 5 && mouseX <= 247 && mouseY >= yy - 2 && mouseY <= yy + 17;
                context.fill(5, yy - 2, 247, yy + 17, hover ? 0xFF28323B : 0xFF1C2228);
                context.drawTextWithShadow(text, text.trimToWidth(preset, 222), 11, yy + 3, 0xFFB9C6CF);
                cinefxGui$presetRows.add(new PresetRow(preset, yy - 2, yy + 17));
            }
            rowY += 20;
        }

        if (cinefxGui$dragging != null) {
            EditorModel.Element target = project.find(cinefxGui$dropTarget);
            String label = target == null ? "Move to scene root" : "Parent to " + target.key();
            int boxY = Math.max(cinefxGui$outlinerHeaderY, Math.min(bottom - 36, mouseY + 10));
            context.fill(8, boxY, 244, boxY + 22, 0xEE202B34);
            context.drawTextWithShadow(text, text.trimToWidth(label, 222), 14, boxY + 7,
                    target == null ? 0xFFD7E3EA : 0xFF8FD6A7);
        }
    }

    @Unique
    private int cinefxGui$appendRows(DrawContext context, TextRenderer text, HierarchyModel.Tree tree,
                                     EditorModel.Element element, int depth, int rowY, int mouseX, int mouseY) {
        if (!tree.visible().contains(element)) return rowY;
        boolean hasChildren = !tree.children().getOrDefault(element.key(), List.of()).isEmpty();
        boolean searchActive = !tree.search().isEmpty();
        boolean collapsed = !searchActive && cinefxGui$collapsedNodes.contains(element.editorId);
        int yy = rowY;
        if (yy >= cinefxGui$outlinerHeaderY && yy < cinefxGui$outlinerBottom - 18) {
            CineFxStudioAccessMixin access = cinefxGui$access();
            boolean selected = element.editorId.equals(access.cinefxGui$getSelectedId());
            boolean hovered = mouseX >= 5 && mouseX <= 247 && mouseY >= yy - 2 && mouseY <= yy + 18;
            boolean drop = cinefxGui$dragging != null && element.editorId.equals(cinefxGui$dropTarget);
            context.fill(5, yy - 2, 247, yy + 18,
                    drop ? 0xFF355640 : selected ? 0xFF304C61 : hovered ? 0xFF27323C : 0xFF1C2228);

            int treeX = 8 + depth * 12;
            for (int i = 0; i < depth; i++) {
                int gx = 13 + i * 12;
                context.fill(gx, yy - 2, gx + 1, yy + 19, 0x3D4B6270);
            }
            if (hasChildren) context.drawTextWithShadow(text, collapsed ? "▸" : "▾", treeX, yy + 4, 0xFF91A4B0);
            else if (depth > 0) context.drawTextWithShadow(text, "·", treeX + 1, yy + 4, 0xFF617784);

            int stateX = treeX + 13;
            context.drawTextWithShadow(text, element.enabled ? "●" : "○", stateX, yy + 4,
                    element.enabled ? 0xFF69D59B : 0xFF707981);
            int labelX = stateX + 16;
            int rightReserve = element.locked ? 26 : 12;
            context.drawTextWithShadow(text,
                    text.trimToWidth(element.key(), Math.max(20, 247 - labelX - rightReserve)),
                    labelX, yy + 4, element.hiddenInEditor ? 0xFF76818A : 0xFFD9E3E9);
            if (element.locked) context.drawTextWithShadow(text, "L", 231, yy + 4, 0xFFCCA55B);
            if (tree.brokenParents().contains(element)) context.drawTextWithShadow(text, "!", 218, yy + 4, 0xFFFF8D7D);
            cinefxGui$rows.add(new TreeRow(element, yy - 2, yy + 18, treeX, hasChildren));
        }
        rowY += 21;
        if (!collapsed) {
            for (EditorModel.Element child : tree.children().getOrDefault(element.key(), List.of())) {
                rowY = cinefxGui$appendRows(context, text, tree, child, depth + 1, rowY, mouseX, mouseY);
            }
        }
        return rowY;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$outlinerClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!cinefxGui$outlinerActive || click.x() < 4 || click.x() > 248
                || click.y() < cinefxGui$outlinerHeaderY - 3 || click.y() > cinefxGui$outlinerBottom - 18) return;
        CineFxStudioAccessMixin access = cinefxGui$access();
        for (TreeRow row : List.copyOf(cinefxGui$rows)) {
            if (!row.contains(click.x(), click.y())) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (row.hasChildren() && click.x() >= row.treeX() - 2 && click.x() <= row.treeX() + 11) {
                    if (!cinefxGui$collapsedNodes.add(row.element().editorId)) cinefxGui$collapsedNodes.remove(row.element().editorId);
                } else {
                    access.cinefxGui$select(row.element());
                    if (!row.element().locked) {
                        cinefxGui$dragCandidate = row.element().editorId;
                        cinefxGui$dragStartX = click.x();
                        cinefxGui$dragStartY = click.y();
                    }
                }
                cir.setReturnValue(true);
                return;
            }
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                String parent = HierarchyModel.parentKey(row.element());
                if (!parent.isBlank() && !row.element().locked) {
                    access.cinefxGui$checkpoint();
                    HierarchyModel.setParent(row.element(), null);
                    access.cinefxGui$markChanged();
                    access.cinefxGui$toast("Unparented " + row.element().key());
                } else access.cinefxGui$toast("Drag an element onto another row to parent it");
                cir.setReturnValue(true);
                return;
            }
        }
        for (PresetRow row : cinefxGui$presetRows) {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && row.contains(click.y())) {
                access.cinefxGui$loadPreset(row.name());
                cir.setReturnValue(true);
                return;
            }
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            access.cinefxGui$select(null);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$outlinerDrag(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || cinefxGui$dragCandidate == null || !cinefxGui$outlinerActive) return;
        if (cinefxGui$dragging == null && Math.hypot(click.x() - cinefxGui$dragStartX, click.y() - cinefxGui$dragStartY) >= 4.0) {
            cinefxGui$dragging = cinefxGui$dragCandidate;
        }
        if (cinefxGui$dragging == null) return;
        cinefxGui$dropTarget = null;
        EditorModel.Project project = cinefxGui$access().cinefxGui$getProject();
        EditorModel.Element moving = project.find(cinefxGui$dragging);
        for (TreeRow row : cinefxGui$rows) {
            if (!row.contains(click.x(), click.y()) || row.element() == moving) continue;
            if (!HierarchyModel.wouldCycle(moving, row.element(), project)) cinefxGui$dropTarget = row.element().editorId;
            break;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$outlinerRelease(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || cinefxGui$dragCandidate == null) return;
        try {
            if (cinefxGui$dragging != null) {
                CineFxStudioAccessMixin access = cinefxGui$access();
                EditorModel.Project project = access.cinefxGui$getProject();
                EditorModel.Element moving = project.find(cinefxGui$dragging);
                EditorModel.Element target = project.find(cinefxGui$dropTarget);
                if (moving != null && !moving.locked) {
                    String oldParent = HierarchyModel.parentKey(moving);
                    String nextParent = target == null ? "" : target.key();
                    if (!oldParent.equals(nextParent)) {
                        access.cinefxGui$checkpoint();
                        HierarchyModel.setParent(moving, target);
                        access.cinefxGui$markChanged();
                        access.cinefxGui$toast(target == null
                                ? "Moved " + moving.key() + " to scene root"
                                : "Parented " + moving.key() + " to " + target.key());
                    }
                }
                cir.setReturnValue(true);
            }
        } finally {
            cinefxGui$dragCandidate = null;
            cinefxGui$dragging = null;
            cinefxGui$dropTarget = null;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$treeKeyboard(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!cinefxGui$outlinerActive || (input.modifiers() & GLFW.GLFW_MOD_ALT) == 0) return;
        if (input.key() != GLFW.GLFW_KEY_LEFT && input.key() != GLFW.GLFW_KEY_RIGHT) return;
        CineFxStudioAccessMixin access = cinefxGui$access();
        EditorModel.Project project = access.cinefxGui$getProject();
        EditorModel.Element selected = project.find(access.cinefxGui$getSelectedId());
        if (selected == null) return;
        HierarchyModel.Tree tree = HierarchyModel.build(project, "");
        List<EditorModel.Element> children = tree.children().getOrDefault(selected.key(), List.of());
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (!children.isEmpty() && cinefxGui$collapsedNodes.remove(selected.editorId)) {
                // Expanded in place.
            } else if (!children.isEmpty()) access.cinefxGui$select(children.getFirst());
        } else if (!children.isEmpty() && !cinefxGui$collapsedNodes.contains(selected.editorId)) {
            cinefxGui$collapsedNodes.add(selected.editorId);
        } else {
            EditorModel.Element parent = tree.byKey().get(HierarchyModel.parentKey(selected));
            if (parent != null) access.cinefxGui$select(parent);
        }
        cir.setReturnValue(true);
    }

    @Unique private CineFxStudioAccessMixin cinefxGui$access() { return (CineFxStudioAccessMixin)(Object)this; }
    @Unique private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    @Unique private record TreeRow(EditorModel.Element element, int y1, int y2, int treeX, boolean hasChildren) {
        boolean contains(double x, double y) { return x >= 5 && x <= 247 && y >= y1 && y <= y2; }
    }
    @Unique private record PresetRow(String name, int y1, int y2) {
        boolean contains(double y) { return y >= y1 && y <= y2; }
    }
}
