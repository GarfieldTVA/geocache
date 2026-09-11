package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Visual-first CineFX workspace layered over the live Minecraft world.
 *
 * This intentionally does not duplicate the renderer/runtime. It gives the existing authoring model
 * a compact Blender/Premiere-like workflow: floating tool docks, dedicated camera/light/animation
 * controls, visual scene handles and a one-key clean viewport mode.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 1500)
public abstract class CineFxStudioProWorkspaceMixin {
    @Unique private static final int TOP = 32;
    @Unique private static final int LEFT = 252;
    @Unique private static final int RIGHT = 364;
    @Unique private static final int TIMELINE = 244;
    @Unique private static final int PANEL_X = LEFT + 12;
    @Unique private static final int PANEL_Y = TOP + 40;
    @Unique private static final int PANEL_W = 326;

    @Unique private static final int PANEL_BG = 0xE5151A21;
    @Unique private static final int PANEL_BG_2 = 0xE91B222C;
    @Unique private static final int CONTROL = 0xE925303C;
    @Unique private static final int CONTROL_HOVER = 0xF0344251;
    @Unique private static final int BORDER = 0xDD43515F;
    @Unique private static final int ACCENT = 0xFF4DA3FF;
    @Unique private static final int TEXT = 0xFFF1F5F8;
    @Unique private static final int DIM = 0xFFA0AEB9;
    @Unique private static final int MUTED = 0xFF6F7D89;
    @Unique private static final int GOOD = 0xFF68D99B;
    @Unique private static final int WARN = 0xFFFFC66B;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private SceneManipulator.Tool tool;
    @Shadow private double cameraSpeed;
    @Shadow private boolean rightLook;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;

    @Unique private CineFxProPanel cinefxGui$panel = CineFxProPanel.NONE;
    @Unique private boolean cinefxGui$cleanViewport;
    @Unique private boolean cinefxGui$autoKey = true;
    @Unique private final List<CineFxProHit> cinefxGui$proHits = new ArrayList<>();
    @Unique private final List<CineFxProSlider> cinefxGui$sliders = new ArrayList<>();
    @Unique private CineFxProSlider cinefxGui$activeSlider;

    @Unique private enum CineFxProPanel { NONE, CAMERA, LIGHT, ANIMATION, ADD, PERFORMANCE }
    @Unique private enum CineFxSliderKind {
        CAMERA_FOV, CAMERA_FOCUS, CAMERA_SHAKE, CAMERA_ROLL,
        LIGHT_INTENSITY, LIGHT_RADIUS, LIGHT_INNER, LIGHT_OUTER
    }

