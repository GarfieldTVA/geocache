package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyJson;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/** Direct-manipulation and discoverability improvements for the main Studio viewport. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioUxMixin {
    @Unique private static final int CINEFX_TOP = 32;
    @Unique private static final int CINEFX_LEFT = 252;
    @Unique private static final int CINEFX_RIGHT = 364;
    @Unique private static final int CINEFX_TIMELINE = 244;
    @Unique private static final int CINEFX_MENU_W = 132;
    @Unique private static final int CINEFX_MENU_ROW = 19;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;
    @Shadow private boolean rightLook;
    @Shadow private double cameraSpeed;

    @Unique private boolean cinefxGui$helpOpen;
    @Unique private String cinefxGui$contextTargetId;
    @Unique private int cinefxGui$contextX;
    @Unique private int cinefxGui$contextY;

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawViewportUx(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int right = client.getWindow().getScaledWidth() - CINEFX_RIGHT;
        int bottom = client.getWindow().getScaledHeight() - CINEFX_TIMELINE;
        if (right <= CINEFX_LEFT + 150 || bottom <= CINEFX_TOP + 80) return;

        int helpX = CINEFX_LEFT + 8;
        int helpY = CINEFX_TOP + 8;
        cinefxGui$button(context, helpX, helpY, 62, 18, cinefxGui$helpOpen ? "Close help" : "? Help", mouseX, mouseY);

        int scaleY = CINEFX_TOP + 8;
        int scaleMinusX = right - 118;
        int scalePlusX = right - 58;
        cinefxGui$button(context, scaleMinusX, scaleY, 54, 18, "Size -", mouseX, mouseY);
        cinefxGui$button(context, scalePlusX, scaleY, 50, 18, "Size +", mouseX, mouseY);

        String hint = "LMB select/gizmo · double LMB focus · RMB object menu · RMB empty + WASD/QE fly · Ctrl+wheel size";
        context.drawTextWithShadow(client.textRenderer, hint, CINEFX_LEFT + 8,
                Math.max(CINEFX_TOP + 31, bottom - 29), 0xCCCAD4DD);

        if (cinefxGui$helpOpen) cinefxGui$drawHelp(context, right, bottom);
        if (cinefxGui$contextTargetId != null) cinefxGui$drawContextMenu(context, mouseX, mouseY, right, bottom);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$viewportMouse(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        double mx = click.x(), my = click.y();
        int right = client.getWindow().getScaledWidth() - CINEFX_RIGHT;
        int bottom = client.getWindow().getScaledHeight() - CINEFX_TIMELINE;

        if (cinefxGui$inside(mx, my, CINEFX_LEFT + 8, CINEFX_TOP + 8, 62, 18)) {
            cinefxGui$helpOpen = !cinefxGui$helpOpen;
            cinefxGui$clearTextFocus();
            cir.setReturnValue(true);
            return;
        }
        if (cinefxGui$inside(mx, my, right - 118, CINEFX_TOP + 8, 54, 18)) {
            cinefxGui$scaleSelected(0.90);
            cinefxGui$clearTextFocus();
            cir.setReturnValue(true);
            return;
        }
        if (cinefxGui$inside(mx, my, right - 58, CINEFX_TOP + 8, 50, 18)) {
            cinefxGui$scaleSelected(1.10);
            cinefxGui$clearTextFocus();
            cir.setReturnValue(true);
            return;
        }

        if (cinefxGui$contextTargetId != null && cinefxGui$handleContextClick(mx, my, click.button())) {
            cir.setReturnValue(true);
            return;
        }

        if (!cinefxGui$inViewport(mx, my, right, bottom)) return;
        cinefxGui$clearTextFocus();

        EditorModel.Element hovered = cinefxGui$pickElement(mx, my, right, bottom);
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hovered != null) {
            access.cinefxGui$select(hovered);
            selectedId = hovered.editorId;
            cinefxGui$contextTargetId = hovered.editorId;
            cinefxGui$contextX = (int)Math.round(mx);
            cinefxGui$contextY = (int)Math.round(my);
            rightLook = false;
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubled && hovered != null) {
            access.cinefxGui$select(hovered);
            selectedId = hovered.editorId;
            cinefxGui$contextTargetId = null;
            access.cinefxGui$focusSelected();
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) cinefxGui$contextTargetId = null;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hovered == null) cinefxGui$contextTargetId = null;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$viewportWheelScale(double mx, double my, double horizontalAmount, double verticalAmount,
                                               CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || verticalAmount == 0.0) return;
        int right = client.getWindow().getScaledWidth() - CINEFX_RIGHT;
        int bottom = client.getWindow().getScaledHeight() - CINEFX_TIMELINE;
        if (!cinefxGui$inViewport(mx, my, right, bottom) || !cinefxGui$ctrlDown(client)) return;
        cinefxGui$scaleSelected(verticalAmount > 0 ? 1.10 : 0.90);
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$discoverableKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (cinefxGui$textFocused()) return;
        if (input.key() == GLFW.GLFW_KEY_H && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
            cinefxGui$helpOpen = !cinefxGui$helpOpen;
            cir.setReturnValue(true);
            return;
        }
        if (input.key() == GLFW.GLFW_KEY_KP_ADD || input.key() == GLFW.GLFW_KEY_EQUAL) {
            cinefxGui$scaleSelected(1.10);
            cir.setReturnValue(true);
            return;
        }
        if (input.key() == GLFW.GLFW_KEY_KP_SUBTRACT || input.key() == GLFW.GLFW_KEY_MINUS) {
            cinefxGui$scaleSelected(0.90);
            cir.setReturnValue(true);
        }
    }

    /** Replaces the old camera strafe implementation whose A/D signs were reversed. */
    @Inject(method = "updateFreeCamera", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$fixedFreeCamera(CallbackInfo ci) {
        ci.cancel();
        MinecraftClient client = MinecraftClient.getInstance();
        if (preview.sceneCameraPreview() || !rightLook || client == null) return;
        long window = client.getWindow().getHandle();
        double speed = cameraSpeed * (cinefxGui$pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || cinefxGui$pressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 4.0 : 1.0);
        float yaw = preview.editorCameraYaw(), pitch = preview.editorCameraPitch();
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d right = new Vec3d(-Math.cos(ry), 0, -Math.sin(ry));
        Vec3d pos = preview.editorCameraPosition();
        boolean changed = false;
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_W)) { pos = pos.add(forward.multiply(speed)); changed = true; }
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_S)) { pos = pos.subtract(forward.multiply(speed)); changed = true; }
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_D)) { pos = pos.add(right.multiply(speed)); changed = true; }
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_A)) { pos = pos.subtract(right.multiply(speed)); changed = true; }
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_E)) { pos = pos.add(0, speed, 0); changed = true; }
        if (cinefxGui$pressed(window, GLFW.GLFW_KEY_Q)) { pos = pos.add(0, -speed, 0); changed = true; }
        if (changed) preview.setEditorCamera(pos, yaw, pitch);
    }

    @Unique
    private void cinefxGui$drawHelp(DrawContext context, int right, int bottom) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = Math.min(510, Math.max(320, right - CINEFX_LEFT - 32));
        int h = 176;
        int x = CINEFX_LEFT + 16;
        int y = CINEFX_TOP + 34;
        if (y + h > bottom - 8) h = Math.max(110, bottom - y - 8);
        context.fill(x, y, x + w, y + h, 0xE91A2027);
        context.fill(x, y, x + w, y + 1, 0xFF6BA9D1);
        int ty = y + 8;
        String[] lines = {
                "QUICK START — H closes this panel",
                "1. Block move: Blocks > choose block · K at tick 0 · tick 40 · G move · K · Space",
                "2. Grow/shrink: K · move playhead · Size +/- or S gizmo · K (Ctrl+wheel also scales)",
                "3. Spin: K · move playhead · R rotate · K · Ctrl+E for smoother curves",
                "4. Camera: RMB empty + WASD/QE · wheel speed · double LMB object to focus · Camera view",
                "5. Direct viewport: LMB object selects · LMB gizmo drags · RMB object opens actions",
                "6. Animation tools: Ctrl+T Dope Sheet · Ctrl+E Curve Editor · Space play/pause",
                "7. Assets/events: Ctrl+Shift+P Asset Browser · Ctrl+Shift+E Event Studio"
        };
        for (String line : lines) {
            if (ty + 10 >= y + h) break;
            context.drawTextWithShadow(client.textRenderer, line, x + 9, ty, ty == y + 8 ? 0xFFF4F7FA : 0xFFD2DCE4);
            ty += 19;
        }
    }

    @Unique
    private void cinefxGui$drawContextMenu(DrawContext context, int mouseX, int mouseY, int right, int bottom) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = Math.max(CINEFX_LEFT + 4, Math.min(right - CINEFX_MENU_W - 4, cinefxGui$contextX));
        int y = Math.max(CINEFX_TOP + 31, Math.min(bottom - CINEFX_MENU_ROW * 5 - 4, cinefxGui$contextY));
        cinefxGui$contextX = x;
        cinefxGui$contextY = y;
        String[] rows = {"Focus", "Shrink 10%", "Grow 10%", "Duplicate", "Delete"};
        context.fill(x, y, x + CINEFX_MENU_W, y + rows.length * CINEFX_MENU_ROW, 0xF21A1F25);
        for (int i = 0; i < rows.length; i++) {
            int yy = y + i * CINEFX_MENU_ROW;
            if (cinefxGui$inside(mouseX, mouseY, x, yy, CINEFX_MENU_W, CINEFX_MENU_ROW))
                context.fill(x + 1, yy + 1, x + CINEFX_MENU_W - 1, yy + CINEFX_MENU_ROW - 1, 0xFF314252);
            context.drawTextWithShadow(client.textRenderer, rows[i], x + 8, yy + 6,
                    i == 4 ? 0xFFFF9B91 : 0xFFE1E8ED);
        }
    }

    @Unique
    private boolean cinefxGui$handleContextClick(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        int x = cinefxGui$contextX, y = cinefxGui$contextY;
        if (!cinefxGui$inside(mx, my, x, y, CINEFX_MENU_W, CINEFX_MENU_ROW * 5)) {
            cinefxGui$contextTargetId = null;
            return false;
        }
        EditorModel.Element target = project.find(cinefxGui$contextTargetId);
        if (target == null) { cinefxGui$contextTargetId = null; return true; }
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$select(target);
        selectedId = target.editorId;
        int row = Math.max(0, Math.min(4, (int)((my - y) / CINEFX_MENU_ROW)));
        switch (row) {
            case 0 -> access.cinefxGui$focusSelected();
            case 1 -> cinefxGui$scaleSelected(0.90);
            case 2 -> cinefxGui$scaleSelected(1.10);
            case 3 -> access.cinefxGui$duplicateSelected();
            case 4 -> access.cinefxGui$deleteSelected();
            default -> { }
        }
        cinefxGui$contextTargetId = null;
        return true;
    }

    @Unique
    private void cinefxGui$scaleSelected(double factor) {
        EditorModel.Element selected = selectedId == null ? null : project.find(selectedId);
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        if (selected == null) { access.cinefxGui$toast("Select an object to resize"); return; }
        if (selected.locked) { access.cinefxGui$toast("Object is locked"); return; }
        double localTick = Math.max(0.0, preview.currentTick() - selected.startTick());
        SceneManipulator.Session session = SceneManipulator.begin(selected, SceneManipulator.Tool.SCALE,
                SceneManipulator.Axis.CENTER, localTick);
        if (session == null) { access.cinefxGui$toast("This object has no editable scale"); return; }
        access.cinefxGui$checkpoint();
        session.applyAxis(factor - 1.0);
        access.cinefxGui$markChanged();
        access.cinefxGui$toast(String.format(Locale.ROOT, "Size × %.2f", factor));
    }

    @Unique
    private EditorModel.Element cinefxGui$pickElement(double mx, double my, int right, int bottom) {
        EditorModel.Element best = null;
        double bestDistance = 15.0;
        HierarchyJson.setProject(project);
        double tick = preview.currentTick();
        for (EditorModel.Element element : project.elements) {
            if (element == null || !element.enabled || element.hiddenInEditor) continue;
            if (tick < element.startTick() || tick > element.endTick()) continue;
            Vec3d relative = HierarchyJson.worldRelativePivot(project, element, tick);
            if (relative == null) {
                relative = SceneManipulator.pivotLocal(element, Math.max(0.0, tick - element.startTick()));
            }
            if (relative == null) continue;
            ViewportGizmo.ScreenPoint p = ViewportGizmo.project(project.anchor().add(relative),
                    preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                    CINEFX_LEFT, CINEFX_TOP, right, bottom);
            if (!p.visible()) continue;
            double distance = Math.hypot(mx - p.x(), my - p.y());
            if (distance < bestDistance) { bestDistance = distance; best = element; }
        }
        return best;
    }

    @Unique private boolean cinefxGui$textFocused() {
        return (searchField != null && searchField.isFocused()) || (valueEditor != null && valueEditor.isFocused());
    }

    @Unique private void cinefxGui$clearTextFocus() {
        if (searchField != null) searchField.setFocused(false);
        if (valueEditor != null) valueEditor.setFocused(false);
    }

    @Unique private static boolean cinefxGui$pressed(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    @Unique private static boolean cinefxGui$ctrlDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return cinefxGui$pressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) || cinefxGui$pressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Unique private static boolean cinefxGui$inViewport(double x, double y, int right, int bottom) {
        return x >= CINEFX_LEFT && x < right && y >= CINEFX_TOP && y < bottom;
    }

    @Unique private static boolean cinefxGui$inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    @Unique private static void cinefxGui$button(DrawContext context, int x, int y, int w, int h,
                                                 String label, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hover = cinefxGui$inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hover ? 0xED3A4A58 : 0xE52A333C);
        context.drawTextWithShadow(client.textRenderer, label, x + 6, y + 5, 0xFFE8EEF2);
    }
}
