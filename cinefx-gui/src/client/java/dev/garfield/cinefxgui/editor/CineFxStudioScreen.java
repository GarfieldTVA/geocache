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
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
 * Flashback-inspired CineFX scene studio. The world remains the viewport; CineFX GUI only owns
 * authoring state and delegates all actual rendering/playback to the separately installed CineFX mod.
 */
public final class CineFxStudioScreen extends Screen {
    private static final int TOP_H = 32;
    private static final int LEFT_W = 252;
    private static final int RIGHT_W = 364;
    private static final int TIMELINE_H = 244;
    private static final int TIMELINE_LABEL_W = 205;
    private static final int ROW_H = 22;

    private enum LeftTab { OUTLINER, ADD, BLOCKS, API }
    private enum DragMode { NONE, PLAYHEAD, CLIP_MOVE, CLIP_LEFT, CLIP_RIGHT, KEYFRAME, GIZMO }

    private EditorModel.Project project;
    private final EditorModel.History history = new EditorModel.History(128);
    private PreviewController preview;
    private String selectedId;
    private LeftTab leftTab = LeftTab.OUTLINER;
    private SceneManipulator.Tool tool = SceneManipulator.Tool.MOVE;
    private SceneManipulator.Axis arrayAxis = SceneManipulator.Axis.X;
    private int arrayCount = 8;
    private double arrayStep = 1.0;
    private boolean guides = true;

    private TextFieldWidget searchField;
    private TextFieldWidget valueEditor;
    private PropertyRow activeProperty;
    private boolean changingEditorText;
    private String searchText = "";
    private final Set<String> collapsed = new HashSet<>();

    private double leftScroll;
    private double inspectorScroll;
    private int timelineScrollRows;
    private double timelineStartTick;
    private double pixelsPerTick = -1;
    private boolean timelineInitialized;
    private int autosaveTicks;
    private String status = "CineFX runtime connected";
    private long statusUntil;

    private final List<Hit> hits = new ArrayList<>();
    private final List<PropertyRow> propertyRows = new ArrayList<>();
    private final List<ClipHit> clipHits = new ArrayList<>();
    private final List<KeyHit> keyHits = new ArrayList<>();
    private final List<ViewportHit> viewportHits = new ArrayList<>();

    private DragMode dragMode = DragMode.NONE;
    private EditorModel.Element dragElement;
    private JsonObject dragKeyframe;
    private JsonArray dragKeyParent;
    private double dragMouseStartTick;
    private double dragOriginalStart;
    private double dragOriginalEnd;
    private double dragOriginalKeyTick;

    private boolean rightLook;
    private double cameraSpeed = 0.35;
    private ViewportGizmo.Layout gizmoLayout;
    private ViewportGizmo.Layout dragGizmoLayout;
    private SceneManipulator.Session gizmoSession;
    private SceneManipulator.Axis gizmoAxis;
    private double gizmoStartX;
    private double gizmoStartY;

    public CineFxStudioScreen() {
        super(Text.literal("CineFX Studio"));
        project = EditorModel.Project.fresh(MinecraftClient.getInstance());
        preview = new PreviewController(project);
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, 8, TOP_H + 51, LEFT_W - 16, 18, Text.literal("Search"));
        searchField.setMaxLength(128);
        searchField.setChangedListener(value -> { searchText = value == null ? "" : value; leftScroll = 0; });
        addDrawableChild(searchField);

        int bottom = timelineTop();
        valueEditor = new TextFieldWidget(textRenderer, width - RIGHT_W + 8, bottom - 24, RIGHT_W - 16, 18, Text.literal("Property value"));
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
        // The live Minecraft world is the editor viewport.
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
        viewportHits.clear();
        gizmoLayout = null;

        int timelineTop = timelineTop();
        int inspectorX = width - RIGHT_W;
        context.fill(0, 0, width, TOP_H, 0xF112151A);
        context.fill(0, TOP_H, LEFT_W, timelineTop, 0xEC171B21);
        context.fill(inspectorX, TOP_H, width, timelineTop, 0xEC171B21);
        context.fill(LEFT_W, TOP_H, inspectorX, timelineTop, 0x16101418);
        context.fill(0, timelineTop, width, height, 0xF512161B);

        drawToolbar(context, mouseX, mouseY);
        drawLeftPanel(context, mouseX, mouseY);
        drawInspector(context, mouseX, mouseY);
        drawTimeline(context, mouseX, mouseY);
        drawViewport(context, mouseX, mouseY);

        searchField.setX(8);
        searchField.setY(TOP_H + 51);
        searchField.setWidth(LEFT_W - 16);
        valueEditor.setX(width - RIGHT_W + 8);
        valueEditor.setY(timelineTop - 24);
        valueEditor.setWidth(RIGHT_W - 16);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawToolbar(DrawContext c, int mx, int my) {
        c.drawTextWithShadow(textRenderer, "CineFX Studio", 8, 11, 0xFFF2F5F7);
        int x = 96;
        x = button(c, mx, my, x, 5, 38, "New", this::newProject) + 3;
        x = button(c, mx, my, x, 5, 42, "Save", this::saveProject) + 3;
        x = button(c, mx, my, x, 5, 28, "↶", this::undo) + 2;
        x = button(c, mx, my, x, 5, 28, "↷", this::redo) + 7;
        x = toolButton(c, mx, my, x, "G", SceneManipulator.Tool.MOVE) + 2;
        x = toolButton(c, mx, my, x, "R", SceneManipulator.Tool.ROTATE) + 2;
        x = toolButton(c, mx, my, x, "S", SceneManipulator.Tool.SCALE) + 7;
        x = button(c, mx, my, x, 5, 50, preview.playing() ? "Pause" : "Play", () -> preview.togglePlay(client)) + 3;
        x = button(c, mx, my, x, 5, 40, "Stop", () -> preview.stopAndRewind(client)) + 3;
        x = button(c, mx, my, x, 5, 50, "Key [K]", this::addKeyframesAtPlayhead) + 7;
        x = button(c, mx, my, x, 5, 82, "Camera view", this::createCameraFromView) + 3;
        x = button(c, mx, my, x, 5, 72, preview.sceneCameraPreview() ? "Scene cam" : "Editor cam",
                () -> preview.setSceneCameraPreview(client, !preview.sceneCameraPreview())) + 3;
        button(c, mx, my, x, 5, 56, "Validate", this::validateProject);

        String time = String.format(Locale.ROOT, "%.2fs / %.2fs", preview.currentTick() / 20.0, project.durationTicks / 20.0);
        c.drawTextWithShadow(textRenderer, time, width - textRenderer.getWidth(time) - 8, 11, 0xFFC9D4DE);
    }

