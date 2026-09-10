package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.SceneDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * In-world CineFX authoring surface. CineFX GUI owns only editor state; every preview is executed by
 * the separately installed CineFX mod through CineFxApi/ClientCineFx.
 */
public final class CineFxEditorScreen extends Screen {
    private static final int TOP_H = 30;
    private static final int LEFT_W = 232;
    private static final int RIGHT_W = 350;
    private static final int TIMELINE_H = 236;
    private static final int TIMELINE_LABEL_W = 190;
    private static final int ROW_H = 22;

    private enum LeftTab { PROJECT, ADD, API }
    private enum DragMode { NONE, PLAYHEAD, CLIP_MOVE, CLIP_LEFT, CLIP_RIGHT, KEYFRAME }

    private EditorModel.Project project;
    private final EditorModel.History history = new EditorModel.History(96);
    private PreviewController preview;
    private String selectedId;
    private LeftTab leftTab = LeftTab.ADD;
    private double sidebarScroll;
    private double inspectorScroll;
    private int timelineScrollRows;
    private double timelineStartTick;
    private double pixelsPerTick = -1.0;
    private boolean timelineInitialized;
    private int autosaveTicks;
    private String status = "Ready";
    private long statusUntil;

    private TextFieldWidget valueEditor;
    private PropertyRow activeProperty;
    private boolean changingEditorText;
    private final Set<String> collapsed = new HashSet<>();

    private final List<Hit> hits = new ArrayList<>();
    private final List<PropertyRow> propertyRows = new ArrayList<>();
    private final List<ClipHit> clipHits = new ArrayList<>();
    private final List<KeyHit> keyHits = new ArrayList<>();

    private DragMode dragMode = DragMode.NONE;
    private EditorModel.Element dragElement;
    private JsonObject dragKeyframe;
    private double dragMouseStartTick;
    private double dragOriginalStart;
    private double dragOriginalEnd;
    private double dragOriginalKeyTick;
    private boolean rightLook;
    private double cameraSpeed = 0.35;

    public CineFxEditorScreen() {
        super(Text.literal("CineFX GUI"));
        project = EditorModel.Project.fresh(MinecraftClient.getInstance());
        preview = new PreviewController(project);
    }

    @Override
    protected void init() {
        int x = Math.max(4, width - RIGHT_W + 8);
        int y = Math.max(TOP_H + 20, height - TIMELINE_H - 25);
        valueEditor = new TextFieldWidget(textRenderer, x, y, Math.max(100, RIGHT_W - 16), 18, Text.literal("Property value"));
        valueEditor.setMaxLength(32767);
        valueEditor.setVisible(false);
        valueEditor.setChangedListener(this::onEditorTextChanged);
        addDrawableChild(valueEditor);
        preview.initializeEditorCamera(client);
        if (!timelineInitialized) {
            fitTimeline();
            timelineInitialized = true;
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Transparent on purpose: Minecraft remains the live 3D viewport.
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null || client.world == null) return;
        updateFreeCamera();
        preview.tick(client);
        if (project.dirty && ++autosaveTicks >= 100) {
            autosaveTicks = 0;
            PresetStore.autosave(project);
        }
    }

