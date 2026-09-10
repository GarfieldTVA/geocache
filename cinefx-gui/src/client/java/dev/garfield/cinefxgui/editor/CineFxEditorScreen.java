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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Full-screen, in-world authoring surface inspired by Flashback/NLE timelines and Blender navigation.
 * Rendering and playback are delegated to the separate CineFX mod through its public API.
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
        this.project = EditorModel.Project.fresh(MinecraftClient.getInstance());
        this.preview = new PreviewController(project);
    }

    @Override
    protected void init() {
        int x = Math.max(4, width - RIGHT_W + 8);
        int y = Math.max(TOP_H + 20, height - TIMELINE_H - 25);
        int w = Math.max(100, RIGHT_W - 16);
        valueEditor = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal("Property value"));
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

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Intentionally transparent: the live Minecraft world is the editor viewport.
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

    private void drawToolbar(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "CineFX GUI", 8, 10, 0xFFF2F6FA);
        int x = 84;
        x = button(context, mouseX, mouseY, x, 4, 42, 21, "New", this::newProject) + 4;
        x = button(context, mouseX, mouseY, x, 4, 46, 21, "Save", this::saveProject) + 4;
        x = button(context, mouseX, mouseY, x, 4, 34, 21, "↶", this::undo) + 4;
        x = button(context, mouseX, mouseY, x, 4, 34, 21, "↷", this::redo) + 10;
        x = button(context, mouseX, mouseY, x, 4, 58, 21, preview.playing() ? "Pause" : "Play", () -> preview.togglePlay(client)) + 4;
        x = button(context, mouseX, mouseY, x, 4, 46, 21, "Stop", () -> preview.stopAndRewind(client)) + 4;
        x = button(context, mouseX, mouseY, x, 4, 66, 21, "Key all", this::addKeyframesAtPlayhead) + 10;
        x = button(context, mouseX, mouseY, x, 4, 106, 21,
                preview.sceneCameraPreview() ? "Scene camera" : "Editor camera",
                () -> preview.setSceneCameraPreview(client, !preview.sceneCameraPreview())) + 4;
        button(context, mouseX, mouseY, x, 4, 58, 21, "Validate", this::validateProject);

        String time = String.format(Locale.ROOT, "%.2fs / %.2fs", preview.currentTick() / 20.0, project.durationTicks / 20.0);
        int tw = textRenderer.getWidth(time);
        context.drawTextWithShadow(textRenderer, time, Math.max(x + 70, width - tw - 8), 10, 0xFFD1DBE6);
    }

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        int bottom = timelineTop();
        context.drawTextWithShadow(textRenderer, "LIBRARY", 8, TOP_H + 8, 0xFF8C9BAA);
        int tabY = TOP_H + 21;
        int tabW = (LEFT_W - 12) / 3;
        tabButton(context, mouseX, mouseY, 4, tabY, tabW, "Project", LeftTab.PROJECT);
        tabButton(context, mouseX, mouseY, 4 + tabW, tabY, tabW, "Add", LeftTab.ADD);
        tabButton(context, mouseX, mouseY, 4 + tabW * 2, tabY, tabW, "API", LeftTab.API);

        int y = tabY + 27 - (int)sidebarScroll;
        if (leftTab == LeftTab.PROJECT) y = drawProjectLibrary(context, mouseX, mouseY, y, bottom);
        else if (leftTab == LeftTab.ADD) y = drawAddLibrary(context, mouseX, mouseY, y, bottom);
        else y = drawApiLibrary(context, mouseX, mouseY, y, bottom);
    }

    private int drawProjectLibrary(DrawContext context, int mouseX, int mouseY, int y, int bottom) {
        y = leftAction(context, mouseX, mouseY, y, bottom, "Save preset", this::saveProject);
        y = leftAction(context, mouseX, mouseY, y, bottom, "Restore autosave", this::restoreAutosave);
        y += 6;
        if (visibleY(y, bottom)) context.drawTextWithShadow(textRenderer, "PRESETS", 8, y, 0xFF748596);
        y += 15;
        for (String preset : PresetStore.list()) {
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> loadPreset(preset));
                context.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mouseX, mouseY, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF28313D : 0xFF1D232C);
                context.drawTextWithShadow(textRenderer, trim(preset, LEFT_W - 24), 12, yy + 2, 0xFFD8E0E8);
            }
            y += 20;
        }
        return y;
    }

    private int drawAddLibrary(DrawContext context, int mouseX, int mouseY, int y, int bottom) {
        String category = null;
        for (CineFxBridge.ElementType type : CineFxBridge.elementTypes()) {
            if (!type.category().equals(category)) {
                category = type.category();
                if (visibleY(y, bottom)) context.drawTextWithShadow(textRenderer, category.toUpperCase(Locale.ROOT), 8, y, 0xFF778A9B);
                y += 15;
            }
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> addElement(type));
                context.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mouseX, mouseY, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF2A3440 : 0xFF1C232B);
                context.drawTextWithShadow(textRenderer, "+  " + trim(type.displayName(), LEFT_W - 34), 11, yy + 2, 0xFFD8E4EE);
            }
            y += 20;
        }
        return y;
    }

    private int drawApiLibrary(DrawContext context, int mouseX, int mouseY, int y, int bottom) {
        if (visibleY(y, bottom)) context.drawTextWithShadow(textRenderer, "REGISTERED CINEFX SCENES", 8, y, 0xFF778A9B);
        y += 17;
        ArrayList<SceneDefinition> scenes = new ArrayList<>(CineFxApi.scenes());
        scenes.removeIf(scene -> scene.id().getNamespace().equals("cinefx_gui") && scene.id().getPath().endsWith("_runtime"));
        scenes.sort(Comparator.comparing(scene -> scene.id().toString()));
        for (SceneDefinition scene : scenes) {
            if (visibleY(y, bottom)) {
                int yy = y;
                addHit(6, yy - 2, LEFT_W - 6, yy + 16, () -> importScene(scene));
                context.fill(6, yy - 2, LEFT_W - 6, yy + 16, hovered(mouseX, mouseY, 6, yy - 2, LEFT_W - 6, yy + 16) ? 0xFF2A3440 : 0xFF1C232B);
                context.drawTextWithShadow(textRenderer, trim(scene.id().toString(), LEFT_W - 22), 10, yy + 2, 0xFFD7E4ED);
            }
            y += 20;
        }
        if (scenes.isEmpty() && visibleY(y, bottom)) context.drawTextWithShadow(textRenderer, "No registered scenes", 10, y, 0xFF78838E);
        return y;
    }

    private void drawInspector(DrawContext context, int mouseX, int mouseY) {
        int x = width - RIGHT_W;
        int bottom = timelineTop();
        context.drawTextWithShadow(textRenderer, selected() == null ? "SCENE INSPECTOR" : "ELEMENT INSPECTOR", x + 8, TOP_H + 8, 0xFF8C9BAA);
        int y = TOP_H + 24;

        EditorModel.Element selected = selected();
        if (selected != null) {
            context.drawTextWithShadow(textRenderer, trim(selected.label + "  ·  " + selected.key(), RIGHT_W - 16), x + 8, y, 0xFFF1F6FA);
            y += 15;
            int bx = x + 8;
            bx = smallButton(context, mouseX, mouseY, bx, y, 54, selected.enabled ? "Enabled" : "Disabled", () -> mutate("Toggle element", () -> selected.enabled = !selected.enabled)) + 4;
            bx = smallButton(context, mouseX, mouseY, bx, y, 48, selected.locked ? "Locked" : "Lock", () -> mutate("Lock element", () -> selected.locked = !selected.locked)) + 4;
            bx = smallButton(context, mouseX, mouseY, bx, y, 54, "Duplicate", this::duplicateSelected) + 4;
            bx = smallButton(context, mouseX, mouseY, bx, y, 44, "Delete", this::deleteSelected) + 4;
            smallButton(context, mouseX, mouseY, bx, y, 72, "Copy JSON", this::copySelectedJson);
            y += 23;
            smallButton(context, mouseX, mouseY, x + 8, y, 72, "Paste JSON", this::pasteSelectedJson);
            smallButton(context, mouseX, mouseY, x + 84, y, 70, "Focus [F]", this::focusSelected);
            y += 27;
            buildElementRows(selected);
        } else {
            context.drawTextWithShadow(textRenderer, trim(project.name + "  ·  " + project.sceneId, RIGHT_W - 16), x + 8, y, 0xFFF1F6FA);
            y += 21;
            buildProjectRows();
        }

        int rowY = y - (int)inspectorScroll;
        for (PropertyRow row : propertyRows) {
            if (rowY + 18 >= TOP_H + 2 && rowY < bottom - 32) drawPropertyRow(context, mouseX, mouseY, row, rowY);
            row.screenY = rowY;
            rowY += 19;
        }

        if (valueEditor != null) {
            valueEditor.setX(x + 8);
            valueEditor.setY(bottom - 24);
            valueEditor.setWidth(RIGHT_W - 16);
            if (valueEditor.isVisible()) context.drawTextWithShadow(textRenderer, "EDIT VALUE", x + 8, bottom - 35, 0xFF748595);
        }
    }

    private void drawPropertyRow(DrawContext context, int mouseX, int mouseY, PropertyRow row, int y) {
        int x = width - RIGHT_W + 7 + row.depth * 10;
        int right = width - 7;
        context.fill(x, y, right, y + 17, hovered(mouseX, mouseY, x, y, right, y + 17) ? 0xFF26303A : 0xFF1C222A);
        int labelWidth = Math.min(132, Math.max(72, (right - x) / 2));
        context.drawTextWithShadow(textRenderer, trim(row.label, labelWidth - 6), x + 4, y + 4, row.container ? 0xFF9FB0BF : 0xFF8193A4);
        String value = propertyValue(row);
        int valueX = x + labelWidth;
        context.drawTextWithShadow(textRenderer, trim(value, right - valueX - 24), valueX, y + 4, row.valueColor());

        if (row.canAdd()) {
            int px = right - 18;
            context.fill(px, y + 1, right - 1, y + 16, 0xFF33404C);
            context.drawTextWithShadow(textRenderer, "+", px + 5, y + 4, 0xFFE7F4FF);
            addHit(px, y + 1, right - 1, y + 16, () -> addContainerValue(row));
        }
        row.x1 = x; row.x2 = right;
    }

    private void drawTimeline(DrawContext context, int mouseX, int mouseY) {
        int top = timelineTop();
        int rulerTop = top + 29;
        int trackTop = rulerTop + 27;
        int x0 = TIMELINE_LABEL_W;
        int x1 = width;
        context.fill(0, top, width, top + 28, 0xFF171C22);
        context.drawTextWithShadow(textRenderer, "TIMELINE", 8, top + 9, 0xFFA9B8C5);
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "zoom %.2f px/t", pixelsPerTick), 82, top + 9, 0xFF71808E);
        context.fill(0, rulerTop, width, rulerTop + 26, 0xFF12171D);
        context.fill(0, trackTop, TIMELINE_LABEL_W, height, 0xFF151A20);

        double step = niceStep(Math.max(0.05, 70.0 / Math.max(0.05, pixelsPerTick)));
        double first = Math.floor(timelineStartTick / step) * step;
        for (double tick = first; tick <= tickFromX(x1) + step; tick += step) {
            int x = tickX(tick);
            if (x < x0 || x > x1) continue;
            context.fill(x, rulerTop + 16, x + 1, height, 0x382D3944);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "%.1fs", tick / 20.0), x + 3, rulerTop + 5, 0xFF7E8D9A);
        }

        List<EditorModel.Element> elements = project.elements;
        int visibleRow = 0;
        for (int i = Math.max(0, timelineScrollRows); i < elements.size(); i++) {
            int y = trackTop + visibleRow * ROW_H;
            if (y + ROW_H > height - 17) break;
            EditorModel.Element element = elements.get(i);
            boolean selected = element.editorId.equals(selectedId);
            context.fill(0, y, width, y + ROW_H - 1, selected ? 0xFF202C38 : (visibleRow % 2 == 0 ? 0xFF151B21 : 0xFF12181E));
            context.drawTextWithShadow(textRenderer, trim(element.key(), TIMELINE_LABEL_W - 38), 8, y + 7, element.enabled ? 0xFFD9E3EC : 0xFF68727C);
            context.drawTextWithShadow(textRenderer, element.enabled ? "●" : "○", TIMELINE_LABEL_W - 22, y + 7, element.enabled ? 0xFF69D79D : 0xFF68727C);

            int sx = tickX(element.startTick());
            int ex = tickX(Math.max(element.endTick(), element.startTick() + 0.05));
            int clipLeft = Math.max(x0, Math.min(x1, sx));
            int clipRight = Math.max(x0, Math.min(x1, ex));
            if (clipRight < clipLeft) { int swap = clipLeft; clipLeft = clipRight; clipRight = swap; }
            if (clipRight - clipLeft < 3) clipRight = clipLeft + 3;
            int clipColor = selected ? 0xFF3B7AA5 : (element.enabled ? 0xFF31566E : 0xFF3A4148);
            context.fill(clipLeft, y + 3, Math.min(x1, clipRight), y + ROW_H - 4, clipColor);
            if (selected) {
                context.fill(clipLeft, y + 3, clipLeft + 2, y + ROW_H - 4, 0xFFB9E3FF);
                context.fill(Math.max(clipLeft, clipRight - 2), y + 3, clipRight, y + ROW_H - 4, 0xFFB9E3FF);
            }
            clipHits.add(new ClipHit(element, clipLeft, y + 2, clipRight, y + ROW_H - 3));
            drawKeyframes(context, element, y, x0, x1);
            visibleRow++;
        }

        int ph = tickX(preview.currentTick());
        if (ph >= x0 && ph <= x1) {
            context.fill(ph - 1, rulerTop, ph + 1, height, 0xFFFFD565);
            context.fill(ph - 4, rulerTop, ph + 5, rulerTop + 5, 0xFFFFD565);
        }
        context.drawTextWithShadow(textRenderer, statusText(), 8, height - 13, 0xFF8798A8);
    }

    private void drawKeyframes(DrawContext context, EditorModel.Element element, int rowY, int x0, int x1) {
        ArrayList<JsonObject> keys = new ArrayList<>();
        collectTickObjects(element.data, keys);
        for (JsonObject key : keys) {
            if (!key.has("tick") || !key.get("tick").isJsonPrimitive()) continue;
            double local;
            try { local = key.get("tick").getAsDouble(); } catch (RuntimeException ignored) { continue; }
            int x = tickX(element.startTick() + local);
            if (x < x0 || x > x1) continue;
            int cy = rowY + ROW_H / 2;
            int color = element.editorId.equals(selectedId) ? 0xFFFFC857 : 0xFF9D8451;
            context.fill(x - 1, cy - 4, x + 2, cy + 5, color);
            context.fill(x - 3, cy - 2, x + 4, cy + 3, color);
            keyHits.add(new KeyHit(element, key, x - 5, cy - 6, x + 6, cy + 7));
        }
    }

    private void drawViewportOverlay(DrawContext context) {
        int right = width - RIGHT_W;
        int y = TOP_H + 8;
        context.drawTextWithShadow(textRenderer,
                preview.sceneCameraPreview() ? "SCENE CAMERA PREVIEW  [C]" : "EDITOR CAMERA · RMB look · WASD · Q/E · Shift boost  [C]",
                LEFT_W + 9, y, 0xD9E4EDF4);
        Vec3d p = preview.editorCameraPosition();
        String cam = String.format(Locale.ROOT, "XYZ %.2f  %.2f  %.2f   yaw %.1f  pitch %.1f   speed %.2f",
                p.x, p.y, p.z, preview.editorCameraYaw(), preview.editorCameraPitch(), cameraSpeed);
        context.drawTextWithShadow(textRenderer, cam, LEFT_W + 9, y + 13, 0xB9AAB8C4);
        if (preview.lastBuild() != null && !preview.lastBuild().errors().isEmpty()) {
            String errors = preview.lastBuild().errors().size() + " invalid draft element(s) skipped in preview";
            context.drawTextWithShadow(textRenderer, errors, Math.max(LEFT_W + 9, right - textRenderer.getWidth(errors) - 8), y, 0xFFFF9C7D);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled) && valueEditor != null && valueEditor.isFocused()) return true;
        double mx = click.x(), my = click.y();

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && inViewport(mx, my)) {
            rightLook = true;
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mx, my)) { hit.action.run(); return true; }
        }

        for (PropertyRow row : propertyRows) {
            if (!row.contains(mx, my)) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && row.isEnum()) {
                row.set(new JsonPrimitive(EditorSchema.cycleEnum(row.type, row.value.getAsString(), -1)));
                propertyChanged("Enum changed");
                return true;
            }
            if (row.value != null && row.value.isJsonPrimitive() && row.value.getAsJsonPrimitive().isBoolean()) {
                row.set(new JsonPrimitive(!row.value.getAsBoolean()));
                propertyChanged("Boolean changed");
            } else if (row.isEnum()) {
                row.set(new JsonPrimitive(EditorSchema.cycleEnum(row.type, row.value.getAsString(), 1)));
                propertyChanged("Enum changed");
            } else if (row.container) {
                toggleCollapsed(row.path);
            } else if (row.value == null || row.value.isJsonNull()) {
                JsonElement created = EditorSchema.defaultValue(row.type, row.label, project.durationTicks);
                if (created != null && !created.isJsonNull()) {
                    row.set(created);
                    propertyChanged("Value created");
                }
            } else beginPropertyEdit(row);
            return true;
        }

        int top = timelineTop();
        if (my >= top) {
            for (KeyHit hit : keyHits) {
                if (hit.contains(mx, my)) {
                    select(hit.element);
                    dragMode = DragMode.KEYFRAME;
                    dragElement = hit.element;
                    dragKeyframe = hit.key;
                    dragMouseStartTick = tickFromX(mx);
                    dragOriginalKeyTick = safeDouble(hit.key.get("tick"), 0.0);
                    return true;
                }
            }
            for (ClipHit hit : clipHits) {
                if (hit.contains(mx, my)) {
                    select(hit.element);
                    if (hit.element.locked) return true;
                    dragElement = hit.element;
                    dragMouseStartTick = tickFromX(mx);
                    dragOriginalStart = hit.element.startTick();
                    dragOriginalEnd = hit.element.endTick();
                    if (Math.abs(mx - hit.x1) <= 5) dragMode = DragMode.CLIP_LEFT;
                    else if (Math.abs(mx - hit.x2) <= 5) dragMode = DragMode.CLIP_RIGHT;
                    else dragMode = DragMode.CLIP_MOVE;
                    history.checkpoint(project);
                    return true;
                }
            }
            if (mx >= TIMELINE_LABEL_W) {
                dragMode = DragMode.PLAYHEAD;
                preview.setTick(client, tickFromX(mx));
                return true;
            }
        }

        if (valueEditor != null) {
            valueEditor.setFocused(false);
            valueEditor.setVisible(false);
            activeProperty = null;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (rightLook && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            preview.setEditorCamera(preview.editorCameraPosition(),
                    preview.editorCameraYaw() + (float)(offsetX * 0.28),
                    preview.editorCameraPitch() + (float)(offsetY * 0.28));
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, offsetX, offsetY);
        double tick = tickFromX(click.x());
        if (dragMode == DragMode.PLAYHEAD) {
            preview.setTick(client, tick);
            return true;
        }
        if (dragElement == null || dragElement.locked) return false;
        double delta = snap(tick - dragMouseStartTick);
        switch (dragMode) {
            case CLIP_MOVE -> {
                double length = dragOriginalEnd - dragOriginalStart;
                double start = Math.max(0.0, snap(dragOriginalStart + delta));
                dragElement.setStartTick(start);
                dragElement.setEndTick(start + Math.max(0.0, length));
                propertyChangedContinuous();
                return true;
            }
            case CLIP_LEFT -> {
                dragElement.setStartTick(Math.max(0.0, Math.min(dragOriginalEnd, snap(dragOriginalStart + delta))));
                propertyChangedContinuous();
                return true;
            }
            case CLIP_RIGHT -> {
                dragElement.setEndTick(Math.max(dragElement.startTick(), snap(dragOriginalEnd + delta)));
                project.durationTicks = Math.max(project.durationTicks, dragElement.endTick());
                propertyChangedContinuous();
                return true;
            }
            case KEYFRAME -> {
                if (dragKeyframe != null) {
                    double local = Math.max(0.0, snap(dragOriginalKeyTick + delta));
                    dragKeyframe.addProperty("tick", local);
                    propertyChangedContinuous();
                    return true;
                }
            }
            default -> { }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightLook = false;
        if (dragMode != DragMode.NONE) {
            if (dragMode != DragMode.PLAYHEAD) sortNestedTicks(project);
            dragMode = DragMode.NONE;
            dragElement = null;
            dragKeyframe = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= timelineTop()) {
            if (isShiftDown()) {
                timelineStartTick -= verticalAmount * Math.max(1.0, 80.0 / Math.max(0.05, pixelsPerTick));
                timelineStartTick = Math.max(-project.durationTicks, timelineStartTick);
            } else if (mouseX < TIMELINE_LABEL_W) {
                timelineScrollRows = Math.max(0, Math.min(Math.max(0, project.elements.size() - 1), timelineScrollRows - (int)Math.signum(verticalAmount)));
            } else {
                double underMouse = tickFromX(mouseX);
                double old = pixelsPerTick;
                pixelsPerTick = Math.max(0.05, Math.min(100.0, pixelsPerTick * Math.pow(1.18, verticalAmount)));
                if (old != pixelsPerTick) timelineStartTick = underMouse - (mouseX - TIMELINE_LABEL_W) / pixelsPerTick;
            }
            return true;
        }
        if (mouseX < LEFT_W) {
            sidebarScroll = Math.max(0, sidebarScroll - verticalAmount * 24.0);
            return true;
        }
        if (mouseX >= width - RIGHT_W) {
            inspectorScroll = Math.max(0, inspectorScroll - verticalAmount * 26.0);
            return true;
        }
        if (inViewport(mouseX, mouseY) && !preview.sceneCameraPreview()) {
            cameraSpeed = Math.max(0.02, Math.min(20.0, cameraSpeed * Math.pow(1.2, verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (valueEditor != null && valueEditor.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                valueEditor.setFocused(false);
                valueEditor.setVisible(false);
                activeProperty = null;
                return true;
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
        if (input.key() == GLFW.GLFW_KEY_HOME) { preview.setTick(client, 0.0); return true; }
        if (input.key() == GLFW.GLFW_KEY_END) { preview.setTick(client, project.durationTicks); return true; }
        if (input.key() == GLFW.GLFW_KEY_F) { focusSelected(); return true; }
        if (input.key() == GLFW.GLFW_KEY_C) {
            preview.setSceneCameraPreview(client, !preview.sceneCameraPreview());
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_K) { addKeyframesAtPlayhead(); return true; }
        return super.keyPressed(input);
    }

    private void updateFreeCamera() {
        if (preview.sceneCameraPreview() || !rightLook || client == null) return;
        long window = client.getWindow().getHandle();
        double speed = cameraSpeed * (pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 4.0 : 1.0);
        float yaw = preview.editorCameraYaw();
        float pitch = preview.editorCameraPitch();
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d flatForward = new Vec3d(-Math.sin(ry), 0, Math.cos(ry));
        Vec3d right = new Vec3d(-flatForward.z, 0, flatForward.x);
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
        history.checkpoint(project);
        EditorModel.Element element = CineFxBridge.newDraft(type, project.durationTicks, project.elements.size());
        ensureUniqueKey(element);
        project.elements.add(element);
        selectedId = element.editorId;
        leftTab = LeftTab.ADD;
        propertyChangedContinuous();
        toast("Added " + type.displayName());
    }

    private void ensureUniqueKey(EditorModel.Element element) {
        Set<String> keys = new HashSet<>();
        for (EditorModel.Element existing : project.elements) keys.add(existing.key());
        String base = element.key();
        if (base == null || base.isBlank() || "element_1".equals(base)) base = slug(element.label);
        String key = base;
        int suffix = 2;
        while (keys.contains(key)) key = base + "_" + suffix++;
        element.setKey(key);
    }

    private void duplicateSelected() {
        EditorModel.Element selected = selected();
        if (selected == null) return;
        history.checkpoint(project);
        EditorModel.Element copy = selected.duplicate();
        ensureUniqueKey(copy);
        project.elements.add(copy);
        selectedId = copy.editorId;
        propertyChangedContinuous();
        toast("Duplicated " + selected.key());
    }

    private void deleteSelected() {
        EditorModel.Element selected = selected();
        if (selected == null || selected.locked) return;
        history.checkpoint(project);
        project.elements.remove(selected);
        selectedId = null;
        propertyChangedContinuous();
        toast("Element deleted");
    }

    private void newProject() {
        PresetStore.autosave(project);
        history.clear();
        project = EditorModel.Project.fresh(client);
        preview.setProject(project);
        selectedId = null;
        sidebarScroll = inspectorScroll = 0;
        timelineStartTick = 0;
        fitTimeline();
        toast("New project");
    }

    private void saveProject() {
        try {
            String name = project.sourcePreset == null ? project.name : project.sourcePreset;
            PresetStore.save(project, name);
            toast("Saved preset: " + project.sourcePreset);
        } catch (Exception exception) {
            toast("Save failed: " + shortError(exception));
        }
    }

    private void loadPreset(String name) {
        try {
            PresetStore.autosave(project);
            project = PresetStore.load(name);
            history.clear();
            preview.setProject(project);
            selectedId = null;
            inspectorScroll = 0;
            fitTimeline();
            toast("Loaded " + name);
        } catch (Exception exception) {
            toast("Load failed: " + shortError(exception));
        }
    }

    private void restoreAutosave() {
        EditorModel.Project restored = PresetStore.loadAutosave();
        if (restored == null) { toast("No autosave available"); return; }
        history.checkpoint(project);
        project = restored;
        preview.setProject(project);
        selectedId = null;
        fitTimeline();
        toast("Autosave restored");
    }

    private void importScene(SceneDefinition scene) {
        PresetStore.autosave(project);
        history.clear();
        project = CineFxBridge.importScene(scene);
        if (client != null && client.player != null) {
            Vec3d p = client.player.getPos();
            project.anchorX = p.x; project.anchorY = p.y; project.anchorZ = p.z;
        }
        preview.setProject(project);
        selectedId = null;
        fitTimeline();
        leftTab = LeftTab.PROJECT;
        toast("Imported " + scene.id());
    }

    private void undo() {
        EditorModel.Project before = project;
        project = history.undo(project);
        if (project == before) { toast("Nothing to undo"); return; }
        preview.setProject(project);
        selectedId = null;
        toast("Undo");
    }

    private void redo() {
        EditorModel.Project before = project;
        project = history.redo(project);
        if (project == before) { toast("Nothing to redo"); return; }
        preview.setProject(project);
        selectedId = null;
        toast("Redo");
    }

    private void validateProject() {
        CineFxBridge.BuildResult result = preview.validateOnly();
        if (result == null || result.errors().isEmpty()) toast("Valid CineFX scene · " + project.elements.size() + " elements");
        else toast(result.errors().size() + " invalid element(s): " + result.errors().getFirst().key() + " · " + result.errors().getFirst().message());
    }

    private void copySelectedJson() {
        EditorModel.Element selected = selected();
        if (selected == null || client == null) return;
        GLFW.glfwSetClipboardString(client.getWindow().getHandle(), EditorModel.GSON.toJson(selected.data));
        toast("Element JSON copied");
    }

    private void pasteSelectedJson() {
        EditorModel.Element selected = selected();
        if (selected == null || client == null || selected.locked) return;
        try {
            String raw = GLFW.glfwGetClipboardString(client.getWindow().getHandle());
            JsonElement parsed = JsonParser.parseString(raw == null ? "" : raw);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("JSON object required");
            history.checkpoint(project);
            selected.data = parsed.getAsJsonObject();
            propertyChangedContinuous();
            toast("Element JSON pasted");
        } catch (Exception exception) {
            toast("Invalid clipboard JSON: " + shortError(exception));
        }
    }

    private void focusSelected() {
        EditorModel.Element selected = selected();
        if (selected == null) return;
        Vec3d local = CineFxBridge.tryResolveEditorPosition(selected);
        if (local == null) { toast("No position field found on selection"); return; }
        Vec3d target = project.anchor().add(local);
        Vec3d forward = cameraForward(preview.editorCameraYaw(), preview.editorCameraPitch());
        preview.setSceneCameraPreview(client, false);
        preview.setEditorCamera(target.subtract(forward.multiply(6.0)), preview.editorCameraYaw(), preview.editorCameraPitch());
        toast("Focused " + selected.key());
    }

    private void addKeyframesAtPlayhead() {
        EditorModel.Element selected = selected();
        if (selected == null || selected.locked) { toast("Select an editable element first"); return; }
        history.checkpoint(project);
        double localTick = Math.max(0.0, preview.currentTick() - selected.startTick());
        int added = addKeyframesRecursive(selected.data, localTick);
        if (added == 0) toast("Selection has no editable keyframe track");
        else {
            sortNestedTicks(project);
            propertyChangedContinuous();
            toast("Added " + added + " keyframe" + (added == 1 ? "" : "s") + " at playhead");
        }
    }

    private int addKeyframesRecursive(JsonElement value, double localTick) {
        if (value == null || value.isJsonNull()) return 0;
        int count = 0;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String kind = object.has("$kind") && !object.get("$kind").isJsonNull() ? object.get("$kind").getAsString() : "";
            if ((kind.endsWith("Track") || "ScalarTrack".equals(kind) || "ColorTrack".equals(kind) || "Vec3Track".equals(kind) || "TransformTrack".equals(kind)) && object.has("keys")) {
                JsonArray keys = object.getAsJsonArray("keys");
                if (!hasTick(keys, localTick)) { keys.add(EditorSchema.duplicateTrackKey(object, localTick)); count++; }
            } else if ("PathTrack".equals(kind) && object.has("points")) {
                JsonArray points = object.getAsJsonArray("points");
                if (!hasTick(points, localTick)) { points.add(EditorSchema.duplicatePathPoint(object, localTick)); count++; }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) if (!entry.getKey().equals("keys") && !entry.getKey().equals("points")) count += addKeyframesRecursive(entry.getValue(), localTick);
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) count += addKeyframesRecursive(child, localTick);
        }
        return count;
    }

    private void buildElementRows(EditorModel.Element selected) {
        Type root = EditorSchema.root(selected);
        for (Map.Entry<String, JsonElement> entry : selected.data.entrySet()) {
            if (entry.getKey().equals("$type")) continue;
            Type type = EditorSchema.field(root, entry.getKey());
            flattenProperty(entry.getKey(), entry.getValue(), type, 0, entry.getKey(), selected.data, entry.getKey(), null, -1);
        }
    }

    private void flattenProperty(String label, JsonElement value, Type type, int depth, String path,
                                 JsonObject parentObject, String parentKey, JsonArray parentArray, int parentIndex) {
        boolean isContainer = value != null && (value.isJsonObject() || value.isJsonArray());
        Consumer<JsonElement> setter = replacement -> {
            if (parentObject != null) parentObject.add(parentKey, replacement == null ? JsonNull.INSTANCE : replacement);
            else if (parentArray != null) parentArray.set(parentIndex, replacement == null ? JsonNull.INSTANCE : replacement);
        };
        PropertyRow row = new PropertyRow(label, path, value == null ? JsonNull.INSTANCE : value, type, depth, isContainer, setter);
        propertyRows.add(row);
        if (!isContainer || collapsed.contains(path)) return;

        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String kind = object.has("$kind") ? safeString(object.get("$kind")) : "";
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("$type") || entry.getKey().equals("$kind")) continue;
                Type childType = kind.isBlank() ? EditorSchema.field(type, entry.getKey()) : specialChildType(kind, entry.getKey());
                flattenProperty(entry.getKey(), entry.getValue(), childType, depth + 1, path + "." + entry.getKey(), object, entry.getKey(), null, -1);
            }
        } else {
            JsonArray array = value.getAsJsonArray();
            Type itemType = EditorSchema.item(type);
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                flattenProperty("[" + i + "]", child, itemType, depth + 1, path + "[" + i + "]", null, null, array, i);
            }
        }
    }

    private Type specialChildType(String kind, String field) {
        if (field.equals("angularDegrees")) return boolean.class;
        if (field.equals("interpolation")) return dev.garfield.cinefx.api.PathTrack.Interpolation.class;
        if (field.equals("preset")) return String.class;
        if (field.equals("keys") || field.equals("points")) return List.class;
        return Object.class;
    }

    private void buildProjectRows() {
        addProjectRow("name", new JsonPrimitive(project.name), String.class, v -> project.name = v.getAsString());
        addProjectRow("sceneId", new JsonPrimitive(project.sceneId), String.class, v -> project.sceneId = v.getAsString());
        addProjectRow("durationTicks", new JsonPrimitive(project.durationTicks), double.class, v -> project.durationTicks = Math.max(1, v.getAsDouble()));
        addProjectRow("priority", new JsonPrimitive(project.priority), int.class, v -> project.priority = v.getAsInt());
        addProjectRow("looping", new JsonPrimitive(project.looping), boolean.class, v -> project.looping = v.getAsBoolean());
        addProjectRow("anchor X", new JsonPrimitive(project.anchorX), double.class, v -> project.anchorX = v.getAsDouble());
        addProjectRow("anchor Y", new JsonPrimitive(project.anchorY), double.class, v -> project.anchorY = v.getAsDouble());
        addProjectRow("anchor Z", new JsonPrimitive(project.anchorZ), double.class, v -> project.anchorZ = v.getAsDouble());
        addProjectRow("seed", new JsonPrimitive(project.seed), long.class, v -> project.seed = v.getAsLong());
        propertyRows.add(PropertyRow.heading("variables", "$variables", () -> {
            String key = uniqueMapKey(project.variables, "variable");
            project.variables.put(key, "value");
            propertyChanged("Variable added");
        }));
        for (Map.Entry<String, String> entry : new ArrayList<>(project.variables.entrySet())) {
            String key = entry.getKey();
            addProjectRow("  " + key, new JsonPrimitive(entry.getValue()), String.class, v -> project.variables.put(key, v.getAsString()));
        }
        propertyRows.add(PropertyRow.heading("metadata", "$metadata", () -> {
            String key = uniqueMapKey(project.metadata, "meta");
            project.metadata.put(key, "value");
            propertyChanged("Metadata added");
        }));
        for (Map.Entry<String, String> entry : new ArrayList<>(project.metadata.entrySet())) {
            String key = entry.getKey();
            addProjectRow("  " + key, new JsonPrimitive(entry.getValue()), String.class, v -> project.metadata.put(key, v.getAsString()));
        }
    }

    private void addProjectRow(String label, JsonElement value, Type type, Consumer<JsonElement> setter) {
        propertyRows.add(new PropertyRow(label, "$project." + label, value, type, 0, false, setter));
    }

    private void addContainerValue(PropertyRow row) {
        if (row.value == null || row.value.isJsonNull()) {
            JsonElement replacement = EditorSchema.defaultValue(row.type, row.label, project.durationTicks);
            row.set(replacement);
            propertyChanged("Value created");
            return;
        }
        if (row.value.isJsonArray()) {
            JsonArray array = row.value.getAsJsonArray();
            if (row.parentObject != null && "keys".equals(row.label)) array.add(EditorSchema.duplicateTrackKey(row.parentObject, Math.max(0, preview.currentTick() - (selected() == null ? 0 : selected().startTick()))));
            else if (row.parentObject != null && "points".equals(row.label)) array.add(EditorSchema.duplicatePathPoint(row.parentObject, Math.max(0, preview.currentTick() - (selected() == null ? 0 : selected().startTick()))));
            else array.add(EditorSchema.defaultValue(EditorSchema.item(row.type), "item", project.durationTicks));
            propertyChanged("List item added");
        } else if (row.value.isJsonObject() && row.path.equals("$variables")) {
            String key = uniqueMapKey(project.variables, "variable"); project.variables.put(key, "value"); propertyChanged("Variable added");
        }
    }

    private void beginPropertyEdit(PropertyRow row) {
        if (valueEditor == null || row.setter == null) return;
        activeProperty = row;
        changingEditorText = true;
        valueEditor.setText(editText(row.value));
        changingEditorText = false;
        valueEditor.setVisible(true);
        valueEditor.setFocused(true);
        valueEditor.setCursorToEnd(false);
    }

    private void onEditorTextChanged(String text) {
        if (changingEditorText || activeProperty == null) return;
        try {
            JsonElement value = parseLike(activeProperty.value, text);
            activeProperty.set(value);
            activeProperty.value = value;
            project.dirty = true;
            preview.markDirty();
        } catch (RuntimeException ignored) {
            // Keep typing; invalid intermediate numeric text is not applied.
        }
    }

    private JsonElement parseLike(JsonElement original, String text) {
        if (original == null || original.isJsonNull()) return new JsonPrimitive(text);
        if (!original.isJsonPrimitive()) return JsonParser.parseString(text);
        JsonPrimitive p = original.getAsJsonPrimitive();
        if (p.isBoolean()) return new JsonPrimitive(Boolean.parseBoolean(text));
        if (p.isNumber()) return new JsonPrimitive(new BigDecimal(text));
        return new JsonPrimitive(text);
    }

    private String editText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        return EditorModel.GSON.toJson(value);
    }

    private void propertyChanged(String message) {
        history.checkpoint(project);
        propertyChangedContinuous();
        toast(message);
    }

    private void propertyChangedContinuous() {
        project.dirty = true;
        preview.markDirty();
    }

    private void mutate(String message, Runnable mutation) {
        history.checkpoint(project);
        mutation.run();
        propertyChangedContinuous();
        toast(message);
    }

    private void select(EditorModel.Element element) {
        selectedId = element == null ? null : element.editorId;
        inspectorScroll = 0;
        activeProperty = null;
        if (valueEditor != null) { valueEditor.setFocused(false); valueEditor.setVisible(false); }
    }

    private EditorModel.Element selected() { return project.find(selectedId); }

    private void fitTimeline() {
        int available = Math.max(240, width - TIMELINE_LABEL_W - 20);
        pixelsPerTick = Math.max(0.15, available / Math.max(1.0, project.durationTicks));
        timelineStartTick = 0.0;
    }

    private int button(DrawContext context, int mouseX, int mouseY, int x, int y, int w, int h, String text, Runnable action) {
        int color = hovered(mouseX, mouseY, x, y, x + w, y + h) ? 0xFF33404D : 0xFF242B34;
        context.fill(x, y, x + w, y + h, color);
        context.drawTextWithShadow(textRenderer, trim(text, w - 8), x + 4, y + 7, 0xFFE7EEF4);
        addHit(x, y, x + w, y + h, action);
        return x + w;
    }

    private int smallButton(DrawContext context, int mouseX, int mouseY, int x, int y, int w, String text, Runnable action) {
        int color = hovered(mouseX, mouseY, x, y, x + w, y + 18) ? 0xFF34414C : 0xFF252C34;
        context.fill(x, y, x + w, y + 18, color);
        context.drawTextWithShadow(textRenderer, trim(text, w - 6), x + 3, y + 5, 0xFFD8E3EC);
        addHit(x, y, x + w, y + 18, action);
        return x + w;
    }

    private void tabButton(DrawContext context, int mouseX, int mouseY, int x, int y, int w, String text, LeftTab tab) {
        int color = leftTab == tab ? 0xFF33485A : (hovered(mouseX, mouseY, x, y, x + w - 2, y + 20) ? 0xFF29333D : 0xFF20262D);
        context.fill(x, y, x + w - 2, y + 20, color);
        context.drawTextWithShadow(textRenderer, text, x + 7, y + 6, 0xFFDCE6EE);
        addHit(x, y, x + w - 2, y + 20, () -> { leftTab = tab; sidebarScroll = 0; });
    }

    private int leftAction(DrawContext context, int mouseX, int mouseY, int y, int bottom, String text, Runnable action) {
        if (visibleY(y, bottom)) {
            context.fill(6, y - 2, LEFT_W - 6, y + 17, hovered(mouseX, mouseY, 6, y - 2, LEFT_W - 6, y + 17) ? 0xFF31404C : 0xFF242C34);
            context.drawTextWithShadow(textRenderer, text, 11, y + 3, 0xFFDCE6EE);
            addHit(6, y - 2, LEFT_W - 6, y + 17, action);
        }
        return y + 23;
    }

    private void addHit(int x1, int y1, int x2, int y2, Runnable action) {
        if (y2 >= TOP_H && y1 <= height) hits.add(new Hit(x1, y1, x2, y2, action));
    }

    private boolean visibleY(int y, int bottom) { return y >= TOP_H + 45 && y < bottom - 18; }
    private int timelineTop() { return Math.max(TOP_H + 160, height - TIMELINE_H); }
    private boolean inViewport(double x, double y) { return x >= LEFT_W && x < width - RIGHT_W && y >= TOP_H && y < timelineTop(); }

    private int tickX(double tick) { return TIMELINE_LABEL_W + (int)Math.round((tick - timelineStartTick) * pixelsPerTick); }
    private double tickFromX(double x) { return timelineStartTick + (x - TIMELINE_LABEL_W) / Math.max(0.0001, pixelsPerTick); }
    private double snap(double tick) { return Math.round(tick * 4.0) / 4.0; }

    private double niceStep(double value) {
        double exponent = Math.pow(10.0, Math.floor(Math.log10(Math.max(1.0e-6, value))));
        double normalized = value / exponent;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return nice * exponent;
    }

    private void collectTickObjects(JsonElement value, List<JsonObject> out) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tick") && object.get("tick").isJsonPrimitive()) out.add(object);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) collectTickObjects(entry.getValue(), out);
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) collectTickObjects(child, out);
    }

    private void sortNestedTicks(EditorModel.Project project) {
        for (EditorModel.Element element : project.elements) sortNestedTicks(element.data);
    }

    private void sortNestedTicks(JsonElement value) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) sortNestedTicks(entry.getValue());
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            boolean sortable = !array.isEmpty();
            ArrayList<JsonObject> values = new ArrayList<>();
            for (JsonElement item : array) {
                if (!item.isJsonObject() || !item.getAsJsonObject().has("tick")) { sortable = false; break; }
                values.add(item.getAsJsonObject());
            }
            if (sortable) {
                values.sort(Comparator.comparingDouble(item -> safeDouble(item.get("tick"), 0.0)));
                while (!array.isEmpty()) array.remove(array.size() - 1);
                values.forEach(array::add);
            } else for (JsonElement item : array) sortNestedTicks(item);
        }
    }

    private boolean hasTick(JsonArray array, double tick) {
        for (JsonElement value : array) if (value.isJsonObject() && Math.abs(safeDouble(value.getAsJsonObject().get("tick"), -999999) - tick) < 0.001) return true;
        return false;
    }

    private void toggleCollapsed(String path) {
        if (!collapsed.add(path)) collapsed.remove(path);
    }

    private String propertyValue(PropertyRow row) {
        if (row.value == null || row.value.isJsonNull()) return "null  [+]";
        if (row.value.isJsonArray()) return (collapsed.contains(row.path) ? "▶ " : "▼ ") + "[" + row.value.getAsJsonArray().size() + "]";
        if (row.value.isJsonObject()) {
            JsonObject object = row.value.getAsJsonObject();
            String kind = object.has("$kind") ? safeString(object.get("$kind")) : object.has("$type") ? simpleType(safeString(object.get("$type"))) : "object";
            return (collapsed.contains(row.path) ? "▶ " : "▼ ") + kind;
        }
        if (row.isEnum()) return "‹ " + row.value.getAsString() + " ›";
        return row.value.getAsString();
    }

    private String statusText() {
        if (System.currentTimeMillis() > statusUntil) {
            CineFxBridge.BuildResult result = preview.lastBuild();
            if (result != null && !result.errors().isEmpty()) return result.errors().size() + " preview validation error(s) · click Validate for first error";
            return project.dirty ? "Unsaved changes · autosave active" : "Saved · CineFX runtime connected";
        }
        return status;
    }

    private void toast(String value) { status = value; statusUntil = System.currentTimeMillis() + 4500L; }
    private String trim(String value, int width) { return textRenderer.trimToWidth(value == null ? "" : value, Math.max(4, width)); }
    private static String slug(String value) { return value == null ? "element" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); }
    private static boolean hovered(double mx, double my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx <= x2 && my >= y1 && my <= y2; }
    private static double safeDouble(JsonElement value, double fallback) { try { return value == null || value.isJsonNull() ? fallback : value.getAsDouble(); } catch (RuntimeException ignored) { return fallback; } }
    private static String safeString(JsonElement value) { try { return value == null || value.isJsonNull() ? "" : value.getAsString(); } catch (RuntimeException ignored) { return ""; } }
    private static String simpleType(String value) { int i = value == null ? -1 : Math.max(value.lastIndexOf('.'), value.lastIndexOf('$')); return i >= 0 ? value.substring(i + 1) : value; }
    private static String shortError(Throwable throwable) { String s = throwable.getMessage(); return s == null || s.isBlank() ? throwable.getClass().getSimpleName() : s; }
    private static boolean pressed(long window, int key) { return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }
    private boolean isShiftDown() { if (client == null) return false; long w = client.getWindow().getHandle(); return pressed(w, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(w, GLFW.GLFW_KEY_RIGHT_SHIFT); }
    private static Vec3d cameraForward(float yaw, float pitch) { double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch); return new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)); }
    private static String uniqueMapKey(Map<String, String> map, String base) { int i = 1; String key = base; while (map.containsKey(key)) key = base + "_" + i++; return key; }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); }
    }

    private record ClipHit(EditorModel.Element element, int x1, int y1, int x2, int y2) {
        boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); }
    }

    private record KeyHit(EditorModel.Element element, JsonObject key, int x1, int y1, int x2, int y2) {
        boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); }
    }

    private static final class PropertyRow {
        final String label;
        final String path;
        JsonElement value;
        final Type type;
        final int depth;
        final boolean container;
        final Consumer<JsonElement> setter;
        JsonObject parentObject;
        int x1, x2, screenY;
        Runnable addAction;

        PropertyRow(String label, String path, JsonElement value, Type type, int depth, boolean container, Consumer<JsonElement> setter) {
            this.label = label; this.path = path; this.value = value; this.type = type; this.depth = depth; this.container = container; this.setter = setter;
        }

        static PropertyRow heading(String label, String path, Runnable add) {
            PropertyRow row = new PropertyRow(label, path, new JsonObject(), Map.class, 0, true, null);
            row.addAction = add;
            return row;
        }

        void set(JsonElement replacement) { if (setter != null) setter.accept(replacement); value = replacement; }
        boolean contains(double x, double y) { return x >= x1 && x <= x2 && y >= screenY && y <= screenY + 17; }
        boolean isEnum() { Class<?> raw = EditorSchema.raw(type); return raw != null && raw.isEnum(); }
        int valueColor() { if (value == null || value.isJsonNull()) return 0xFFB47B78; if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return 0xFF8FD9B7; if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return 0xFF8EC5E8; return 0xFFC8D3DC; }
        boolean canAdd() { return addAction != null || value == null || value.isJsonNull() || value.isJsonArray(); }
    }
}