    private int toolButton(DrawContext c, int mx, int my, int x, String label, SceneManipulator.Tool candidate) {
        int color = tool == candidate ? 0xFF3C6685 : hovered(mx, my, x, 5, x + 29, 27) ? 0xFF34404A : 0xFF242B31;
        c.fill(x, 5, x + 29, 27, color);
        c.drawTextWithShadow(textRenderer, label, x + 11, 12, 0xFFF0F5F8);
        addHit(x, 5, x + 29, 27, () -> { tool = candidate; toast(candidate.name().toLowerCase(Locale.ROOT)); });
        return x + 29;
    }

    private void drawLeftPanel(DrawContext c, int mx, int my) {
        int bottom = timelineTop();
        c.drawTextWithShadow(textRenderer, "SCENE", 8, TOP_H + 8, 0xFF8494A2);
        int tabY = TOP_H + 22;
        int tabW = (LEFT_W - 8) / 4;
        tab(c, mx, my, 4, tabY, tabW, "Outliner", LeftTab.OUTLINER);
        tab(c, mx, my, 4 + tabW, tabY, tabW, "Add", LeftTab.ADD);
        tab(c, mx, my, 4 + tabW * 2, tabY, tabW, "Blocks", LeftTab.BLOCKS);
        tab(c, mx, my, 4 + tabW * 3, tabY, tabW, "API", LeftTab.API);
        c.drawTextWithShadow(textRenderer, "Search", 8, TOP_H + 43, 0xFF657582);
        int y = TOP_H + 75 - (int)leftScroll;
        switch (leftTab) {
            case OUTLINER -> drawOutliner(c, mx, my, y, bottom);
            case ADD -> drawAdd(c, mx, my, y, bottom);
            case BLOCKS -> drawBlocks(c, mx, my, y, bottom);
            case API -> drawApi(c, mx, my, y, bottom);
        }
    }