    @Override
    public void removed() {
        PresetStore.autosave(project);
        preview.close();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        hits.clear();
        propertyRows.clear();
        clipHits.clear();
        keyHits.clear();
        int timelineTop = timelineTop();
        int inspectorX = width - RIGHT_W;
        context.fill(0, 0, width, TOP_H, 0xE511141A);
        context.fill(0, TOP_H, LEFT_W, timelineTop, 0xE8161A21);
        context.fill(inspectorX, TOP_H, width, timelineTop, 0xE8161A21);
        context.fill(LEFT_W, TOP_H, inspectorX, timelineTop, 0x32101318);
        context.fill(0, timelineTop, width, height, 0xF4111419);
        drawToolbar(context, mouseX, mouseY);
        drawLeftPanel(context, mouseX, mouseY);
        drawInspector(context, mouseX, mouseY);
        drawTimeline(context, mouseX, mouseY);
        drawViewportOverlay(context);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawToolbar(DrawContext c, int mx, int my) {
        c.drawTextWithShadow(textRenderer, "CineFX GUI", 8, 10, 0xFFF2F6FA);
        int x = 84;
        x = button(c, mx, my, x, 4, 42, "New", this::newProject) + 4;
        x = button(c, mx, my, x, 4, 46, "Save", this::saveProject) + 4;
        x = button(c, mx, my, x, 4, 34, "↶", this::undo) + 4;
        x = button(c, mx, my, x, 4, 34, "↷", this::redo) + 10;
        x = button(c, mx, my, x, 4, 58, preview.playing() ? "Pause" : "Play", () -> preview.togglePlay(client)) + 4;
        x = button(c, mx, my, x, 4, 46, "Stop", () -> preview.stopAndRewind(client)) + 4;
        x = button(c, mx, my, x, 4, 66, "Key all", this::addKeyframesAtPlayhead) + 10;
        x = button(c, mx, my, x, 4, 106, preview.sceneCameraPreview() ? "Scene camera" : "Editor camera",
                () -> preview.setSceneCameraPreview(client, !preview.sceneCameraPreview())) + 4;
        button(c, mx, my, x, 4, 58, "Validate", this::validateProject);
        String time = String.format(Locale.ROOT, "%.2fs / %.2fs", preview.currentTick() / 20.0, project.durationTicks / 20.0);
        c.drawTextWithShadow(textRenderer, time, width - textRenderer.getWidth(time) - 8, 10, 0xFFD1DBE6);
    }

    private void drawLeftPanel(DrawContext c, int mx, int my) {
        int bottom = timelineTop();
        c.drawTextWithShadow(textRenderer, "LIBRARY", 8, TOP_H + 8, 0xFF8C9BAA);
        int tabY = TOP_H + 21;
        int tabW = (LEFT_W - 12) / 3;
        tabButton(c, mx, my, 4, tabY, tabW, "Project", LeftTab.PROJECT);
        tabButton(c, mx, my, 4 + tabW, tabY, tabW, "Add", LeftTab.ADD);
        tabButton(c, mx, my, 4 + tabW * 2, tabY, tabW, "API", LeftTab.API);
        int y = tabY + 27 - (int) sidebarScroll;
        switch (leftTab) {
            case PROJECT -> drawProjectLibrary(c, mx, my, y, bottom);
            case ADD -> drawAddLibrary(c, mx, my, y, bottom);
            case API -> drawApiLibrary(c, mx, my, y, bottom);
        }
    }

    private void drawProjectLibrary(DrawContext c, int mx, int my, int y, int bottom) {
        y = leftAction(c, mx, my, y, bottom, "Save preset", this::saveProject);
        y = leftAction(c, mx, my, y, bottom, "Restore autosave", this::restoreAutosave) + 6;
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "PRESETS", 8, y, 0xFF748596);
        y += 15;
        for (String preset : PresetStore.list()) {
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> loadPreset(preset));
                c.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mx, my, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF28313D : 0xFF1D232C);
                c.drawTextWithShadow(textRenderer, trim(preset, LEFT_W - 24), 12, yy + 2, 0xFFD8E0E8);
            }
            y += 20;
        }
    }

    private void drawAddLibrary(DrawContext c, int mx, int my, int y, int bottom) {
        String category = null;
        for (CineFxBridge.ElementType type : CineFxBridge.elementTypes()) {
            if (!type.category().equals(category)) {
                category = type.category();
                if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, category.toUpperCase(Locale.ROOT), 8, y, 0xFF778A9B);
                y += 15;
            }
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> addElement(type));
                c.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mx, my, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF2A3440 : 0xFF1C232B);
                c.drawTextWithShadow(textRenderer, "+  " + trim(type.displayName(), LEFT_W - 34), 11, yy + 2, 0xFFD8E4EE);
            }
            y += 20;
        }
    }

    private void drawApiLibrary(DrawContext c, int mx, int my, int y, int bottom) {
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "REGISTERED CINEFX SCENES", 8, y, 0xFF778A9B);
        y += 17;
        ArrayList<SceneDefinition> scenes = new ArrayList<>(CineFxApi.scenes());
        scenes.removeIf(s -> s.id().getNamespace().equals("cinefx_gui") && s.id().getPath().endsWith("_runtime"));
        scenes.sort(Comparator.comparing(s -> s.id().toString()));
        for (SceneDefinition scene : scenes) {
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> importScene(scene));
                c.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mx, my, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF2A3440 : 0xFF1C232B);
                c.drawTextWithShadow(textRenderer, trim(scene.id().toString(), LEFT_W - 22), 10, yy + 2, 0xFFD7E4ED);
            }
            y += 20;
        }
        if (scenes.isEmpty() && visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "No registered scenes", 10, y, 0xFF78838E);
    }

    private void drawInspector(DrawContext c, int mx, int my) {
        int x = width - RIGHT_W;
        int bottom = timelineTop();
        EditorModel.Element selected = selected();
        c.drawTextWithShadow(textRenderer, selected == null ? "SCENE INSPECTOR" : "ELEMENT INSPECTOR", x + 8, TOP_H + 8, 0xFF8C9BAA);
        int y = TOP_H + 24;
        if (selected != null) {
            c.drawTextWithShadow(textRenderer, trim(selected.label + " · " + selected.key(), RIGHT_W - 16), x + 8, y, 0xFFF1F6FA);
            y += 15;
            int bx = x + 8;
            bx = smallButton(c, mx, my, bx, y, 54, selected.enabled ? "Enabled" : "Disabled", () -> mutate("Element visibility", () -> selected.enabled = !selected.enabled)) + 4;
            bx = smallButton(c, mx, my, bx, y, 48, selected.locked ? "Locked" : "Lock", () -> mutate("Element lock", () -> selected.locked = !selected.locked)) + 4;
            bx = smallButton(c, mx, my, bx, y, 54, "Duplicate", this::duplicateSelected) + 4;
            bx = smallButton(c, mx, my, bx, y, 44, "Delete", this::deleteSelected) + 4;
            smallButton(c, mx, my, bx, y, 72, "Copy JSON", this::copySelectedJson);
            y += 23;
            smallButton(c, mx, my, x + 8, y, 72, "Paste JSON", this::pasteSelectedJson);
            smallButton(c, mx, my, x + 84, y, 70, "Focus [F]", this::focusSelected);
            y += 27;
            buildElementRows(selected);
        } else {
            c.drawTextWithShadow(textRenderer, trim(project.name + " · " + project.sceneId, RIGHT_W - 16), x + 8, y, 0xFFF1F6FA);
            y += 21;
            buildProjectRows();
        }
        int rowY = y - (int) inspectorScroll;
        for (PropertyRow row : propertyRows) {
            row.screenY = rowY;
            if (rowY + 18 >= TOP_H + 2 && rowY < bottom - 32) drawPropertyRow(c, mx, my, row, rowY);
            rowY += 19;
        }
        valueEditor.setX(x + 8);
        valueEditor.setY(bottom - 24);
        valueEditor.setWidth(RIGHT_W - 16);
        if (valueEditor.isVisible()) c.drawTextWithShadow(textRenderer, "EDIT VALUE", x + 8, bottom - 35, 0xFF748595);
    }

    private void drawPropertyRow(DrawContext c, int mx, int my, PropertyRow row, int y) {
        int x = width - RIGHT_W + 7 + row.depth * 10;
        int right = width - 7;
        row.x1 = x;
        row.x2 = right;
        c.fill(x, y, right, y + 17, hovered(mx, my, x, y, right, y + 17) ? 0xFF26303A : 0xFF1C222A);
        int labelWidth = Math.min(132, Math.max(72, (right - x) / 2));
        c.drawTextWithShadow(textRenderer, trim(row.label, labelWidth - 6), x + 4, y + 4, row.container ? 0xFF9FB0BF : 0xFF8193A4);
        c.drawTextWithShadow(textRenderer, trim(propertyValue(row), right - x - labelWidth - 24), x + labelWidth, y + 4, row.valueColor());
        if (row.canAdd()) {
            int px = right - 18;
            c.fill(px, y + 1, right - 1, y + 16, 0xFF33404C);
            c.drawTextWithShadow(textRenderer, "+", px + 5, y + 4, 0xFFE7F4FF);
            addHit(px, y + 1, right - 1, y + 16, () -> addContainerValue(row));
        }
    }

    private void drawTimeline(DrawContext c, int mx, int my) {
        int top = timelineTop();
        int rulerTop = top + 29;
        int trackTop = rulerTop + 27;
        c.fill(0, top, width, top + 28, 0xFF171C22);
        c.drawTextWithShadow(textRenderer, "TIMELINE", 8, top + 9, 0xFFA9B8C5);
        c.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "zoom %.2f px/t", pixelsPerTick), 82, top + 9, 0xFF71808E);
        c.fill(0, rulerTop, width, rulerTop + 26, 0xFF12171D);
        c.fill(0, trackTop, TIMELINE_LABEL_W, height, 0xFF151A20);
        double step = niceStep(Math.max(0.05, 70.0 / Math.max(0.05, pixelsPerTick)));
        double first = Math.floor(timelineStartTick / step) * step;
        for (double tick = first; tick <= tickFromX(width) + step; tick += step) {
            int x = tickX(tick);
            if (x < TIMELINE_LABEL_W || x > width) continue;
            c.fill(x, rulerTop + 16, x + 1, height, 0x382D3944);
            c.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "%.1fs", tick / 20.0), x + 3, rulerTop + 5, 0xFF7E8D9A);
        }
        int visibleRow = 0;
        for (int i = Math.max(0, timelineScrollRows); i < project.elements.size(); i++) {
            int y = trackTop + visibleRow * ROW_H;
            if (y + ROW_H > height - 17) break;
            EditorModel.Element e = project.elements.get(i);
            boolean sel = e.editorId.equals(selectedId);
            c.fill(0, y, width, y + ROW_H - 1, sel ? 0xFF202C38 : (visibleRow % 2 == 0 ? 0xFF151B21 : 0xFF12181E));
            c.drawTextWithShadow(textRenderer, trim(e.key(), TIMELINE_LABEL_W - 38), 8, y + 7, e.enabled ? 0xFFD9E3EC : 0xFF68727C);
            c.drawTextWithShadow(textRenderer, e.enabled ? "●" : "○", TIMELINE_LABEL_W - 22, y + 7, e.enabled ? 0xFF69D79D : 0xFF68727C);
            int sx = Math.max(TIMELINE_LABEL_W, Math.min(width, tickX(e.startTick())));
            int ex = Math.max(TIMELINE_LABEL_W, Math.min(width, tickX(Math.max(e.endTick(), e.startTick() + .05))));
            if (ex < sx) { int swap = sx; sx = ex; ex = swap; }
            if (ex - sx < 3) ex = sx + 3;
            c.fill(sx, y + 3, Math.min(width, ex), y + ROW_H - 4, sel ? 0xFF3B7AA5 : (e.enabled ? 0xFF31566E : 0xFF3A4148));
            if (sel) {
                c.fill(sx, y + 3, sx + 2, y + ROW_H - 4, 0xFFB9E3FF);
                c.fill(Math.max(sx, ex - 2), y + 3, ex, y + ROW_H - 4, 0xFFB9E3FF);
            }
            clipHits.add(new ClipHit(e, sx, y + 2, ex, y + ROW_H - 3));
            drawKeyframes(c, e, y);
            visibleRow++;
        }
        int ph = tickX(preview.currentTick());
        if (ph >= TIMELINE_LABEL_W && ph <= width) {
            c.fill(ph - 1, rulerTop, ph + 1, height, 0xFFFFD565);
            c.fill(ph - 4, rulerTop, ph + 5, rulerTop + 5, 0xFFFFD565);
        }
        c.drawTextWithShadow(textRenderer, statusText(), 8, height - 13, 0xFF8798A8);
    }

    private void drawKeyframes(DrawContext c, EditorModel.Element element, int rowY) {
        ArrayList<JsonObject> keys = new ArrayList<>();
        collectTickObjects(element.data, keys);
        for (JsonObject key : keys) {
            if (!key.has("tick") || !key.get("tick").isJsonPrimitive()) continue;
            double local = safeDouble(key.get("tick"), Double.NaN);
            if (!Double.isFinite(local)) continue;
            int x = tickX(element.startTick() + local);
            if (x < TIMELINE_LABEL_W || x > width) continue;
            int cy = rowY + ROW_H / 2;
            int color = element.editorId.equals(selectedId) ? 0xFFFFC857 : 0xFF9D8451;
            c.fill(x - 1, cy - 4, x + 2, cy + 5, color);
            c.fill(x - 3, cy - 2, x + 4, cy + 3, color);
            keyHits.add(new KeyHit(element, key, x - 5, cy - 6, x + 6, cy + 7));
        }
    }

    private void drawViewportOverlay(DrawContext c) {
        int y = TOP_H + 8;
        c.drawTextWithShadow(textRenderer, preview.sceneCameraPreview() ? "SCENE CAMERA PREVIEW [C]" : "EDITOR CAMERA · RMB look · WASD · Q/E · Shift boost [C]", LEFT_W + 9, y, 0xD9E4EDF4);
        Vec3d p = preview.editorCameraPosition();
        String cam = String.format(Locale.ROOT, "XYZ %.2f %.2f %.2f · yaw %.1f · pitch %.1f · speed %.2f", p.x, p.y, p.z, preview.editorCameraYaw(), preview.editorCameraPitch(), cameraSpeed);
        c.drawTextWithShadow(textRenderer, cam, LEFT_W + 9, y + 13, 0xB9AAB8C4);
        if (preview.lastBuild() != null && !preview.lastBuild().errors().isEmpty()) {
            String errors = preview.lastBuild().errors().size() + " invalid draft element(s) skipped";
            c.drawTextWithShadow(textRenderer, errors, width - RIGHT_W - textRenderer.getWidth(errors) - 8, y, 0xFFFF9C7D);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled) && valueEditor.isFocused()) return true;
        double mx = click.x(), my = click.y();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && inViewport(mx, my)) {
            rightLook = true;
            return true;
        }
        for (Hit hit : List.copyOf(hits)) if (hit.contains(mx, my)) { hit.action.run(); return true; }
        for (PropertyRow row : propertyRows) {
            if (!row.contains(mx, my)) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && row.isEnum()) {
                row.set(new JsonPrimitive(EditorSchema.cycleEnum(row.type, row.value.getAsString(), -1)));
                markChanged("Enum changed");
            } else if (row.value != null && row.value.isJsonPrimitive() && row.value.getAsJsonPrimitive().isBoolean()) {
                checkpoint(); row.set(new JsonPrimitive(!row.value.getAsBoolean())); markChangedContinuous();
            } else if (row.isEnum()) {
                checkpoint(); row.set(new JsonPrimitive(EditorSchema.cycleEnum(row.type, row.value.getAsString(), 1))); markChangedContinuous();
            } else if (row.container) toggleCollapsed(row.path);
            else if (row.value == null || row.value.isJsonNull()) {
                checkpoint(); row.set(EditorSchema.defaultValue(row.type, row.label, project.durationTicks)); markChangedContinuous();
            } else beginPropertyEdit(row);
            return true;
        }
        if (my >= timelineTop()) {
            for (KeyHit hit : keyHits) if (hit.contains(mx, my)) {
                select(hit.element); dragMode = DragMode.KEYFRAME; dragElement = hit.element; dragKeyframe = hit.key;
                dragMouseStartTick = tickFromX(mx); dragOriginalKeyTick = safeDouble(hit.key.get("tick"), 0); checkpoint(); return true;
            }
            for (ClipHit hit : clipHits) if (hit.contains(mx, my)) {
                select(hit.element); if (hit.element.locked) return true;
                dragElement = hit.element; dragMouseStartTick = tickFromX(mx); dragOriginalStart = hit.element.startTick(); dragOriginalEnd = hit.element.endTick();
                dragMode = Math.abs(mx - hit.x1) <= 5 ? DragMode.CLIP_LEFT : Math.abs(mx - hit.x2) <= 5 ? DragMode.CLIP_RIGHT : DragMode.CLIP_MOVE;
                checkpoint(); return true;
            }
            if (mx >= TIMELINE_LABEL_W) { dragMode = DragMode.PLAYHEAD; preview.setTick(client, tickFromX(mx)); return true; }
        }
        valueEditor.setFocused(false);
        valueEditor.setVisible(false);
        activeProperty = null;
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (rightLook && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            preview.setEditorCamera(preview.editorCameraPosition(), preview.editorCameraYaw() + (float)(dx * .28), preview.editorCameraPitch() + (float)(dy * .28));
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, dx, dy);
        double tick = tickFromX(click.x());
        if (dragMode == DragMode.PLAYHEAD) { preview.setTick(client, tick); return true; }
        if (dragElement == null || dragElement.locked) return false;
        double delta = snap(tick - dragMouseStartTick);
        switch (dragMode) {
            case CLIP_MOVE -> {
                double length = dragOriginalEnd - dragOriginalStart;
                double start = Math.max(0, snap(dragOriginalStart + delta));
                dragElement.setStartTick(start); dragElement.setEndTick(start + Math.max(0, length)); markChangedContinuous(); return true;
            }
            case CLIP_LEFT -> { dragElement.setStartTick(Math.max(0, Math.min(dragOriginalEnd, snap(dragOriginalStart + delta)))); markChangedContinuous(); return true; }
            case CLIP_RIGHT -> { dragElement.setEndTick(Math.max(dragElement.startTick(), snap(dragOriginalEnd + delta))); project.durationTicks = Math.max(project.durationTicks, dragElement.endTick()); markChangedContinuous(); return true; }
            case KEYFRAME -> { if (dragKeyframe != null) { dragKeyframe.addProperty("tick", Math.max(0, snap(dragOriginalKeyTick + delta))); markChangedContinuous(); return true; } }
            default -> { }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightLook = false;
        if (dragMode != DragMode.NONE) {
            if (dragMode != DragMode.PLAYHEAD) sortNestedTicks(project);
            dragMode = DragMode.NONE; dragElement = null; dragKeyframe = null; return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (my >= timelineTop()) {
            if (isShiftDown()) timelineStartTick = Math.max(-project.durationTicks, timelineStartTick - verticalAmount * Math.max(1, 80 / Math.max(.05, pixelsPerTick)));
            else if (mx < TIMELINE_LABEL_W) timelineScrollRows = Math.max(0, Math.min(Math.max(0, project.elements.size() - 1), timelineScrollRows - (int)Math.signum(verticalAmount)));
            else {
                double underMouse = tickFromX(mx);
                pixelsPerTick = Math.max(.05, Math.min(100, pixelsPerTick * Math.pow(1.18, verticalAmount)));
                timelineStartTick = underMouse - (mx - TIMELINE_LABEL_W) / pixelsPerTick;
            }
            return true;
        }
        if (mx < LEFT_W) { sidebarScroll = Math.max(0, sidebarScroll - verticalAmount * 24); return true; }
        if (mx >= width - RIGHT_W) { inspectorScroll = Math.max(0, inspectorScroll - verticalAmount * 26); return true; }
        if (inViewport(mx, my) && !preview.sceneCameraPreview()) { cameraSpeed = Math.max(.02, Math.min(20, cameraSpeed * Math.pow(1.2, verticalAmount))); return true; }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (valueEditor.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                valueEditor.setFocused(false); valueEditor.setVisible(false); activeProperty = null; return true;
            }
            if (super.keyPressed(input)) return true;
        }
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && input.key() == GLFW.GLFW_KEY_S) { saveProject(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_D) { duplicateSelected(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_C && selected() != null) { copySelectedJson(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_V && selected() != null) { pasteSelectedJson(); return true; }
        if (input.key() == GLFW.GLFW_KEY_SPACE) { preview.togglePlay(client); return true; }
        if (input.key() == GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
        if (input.key() == GLFW.GLFW_KEY_HOME) { preview.setTick(client, 0); return true; }
        if (input.key() == GLFW.GLFW_KEY_END) { preview.setTick(client, project.durationTicks); return true; }
        if (input.key() == GLFW.GLFW_KEY_F) { focusSelected(); return true; }
        if (input.key() == GLFW.GLFW_KEY_C) { preview.setSceneCameraPreview(client, !preview.sceneCameraPreview()); return true; }
        if (input.key() == GLFW.GLFW_KEY_K) { addKeyframesAtPlayhead(); return true; }
        return super.keyPressed(input);
    }

    private void updateFreeCamera() {
        if (preview.sceneCameraPreview() || !rightLook || client == null) return;
        long window = client.getWindow().getHandle();
        double speed = cameraSpeed * (pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 4 : 1);
        float yaw = preview.editorCameraYaw(), pitch = preview.editorCameraPitch();
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d flat = new Vec3d(-Math.sin(ry), 0, Math.cos(ry));
        Vec3d right = new Vec3d(-flat.z, 0, flat.x);
        Vec3d pos = preview.editorCameraPosition();
        boolean changed = false;
        if (pressed(window, GLFW.GLFW_KEY_W)) { pos = pos.add(forward.multiply(speed)); changed = true; }
        if (pressed(window, GLFW.GLFW_KEY_S)) { pos = pos.subtract(forward.multiply(speed)); changed = true; }
        if (pressed(window, GLFW.GLFW_KEY_D)) { pos = pos.subtract(right.multiply(speed)); changed = true; }
        if (pressed(window, GLFW.GLFW_KEY_A)) { pos = pos.add(right.multiply(speed)); changed = true; }
        if (pressed(window, GLFW.GLFW_KEY_E)) { pos = pos.add(0, speed, 0); changed = true; }
        if (pressed(window, GLFW.GLFW_KEY_Q)) { pos = pos.add(0, -speed, 0); changed = true; }
        if (changed) preview.setEditorCamera(pos, yaw, pitch);
    }

    private void addElement(CineFxBridge.ElementType type) {
        checkpoint();
        EditorModel.Element e = CineFxBridge.newDraft(type, project.durationTicks, project.elements.size());
        ensureUniqueKey(e); project.elements.add(e); select(e); markChangedContinuous(); toast("Added " + type.displayName());
    }

    private void duplicateSelected() {
        EditorModel.Element e = selected(); if (e == null) return;
        checkpoint(); EditorModel.Element copy = e.duplicate(); ensureUniqueKey(copy); project.elements.add(copy); select(copy); markChangedContinuous(); toast("Duplicated " + e.key());
    }

    private void deleteSelected() {
        EditorModel.Element e = selected(); if (e == null || e.locked) return;
        checkpoint(); project.elements.remove(e); select(null); markChangedContinuous(); toast("Element deleted");
    }

    private void newProject() {
        PresetStore.autosave(project); history.clear(); project = EditorModel.Project.fresh(client); preview.setProject(project); select(null); sidebarScroll = 0; inspectorScroll = 0; fitTimeline(); toast("New project");
    }

    private void saveProject() {
        try { PresetStore.save(project, project.sourcePreset == null ? project.name : project.sourcePreset); toast("Saved preset: " + project.sourcePreset); }
        catch (Exception e) { toast("Save failed: " + shortError(e)); }
    }

    private void loadPreset(String name) {
        try { PresetStore.autosave(project); project = PresetStore.load(name); history.clear(); preview.setProject(project); select(null); fitTimeline(); toast("Loaded " + name); }
        catch (Exception e) { toast("Load failed: " + shortError(e)); }
    }

    private void restoreAutosave() {
        EditorModel.Project restored = PresetStore.loadAutosave();
        if (restored == null) { toast("No autosave available"); return; }
        checkpoint(); project = restored; preview.setProject(project); select(null); fitTimeline(); toast("Autosave restored");
    }

    private void importScene(SceneDefinition scene) {
        PresetStore.autosave(project); history.clear(); project = CineFxBridge.importScene(scene);
        if (client != null && client.player != null) {
            project.anchorX = client.player.getX();
            project.anchorY = client.player.getY();
            project.anchorZ = client.player.getZ();
        }
        preview.setProject(project); select(null); fitTimeline(); leftTab = LeftTab.PROJECT; toast("Imported " + scene.id());
    }

    private void undo() {
        EditorModel.Project before = project; project = history.undo(project);
        if (project == before) { toast("Nothing to undo"); return; }
        preview.setProject(project); select(null); toast("Undo");
    }

    private void redo() {
        EditorModel.Project before = project; project = history.redo(project);
        if (project == before) { toast("Nothing to redo"); return; }
        preview.setProject(project); select(null); toast("Redo");
    }

    private void validateProject() {
        CineFxBridge.BuildResult result = preview.validateOnly();
        if (result == null || result.errors().isEmpty()) toast("Valid CineFX scene · " + project.elements.size() + " elements");
        else toast(result.errors().size() + " invalid: " + result.errors().getFirst().key() + " · " + result.errors().getFirst().message());
    }

    private void copySelectedJson() {
        EditorModel.Element e = selected(); if (e == null || client == null) return;
        GLFW.glfwSetClipboardString(client.getWindow().getHandle(), EditorModel.GSON.toJson(e.data)); toast("Element JSON copied");
    }

    private void pasteSelectedJson() {
        EditorModel.Element e = selected(); if (e == null || client == null || e.locked) return;
        try {
            String raw = GLFW.glfwGetClipboardString(client.getWindow().getHandle());
            JsonElement parsed = JsonParser.parseString(raw == null ? "" : raw);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("JSON object required");
            checkpoint(); e.data = parsed.getAsJsonObject(); markChangedContinuous(); toast("Element JSON pasted");
        } catch (Exception ex) { toast("Invalid clipboard JSON: " + shortError(ex)); }
    }

    private void focusSelected() {
        EditorModel.Element e = selected(); if (e == null) return;
        Vec3d local = CineFxBridge.tryResolveEditorPosition(e); if (local == null) { toast("No position field found"); return; }
        Vec3d target = project.anchor().add(local), forward = cameraForward(preview.editorCameraYaw(), preview.editorCameraPitch());
        preview.setSceneCameraPreview(client, false); preview.setEditorCamera(target.subtract(forward.multiply(6)), preview.editorCameraYaw(), preview.editorCameraPitch()); toast("Focused " + e.key());
    }

    private void addKeyframesAtPlayhead() {
        EditorModel.Element e = selected(); if (e == null || e.locked) { toast("Select an editable element first"); return; }
        checkpoint(); int added = addKeyframesRecursive(e.data, Math.max(0, preview.currentTick() - e.startTick()));
        if (added == 0) toast("Selection has no editable track");
        else { sortNestedTicks(project); markChangedContinuous(); toast("Added " + added + " keyframe(s)"); }
    }

    private int addKeyframesRecursive(JsonElement value, double tick) {
        if (value == null || value.isJsonNull()) return 0;
        int count = 0;
        if (value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            String kind = o.has("$kind") ? safeString(o.get("$kind")) : "";
            if (kind.endsWith("Track") && !kind.equals("PathTrack") && o.has("keys") && o.get("keys").isJsonArray()) {
                JsonArray keys = o.getAsJsonArray("keys"); if (!hasTick(keys, tick)) { keys.add(EditorSchema.duplicateTrackKey(o, tick)); count++; }
            } else if (kind.equals("PathTrack") && o.has("points") && o.get("points").isJsonArray()) {
                JsonArray points = o.getAsJsonArray("points"); if (!hasTick(points, tick)) { points.add(EditorSchema.duplicatePathPoint(o, tick)); count++; }
            }
            for (Map.Entry<String, JsonElement> entry : o.entrySet()) if (!entry.getKey().equals("keys") && !entry.getKey().equals("points")) count += addKeyframesRecursive(entry.getValue(), tick);
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) count += addKeyframesRecursive(child, tick);
        return count;
    }

    private void buildElementRows(EditorModel.Element element) {
        Type root = EditorSchema.root(element);
        for (Map.Entry<String, JsonElement> entry : element.data.entrySet()) {
            if (entry.getKey().equals("$type")) continue;
            flatten(entry.getKey(), entry.getValue(), EditorSchema.field(root, entry.getKey()), 0, entry.getKey(), element.data, entry.getKey(), null, -1, null);
        }
    }

    private void flatten(String label, JsonElement value, Type type, int depth, String path,
                         JsonObject parentObject, String parentKey, JsonArray parentArray, int parentIndex, JsonObject owningObject) {
        Consumer<JsonElement> setter = replacement -> {
            if (parentObject != null) parentObject.add(parentKey, replacement == null ? JsonNull.INSTANCE : replacement);
            else if (parentArray != null) parentArray.set(parentIndex, replacement == null ? JsonNull.INSTANCE : replacement);
        };
        boolean container = value != null && (value.isJsonObject() || value.isJsonArray());
        PropertyRow row = new PropertyRow(label, path, value == null ? JsonNull.INSTANCE : value, type, depth, container, setter);
        row.parentObject = owningObject;
        propertyRows.add(row);
        if (!container || collapsed.contains(path)) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("$type") || entry.getKey().equals("$kind")) continue;
                flatten(entry.getKey(), entry.getValue(), EditorSchema.field(type, entry.getKey()), depth + 1, path + "." + entry.getKey(), object, entry.getKey(), null, -1, object);
            }
        } else {
            JsonArray array = value.getAsJsonArray(); Type item = EditorSchema.item(type);
            for (int i = 0; i < array.size(); i++) flatten("[" + i + "]", array.get(i), item, depth + 1, path + "[" + i + "]", null, null, array, i, parentObject);
        }
    }

    private void buildProjectRows() {
        projectRow("name", new JsonPrimitive(project.name), String.class, v -> project.name = v.getAsString());
        projectRow("sceneId", new JsonPrimitive(project.sceneId), String.class, v -> project.sceneId = v.getAsString());
        projectRow("durationTicks", new JsonPrimitive(project.durationTicks), double.class, v -> project.durationTicks = Math.max(1, v.getAsDouble()));
        projectRow("priority", new JsonPrimitive(project.priority), int.class, v -> project.priority = v.getAsInt());
        projectRow("looping", new JsonPrimitive(project.looping), boolean.class, v -> project.looping = v.getAsBoolean());
        projectRow("anchor X", new JsonPrimitive(project.anchorX), double.class, v -> project.anchorX = v.getAsDouble());
        projectRow("anchor Y", new JsonPrimitive(project.anchorY), double.class, v -> project.anchorY = v.getAsDouble());
        projectRow("anchor Z", new JsonPrimitive(project.anchorZ), double.class, v -> project.anchorZ = v.getAsDouble());
        projectRow("seed", new JsonPrimitive(project.seed), long.class, v -> project.seed = v.getAsLong());
        PropertyRow vars = PropertyRow.heading("variables", "$variables", () -> { checkpoint(); project.variables.put(uniqueMapKey(project.variables, "variable"), "value"); markChangedContinuous(); });
        propertyRows.add(vars);
        for (Map.Entry<String, String> e : new ArrayList<>(project.variables.entrySet())) { String key = e.getKey(); projectRow("  " + key, new JsonPrimitive(e.getValue()), String.class, v -> project.variables.put(key, v.getAsString())); }
        PropertyRow meta = PropertyRow.heading("metadata", "$metadata", () -> { checkpoint(); project.metadata.put(uniqueMapKey(project.metadata, "meta"), "value"); markChangedContinuous(); });
        propertyRows.add(meta);
        for (Map.Entry<String, String> e : new ArrayList<>(project.metadata.entrySet())) { String key = e.getKey(); projectRow("  " + key, new JsonPrimitive(e.getValue()), String.class, v -> project.metadata.put(key, v.getAsString())); }
    }

    private void projectRow(String label, JsonElement value, Type type, Consumer<JsonElement> setter) {
        propertyRows.add(new PropertyRow(label, "$project." + label, value, type, 0, false, setter));
    }

    private void addContainerValue(PropertyRow row) {
        if (row.addAction != null) { row.addAction.run(); return; }
        checkpoint();
        if (row.value == null || row.value.isJsonNull()) row.set(EditorSchema.defaultValue(row.type, row.label, project.durationTicks));
        else if (row.value.isJsonArray()) {
            JsonArray a = row.value.getAsJsonArray();
            double local = Math.max(0, preview.currentTick() - (selected() == null ? 0 : selected().startTick()));
            if ("keys".equals(row.label) && row.parentObject != null) a.add(EditorSchema.duplicateTrackKey(row.parentObject, local));
            else if ("points".equals(row.label) && row.parentObject != null) a.add(EditorSchema.duplicatePathPoint(row.parentObject, local));
            else a.add(EditorSchema.defaultValue(EditorSchema.item(row.type), "item", project.durationTicks));
        }
        markChangedContinuous();
    }

    private void beginPropertyEdit(PropertyRow row) {
        if (row.setter == null) return;
        checkpoint(); activeProperty = row; changingEditorText = true; valueEditor.setText(editText(row.value)); changingEditorText = false;
        valueEditor.setVisible(true); valueEditor.setFocused(true); valueEditor.setCursorToEnd(false);
    }

    private void onEditorTextChanged(String text) {
        if (changingEditorText || activeProperty == null) return;
        try { JsonElement v = parseLike(activeProperty.value, text); activeProperty.set(v); activeProperty.value = v; markChangedContinuous(); }
        catch (RuntimeException ignored) { }
    }

    private JsonElement parseLike(JsonElement original, String text) {
        if (original == null || original.isJsonNull()) return new JsonPrimitive(text);
        if (!original.isJsonPrimitive()) return JsonParser.parseString(text);
        JsonPrimitive p = original.getAsJsonPrimitive();
        if (p.isBoolean()) return new JsonPrimitive(Boolean.parseBoolean(text));
        if (p.isNumber()) return new JsonPrimitive(new BigDecimal(text));
        return new JsonPrimitive(text);
    }

    private void checkpoint() { history.checkpoint(project); }
    private void markChanged(String message) { checkpoint(); markChangedContinuous(); toast(message); }
    private void markChangedContinuous() { project.dirty = true; preview.markDirty(); }
    private void mutate(String message, Runnable r) { checkpoint(); r.run(); markChangedContinuous(); toast(message); }

    private void select(EditorModel.Element e) {
        selectedId = e == null ? null : e.editorId; inspectorScroll = 0; activeProperty = null;
        if (valueEditor != null) { valueEditor.setFocused(false); valueEditor.setVisible(false); }
    }
    private EditorModel.Element selected() { return project.find(selectedId); }

    private void ensureUniqueKey(EditorModel.Element e) {
        Set<String> keys = new HashSet<>(); for (EditorModel.Element existing : project.elements) keys.add(existing.key());
        String base = e.key(); if (base == null || base.isBlank() || base.startsWith("element_")) base = slug(e.label);
        String key = base; int suffix = 2; while (keys.contains(key)) key = base + "_" + suffix++; e.setKey(key);
    }

    private void fitTimeline() { pixelsPerTick = Math.max(.15, Math.max(240, width - TIMELINE_LABEL_W - 20) / Math.max(1.0, project.durationTicks)); timelineStartTick = 0; }
    private int timelineTop() { return Math.max(TOP_H + 160, height - TIMELINE_H); }
    private boolean inViewport(double x, double y) { return x >= LEFT_W && x < width - RIGHT_W && y >= TOP_H && y < timelineTop(); }
    private int tickX(double tick) { return TIMELINE_LABEL_W + (int)Math.round((tick - timelineStartTick) * pixelsPerTick); }
    private double tickFromX(double x) { return timelineStartTick + (x - TIMELINE_LABEL_W) / Math.max(.0001, pixelsPerTick); }
    private double snap(double tick) { return Math.round(tick * 4) / 4.0; }
    private double niceStep(double value) { double exp = Math.pow(10, Math.floor(Math.log10(Math.max(1e-6, value)))); double n = value / exp; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * exp; }

    private int button(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action) {
        c.fill(x, y, x + w, y + 21, hovered(mx, my, x, y, x + w, y + 21) ? 0xFF33404D : 0xFF242B34);
        c.drawTextWithShadow(textRenderer, trim(text, w - 8), x + 4, y + 7, 0xFFE7EEF4); addHit(x, y, x + w, y + 21, action); return x + w;
    }
    private int smallButton(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action) {
        c.fill(x, y, x + w, y + 18, hovered(mx, my, x, y, x + w, y + 18) ? 0xFF34414C : 0xFF252C34);
        c.drawTextWithShadow(textRenderer, trim(text, w - 6), x + 3, y + 5, 0xFFD8E3EC); addHit(x, y, x + w, y + 18, action); return x + w;
    }
    private void tabButton(DrawContext c, int mx, int my, int x, int y, int w, String text, LeftTab tab) {
        c.fill(x, y, x + w - 2, y + 20, leftTab == tab ? 0xFF33485A : hovered(mx, my, x, y, x + w - 2, y + 20) ? 0xFF29333D : 0xFF20262D);
        c.drawTextWithShadow(textRenderer, text, x + 7, y + 6, 0xFFDCE6EE); addHit(x, y, x + w - 2, y + 20, () -> { leftTab = tab; sidebarScroll = 0; });
    }
    private int leftAction(DrawContext c, int mx, int my, int y, int bottom, String text, Runnable action) {
        if (visibleY(y, bottom)) { c.fill(6, y - 2, LEFT_W - 6, y + 17, hovered(mx, my, 6, y - 2, LEFT_W - 6, y + 17) ? 0xFF31404C : 0xFF242C34); c.drawTextWithShadow(textRenderer, text, 11, y + 3, 0xFFDCE6EE); addHit(6, y - 2, LEFT_W - 6, y + 17, action); }
        return y + 23;
    }
    private void addHit(int x1, int y1, int x2, int y2, Runnable action) { if (y2 >= TOP_H && y1 <= height) hits.add(new Hit(x1, y1, x2, y2, action)); }
    private boolean visibleY(int y, int bottom) { return y >= TOP_H + 45 && y < bottom - 18; }

    private void collectTickObjects(JsonElement value, List<JsonObject> out) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) { JsonObject o = value.getAsJsonObject(); if (o.has("tick") && o.get("tick").isJsonPrimitive()) out.add(o); for (Map.Entry<String, JsonElement> e : o.entrySet()) collectTickObjects(e.getValue(), out); }
        else if (value.isJsonArray()) for (JsonElement e : value.getAsJsonArray()) collectTickObjects(e, out);
    }
    private void sortNestedTicks(EditorModel.Project p) { for (EditorModel.Element e : p.elements) sortNestedTicks(e.data); }
    private void sortNestedTicks(JsonElement value) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) sortNestedTicks(e.getValue());
        else if (value.isJsonArray()) {
            JsonArray a = value.getAsJsonArray(); ArrayList<JsonObject> values = new ArrayList<>(); boolean sortable = !a.isEmpty();
            for (JsonElement item : a) { if (!item.isJsonObject() || !item.getAsJsonObject().has("tick")) { sortable = false; break; } values.add(item.getAsJsonObject()); }
            if (sortable) { values.sort(Comparator.comparingDouble(o -> safeDouble(o.get("tick"), 0))); while (!a.isEmpty()) a.remove(a.size() - 1); values.forEach(a::add); }
            else for (JsonElement item : a) sortNestedTicks(item);
        }
    }

    private boolean hasTick(JsonArray a, double tick) { for (JsonElement v : a) if (v.isJsonObject() && Math.abs(safeDouble(v.getAsJsonObject().get("tick"), -999999) - tick) < .001) return true; return false; }
    private void toggleCollapsed(String path) { if (!collapsed.add(path)) collapsed.remove(path); }
    private String propertyValue(PropertyRow row) {
        if (row.value == null || row.value.isJsonNull()) return "null";
        if (row.value.isJsonArray()) return (collapsed.contains(row.path) ? "▶ " : "▼ ") + "[" + row.value.getAsJsonArray().size() + "]";
        if (row.value.isJsonObject()) { JsonObject o = row.value.getAsJsonObject(); String kind = o.has("$kind") ? safeString(o.get("$kind")) : o.has("$type") ? simpleType(safeString(o.get("$type"))) : "object"; return (collapsed.contains(row.path) ? "▶ " : "▼ ") + kind; }
        if (row.isEnum()) return "‹ " + row.value.getAsString() + " ›";
        return row.value.getAsString();
    }
    private String statusText() {
        if (System.currentTimeMillis() <= statusUntil) return status;
        CineFxBridge.BuildResult result = preview.lastBuild();
        if (result != null && !result.errors().isEmpty()) return result.errors().size() + " preview validation error(s)";
        return project.dirty ? "Unsaved changes · autosave active" : "Saved · CineFX runtime connected";
    }
    private void toast(String s) { status = s; statusUntil = System.currentTimeMillis() + 4500; }
    private String editText(JsonElement v) { return v == null || v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : EditorModel.GSON.toJson(v); }
    private String trim(String v, int w) { return textRenderer.trimToWidth(v == null ? "" : v, Math.max(4, w)); }

    private static boolean hovered(double mx, double my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx <= x2 && my >= y1 && my <= y2; }
    private static double safeDouble(JsonElement v, double fallback) { try { return v == null || v.isJsonNull() ? fallback : v.getAsDouble(); } catch (RuntimeException e) { return fallback; } }
    private static String safeString(JsonElement v) { try { return v == null || v.isJsonNull() ? "" : v.getAsString(); } catch (RuntimeException e) { return ""; } }
    private static String simpleType(String v) { int i = v == null ? -1 : Math.max(v.lastIndexOf('.'), v.lastIndexOf('$')); return i >= 0 ? v.substring(i + 1) : v; }
    private static String shortError(Throwable t) { String s = t.getMessage(); return s == null || s.isBlank() ? t.getClass().getSimpleName() : s; }
    private static String slug(String v) { String s = v == null ? "element" : v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); return s.isBlank() ? "element" : s; }
    private static boolean pressed(long window, int key) { return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }
    private boolean isShiftDown() { if (client == null) return false; long w = client.getWindow().getHandle(); return pressed(w, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(w, GLFW.GLFW_KEY_RIGHT_SHIFT); }
    private static Vec3d cameraForward(float yaw, float pitch) { double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch); return new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)); }
    private static String uniqueMapKey(Map<String, String> map, String base) { int i = 1; String key = base; while (map.containsKey(key)) key = base + "_" + i++; return key; }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }
    private record ClipHit(EditorModel.Element element, int x1, int y1, int x2, int y2) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }
    private record KeyHit(EditorModel.Element element, JsonObject key, int x1, int y1, int x2, int y2) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }

    private static final class PropertyRow {
        final String label, path;
        JsonElement value;
        final Type type;
        final int depth;
        final boolean container;
        final Consumer<JsonElement> setter;
        JsonObject parentObject;
        Runnable addAction;
        int x1, x2, screenY;
        PropertyRow(String label, String path, JsonElement value, Type type, int depth, boolean container, Consumer<JsonElement> setter) {
            this.label = label; this.path = path; this.value = value; this.type = type; this.depth = depth; this.container = container; this.setter = setter;
        }
        static PropertyRow heading(String label, String path, Runnable add) { PropertyRow r = new PropertyRow(label, path, new JsonObject(), Map.class, 0, true, null); r.addAction = add; return r; }
        void set(JsonElement replacement) { if (setter != null) setter.accept(replacement); value = replacement; }
        boolean contains(double x, double y) { return x >= x1 && x <= x2 && y >= screenY && y <= screenY + 17; }
        boolean isEnum() { Class<?> raw = EditorSchema.raw(type); return raw != null && raw.isEnum(); }
        boolean canAdd() { return addAction != null || value == null || value.isJsonNull() || value.isJsonArray(); }
        int valueColor() { if (value == null || value.isJsonNull()) return 0xFFB47B78; if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return 0xFF8FD9B7; if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return 0xFF8EC5E8; return 0xFFC8D3DC; }
    }
}