    @Unique private record CineFxProHit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }
    @Unique private record CineFxProSlider(int x1, int y1, int x2, int y2, double min, double max,
                                           CineFxSliderKind kind) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
        double valueAt(double x) {
            double t = Math.max(0.0, Math.min(1.0, (x - x1) / Math.max(1.0, x2 - x1)));
            return min + (max - min) * t;
        }
    }

    /* ---------- Make the existing docks read as overlays instead of opaque Minecraft menus. ---------- */

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void cinefxGui$transparentWorkspaceBase(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (cinefxGui$cleanViewport) return;
        if (color == 0x16101418) return; // never dim the Minecraft viewport itself
        int alpha = y1 == 0 ? 0xE4 : 0xB8;
        context.fill(x1, y1, x2, y2, cinefxGui$alpha(color, alpha));
    }

    @Redirect(method = {"drawOutliner", "drawAdd", "drawBlocks", "drawApi", "drawInspector", "drawPropertyRow", "drawTimeline"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void cinefxGui$transparentDockRows(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (cinefxGui$cleanViewport) return;
        int alpha = (color >>> 24) & 0xFF;
        int target = alpha > 0xE0 ? 0xD1 : Math.min(alpha, 0xC0);
        context.fill(x1, y1, x2, y2, cinefxGui$alpha(color, target));
    }

    @Inject(method = "drawToolbar", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideToolbarInCleanView(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (cinefxGui$cleanViewport) ci.cancel();
    }

    @Inject(method = "drawLeftPanel", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideLeftInCleanView(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (cinefxGui$cleanViewport) ci.cancel();
    }

    @Inject(method = "drawInspector", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideInspectorInCleanView(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (cinefxGui$cleanViewport) ci.cancel();
    }

    @Inject(method = "drawTimeline", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideTimelineInCleanView(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (cinefxGui$cleanViewport) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cinefxGui$prepareProRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (searchField != null) searchField.setVisible(!cinefxGui$cleanViewport);
        if (cinefxGui$cleanViewport && valueEditor != null) valueEditor.setVisible(false);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$drawProWorkspace(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        cinefxGui$proHits.clear();
        cinefxGui$sliders.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        if (cinefxGui$cleanViewport) {
            String hint = "TAB  Show CineFX UI   ·   RMB + WASD/QE  Fly   ·   F  Focus   ·   G/R/S  Transform";
            int w = client.textRenderer.getWidth(hint) + 20;
            int x = (client.getWindow().getScaledWidth() - w) / 2;
            cinefxGui$box(context, x, 9, w, 22, 0xC7131820, 0xA83C4855);
            context.drawTextWithShadow(client.textRenderer, hint, x + 10, 16, 0xFFDCE5EA);
            return;
        }

        cinefxGui$drawWorkspaceRail(context, mouseX, mouseY);
        if (cinefxGui$panel != CineFxProPanel.NONE) cinefxGui$drawActivePanel(context, mouseX, mouseY);
    }

    @Unique
    private void cinefxGui$drawWorkspaceRail(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        String[] labels = {"CAMERA", "LIGHT", "ANIM", "ADD", "PERF", "VIEW"};
        CineFxProPanel[] panels = {CineFxProPanel.CAMERA, CineFxProPanel.LIGHT, CineFxProPanel.ANIMATION,
                CineFxProPanel.ADD, CineFxProPanel.PERFORMANCE, CineFxProPanel.NONE};
        int x = LEFT + 10;
        int y = TOP + 8;
        for (int i = 0; i < labels.length; i++) {
            int width = client.textRenderer.getWidth(labels[i]) + 16;
            boolean active = i < 5 && cinefxGui$panel == panels[i];
            boolean hover = cinefxGui$hover(mouseX, mouseY, x, y, width, 20);
            cinefxGui$box(context, x, y, width, 20,
                    active ? 0xEA235C86 : hover ? 0xE6344251 : 0xD7192029,
                    active ? ACCENT : 0xB844515E);
            context.drawTextWithShadow(client.textRenderer, labels[i], x + 8, y + 6, active ? 0xFFFFFFFF : 0xFFD4DEE5);
            final int index = i;
            cinefxGui$proHits.add(new CineFxProHit(x, y, x + width, y + 20, () -> {
                if (index == 5) {
                    cinefxGui$cleanViewport = true;
                    cinefxGui$panel = CineFxProPanel.NONE;
                } else {
                    CineFxProPanel candidate = panels[index];
                    cinefxGui$panel = cinefxGui$panel == candidate ? CineFxProPanel.NONE : candidate;
                }
            }));
            x += width + 5;
        }

        int toolX = Math.max(x + 8, client.getWindow().getScaledWidth() - RIGHT - 182);
        toolX = cinefxGui$toolChip(context, mouseX, mouseY, toolX, y, "MOVE", SceneManipulator.Tool.MOVE);
        toolX = cinefxGui$toolChip(context, mouseX, mouseY, toolX + 4, y, "ROT", SceneManipulator.Tool.ROTATE);
        cinefxGui$toolChip(context, mouseX, mouseY, toolX + 4, y, "SCALE", SceneManipulator.Tool.SCALE);
    }

    @Unique
    private int cinefxGui$toolChip(DrawContext context, int mouseX, int mouseY, int x, int y,
                                   String text, SceneManipulator.Tool candidate) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.textRenderer.getWidth(text) + 14;
        boolean active = tool == candidate;
        boolean hover = cinefxGui$hover(mouseX, mouseY, x, y, width, 20);
        cinefxGui$box(context, x, y, width, 20, active ? 0xE9255A7D : hover ? CONTROL_HOVER : CONTROL,
                active ? ACCENT : 0xA8414E5B);
        context.drawTextWithShadow(client.textRenderer, text, x + 7, y + 6, active ? 0xFFFFFFFF : DIM);
        cinefxGui$proHits.add(new CineFxProHit(x, y, x + width, y + 20, () -> tool = candidate));
        return x + width;
    }

    @Unique
    private void cinefxGui$drawActivePanel(DrawContext context, int mouseX, int mouseY) {
        int panelH = switch (cinefxGui$panel) {
            case CAMERA -> 316;
            case LIGHT -> 336;
            case ANIMATION -> 300;
            case ADD -> 272;
            case PERFORMANCE -> 222;
            default -> 100;
        };
        cinefxGui$box(context, PANEL_X, PANEL_Y, PANEL_W, panelH, PANEL_BG, BORDER);
        MinecraftClient client = MinecraftClient.getInstance();
        String title = switch (cinefxGui$panel) {
            case CAMERA -> "CAMERA RIG";
            case LIGHT -> "LIGHTING";
            case ANIMATION -> "ANIMATION";
            case ADD -> "QUICK ADD";
            case PERFORMANCE -> "PERFORMANCE";
            default -> "CINEFX";
        };
        context.drawTextWithShadow(client.textRenderer, title, PANEL_X + 12, PANEL_Y + 11, TEXT);
        String close = "×";
        context.drawTextWithShadow(client.textRenderer, close, PANEL_X + PANEL_W - 18, PANEL_Y + 11, DIM);
        cinefxGui$proHits.add(new CineFxProHit(PANEL_X + PANEL_W - 28, PANEL_Y + 3,
                PANEL_X + PANEL_W - 4, PANEL_Y + 27, () -> cinefxGui$panel = CineFxProPanel.NONE));
        context.fill(PANEL_X + 10, PANEL_Y + 30, PANEL_X + PANEL_W - 10, PANEL_Y + 31, 0x99465360);

        switch (cinefxGui$panel) {
            case CAMERA -> cinefxGui$drawCameraPanel(context, mouseX, mouseY);
            case LIGHT -> cinefxGui$drawLightPanel(context, mouseX, mouseY);
            case ANIMATION -> cinefxGui$drawAnimationPanel(context, mouseX, mouseY);
            case ADD -> cinefxGui$drawAddPanel(context, mouseX, mouseY);
            case PERFORMANCE -> cinefxGui$drawPerformancePanel(context, mouseX, mouseY);
            default -> { }
        }
    }

    /* ---------- Camera panel ---------- */

    @Unique
    private void cinefxGui$drawCameraPanel(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        EditorModel.Element camera = cinefxGui$selectedCamera();
        int x = PANEL_X + 12;
        int y = PANEL_Y + 42;

        if (camera == null) {
            c.drawTextWithShadow(client.textRenderer, "No camera selected", x, y, DIM);
            y += 20;
            cinefxGui$button(c, mx, my, x, y, 142, "Create Camera Rig", this::cinefxGui$createCameraRigFromView);
            cinefxGui$button(c, mx, my, x + 150, y, 150, "Legacy Camera from View",
                    () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$createCameraFromView());
            y += 34;
            c.drawTextWithShadow(client.textRenderer, "Camera Rig gives visual path, FOV, focus and shake.", x, y, MUTED);
            return;
        }

        String name = cinefxGui$short(camera.key(), 210);
        c.drawTextWithShadow(client.textRenderer, name, x, y, TEXT);
        cinefxGui$badge(c, PANEL_X + PANEL_W - 91, y - 4, cinefxGui$isCameraRig(camera) ? "RIG" : "CAMERA",
                cinefxGui$isCameraRig(camera) ? GOOD : WARN);
        y += 23;

        if (!cinefxGui$isCameraRig(camera)) {
            c.drawTextWithShadow(client.textRenderer, "Basic camera selected. Use Camera Rig for FOV/path tools.", x, y, WARN);
            y += 22;
            cinefxGui$button(c, mx, my, x, y, 142, "Create Camera Rig", this::cinefxGui$createCameraRigFromView);
            cinefxGui$button(c, mx, my, x + 150, y, 144, preview.sceneCameraPreview() ? "Editor View" : "Preview Camera",
                    () -> preview.setSceneCameraPreview(client, !preview.sceneCameraPreview()));
            y += 32;
            cinefxGui$button(c, mx, my, x, y, 142, "Key Camera [K]",
                    () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
            return;
        }

        String mode = cinefxGui$string(camera.data.get("mode"), "RAIL");
        cinefxGui$button(c, mx, my, x, y, 142, "Mode: " + mode, () -> cinefxGui$cycleCameraMode(camera));
        cinefxGui$button(c, mx, my, x + 150, y, 144, preview.sceneCameraPreview() ? "Editor View" : "Preview Camera",
                () -> preview.setSceneCameraPreview(client, !preview.sceneCameraPreview()));
        y += 31;

        double local = Math.max(0.0, preview.currentTick() - camera.startTick());
        y = cinefxGui$slider(c, mx, my, x, y, "FOV", cinefxGui$scalar(camera, "fovDegrees", local, 70.0),
                20.0, 120.0, CineFxSliderKind.CAMERA_FOV, "°");
        y = cinefxGui$slider(c, mx, my, x, y, "Focus distance", cinefxGui$scalar(camera, "focusDistance", local, 8.0),
                0.5, 64.0, CineFxSliderKind.CAMERA_FOCUS, "m");
        y = cinefxGui$slider(c, mx, my, x, y, "Shake", cinefxGui$scalar(camera, "shakeTranslation", local, 0.0),
                0.0, 1.5, CineFxSliderKind.CAMERA_SHAKE, "");
        y = cinefxGui$slider(c, mx, my, x, y, "Roll", cinefxGui$scalar(camera, "rollDegrees", local, 0.0),
                -180.0, 180.0, CineFxSliderKind.CAMERA_ROLL, "°");

        boolean collide = cinefxGui$bool(camera.data.get("collideWorld"), false);
        cinefxGui$button(c, mx, my, x, y, 142, collide ? "Collision: ON" : "Collision: OFF",
                () -> { camera.data.addProperty("collideWorld", !collide); cinefxGui$changed("Camera collision"); });
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Add path point",
                () -> cinefxGui$addCameraPathPoint(camera));
        y += 31;
        cinefxGui$button(c, mx, my, x, y, 142, "Aim from current view", () -> cinefxGui$aimCameraFromView(camera));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Key camera now",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        y += 31;
        c.drawTextWithShadow(client.textRenderer,
                cinefxGui$autoKey ? "Auto-key ON · slider changes create/update a key at playhead" : "Auto-key OFF · sliders edit nearest key",
                x, y + 2, cinefxGui$autoKey ? GOOD : MUTED);
    }

    /* ---------- Lighting panel ---------- */

    @Unique
    private void cinefxGui$drawLightPanel(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        EditorModel.Element light = cinefxGui$selectedLight();
        int x = PANEL_X + 12;
        int y = PANEL_Y + 42;

        if (light == null) {
            c.drawTextWithShadow(client.textRenderer, "No light selected", x, y, DIM);
            y += 22;
            cinefxGui$button(c, mx, my, x, y, 142, "Add Point Light", () -> cinefxGui$createLight("POINT"));
            cinefxGui$button(c, mx, my, x + 150, y, 144, "Add Spot Light", () -> cinefxGui$createLight("SPOT"));
            y += 34;
            c.drawTextWithShadow(client.textRenderer, "Lights are placed in front of the editor camera.", x, y, MUTED);
            return;
        }

        c.drawTextWithShadow(client.textRenderer, cinefxGui$short(light.key(), 205), x, y, TEXT);
        String kind = cinefxGui$string(light.data.get("kind"), "POINT");
        cinefxGui$badge(c, PANEL_X + PANEL_W - 94, y - 4, kind, 0xFFFFD47A);
        y += 23;

        cinefxGui$button(c, mx, my, x, y, 142, "Type: " + kind, () -> cinefxGui$cycleLightKind(light));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Aim with view", () -> cinefxGui$aimLightFromView(light));
        y += 31;

        double local = Math.max(0.0, preview.currentTick() - light.startTick());
        y = cinefxGui$slider(c, mx, my, x, y, "Intensity", cinefxGui$scalar(light, "intensity", local, 1.0),
                0.0, 12.0, CineFxSliderKind.LIGHT_INTENSITY, "x");
        y = cinefxGui$slider(c, mx, my, x, y, "Radius", cinefxGui$scalar(light, "radius", local, 8.0),
                0.5, 48.0, CineFxSliderKind.LIGHT_RADIUS, "m");
        if ("SPOT".equalsIgnoreCase(kind)) {
            y = cinefxGui$slider(c, mx, my, x, y, "Inner cone", cinefxGui$number(light.data.get("innerConeDegrees"), 20.0),
                    0.0, 90.0, CineFxSliderKind.LIGHT_INNER, "°");
            y = cinefxGui$slider(c, mx, my, x, y, "Outer cone", cinefxGui$number(light.data.get("outerConeDegrees"), 38.0),
                    1.0, 120.0, CineFxSliderKind.LIGHT_OUTER, "°");
        }

        c.drawTextWithShadow(client.textRenderer, "COLOR", x, y + 3, DIM);
        int swatchX = x + 58;
        int[][] colors = {
                {0xFFFFFFFF, 0xFFFFFFFF}, {0xFFFFD6A3, 0xFFFFD6A3}, {0xFFB9D7FF, 0xFFB9D7FF},
                {0xFFFF6A67, 0xFFFF6A67}, {0xFF68E69B, 0xFF68E69B}, {0xFF6C8FFF, 0xFF6C8FFF}
        };
        for (int[] color : colors) {
            int sx = swatchX;
            c.fill(sx, y, sx + 24, y + 18, color[0]);
            c.fill(sx, y, sx + 24, y + 1, 0xFFFFFFFF);
            final int argb = color[1];
            cinefxGui$proHits.add(new CineFxProHit(sx, y, sx + 24, y + 18, () -> cinefxGui$setColor(light, "color", argb)));
            swatchX += 30;
        }
        y += 27;
        cinefxGui$button(c, mx, my, x, y, 142, "Key light now",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Focus light",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$focusSelected());
    }

    /* ---------- Animation panel ---------- */

    @Unique
    private void cinefxGui$drawAnimationPanel(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = PANEL_X + 12;
        int y = PANEL_Y + 42;
        String time = String.format(Locale.ROOT, "%.2fs / %.2fs", preview.currentTick() / 20.0, project.durationTicks / 20.0);
        c.drawTextWithShadow(client.textRenderer, time, x, y, TEXT);
        y += 21;

        cinefxGui$button(c, mx, my, x, y, 94, preview.playing() ? "Pause" : "Play", () -> preview.togglePlay(client));
        cinefxGui$button(c, mx, my, x + 101, y, 94, "Stop", () -> preview.stopAndRewind(client));
        cinefxGui$button(c, mx, my, x + 202, y, 94, "Key All", () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        y += 31;

        cinefxGui$button(c, mx, my, x, y, 142, cinefxGui$autoKey ? "Auto-key: ON" : "Auto-key: OFF",
                () -> cinefxGui$autoKey = !cinefxGui$autoKey);
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Fit Timeline",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$fitTimeline());
        y += 34;

        c.drawTextWithShadow(client.textRenderer, "ADD DURATION", x, y, DIM);
        y += 14;
        cinefxGui$button(c, mx, my, x, y, 94, "+1 sec", () -> cinefxGui$extendDuration(20.0));
        cinefxGui$button(c, mx, my, x + 101, y, 94, "+5 sec", () -> cinefxGui$extendDuration(100.0));
        cinefxGui$button(c, mx, my, x + 202, y, 94, "+10 sec", () -> cinefxGui$extendDuration(200.0));
        y += 34;

        c.drawTextWithShadow(client.textRenderer, "INSERT AT PLAYHEAD", x, y, DIM);
        y += 14;
        cinefxGui$button(c, mx, my, x, y, 142, "Insert 1 sec", () -> cinefxGui$insertTime(20.0));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Insert 5 sec", () -> cinefxGui$insertTime(100.0));
        y += 34;

        c.drawTextWithShadow(client.textRenderer, "KEY EASING", x, y, DIM);
        y += 14;
        cinefxGui$button(c, mx, my, x, y, 94, "Linear", () -> cinefxGui$setNearestEasing(Easing.LINEAR));
        cinefxGui$button(c, mx, my, x + 101, y, 94, "Smooth", () -> cinefxGui$setNearestEasing(Easing.SMOOTHER_STEP));
        cinefxGui$button(c, mx, my, x + 202, y, 94, "Ease In/Out", () -> cinefxGui$setNearestEasing(Easing.EASE_IN_OUT_CUBIC));
        y += 31;
        c.drawTextWithShadow(client.textRenderer, "Ctrl+T Dope Sheet   ·   Ctrl+E Curve Editor", x, y + 4, MUTED);
    }

    /* ---------- Quick Add ---------- */

    @Unique
    private void cinefxGui$drawAddPanel(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = PANEL_X + 12;
        int y = PANEL_Y + 42;
        c.drawTextWithShadow(client.textRenderer, "Create at the current editor view", x, y, DIM);
        y += 22;
        cinefxGui$button(c, mx, my, x, y, 142, "Block", () -> cinefxGui$addGeneric("SceneElement$Block", "Block"));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Camera Rig", this::cinefxGui$createCameraRigFromView);
        y += 31;
        cinefxGui$button(c, mx, my, x, y, 142, "Point Light", () -> cinefxGui$createLight("POINT"));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Spot Light", () -> cinefxGui$createLight("SPOT"));
        y += 31;
        cinefxGui$button(c, mx, my, x, y, 142, "Particles", () -> cinefxGui$addGeneric("EventElement$Emitter", "Emitter"));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Audio Cue", () -> cinefxGui$addGeneric("EventElement$AudioCue", "Audio Cue"));
        y += 31;
        cinefxGui$button(c, mx, my, x, y, 142, "Text", () -> cinefxGui$addGeneric("Text", "Text"));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Marker", () -> cinefxGui$addGeneric("EventElement$Marker", "Marker"));
        y += 31;
        cinefxGui$button(c, mx, my, x, y, 142, "Atmosphere", () -> cinefxGui$addGeneric("EventElement$Atmosphere", "Atmosphere"));
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Post Process", () -> cinefxGui$addGeneric("UltraEventElement$PostProcess", "Post Process"));
        y += 39;
        c.drawTextWithShadow(client.textRenderer, "Advanced resources stay available in the left Add/API panels.", x, y, MUTED);
    }

    /* ---------- Performance ---------- */

    @Unique
    private void cinefxGui$drawPerformancePanel(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = PANEL_X + 12;
        int y = PANEL_Y + 42;
        double last = preview.lastBuildMillis();
        double avg = preview.averageBuildMillis();
        int costColor = last < 8 ? GOOD : last < 20 ? WARN : 0xFFFF7777;
        c.drawTextWithShadow(client.textRenderer, "Preview rebuild", x, y, DIM);
        c.drawTextWithShadow(client.textRenderer, String.format(Locale.ROOT, "%.2f ms", last), x + 165, y, costColor);
        y += 18;
        c.drawTextWithShadow(client.textRenderer, "Average rebuild", x, y, DIM);
        c.drawTextWithShadow(client.textRenderer, String.format(Locale.ROOT, "%.2f ms", avg), x + 165, y, TEXT);
        y += 18;
        c.drawTextWithShadow(client.textRenderer, "Builds this session", x, y, DIM);
        c.drawTextWithShadow(client.textRenderer, Long.toString(preview.previewBuildCount()), x + 165, y, TEXT);
        y += 24;
        c.drawTextWithShadow(client.textRenderer, "Live authoring rebuilds are capped to 10 Hz.", x, y, GOOD);
        y += 17;
        c.drawTextWithShadow(client.textRenderer, "Paused seek is sent only when the playhead changes.", x, y, GOOD);
        y += 24;
        cinefxGui$button(c, mx, my, x, y, 142, "Validate Scene",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$validateProject());
        cinefxGui$button(c, mx, my, x + 150, y, 144, "Save",
                () -> ((CineFxStudioAccessMixin)(Object)this).cinefxGui$saveProject());
        y += 35;
        c.drawTextWithShadow(client.textRenderer,
                last > 20 ? "Heavy preview detected: simplify active high-cost elements while editing." : "Preview cost is in a healthy editing range.",
                x, y, last > 20 ? WARN : MUTED);
    }

    /* ---------- Input ---------- */

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$proKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() == GLFW.GLFW_KEY_TAB && input.modifiers() == 0) {
            cinefxGui$cleanViewport = !cinefxGui$cleanViewport;
            if (cinefxGui$cleanViewport) cinefxGui$panel = CineFxProPanel.NONE;
            if (searchField != null) searchField.setFocused(false);
            if (valueEditor != null) valueEditor.setFocused(false);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$proClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (cinefxGui$cleanViewport) return;
        double mx = click.x(), my = click.y();
        for (int i = cinefxGui$sliders.size() - 1; i >= 0; i--) {
            CineFxProSlider slider = cinefxGui$sliders.get(i);
            if (!slider.contains(mx, my) || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) continue;
            cinefxGui$activeSlider = slider;
            cinefxGui$applySlider(slider, mx);
            cir.setReturnValue(true);
            return;
        }
        for (int i = cinefxGui$proHits.size() - 1; i >= 0; i--) {
            CineFxProHit hit = cinefxGui$proHits.get(i);
            if (!hit.contains(mx, my)) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (searchField != null) searchField.setFocused(false);
                if (valueEditor != null) valueEditor.setFocused(false);
                hit.action.run();
                cir.setReturnValue(true);
                return;
            }
        }
        if (cinefxGui$panel != CineFxProPanel.NONE
                && mx >= PANEL_X && mx < PANEL_X + PANEL_W && my >= PANEL_Y && my < PANEL_Y + 350) {
            cir.setReturnValue(true); // never manipulate the world through a floating editor card
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$proSliderDrag(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (cinefxGui$activeSlider == null || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        cinefxGui$applySlider(cinefxGui$activeSlider, click.x());
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void cinefxGui$proSliderRelease(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) cinefxGui$activeSlider = null;
    }

    /** Corrected Blender-style free-fly camera; this replaces the older reversed A/D implementation. */
    @Inject(method = "updateFreeCamera", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$proFreeCamera(CallbackInfo ci) {
        ci.cancel();
        MinecraftClient client = MinecraftClient.getInstance();
        if (preview.sceneCameraPreview() || !rightLook || client == null) return;
        long window = client.getWindow().getHandle();
        double boost = cinefxGui$pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || cinefxGui$pressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 4.0 : 1.0;
        double precision = cinefxGui$pressed(window, GLFW.GLFW_KEY_LEFT_ALT) || cinefxGui$pressed(window, GLFW.GLFW_KEY_RIGHT_ALT) ? 0.25 : 1.0;
        double speed = cameraSpeed * boost * precision;
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

    /* ---------- Visual helpers in the actual Minecraft viewport ---------- */

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawVisualSceneHelpers(DrawContext c, int mx, int my, CallbackInfo ci) {
        if (cinefxGui$cleanViewport) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int right = client.getWindow().getScaledWidth() - RIGHT;
        int bottom = client.getWindow().getScaledHeight() - TIMELINE;
        double tick = preview.currentTick();

        for (EditorModel.Element element : project.elements) {
            if (element == null || !element.enabled || element.hiddenInEditor) continue;
            boolean camera = cinefxGui$isCamera(element);
            boolean light = cinefxGui$isLight(element);
            if (!camera && !light) continue;
            Vec3d local = SceneManipulator.pivotLocal(element, Math.max(0.0, tick - element.startTick()));
            if (local == null) continue;
            Vec3d world = project.anchor().add(local);
            ViewportGizmo.ScreenPoint p = ViewportGizmo.project(world, preview.editorCameraPosition(),
                    preview.editorCameraYaw(), preview.editorCameraPitch(), LEFT, TOP, right, bottom);
            if (!p.visible()) continue;
            int px = (int)Math.round(p.x()), py = (int)Math.round(p.y());
            int color = camera ? 0xFF62B6FF : 0xFFFFD45C;
            c.fill(px - 6, py - 1, px + 7, py + 2, color);
            c.fill(px - 1, py - 6, px + 2, py + 7, color);
            c.drawTextWithShadow(client.textRenderer, camera ? "CAM" : "LIGHT", px + 9, py - 4, color);

            if (light) cinefxGui$drawLightHelper(c, element, world, p, right, bottom);
            if (camera) cinefxGui$drawCameraHelper(c, element, world, p, right, bottom);
        }
    }

    @Unique
    private void cinefxGui$drawLightHelper(DrawContext c, EditorModel.Element light, Vec3d world,
                                           ViewportGizmo.ScreenPoint center, int right, int bottom) {
        double local = Math.max(0.0, preview.currentTick() - light.startTick());
        double radius = cinefxGui$scalar(light, "radius", local, 8.0);
        int r = (int)Math.max(9, Math.min(64, radius * 2.5));
        int cx = (int)Math.round(center.x()), cy = (int)Math.round(center.y());
        int segments = 20;
        double lastX = cx + r, lastY = cy;
        for (int i = 1; i <= segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            double nx = cx + Math.cos(a) * r;
            double ny = cy + Math.sin(a) * r;
            ViewportGizmo.drawLine(c, lastX, lastY, nx, ny, 0x88FFD45C, 1);
            lastX = nx; lastY = ny;
        }
        if ("SPOT".equalsIgnoreCase(cinefxGui$string(light.data.get("kind"), "POINT"))) {
            Vec3d dir = cinefxGui$vec(light.data.get("direction"), new Vec3d(0, -1, 0));
            ViewportGizmo.ScreenPoint tip = ViewportGizmo.project(world.add(dir.multiply(Math.min(radius, 10.0))),
                    preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                    LEFT, TOP, right, bottom);
            if (tip.visible()) ViewportGizmo.drawLine(c, center.x(), center.y(), tip.x(), tip.y(), 0xCCFFD45C, 2);
        }
    }

    @Unique
    private void cinefxGui$drawCameraHelper(DrawContext c, EditorModel.Element camera, Vec3d world,
                                            ViewportGizmo.ScreenPoint center, int right, int bottom) {
        JsonElement targetRaw = camera.data.get("lookAtOffset");
        if (targetRaw == null || !targetRaw.isJsonObject()) return;
        Vec3d targetLocal = cinefxGui$vec(targetRaw, Vec3d.ZERO);
        ViewportGizmo.ScreenPoint target = ViewportGizmo.project(project.anchor().add(targetLocal),
                preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                LEFT, TOP, right, bottom);
        if (target.visible()) {
            ViewportGizmo.drawLine(c, center.x(), center.y(), target.x(), target.y(), 0xAA62B6FF, 1);
            int tx = (int)Math.round(target.x()), ty = (int)Math.round(target.y());
            c.fill(tx - 3, ty - 3, tx + 4, ty + 4, 0xFF62B6FF);
        }
    }

    /* ---------- Camera actions ---------- */

    @Unique
    private void cinefxGui$createCameraRigFromView() {
        CineFxBridge.ElementType type = cinefxGui$type("UltraEventElement$CameraRig", "Camera Rig");
        if (type == null) { cinefxGui$toast("Camera Rig is unavailable in this CineFX runtime"); return; }
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        EditorModel.Element target = cinefxGui$selected();
        EditorModel.Element camera = CineFxBridge.newDraft(type, project.durationTicks, project.elements.size());
        camera.setKey(cinefxGui$uniqueKey("camera_rig"));
        camera.setStartTick(preview.currentTick());
        camera.setEndTick(project.durationTicks);
        camera.data.addProperty("mode", "RAIL");
        camera.data.addProperty("collideWorld", true);

        Vec3d local = preview.editorCameraPosition().subtract(project.anchor());
        JsonObject path = camera.data.has("path") && camera.data.get("path").isJsonObject()
                ? camera.data.getAsJsonObject("path") : cinefxGui$pathTrack();
        JsonArray points = new JsonArray();
        points.add(cinefxGui$pathPoint(0.0, local));
        points.add(cinefxGui$pathPoint(Math.max(1.0, project.durationTicks - camera.startTick()), local));
        path.add("points", points);
        camera.data.add("path", path);

        Vec3d targetLocal;
        if (target != null && !cinefxGui$isCamera(target)) {
            Vec3d pivot = SceneManipulator.pivotLocal(target, Math.max(0.0, preview.currentTick() - target.startTick()));
            targetLocal = pivot == null ? local.add(cinefxGui$cameraForward().multiply(10.0)) : pivot;
        } else targetLocal = local.add(cinefxGui$cameraForward().multiply(10.0));
        camera.data.add("lookAtOffset", cinefxGui$vecJson(targetLocal));
        cinefxGui$setScalar(camera, "fovDegrees", 70.0);
        cinefxGui$setScalar(camera, "focusDistance", 8.0);
        cinefxGui$setScalar(camera, "focusRange", 4.0);
        project.elements.add(camera);
        access.cinefxGui$select(camera);
        access.cinefxGui$markChanged();
        access.cinefxGui$toast("Camera Rig created from editor view");
    }

    @Unique
    private void cinefxGui$addCameraPathPoint(EditorModel.Element camera) {
        if (!cinefxGui$isCameraRig(camera)) return;
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        JsonObject path = camera.data.has("path") && camera.data.get("path").isJsonObject()
                ? camera.data.getAsJsonObject("path") : cinefxGui$pathTrack();
        JsonArray points = path.has("points") && path.get("points").isJsonArray() ? path.getAsJsonArray("points") : new JsonArray();
        double localTick = Math.max(0.0, preview.currentTick() - camera.startTick());
        Vec3d localPos = preview.editorCameraPosition().subtract(project.anchor());
        JsonObject existing = cinefxGui$timedAt(points, localTick);
        if (existing != null) existing.add("position", cinefxGui$vecJson(localPos));
        else points.add(cinefxGui$pathPoint(localTick, localPos));
        cinefxGui$sortTicks(points);
        path.add("points", points);
        camera.data.add("path", path);
        access.cinefxGui$markChanged();
        access.cinefxGui$toast("Camera path point added at playhead");
    }

    @Unique
    private void cinefxGui$aimCameraFromView(EditorModel.Element camera) {
        Vec3d local = preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(12.0)).subtract(project.anchor());
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        camera.data.add("lookAtOffset", cinefxGui$vecJson(local));
        cinefxGui$changed("Camera target updated");
    }

    @Unique
    private void cinefxGui$cycleCameraMode(EditorModel.Element camera) {
        String[] modes = {"RAIL", "DOLLY", "CRANE", "ORBIT", "HANDHELD", "FOLLOW", "LOCKED", "FREE"};
        String current = cinefxGui$string(camera.data.get("mode"), "RAIL");
        int index = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(current)) index = i;
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        camera.data.addProperty("mode", modes[(index + 1) % modes.length]);
        cinefxGui$changed("Camera mode");
    }

    /* ---------- Light actions ---------- */

    @Unique
    private void cinefxGui$createLight(String kind) {
        CineFxBridge.ElementType type = cinefxGui$type("SceneLight", "Scene Light");
        if (type == null) { cinefxGui$toast("SceneLight is unavailable in this CineFX runtime"); return; }
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        EditorModel.Element light = CineFxBridge.newDraft(type, project.durationTicks, project.elements.size());
        light.setKey(cinefxGui$uniqueKey(kind.equals("SPOT") ? "spot_light" : "point_light"));
        light.setStartTick(preview.currentTick());
        light.setEndTick(project.durationTicks);
        light.data.addProperty("kind", kind);
        Vec3d world = preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(4.0));
        SceneManipulator.setBaseOffset(light, world.subtract(project.anchor()));
        light.data.add("direction", cinefxGui$vecJson(cinefxGui$cameraForward()));
        light.data.addProperty("innerConeDegrees", 20.0);
        light.data.addProperty("outerConeDegrees", 38.0);
        cinefxGui$setScalar(light, "intensity", 1.0);
        cinefxGui$setScalar(light, "radius", 8.0);
        cinefxGui$setColorBase(light, "color", 0xFFFFFFFF);
        project.elements.add(light);
        access.cinefxGui$select(light);
        access.cinefxGui$markChanged();
        access.cinefxGui$toast(kind.equals("SPOT") ? "Spot Light added" : "Point Light added");
    }

    @Unique
    private void cinefxGui$cycleLightKind(EditorModel.Element light) {
        String current = cinefxGui$string(light.data.get("kind"), "POINT");
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        light.data.addProperty("kind", "SPOT".equalsIgnoreCase(current) ? "POINT" : "SPOT");
        cinefxGui$changed("Light type");
    }

    @Unique
    private void cinefxGui$aimLightFromView(EditorModel.Element light) {
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        light.data.add("direction", cinefxGui$vecJson(cinefxGui$cameraForward()));
        cinefxGui$changed("Light direction updated");
    }

    /* ---------- Animation actions ---------- */

    @Unique
    private void cinefxGui$extendDuration(double delta) {
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        project.durationTicks = Math.max(1.0, project.durationTicks + delta);
        access.cinefxGui$markChanged();
        access.cinefxGui$fitTimeline();
        access.cinefxGui$toast(String.format(Locale.ROOT, "Duration +%.1fs", delta / 20.0));
    }

    @Unique
    private void cinefxGui$insertTime(double delta) {
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        double at = preview.currentTick();
        for (EditorModel.Element element : project.elements) {
            double oldStart = element.startTick();
            double oldEnd = element.endTick();
            if (oldStart >= at) {
                element.setStartTick(oldStart + delta);
                element.setEndTick(oldEnd + delta);
            } else if (oldEnd >= at) {
                element.setEndTick(oldEnd + delta);
                cinefxGui$shiftNestedTicks(element.data, at - oldStart, delta);
            }
        }
        project.durationTicks += delta;
        access.cinefxGui$markChanged();
        access.cinefxGui$fitTimeline();
        access.cinefxGui$toast(String.format(Locale.ROOT, "Inserted %.1fs at playhead", delta / 20.0));
    }

    @Unique
    private void cinefxGui$shiftNestedTicks(JsonElement value, double localCut, double delta) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tick") && object.get("tick").isJsonPrimitive()) {
                try {
                    double tick = object.get("tick").getAsDouble();
                    if (tick >= localCut) object.addProperty("tick", tick + delta);
                } catch (RuntimeException ignored) { }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!"startTick".equals(entry.getKey()) && !"endTick".equals(entry.getKey()))
                    cinefxGui$shiftNestedTicks(entry.getValue(), localCut, delta);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) cinefxGui$shiftNestedTicks(child, localCut, delta);
        }
    }

    @Unique
    private void cinefxGui$setNearestEasing(Easing easing) {
        EditorModel.Element element = cinefxGui$selected();
        if (element == null) { cinefxGui$toast("Select an animated object first"); return; }
        double local = Math.max(0.0, preview.currentTick() - element.startTick());
        JsonObject nearest = cinefxGui$nearestTimed(element.data, local, new double[]{Double.POSITIVE_INFINITY});
        if (nearest == null) { cinefxGui$toast("No keyframe found"); return; }
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        nearest.addProperty("easing", easing.name());
        cinefxGui$changed("Easing: " + easing.name());
    }

    /* ---------- Generic add ---------- */

    @Unique
    private void cinefxGui$addGeneric(String classHint, String displayHint) {
        CineFxBridge.ElementType type = cinefxGui$type(classHint, displayHint);
        if (type == null) { cinefxGui$toast(displayHint + " is unavailable"); return; }
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$checkpoint();
        EditorModel.Element element = CineFxBridge.newDraft(type, project.durationTicks, project.elements.size());
        element.setKey(cinefxGui$uniqueKey(displayHint.toLowerCase(Locale.ROOT).replace(' ', '_')));
        element.setStartTick(preview.currentTick());
        element.setEndTick(project.durationTicks);
        Vec3d spawn = preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(4.0)).subtract(project.anchor());
        SceneManipulator.setBaseOffset(element, spawn);
        project.elements.add(element);
        access.cinefxGui$select(element);
        access.cinefxGui$markChanged();
        access.cinefxGui$toast("Added " + type.displayName());
    }

    /* ---------- Slider and track data ---------- */

    @Unique
    private int cinefxGui$slider(DrawContext c, int mx, int my, int x, int y, String label, double value,
                                 double min, double max, CineFxSliderKind kind, String suffix) {
        MinecraftClient client = MinecraftClient.getInstance();
        String valueText = String.format(Locale.ROOT, Math.abs(value) >= 100 ? "%.0f%s" : "%.2f%s", value, suffix);
        c.drawTextWithShadow(client.textRenderer, label, x, y + 3, DIM);
        c.drawTextWithShadow(client.textRenderer, valueText, x + 216, y + 3, TEXT);
        int sx = x + 92, sy = y + 1, sw = 116;
        c.fill(sx, sy + 6, sx + sw, sy + 10, 0xFF28333E);
        double t = (value - min) / Math.max(1e-9, max - min);
        t = Math.max(0.0, Math.min(1.0, t));
        int px = sx + (int)Math.round(sw * t);
        c.fill(sx, sy + 6, px, sy + 10, ACCENT);
        c.fill(px - 2, sy + 2, px + 3, sy + 14, 0xFFFFFFFF);
        CineFxProSlider slider = new CineFxProSlider(sx, sy, sx + sw, sy + 17, min, max, kind);
        cinefxGui$sliders.add(slider);
        return y + 25;
    }

    @Unique
    private void cinefxGui$applySlider(CineFxProSlider slider, double mouseX) {
        double value = slider.valueAt(mouseX);
        EditorModel.Element selected = cinefxGui$selected();
        if (selected == null) return;
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        if (cinefxGui$activeSlider == slider) access.cinefxGui$checkpoint();
        switch (slider.kind) {
            case CAMERA_FOV -> cinefxGui$setScalar(selected, "fovDegrees", value);
            case CAMERA_FOCUS -> cinefxGui$setScalar(selected, "focusDistance", value);
            case CAMERA_SHAKE -> cinefxGui$setScalar(selected, "shakeTranslation", value);
            case CAMERA_ROLL -> cinefxGui$setScalar(selected, "rollDegrees", value);
            case LIGHT_INTENSITY -> cinefxGui$setScalar(selected, "intensity", value);
            case LIGHT_RADIUS -> cinefxGui$setScalar(selected, "radius", value);
            case LIGHT_INNER -> {
                double outer = cinefxGui$number(selected.data.get("outerConeDegrees"), 38.0);
                selected.data.addProperty("innerConeDegrees", Math.min(value, outer));
            }
            case LIGHT_OUTER -> {
                double inner = cinefxGui$number(selected.data.get("innerConeDegrees"), 20.0);
                selected.data.addProperty("outerConeDegrees", Math.max(value, inner));
            }
        }
        access.cinefxGui$markChanged();
    }

    @Unique
    private void cinefxGui$setScalar(EditorModel.Element element, String field, double value) {
        JsonObject track = cinefxGui$track(element.data, field, "ScalarTrack");
        JsonArray keys = track.getAsJsonArray("keys");
        double local = Math.max(0.0, preview.currentTick() - element.startTick());
        JsonObject key = cinefxGui$autoKey ? cinefxGui$timedAt(keys, local) : cinefxGui$nearestTimed(keys, local);
        if (key == null) {
            key = new JsonObject();
            key.addProperty("tick", cinefxGui$autoKey ? local : 0.0);
            key.addProperty("easing", Easing.SMOOTH_STEP.name());
            keys.add(key);
        }
        key.addProperty("value", value);
        cinefxGui$sortTicks(keys);
    }

    @Unique
    private void cinefxGui$setColor(EditorModel.Element element, String field, int argb) {
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();
        JsonObject track = cinefxGui$track(element.data, field, "ColorTrack");
        JsonArray keys = track.getAsJsonArray("keys");
        double local = Math.max(0.0, preview.currentTick() - element.startTick());
        JsonObject key = cinefxGui$autoKey ? cinefxGui$timedAt(keys, local) : cinefxGui$nearestTimed(keys, local);
        if (key == null) {
            key = new JsonObject();
            key.addProperty("tick", cinefxGui$autoKey ? local : 0.0);
            key.addProperty("easing", Easing.SMOOTH_STEP.name());
            keys.add(key);
        }
        key.addProperty("value", String.format(Locale.ROOT, "#%08X", argb));
        cinefxGui$sortTicks(keys);
        cinefxGui$changed("Light color");
    }

    @Unique
    private void cinefxGui$setColorBase(EditorModel.Element element, String field, int argb) {
        JsonObject track = cinefxGui$track(element.data, field, "ColorTrack");
        JsonArray keys = track.getAsJsonArray("keys");
        if (keys.isEmpty()) {
            JsonObject key = new JsonObject();
            key.addProperty("tick", 0.0);
            key.addProperty("easing", Easing.LINEAR.name());
            keys.add(key);
        }
        keys.get(0).getAsJsonObject().addProperty("value", String.format(Locale.ROOT, "#%08X", argb));
    }

    @Unique
    private JsonObject cinefxGui$track(JsonObject root, String field, String kind) {
        if (root.has(field) && root.get(field).isJsonObject()) {
            JsonObject track = root.getAsJsonObject(field);
            if (!track.has("keys") || !track.get("keys").isJsonArray()) track.add("keys", new JsonArray());
            return track;
        }
        JsonObject track = new JsonObject();
        track.addProperty("$kind", kind);
        track.add("keys", new JsonArray());
        root.add(field, track);
        return track;
    }

    @Unique
    private double cinefxGui$scalar(EditorModel.Element element, String field, double localTick, double fallback) {
        if (element == null || !element.data.has(field) || !element.data.get(field).isJsonObject()) return fallback;
        JsonObject track = element.data.getAsJsonObject(field);
        if (!track.has("keys") || !track.get("keys").isJsonArray()) return fallback;
        JsonObject key = cinefxGui$nearestTimed(track.getAsJsonArray("keys"), localTick);
        return key == null ? fallback : cinefxGui$number(key.get("value"), fallback);
    }

    /* ---------- Data helpers ---------- */

    @Unique
    private EditorModel.Element cinefxGui$selected() { return selectedId == null ? null : project.find(selectedId); }
    @Unique
    private EditorModel.Element cinefxGui$selectedCamera() {
        EditorModel.Element selected = cinefxGui$selected();
        if (cinefxGui$isCamera(selected)) return selected;
        return project.elements.stream().filter(this::cinefxGui$isCamera).findFirst().orElse(null);
    }
    @Unique
    private EditorModel.Element cinefxGui$selectedLight() {
        EditorModel.Element selected = cinefxGui$selected();
        if (cinefxGui$isLight(selected)) return selected;
        return project.elements.stream().filter(this::cinefxGui$isLight).findFirst().orElse(null);
    }
    @Unique private boolean cinefxGui$isCamera(EditorModel.Element e) {
        return e != null && e.apiClass != null && (e.apiClass.endsWith("EventElement$Camera") || e.apiClass.endsWith("UltraEventElement$CameraRig"));
    }
    @Unique private boolean cinefxGui$isCameraRig(EditorModel.Element e) {
        return e != null && e.apiClass != null && e.apiClass.endsWith("UltraEventElement$CameraRig");
    }
    @Unique private boolean cinefxGui$isLight(EditorModel.Element e) {
        return e != null && e.apiClass != null && (e.apiClass.endsWith("SceneLight") || e.apiClass.endsWith("UltraEventElement$LightRig"));
    }

    @Unique
    private CineFxBridge.ElementType cinefxGui$type(String classHint, String displayHint) {
        String classNeedle = classHint.toLowerCase(Locale.ROOT);
        String displayNeedle = displayHint.toLowerCase(Locale.ROOT);
        for (CineFxBridge.ElementType type : CineFxBridge.elementTypes()) {
            String name = type.apiClass().getName().toLowerCase(Locale.ROOT);
            String display = type.displayName().toLowerCase(Locale.ROOT);
            if (name.endsWith(classNeedle.toLowerCase(Locale.ROOT)) || name.contains(classNeedle)
                    || display.equals(displayNeedle) || display.contains(displayNeedle)) return type;
        }
        return null;
    }

    @Unique
    private String cinefxGui$uniqueKey(String base) {
        String normalized = base == null || base.isBlank() ? "element" : base.replaceAll("[^a-zA-Z0-9_]+", "_").toLowerCase(Locale.ROOT);
        String key = normalized;
        int i = 2;
        boolean exists;
        do {
            exists = false;
            for (EditorModel.Element element : project.elements) if (key.equals(element.key())) { exists = true; break; }
            if (exists) key = normalized + "_" + i++;
        } while (exists);
        return key;
    }

    @Unique private Vec3d cinefxGui$cameraForward() {
        double yaw = Math.toRadians(preview.editorCameraYaw());
        double pitch = Math.toRadians(preview.editorCameraPitch());
        return new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
    }

    @Unique private JsonObject cinefxGui$pathTrack() {
        JsonObject path = new JsonObject();
        path.addProperty("$kind", "PathTrack");
        path.addProperty("interpolation", "CATMULL_ROM");
        path.add("points", new JsonArray());
        return path;
    }
    @Unique private JsonObject cinefxGui$pathPoint(double tick, Vec3d position) {
        JsonObject point = new JsonObject();
        point.addProperty("tick", tick);
        point.add("position", cinefxGui$vecJson(position));
        point.add("inHandle", com.google.gson.JsonNull.INSTANCE);
        point.add("outHandle", com.google.gson.JsonNull.INSTANCE);
        point.addProperty("easing", Easing.SMOOTH_STEP.name());
        return point;
    }
    @Unique private JsonObject cinefxGui$vecJson(Vec3d value) {
        JsonObject object = new JsonObject();
        object.addProperty("x", value.x); object.addProperty("y", value.y); object.addProperty("z", value.z);
        return object;
    }
    @Unique private Vec3d cinefxGui$vec(JsonElement value, Vec3d fallback) {
        if (value == null || !value.isJsonObject()) return fallback;
        JsonObject o = value.getAsJsonObject();
        return new Vec3d(cinefxGui$number(o.get("x"), fallback.x), cinefxGui$number(o.get("y"), fallback.y), cinefxGui$number(o.get("z"), fallback.z));
    }

    @Unique private JsonObject cinefxGui$timedAt(JsonArray keys, double tick) {
        if (keys == null) return null;
        for (JsonElement raw : keys) if (raw.isJsonObject()) {
            JsonObject key = raw.getAsJsonObject();
            if (Math.abs(cinefxGui$number(key.get("tick"), -1e9) - tick) < 0.001) return key;
        }
        return null;
    }
    @Unique private JsonObject cinefxGui$nearestTimed(JsonArray keys, double tick) {
        if (keys == null || keys.isEmpty()) return null;
        JsonObject best = null; double bestDistance = Double.POSITIVE_INFINITY;
        for (JsonElement raw : keys) if (raw.isJsonObject()) {
            JsonObject key = raw.getAsJsonObject();
            double distance = Math.abs(cinefxGui$number(key.get("tick"), 0.0) - tick);
            if (distance < bestDistance) { bestDistance = distance; best = key; }
        }
        return best;
    }
    @Unique private JsonObject cinefxGui$nearestTimed(JsonElement value, double target, double[] bestDistance) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject best = null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tick") && object.get("tick").isJsonPrimitive()) {
                double d = Math.abs(cinefxGui$number(object.get("tick"), 0.0) - target);
                if (d < bestDistance[0]) { bestDistance[0] = d; best = object; }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonObject nested = cinefxGui$nearestTimed(entry.getValue(), target, bestDistance);
                if (nested != null && bestDistance[0] <= Math.abs(cinefxGui$number(best == null ? null : best.get("tick"), 0.0) - target)) best = nested;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject nested = cinefxGui$nearestTimed(child, target, bestDistance);
                if (nested != null) best = nested;
            }
        }
        return best;
    }
    @Unique private void cinefxGui$sortTicks(JsonArray array) {
        ArrayList<JsonObject> values = new ArrayList<>();
        for (JsonElement raw : array) if (raw.isJsonObject()) values.add(raw.getAsJsonObject());
        values.sort(Comparator.comparingDouble(o -> cinefxGui$number(o.get("tick"), 0.0)));
        while (!array.isEmpty()) array.remove(array.size() - 1);
        values.forEach(array::add);
    }

    /* ---------- Styling ---------- */

    @Unique private void cinefxGui$button(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action) {
        boolean hover = cinefxGui$hover(mx, my, x, y, w, 22);
        cinefxGui$box(c, x, y, w, 22, hover ? CONTROL_HOVER : CONTROL, hover ? 0xFF536474 : 0xBB3C4855);
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, cinefxGui$short(text, w - 12), x + 7, y + 7, hover ? 0xFFFFFFFF : 0xFFDDE6EC);
        cinefxGui$proHits.add(new CineFxProHit(x, y, x + w, y + 22, action));
    }
    @Unique private void cinefxGui$badge(DrawContext c, int x, int y, String text, int color) {
        int w = MinecraftClient.getInstance().textRenderer.getWidth(text) + 14;
        cinefxGui$box(c, x, y, w, 18, 0xE8222932, 0xBB46535F);
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, x + 7, y + 6, color);
    }
    @Unique private void cinefxGui$box(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x, y, x + w, y + 1, border); c.fill(x, y + h - 1, x + w, y + h, border);
        c.fill(x, y, x + 1, y + h, border); c.fill(x + w - 1, y, x + w, y + h, border);
    }
    @Unique private boolean cinefxGui$hover(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    @Unique private String cinefxGui$short(String text, int width) {
        return MinecraftClient.getInstance().textRenderer.trimToWidth(text == null ? "" : text, Math.max(6, width));
    }
    @Unique private void cinefxGui$changed(String message) {
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        access.cinefxGui$markChanged(); access.cinefxGui$toast(message);
    }
    @Unique private void cinefxGui$toast(String message) { ((CineFxStudioAccessMixin)(Object)this).cinefxGui$toast(message); }
    @Unique private static int cinefxGui$alpha(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }
    @Unique private static boolean cinefxGui$pressed(long window, int key) { return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }
    @Unique private static double cinefxGui$number(JsonElement value, double fallback) {
        try { return value == null || value.isJsonNull() ? fallback : value.getAsDouble(); } catch (RuntimeException ignored) { return fallback; }
    }
    @Unique private static String cinefxGui$string(JsonElement value, String fallback) {
        try { return value == null || value.isJsonNull() ? fallback : value.getAsString(); } catch (RuntimeException ignored) { return fallback; }
    }
    @Unique private static boolean cinefxGui$bool(JsonElement value, boolean fallback) {
        try { return value == null || value.isJsonNull() ? fallback : value.getAsBoolean(); } catch (RuntimeException ignored) { return fallback; }
    }
}