    private void drawOutliner(DrawContext c, int mx, int my, int y, int bottom) {
        y = leftAction(c, mx, my, y, bottom, "Save preset", this::saveProject);
        y = leftAction(c, mx, my, y, bottom, "Restore autosave", this::restoreAutosave) + 3;
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "ELEMENTS · " + project.elements.size(), 8, y, 0xFF748695);
        y += 16;
        for (EditorModel.Element e : project.elements) {
            if (!matches(e.key() + " " + e.label)) continue;
            int yy = y;
            if (visibleY(yy, bottom)) {
                boolean selected = e.editorId.equals(selectedId);
                c.fill(5, yy - 2, LEFT_W - 5, yy + 18, selected ? 0xFF304C61 : hovered(mx, my, 5, yy - 2, LEFT_W - 5, yy + 18) ? 0xFF27323C : 0xFF1C2228);
                c.drawTextWithShadow(textRenderer, e.enabled ? "●" : "○", 10, yy + 4, e.enabled ? 0xFF69D59B : 0xFF707981);
                c.drawTextWithShadow(textRenderer, trim(e.key(), LEFT_W - 75), 26, yy + 4, e.hiddenInEditor ? 0xFF76818A : 0xFFD9E3E9);
                if (e.locked) c.drawTextWithShadow(textRenderer, "L", LEFT_W - 21, yy + 4, 0xFFCCA55B);
                addHit(5, yy - 2, LEFT_W - 5, yy + 18, () -> select(e));
            }
            y += 21;
        }
        y += 5;
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "PRESETS", 8, y, 0xFF748695);
        y += 16;
        for (String preset : PresetStore.list()) {
            if (!matches(preset)) continue;
            int yy = y;
            if (visibleY(yy, bottom)) {
                c.fill(5, yy - 2, LEFT_W - 5, yy + 17, hovered(mx, my, 5, yy - 2, LEFT_W - 5, yy + 17) ? 0xFF28323B : 0xFF1C2228);
                c.drawTextWithShadow(textRenderer, trim(preset, LEFT_W - 24), 11, yy + 3, 0xFFB9C6CF);
                addHit(5, yy - 2, LEFT_W - 5, yy + 17, () -> loadPreset(preset));
            }
            y += 20;
        }
    }

    private void drawAdd(DrawContext c, int mx, int my, int y, int bottom) {
        String category = null;
        for (CineFxBridge.ElementType type : CineFxBridge.elementTypes()) {
            if (!matches(type.category() + " " + type.displayName())) continue;
            if (!type.category().equals(category)) {
                category = type.category();
                if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, category.toUpperCase(Locale.ROOT), 8, y, 0xFF748695);
                y += 16;
            }
            int yy = y;
            if (visibleY(yy, bottom)) {
                c.fill(5, yy - 2, LEFT_W - 5, yy + 18, hovered(mx, my, 5, yy - 2, LEFT_W - 5, yy + 18) ? 0xFF293640 : 0xFF1C2329);
                c.drawTextWithShadow(textRenderer, "+", 10, yy + 4, 0xFF75B9E7);
                c.drawTextWithShadow(textRenderer, trim(type.displayName(), LEFT_W - 38), 27, yy + 4, 0xFFDCE6EC);
                addHit(5, yy - 2, LEFT_W - 5, yy + 18, () -> addElement(type));
            }
            y += 21;
        }
    }

    private void drawBlocks(DrawContext c, int mx, int my, int y, int bottom) {
        String current = SceneManipulator.blockId(selected());
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, current == null ? "Click = create block" : "Selected: " + trim(current, LEFT_W - 90), 8, y, 0xFF7E909F);
        y += 17;
        ArrayList<Identifier> ids = new ArrayList<>(Registries.BLOCK.getIds());
        ids.sort(Comparator.comparing(Identifier::toString));
        for (Identifier id : ids) {
            if (!matches(id.toString())) continue;
            int yy = y;
            if (visibleY(yy, bottom)) {
                boolean chosen = id.toString().equals(current);
                c.fill(5, yy - 2, LEFT_W - 5, yy + 18, chosen ? 0xFF314B3B : hovered(mx, my, 5, yy - 2, LEFT_W - 5, yy + 18) ? 0xFF29343D : 0xFF1B2228);
                c.drawTextWithShadow(textRenderer, "■", 10, yy + 4, chosen ? 0xFF7BD896 : 0xFF8797A3);
                c.drawTextWithShadow(textRenderer, trim(id.toString(), LEFT_W - 38), 27, yy + 4, 0xFFD6E0E6);
                addHit(5, yy - 2, LEFT_W - 5, yy + 18, () -> chooseBlock(id.toString()));
            }
            y += 21;
        }
    }

    private void drawApi(DrawContext c, int mx, int my, int y, int bottom) {
        if (visibleY(y, bottom)) c.drawTextWithShadow(textRenderer, "REGISTERED CINEFX SCENES", 8, y, 0xFF748695);
        y += 17;
        ArrayList<SceneDefinition> scenes = new ArrayList<>(CineFxApi.scenes());
        scenes.removeIf(s -> s.id().getNamespace().equals("cinefx_gui") && s.id().getPath().endsWith("_runtime"));
        scenes.sort(Comparator.comparing(s -> s.id().toString()));
        for (SceneDefinition scene : scenes) {
            if (!matches(scene.id().toString())) continue;
            int yy = y;
            if (visibleY(yy, bottom)) {
                c.fill(5, yy - 2, LEFT_W - 5, yy + 18, hovered(mx, my, 5, yy - 2, LEFT_W - 5, yy + 18) ? 0xFF29343D : 0xFF1B2228);
                c.drawTextWithShadow(textRenderer, trim(scene.id().toString(), LEFT_W - 24), 10, yy + 4, 0xFFD6E0E6);
                addHit(5, yy - 2, LEFT_W - 5, yy + 18, () -> importScene(scene));
            }
            y += 21;
        }
    }

    private void drawInspector(DrawContext c, int mx, int my) {
        int x = width - RIGHT_W;
        int bottom = timelineTop();
        EditorModel.Element selected = selected();
        c.drawTextWithShadow(textRenderer, selected == null ? "SCENE PROPERTIES" : "ELEMENT PROPERTIES", x + 8, TOP_H + 8, 0xFF8797A4);
        int y = TOP_H + 25;
        if (selected != null) {
            c.drawTextWithShadow(textRenderer, trim(selected.key() + " · " + selected.label, RIGHT_W - 16), x + 8, y, 0xFFF0F4F7);
            y += 16;
            int bx = x + 8;
            bx = smallButton(c, mx, my, bx, y, 50, selected.enabled ? "On" : "Off", () -> mutate("Visibility", () -> selected.enabled = !selected.enabled)) + 3;
            bx = smallButton(c, mx, my, bx, y, 52, selected.hiddenInEditor ? "Show 3D" : "Hide 3D", () -> mutate("Editor visibility", () -> selected.hiddenInEditor = !selected.hiddenInEditor)) + 3;
            bx = smallButton(c, mx, my, bx, y, 45, selected.locked ? "Unlock" : "Lock", () -> mutate("Lock", () -> selected.locked = !selected.locked)) + 3;
            bx = smallButton(c, mx, my, bx, y, 50, "Duplicate", this::duplicateSelected) + 3;
            smallButton(c, mx, my, bx, y, 42, "Delete", this::deleteSelected);
            y += 22;
            bx = x + 8;
            bx = smallButton(c, mx, my, bx, y, 62, "Focus [F]", this::focusSelected) + 3;
            bx = smallButton(c, mx, my, bx, y, 62, "Key [K]", this::addKeyframesAtPlayhead) + 3;
            bx = smallButton(c, mx, my, bx, y, 66, "Copy JSON", this::copySelectedJson) + 3;
            smallButton(c, mx, my, bx, y, 66, "Paste JSON", this::pasteSelectedJson);
            y += 24;

            c.drawTextWithShadow(textRenderer, "ARRAY DUPLICATE", x + 8, y + 4, 0xFF738593);
            y += 17;
            bx = x + 8;
            bx = smallButton(c, mx, my, bx, y, 57, "Count " + arrayCount, this::cycleArrayCount) + 3;
            bx = smallButton(c, mx, my, bx, y, 49, "Axis " + arrayAxis.name(), this::cycleArrayAxis) + 3;
            bx = smallButton(c, mx, my, bx, y, 59, "Step " + format(arrayStep), this::cycleArrayStep) + 3;
            smallButton(c, mx, my, bx, y, 48, "Create", this::arrayDuplicate);
            y += 24;

            String block = SceneManipulator.blockId(selected);
            if (block != null) {
                c.drawTextWithShadow(textRenderer, "Block: " + trim(block, RIGHT_W - 58), x + 8, y + 3, 0xFF8FB79E);
                smallButton(c, mx, my, width - 80, y - 2, 72, "Choose…", () -> { leftTab = LeftTab.BLOCKS; leftScroll = 0; searchField.setText(""); });
                y += 21;
            }
            buildElementRows(selected);
        } else {
            c.drawTextWithShadow(textRenderer, trim(project.name + " · " + project.sceneId, RIGHT_W - 16), x + 8, y, 0xFFF0F4F7);
            y += 21;
            buildProjectRows();
        }

        int rowY = y - (int)inspectorScroll;
        for (PropertyRow row : propertyRows) {
            row.screenY = rowY;
            if (rowY + 18 >= TOP_H + 2 && rowY < bottom - 32) drawPropertyRow(c, mx, my, row, rowY);
            rowY += 19;
        }
        if (valueEditor.isVisible()) c.drawTextWithShadow(textRenderer, "EDIT VALUE", x + 8, bottom - 35, 0xFF70818F);
    }

    private void drawPropertyRow(DrawContext c, int mx, int my, PropertyRow row, int y) {
        int x = width - RIGHT_W + 7 + row.depth * 10;
        int right = width - 7;
        row.x1 = x; row.x2 = right;
        c.fill(x, y, right, y + 17, hovered(mx, my, x, y, right, y + 17) ? 0xFF26313A : 0xFF1C2228);
        int labelWidth = Math.min(138, Math.max(74, (right - x) / 2));
        c.drawTextWithShadow(textRenderer, trim(row.label, labelWidth - 7), x + 4, y + 4, row.container ? 0xFFA8B8C4 : 0xFF8496A4);
        c.drawTextWithShadow(textRenderer, trim(propertyValue(row), right - x - labelWidth - 24), x + labelWidth, y + 4, row.valueColor());
        if (row.canAdd()) {
            int px = right - 18;
            c.fill(px, y + 1, right - 1, y + 16, 0xFF35424C);
            c.drawTextWithShadow(textRenderer, "+", px + 5, y + 4, 0xFFE8F4FA);
            addHit(px, y + 1, right - 1, y + 16, () -> addContainerValue(row));
        }
    }

    private void drawTimeline(DrawContext c, int mx, int my) {
        int top = timelineTop();
        int rulerTop = top + 29;
        int trackTop = rulerTop + 27;
        c.fill(0, top, width, top + 28, 0xFF181D22);
        c.drawTextWithShadow(textRenderer, "TIMELINE", 8, top + 9, 0xFFA8B7C2);
        c.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "%.2f px/t · Shift-wheel pan · wheel zoom · RMB add key", pixelsPerTick), 82, top + 9, 0xFF71808B);
        c.fill(0, rulerTop, width, rulerTop + 26, 0xFF11161B);
        c.fill(0, trackTop, TIMELINE_LABEL_W, height, 0xFF151A1F);

        double step = niceStep(Math.max(.05, 72.0 / Math.max(.05, pixelsPerTick)));
        double first = Math.floor(timelineStartTick / step) * step;
        for (double tick = first; tick <= tickFromX(width) + step; tick += step) {
            int xx = tickX(tick);
            if (xx < TIMELINE_LABEL_W || xx > width) continue;
            c.fill(xx, rulerTop + 16, xx + 1, height, 0x382E3942);
            c.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "%.1fs", tick / 20.0), xx + 3, rulerTop + 5, 0xFF798995);
        }

        int visibleRow = 0;
        for (int i = Math.max(0, timelineScrollRows); i < project.elements.size(); i++) {
            int y = trackTop + visibleRow * ROW_H;
            if (y + ROW_H > height - 17) break;
            EditorModel.Element e = project.elements.get(i);
            boolean sel = e.editorId.equals(selectedId);
            c.fill(0, y, width, y + ROW_H - 1, sel ? 0xFF202D37 : (visibleRow % 2 == 0 ? 0xFF151B20 : 0xFF12181D));
            c.drawTextWithShadow(textRenderer, trim(e.key(), TIMELINE_LABEL_W - 48), 8, y + 7, e.enabled ? 0xFFD8E2E8 : 0xFF69737A);
            c.drawTextWithShadow(textRenderer, e.locked ? "L" : (e.enabled ? "●" : "○"), TIMELINE_LABEL_W - 24, y + 7, e.locked ? 0xFFD4A95A : e.enabled ? 0xFF68D399 : 0xFF69737A);
            int sx = clampX(tickX(e.startTick()));
            int ex = clampX(tickX(Math.max(e.endTick(), e.startTick() + .05)));
            if (ex < sx) { int swap = sx; sx = ex; ex = swap; }
            if (ex - sx < 4) ex = sx + 4;
            c.fill(sx, y + 3, Math.min(width, ex), y + ROW_H - 4, sel ? 0xFF3A7AA6 : e.enabled ? 0xFF31576E : 0xFF3B4248);
            if (sel) {
                c.fill(sx, y + 3, sx + 2, y + ROW_H - 4, 0xFFC0E7FF);
                c.fill(Math.max(sx, ex - 2), y + 3, ex, y + ROW_H - 4, 0xFFC0E7FF);
            }
            clipHits.add(new ClipHit(e, sx, y + 2, ex, y + ROW_H - 3));
            drawKeyframes(c, e, y);
            visibleRow++;
        }

        int ph = tickX(preview.currentTick());
        if (ph >= TIMELINE_LABEL_W && ph <= width) {
            c.fill(ph - 1, rulerTop, ph + 1, height, 0xFFFFD464);
            c.fill(ph - 4, rulerTop, ph + 5, rulerTop + 6, 0xFFFFD464);
        }
        c.drawTextWithShadow(textRenderer, statusText(), 8, height - 13, 0xFF8798A4);
    }

    private void drawKeyframes(DrawContext c, EditorModel.Element element, int rowY) {
        ArrayList<KeyRef> refs = new ArrayList<>();
        collectTimed(element.data, null, refs);
        for (KeyRef ref : refs) {
            JsonObject key = ref.key;
            double local = safeDouble(key.get("tick"), Double.NaN);
            if (!Double.isFinite(local)) continue;
            int x = tickX(element.startTick() + local);
            if (x < TIMELINE_LABEL_W || x > width) continue;
            int cy = rowY + ROW_H / 2;
            int color = element.editorId.equals(selectedId) ? 0xFFFFC85A : 0xFF927C4D;
            c.fill(x - 1, cy - 5, x + 2, cy + 6, color);
            c.fill(x - 4, cy - 2, x + 5, cy + 3, color);
            keyHits.add(new KeyHit(element, key, ref.parent, x - 6, cy - 7, x + 7, cy + 8));
        }
    }

    private void drawViewport(DrawContext c, int mx, int my) {
        int left = LEFT_W, top = TOP_H, right = width - RIGHT_W, bottom = timelineTop();
        if (right <= left || bottom <= top) return;
        c.drawTextWithShadow(textRenderer, preview.sceneCameraPreview() ? "SCENE CAMERA [C]" : "EDITOR CAMERA · RMB + WASD/QE · wheel speed · G/R/S", left + 9, top + 8, 0xDDE7EEF3);
        c.drawTextWithShadow(textRenderer, "F focus · Num1/3/7 views · Shift fine · Ctrl snap", left + 9, top + 21, 0xAD9DACB7);

        if (guides) {
            int vx1 = left + (right - left) / 3, vx2 = left + (right - left) * 2 / 3;
            int vy1 = top + (bottom - top) / 3, vy2 = top + (bottom - top) * 2 / 3;
            c.fill(vx1, top, vx1 + 1, bottom, 0x22FFFFFF); c.fill(vx2, top, vx2 + 1, bottom, 0x22FFFFFF);
            c.fill(left, vy1, right, vy1 + 1, 0x22FFFFFF); c.fill(left, vy2, right, vy2 + 1, 0x22FFFFFF);
            c.fill((left + right) / 2, (top + bottom) / 2 - 5, (left + right) / 2 + 1, (top + bottom) / 2 + 6, 0x55FFFFFF);
            c.fill((left + right) / 2 - 5, (top + bottom) / 2, (left + right) / 2 + 6, (top + bottom) / 2 + 1, 0x55FFFFFF);
        }

        Vec3d cam = preview.editorCameraPosition();
        float yaw = preview.editorCameraYaw(), pitch = preview.editorCameraPitch();
        EditorModel.Element selected = selected();
        if (selected != null) drawAnimationPath(c, selected, cam, yaw, pitch, left, top, right, bottom);

        for (EditorModel.Element e : project.elements) {
            if (e.hiddenInEditor) continue;
            Vec3d local = SceneManipulator.pivotLocal(e, Math.max(0, preview.currentTick() - e.startTick()));
            if (local == null) continue;
            Vec3d world = project.anchor().add(local);
            ViewportGizmo.ScreenPoint point = ViewportGizmo.project(world, cam, yaw, pitch, left, top, right, bottom);
            if (!point.visible() || point.x() < left || point.x() >= right || point.y() < top || point.y() >= bottom) continue;
            int px = (int)Math.round(point.x()), py = (int)Math.round(point.y());
            boolean sel = e.editorId.equals(selectedId);
            int color = sel ? 0xFFFFCF62 : e.enabled ? 0xFF9FC7DF : 0xFF68737B;
            c.fill(px - (sel ? 5 : 3), py - (sel ? 5 : 3), px + (sel ? 6 : 4), py + (sel ? 6 : 4), 0xB9000000);
            c.fill(px - (sel ? 3 : 2), py - (sel ? 3 : 2), px + (sel ? 4 : 3), py + (sel ? 4 : 3), color);
            if (sel || Math.hypot(mx - px, my - py) < 18) c.drawTextWithShadow(textRenderer, trim(e.key(), 130), px + 8, py - 4, color);
            viewportHits.add(new ViewportHit(e, px - 8, py - 8, px + 9, py + 9));
        }

        if (selected != null && !selected.locked && !preview.sceneCameraPreview()) {
            Vec3d local = SceneManipulator.pivotLocal(selected, Math.max(0, preview.currentTick() - selected.startTick()));
            if (local != null) {
                gizmoLayout = ViewportGizmo.layout(project.anchor().add(local), cam, yaw, pitch, left, top, right, bottom);
                ViewportGizmo.draw(c, gizmoLayout, tool, gizmoAxis, mx, my);
            }
        }

        Vec3d p = preview.editorCameraPosition();
        String camera = String.format(Locale.ROOT, "XYZ %.2f %.2f %.2f · yaw %.1f · pitch %.1f · speed %.2f", p.x, p.y, p.z, yaw, pitch, cameraSpeed);
        c.drawTextWithShadow(textRenderer, camera, left + 9, bottom - 15, 0xAEB7C3CB);
        if (preview.lastBuild() != null && !preview.lastBuild().errors().isEmpty()) {
            String errors = preview.lastBuild().errors().size() + " invalid draft(s) skipped";
            c.drawTextWithShadow(textRenderer, errors, right - textRenderer.getWidth(errors) - 8, top + 8, 0xFFFF9879);
        }
    }

    private void drawAnimationPath(DrawContext c, EditorModel.Element e, Vec3d cam, float yaw, float pitch,
                                   int left, int top, int right, int bottom) {
        List<Vec3d> path = SceneManipulator.animationPathLocal(e);
        if (path.size() < 2) return;
        ViewportGizmo.ScreenPoint previous = null;
        for (Vec3d local : path) {
            ViewportGizmo.ScreenPoint current = ViewportGizmo.project(project.anchor().add(local), cam, yaw, pitch, left, top, right, bottom);
            if (previous != null && previous.visible() && current.visible()) ViewportGizmo.drawLine(c, previous.x(), previous.y(), current.x(), current.y(), 0xAA63B7E9, 1);
            if (current.visible()) c.fill((int)current.x() - 2, (int)current.y() - 2, (int)current.x() + 3, (int)current.y() + 3, 0xCC63B7E9);
            previous = current;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled) && (searchField.isFocused() || valueEditor.isFocused())) return true;
        double mx = click.x(), my = click.y();

        if (my >= timelineTop()) {
            for (KeyHit hit : List.copyOf(keyHits)) if (hit.contains(mx, my)) {
                select(hit.element);
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (hit.parent != null && hit.parent.size() > 1) {
                        checkpoint(); removeIdentity(hit.parent, hit.key); markChangedContinuous(); toast("Keyframe removed");
                    } else toast("A track needs at least one keyframe");
                    return true;
                }
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !hit.element.locked) {
                    dragMode = DragMode.KEYFRAME; dragElement = hit.element; dragKeyframe = hit.key; dragKeyParent = hit.parent;
                    dragMouseStartTick = tickFromX(mx); dragOriginalKeyTick = safeDouble(hit.key.get("tick"), 0); checkpoint(); return true;
                }
            }
            for (ClipHit hit : List.copyOf(clipHits)) if (hit.contains(mx, my) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                select(hit.element); if (hit.element.locked) return true;
                dragElement = hit.element; dragMouseStartTick = tickFromX(mx); dragOriginalStart = hit.element.startTick(); dragOriginalEnd = hit.element.endTick();
                dragMode = Math.abs(mx - hit.x1) <= 5 ? DragMode.CLIP_LEFT : Math.abs(mx - hit.x2) <= 5 ? DragMode.CLIP_RIGHT : DragMode.CLIP_MOVE;
                checkpoint(); return true;
            }
            if (mx >= TIMELINE_LABEL_W) {
                preview.setTick(client, tickFromX(mx));
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) addKeyframesAtPlayhead();
                else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragMode = DragMode.PLAYHEAD;
                return true;
            }
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inViewport(mx, my) && gizmoLayout != null && selected() != null) {
            SceneManipulator.Axis axis = ViewportGizmo.hit(gizmoLayout, tool, mx, my);
            if (axis != null && !(tool == SceneManipulator.Tool.ROTATE && axis == SceneManipulator.Axis.CENTER)) {
                double localTick = Math.max(0, preview.currentTick() - selected().startTick());
                SceneManipulator.Session session = SceneManipulator.begin(selected(), tool, axis, localTick);
                if (session != null) {
                    checkpoint(); dragMode = DragMode.GIZMO; dragElement = selected(); gizmoSession = session; gizmoAxis = axis;
                    dragGizmoLayout = gizmoLayout; gizmoStartX = mx; gizmoStartY = my; return true;
                }
            }
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inViewport(mx, my)) {
            ViewportHit closest = null;
            double best = Double.POSITIVE_INFINITY;
            for (ViewportHit hit : viewportHits) if (hit.contains(mx, my)) {
                double d = Math.hypot(mx - (hit.x1 + hit.x2) * .5, my - (hit.y1 + hit.y2) * .5);
                if (d < best) { best = d; closest = hit; }
            }
            if (closest != null) { select(closest.element); return true; }
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && inViewport(mx, my)) {
            rightLook = true;
            return true;
        }

        for (Hit hit : List.copyOf(hits)) if (hit.contains(mx, my)) { hit.action.run(); return true; }

        for (PropertyRow row : propertyRows) {
            if (!row.contains(mx, my)) continue;
            if (row.value != null && row.value.isJsonPrimitive() && row.value.getAsJsonPrimitive().isBoolean()) {
                checkpoint(); row.set(new JsonPrimitive(!row.value.getAsBoolean())); markChangedContinuous();
            } else if (row.isEnum()) {
                checkpoint(); int direction = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
                row.set(new JsonPrimitive(EditorSchema.cycleEnum(row.type, row.value.getAsString(), direction))); markChangedContinuous();
            } else if (row.container) toggleCollapsed(row.path);
            else if (row.value == null || row.value.isJsonNull()) {
                checkpoint(); row.set(EditorSchema.defaultValue(row.type, row.label, project.durationTicks)); markChangedContinuous();
            } else beginPropertyEdit(row);
            return true;
        }

        valueEditor.setFocused(false); valueEditor.setVisible(false); activeProperty = null;
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (rightLook && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            preview.setEditorCamera(preview.editorCameraPosition(), preview.editorCameraYaw() + (float)(dx * .28), preview.editorCameraPitch() + (float)(dy * .28));
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, dx, dy);

        if (dragMode == DragMode.GIZMO && gizmoSession != null && dragGizmoLayout != null) {
            boolean fine = isShiftDown(); boolean snapping = isCtrlDown();
            if (gizmoAxis == SceneManipulator.Axis.CENTER) {
                if (tool == SceneManipulator.Tool.MOVE) {
                    Vec3d delta = ViewportGizmo.planeMove(dragGizmoLayout, preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                            click.x() - gizmoStartX, click.y() - gizmoStartY, fine, snapping);
                    gizmoSession.applyPlane(delta);
                } else if (tool == SceneManipulator.Tool.SCALE) {
                    double amount = -(click.y() - gizmoStartY) / 100.0;
                    if (fine) amount *= .2;
                    if (snapping) amount = Math.round(amount * 10.0) / 10.0;
                    gizmoSession.applyAxis(amount);
                }
            } else {
                double amount = ViewportGizmo.axisAmount(dragGizmoLayout, gizmoAxis, tool, gizmoStartX, gizmoStartY, click.x(), click.y(), fine, snapping);
                gizmoSession.applyAxis(amount);
            }
            markChangedContinuous(); return true;
        }

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
            dragMode = DragMode.NONE; dragElement = null; dragKeyframe = null; dragKeyParent = null;
            gizmoSession = null; gizmoAxis = null; dragGizmoLayout = null;
            return true;
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
        if (mx < LEFT_W) { leftScroll = Math.max(0, leftScroll - verticalAmount * 25); return true; }
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
        if (searchField.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) searchField.setFocused(false);
            else if (super.keyPressed(input)) return true;
        }
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && input.key() == GLFW.GLFW_KEY_S) { saveProject(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if ((ctrl || shift) && input.key() == GLFW.GLFW_KEY_D) { duplicateSelected(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_C && selected() != null) { copySelectedJson(); return true; }
        if (ctrl && input.key() == GLFW.GLFW_KEY_V && selected() != null) { pasteSelectedJson(); return true; }
        if (input.key() == GLFW.GLFW_KEY_G) { tool = SceneManipulator.Tool.MOVE; return true; }
        if (input.key() == GLFW.GLFW_KEY_R) { tool = SceneManipulator.Tool.ROTATE; return true; }
        if (input.key() == GLFW.GLFW_KEY_S) { tool = SceneManipulator.Tool.SCALE; return true; }
        if (input.key() == GLFW.GLFW_KEY_SPACE) { preview.togglePlay(client); return true; }
        if (input.key() == GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
        if (input.key() == GLFW.GLFW_KEY_HOME) { preview.setTick(client, 0); return true; }
        if (input.key() == GLFW.GLFW_KEY_END) { preview.setTick(client, project.durationTicks); return true; }
        if (input.key() == GLFW.GLFW_KEY_F) { focusSelected(); return true; }
        if (input.key() == GLFW.GLFW_KEY_C) { preview.setSceneCameraPreview(client, !preview.sceneCameraPreview()); return true; }
        if (input.key() == GLFW.GLFW_KEY_K || input.key() == GLFW.GLFW_KEY_I) { addKeyframesAtPlayhead(); return true; }
        if (input.key() == GLFW.GLFW_KEY_KP_1) { snapView(0, 0); return true; }
        if (input.key() == GLFW.GLFW_KEY_KP_3) { snapView(-90, 0); return true; }
        if (input.key() == GLFW.GLFW_KEY_KP_7) { snapView(0, 89); return true; }
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

    private void chooseBlock(String blockId) {
        EditorModel.Element e = selected();
        if (e != null && SceneManipulator.blockId(e) != null) {
            mutate("Block changed", () -> SceneManipulator.setBlock(e, blockId)); return;
        }
        CineFxBridge.ElementType blockType = CineFxBridge.elementTypes().stream()
                .filter(t -> t.apiClass().getName().endsWith("SceneElement$Block") || t.displayName().equalsIgnoreCase("Block"))
                .findFirst().orElse(null);
        if (blockType == null) { toast("CineFX Block element unavailable"); return; }
        checkpoint();
        EditorModel.Element block = CineFxBridge.newDraft(blockType, project.durationTicks, project.elements.size());
        ensureUniqueKey(block); SceneManipulator.setBlock(block, blockId);
        Vec3d spawn = preview.editorCameraPosition().add(cameraForward(preview.editorCameraYaw(), preview.editorCameraPitch()).multiply(4)).subtract(project.anchor());
        SceneManipulator.setBaseOffset(block, spawn);
        project.elements.add(block); select(block); markChangedContinuous(); toast("Added " + blockId);
    }

    private void createCameraFromView() {
        EditorModel.Element target = selected();
        Vec3d targetLocal = target == null ? null : SceneManipulator.pivotLocal(target, Math.max(0, preview.currentTick() - target.startTick()));
        CineFxBridge.ElementType cameraType = CineFxBridge.elementTypes().stream()
                .filter(t -> t.apiClass().getName().endsWith("EventElement$Camera") || t.displayName().equalsIgnoreCase("Camera"))
                .findFirst().orElse(null);
        if (cameraType == null) { toast("CineFX Camera element unavailable"); return; }
        checkpoint();
        EditorModel.Element camera = CineFxBridge.newDraft(cameraType, project.durationTicks, project.elements.size());
        ensureUniqueKey(camera);
        camera.setStartTick(preview.currentTick()); camera.setEndTick(project.durationTicks);
        if (camera.data.has("mode")) camera.data.addProperty("mode", "ANCHOR_ABSOLUTE");
        SceneManipulator.setBaseOffset(camera, preview.editorCameraPosition().subtract(project.anchor()));
        JsonObject value = SceneManipulator.ensureTransformKey(camera, 0);
        if (value != null) {
            JsonObject rot = value.has("rotationDegrees") && value.get("rotationDegrees").isJsonObject() ? value.getAsJsonObject("rotationDegrees") : new JsonObject();
            rot.addProperty("x", preview.editorCameraPitch()); rot.addProperty("y", preview.editorCameraYaw()); rot.addProperty("z", 0); value.add("rotationDegrees", rot);
        }
        if (targetLocal != null) SceneManipulator.setLookAt(camera, targetLocal);
        project.elements.add(camera); select(camera); markChangedContinuous(); toast(targetLocal == null ? "Camera created from view" : "Camera created + look-at target");
    }

    private void duplicateSelected() {
        EditorModel.Element e = selected(); if (e == null) return;
        checkpoint(); EditorModel.Element copy = e.duplicate(); ensureUniqueKey(copy); project.elements.add(copy); select(copy); markChangedContinuous(); toast("Duplicated " + e.key());
    }

    private void arrayDuplicate() {
        EditorModel.Element source = selected(); if (source == null || source.locked) return;
        checkpoint();
        Vec3d axis = arrayAxis == SceneManipulator.Axis.X ? new Vec3d(1,0,0) : arrayAxis == SceneManipulator.Axis.Y ? new Vec3d(0,1,0) : new Vec3d(0,0,1);
        double localTick = Math.max(0, preview.currentTick() - source.startTick());
        EditorModel.Element last = source;
        for (int i = 1; i < arrayCount; i++) {
            EditorModel.Element copy = source.duplicate(); ensureUniqueKey(copy);
            SceneManipulator.translate(copy, axis.multiply(arrayStep * i), localTick);
            project.elements.add(copy); last = copy;
        }
        select(last); markChangedContinuous(); toast("Array: " + arrayCount + " objects on " + arrayAxis.name());
    }

    private void cycleArrayCount() { arrayCount = arrayCount == 4 ? 8 : arrayCount == 8 ? 16 : arrayCount == 16 ? 32 : 4; }
    private void cycleArrayAxis() { arrayAxis = arrayAxis == SceneManipulator.Axis.X ? SceneManipulator.Axis.Y : arrayAxis == SceneManipulator.Axis.Y ? SceneManipulator.Axis.Z : SceneManipulator.Axis.X; }
    private void cycleArrayStep() { arrayStep = arrayStep == .25 ? .5 : arrayStep == .5 ? 1 : arrayStep == 1 ? 2 : arrayStep == 2 ? 4 : .25; }

    private void deleteSelected() {
        EditorModel.Element e = selected(); if (e == null || e.locked) return;
        checkpoint(); project.elements.remove(e); select(null); markChangedContinuous(); toast("Element deleted");
    }

    private void focusSelected() {
        EditorModel.Element e = selected(); if (e == null) return;
        Vec3d local = SceneManipulator.pivotLocal(e, Math.max(0, preview.currentTick() - e.startTick()));
        if (local == null) { toast("No editable position found"); return; }
        Vec3d target = project.anchor().add(local), forward = cameraForward(preview.editorCameraYaw(), preview.editorCameraPitch());
        preview.setSceneCameraPreview(client, false); preview.setEditorCamera(target.subtract(forward.multiply(6)), preview.editorCameraYaw(), preview.editorCameraPitch());
        toast("Focused " + e.key());
    }

    private void snapView(float yaw, float pitch) {
        EditorModel.Element e = selected();
        Vec3d target = e == null ? project.anchor() : project.anchor().add(orZero(SceneManipulator.pivotLocal(e, Math.max(0, preview.currentTick() - e.startTick()))));
        Vec3d forward = cameraForward(yaw, pitch);
        preview.setSceneCameraPreview(client, false); preview.setEditorCamera(target.subtract(forward.multiply(8)), yaw, pitch);
    }

    private void newProject() {
        PresetStore.autosave(project); history.clear(); project = EditorModel.Project.fresh(client); preview.setProject(project); select(null);
        leftScroll = inspectorScroll = 0; fitTimeline(); toast("New project");
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
        if (client != null && client.player != null) { project.anchorX = client.player.getX(); project.anchorY = client.player.getY(); project.anchorZ = client.player.getZ(); }
        preview.setProject(project); select(null); fitTimeline(); leftTab = LeftTab.OUTLINER; toast("Imported " + scene.id());
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
    private void markChangedContinuous() { project.dirty = true; preview.markDirty(); }
    private void mutate(String message, Runnable action) { checkpoint(); action.run(); markChangedContinuous(); toast(message); }

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

    private void collectTimed(JsonElement value, JsonArray parent, List<KeyRef> out) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            if (parent != null && o.has("tick") && o.get("tick").isJsonPrimitive()) out.add(new KeyRef(o, parent));
            for (Map.Entry<String, JsonElement> e : o.entrySet()) collectTimed(e.getValue(), null, out);
        } else if (value.isJsonArray()) {
            JsonArray a = value.getAsJsonArray();
            for (JsonElement child : a) {
                if (child.isJsonObject() && child.getAsJsonObject().has("tick")) out.add(new KeyRef(child.getAsJsonObject(), a));
                else collectTimed(child, a, out);
            }
        }
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

    private int button(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action) {
        c.fill(x, y, x + w, y + 22, hovered(mx, my, x, y, x + w, y + 22) ? 0xFF34404A : 0xFF242B31);
        c.drawTextWithShadow(textRenderer, trim(text, w - 8), x + 4, y + 7, 0xFFE7EDF1); addHit(x, y, x + w, y + 22, action); return x + w;
    }
    private int smallButton(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action) {
        c.fill(x, y, x + w, y + 18, hovered(mx, my, x, y, x + w, y + 18) ? 0xFF35414A : 0xFF252C32);
        c.drawTextWithShadow(textRenderer, trim(text, w - 6), x + 3, y + 5, 0xFFD8E2E8); addHit(x, y, x + w, y + 18, action); return x + w;
    }
    private void tab(DrawContext c, int mx, int my, int x, int y, int w, String text, LeftTab candidate) {
        c.fill(x, y, x + w - 2, y + 19, leftTab == candidate ? 0xFF304A5E : hovered(mx, my, x, y, x + w - 2, y + 19) ? 0xFF29333B : 0xFF20262C);
        c.drawTextWithShadow(textRenderer, trim(text, w - 8), x + 5, y + 6, 0xFFDCE5EA);
        addHit(x, y, x + w - 2, y + 19, () -> { leftTab = candidate; leftScroll = 0; });
    }
    private int leftAction(DrawContext c, int mx, int my, int y, int bottom, String text, Runnable action) {
        if (visibleY(y, bottom)) { c.fill(5, y - 2, LEFT_W - 5, y + 17, hovered(mx, my, 5, y - 2, LEFT_W - 5, y + 17) ? 0xFF30404B : 0xFF242C32); c.drawTextWithShadow(textRenderer, text, 11, y + 3, 0xFFDCE5EA); addHit(5, y - 2, LEFT_W - 5, y + 17, action); }
        return y + 22;
    }
    private void addHit(int x1, int y1, int x2, int y2, Runnable action) { hits.add(new Hit(x1, y1, x2, y2, action)); }

    private void fitTimeline() { pixelsPerTick = Math.max(.15, Math.max(240, width - TIMELINE_LABEL_W - 20) / Math.max(1.0, project.durationTicks)); timelineStartTick = 0; }
    private int timelineTop() { return Math.max(TOP_H + 170, height - TIMELINE_H); }
    private boolean inViewport(double x, double y) { return x >= LEFT_W && x < width - RIGHT_W && y >= TOP_H && y < timelineTop(); }
    private int tickX(double tick) { return TIMELINE_LABEL_W + (int)Math.round((tick - timelineStartTick) * pixelsPerTick); }
    private double tickFromX(double x) { return timelineStartTick + (x - TIMELINE_LABEL_W) / Math.max(.0001, pixelsPerTick); }
    private int clampX(int x) { return Math.max(TIMELINE_LABEL_W, Math.min(width, x)); }
    private double snap(double tick) { return Math.round(tick * 4) / 4.0; }
    private double niceStep(double value) { double exp = Math.pow(10, Math.floor(Math.log10(Math.max(1e-6, value)))); double n = value / exp; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * exp; }
    private boolean visibleY(int y, int bottom) { return y >= TOP_H + 70 && y < bottom - 18; }
    private boolean matches(String value) { return searchText == null || searchText.isBlank() || (value != null && value.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT).trim())); }
    private String format(double v) { return v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.2f", v); }

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
    private void toast(String message) { status = message; statusUntil = System.currentTimeMillis() + 4500; }
    private String editText(JsonElement v) { return v == null || v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : EditorModel.GSON.toJson(v); }
    private String trim(String value, int width) { return textRenderer.trimToWidth(value == null ? "" : value, Math.max(4, width)); }

    private static boolean hovered(double mx, double my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx <= x2 && my >= y1 && my <= y2; }
    private static double safeDouble(JsonElement v, double fallback) { try { return v == null || v.isJsonNull() ? fallback : v.getAsDouble(); } catch (RuntimeException e) { return fallback; } }
    private static String safeString(JsonElement v) { try { return v == null || v.isJsonNull() ? "" : v.getAsString(); } catch (RuntimeException e) { return ""; } }
    private static String simpleType(String v) { int i = v == null ? -1 : Math.max(v.lastIndexOf('.'), v.lastIndexOf('$')); return i >= 0 ? v.substring(i + 1) : v; }
    private static String shortError(Throwable t) { String s = t.getMessage(); return s == null || s.isBlank() ? t.getClass().getSimpleName() : s; }
    private static String slug(String v) { String s = v == null ? "element" : v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); return s.isBlank() ? "element" : s; }
    private static boolean pressed(long window, int key) { return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }
    private boolean isShiftDown() { if (client == null) return false; long w = client.getWindow().getHandle(); return pressed(w, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(w, GLFW.GLFW_KEY_RIGHT_SHIFT); }
    private boolean isCtrlDown() { if (client == null) return false; long w = client.getWindow().getHandle(); return pressed(w, GLFW.GLFW_KEY_LEFT_CONTROL) || pressed(w, GLFW.GLFW_KEY_RIGHT_CONTROL); }
    private static Vec3d cameraForward(float yaw, float pitch) { double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch); return new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)); }
    private static Vec3d orZero(Vec3d value) { return value == null ? Vec3d.ZERO : value; }
    private static String uniqueMapKey(Map<String, String> map, String base) { int i = 1; String key = base; while (map.containsKey(key)) key = base + "_" + i++; return key; }
    private static void removeIdentity(JsonArray array, JsonObject object) { for (int i = 0; i < array.size(); i++) if (array.get(i) == object) { array.remove(i); return; } }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }
    private record ClipHit(EditorModel.Element element, int x1, int y1, int x2, int y2) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }
    private record KeyRef(JsonObject key, JsonArray parent) { }
    private record KeyHit(EditorModel.Element element, JsonObject key, JsonArray parent, int x1, int y1, int x2, int y2) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }
    private record ViewportHit(EditorModel.Element element, int x1, int y1, int x2, int y2) { boolean contains(double x, double y) { return hovered(x, y, x1, y1, x2, y2); } }

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
        static PropertyRow heading(String label, String path, Runnable add) { PropertyRow row = new PropertyRow(label, path, JsonNull.INSTANCE, Object.class, 0, true, null); row.addAction = add; return row; }
        boolean contains(double x, double y) { return hovered(x, y, x1, screenY, x2, screenY + 17); }
        boolean isEnum() { Class<?> raw = EditorSchema.raw(type); return raw != null && raw.isEnum() && value != null && value.isJsonPrimitive(); }
        boolean canAdd() { return addAction != null || value != null && (value.isJsonArray() || value.isJsonNull()); }
        void set(JsonElement replacement) { if (setter != null) setter.accept(replacement == null ? JsonNull.INSTANCE : replacement); value = replacement; }
        int valueColor() { if (value == null || value.isJsonNull()) return 0xFF7B858C; if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean() ? 0xFF78D49B : 0xFFE1867C; if (isEnum()) return 0xFF72B8E8; return 0xFFD3DDE3; }
    }
}
