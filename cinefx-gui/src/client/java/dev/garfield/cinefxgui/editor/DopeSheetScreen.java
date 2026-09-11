package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Flashback/Blender-style channel Dope Sheet backed by the exact CurveChannels key JSON. */
public final class DopeSheetScreen extends Screen {
    private static final int TOP = 34;
    private static final int RULER_H = 28;
    private static final int LEFT = 300;
    private static final int ROW_H = 22;

    private final Screen parent;
    private final EditorModel.Project project;
    private final EditorModel.Element element;
    private final PreviewController preview;
    private final EditorModel.History history;
    private final List<CurveChannels.Channel> channels;
    private final Set<String> collapsedGroups = new HashSet<>();
    private final IdentityHashMap<JsonObject, JsonArray> selected = new IdentityHashMap<>();
    private final IdentityHashMap<JsonObject, Double> dragStartTicks = new IdentityHashMap<>();
    private final List<KeyHit> keyHits = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();

    private double startTick;
    private double pixelsPerTick = 5.0;
    private double verticalScroll;
    private boolean draggingKeys;
    private double dragMouseTick;
    private boolean boxSelecting;
    private double boxX1, boxY1, boxX2, boxY2;
    private boolean fitted;

    public DopeSheetScreen(Screen parent, EditorModel.Project project, EditorModel.Element element,
                           PreviewController preview, EditorModel.History history) {
        super(Text.literal("CineFX Dope Sheet"));
        this.parent = parent;
        this.project = project;
        this.element = element;
        this.preview = preview;
        this.history = history;
        this.channels = CurveChannels.discover(element);
    }

    @Override protected void init() { if (!fitted) { fit(); fitted = true; } }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override
    public void tick() {
        super.tick();
        if (client != null && client.world != null) preview.tick(client);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float deltaTicks) {
        keyHits.clear(); rowHits.clear();
        c.fill(0, 0, width, height, 0xFF11161B);
        c.fill(0, 0, width, TOP, 0xFF1C2229);
        c.drawTextWithShadow(textRenderer, "CineFX Dope Sheet", 10, 12, 0xFFF0F4F7);
        button(c, mx, my, 144, 6, 48, "Back");
        button(c, mx, my, 198, 6, 40, "Fit");
        button(c, mx, my, 244, 6, 62, "Curves");
        c.drawTextWithShadow(textRenderer,
                "Ctrl+A select · drag keys · Alt-drag box · Delete · wheel zoom · Shift-wheel pan",
                320, 12, 0xFF8394A1);

        int rulerY = TOP;
        c.fill(0, rulerY, width, rulerY + RULER_H, 0xFF12181D);
        c.fill(0, rulerY, LEFT, height, 0xFF171C22);
        drawRuler(c, rulerY);
        drawRows(c, mx, my, rulerY + RULER_H);
        drawPlayhead(c, rulerY);
        drawSelectionBox(c);
        super.render(c, mx, my, deltaTicks);
    }

    private void drawRuler(DrawContext c, int rulerY) {
        c.drawTextWithShadow(textRenderer, trim(element.key(), LEFT - 18), 10, rulerY + 10, 0xFFC9D5DC);
        double step = niceStep(Math.max(.05, 72.0 / Math.max(.05, pixelsPerTick)));
        double first = Math.floor(startTick / step) * step;
        for (double tick = first; tick <= tickAt(width) + step; tick += step) {
            int x = tickX(tick);
            if (x < LEFT || x > width) continue;
            c.fill(x, rulerY + 16, x + 1, height, 0x303B4852);
            c.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "%.1fs", tick / 20.0), x + 3, rulerY + 5, 0xFF798995);
        }
    }

    private void drawRows(DrawContext c, int mx, int my, int top) {
        List<Row> rows = rows();
        int y = top - (int)verticalScroll;
        int visibleIndex = 0;
        for (Row row : rows) {
            if (y + ROW_H >= top && y < height) {
                int bg = visibleIndex % 2 == 0 ? 0xFF151B20 : 0xFF12181D;
                c.fill(0, y, width, y + ROW_H - 1, bg);
                if (row.group) {
                    c.fill(0, y, width, y + ROW_H - 1, 0xFF1B232A);
                    boolean closed = collapsedGroups.contains(row.label);
                    c.drawTextWithShadow(textRenderer, closed ? "▸" : "▾", 10, y + 7, 0xFF91A4B0);
                    c.drawTextWithShadow(textRenderer, trim(row.label, LEFT - 48), 28, y + 7, 0xFFC9D5DC);
                    rowHits.add(new RowHit(row, y, y + ROW_H));
                } else {
                    String channelName = shortChannel(row.channel.label(), row.label);
                    c.drawTextWithShadow(textRenderer, trim(channelName, LEFT - 46), 30, y + 7, 0xFF9FB0BC);
                    drawChannelKeys(c, row.channel, y);
                }
                c.fill(LEFT - 1, y, LEFT, y + ROW_H, 0xFF33414B);
            }
            y += ROW_H;
            visibleIndex++;
        }
        if (rows.isEmpty()) {
            c.drawTextWithShadow(textRenderer, "No numeric animation channels", 12, top + 16, 0xFFFFA07F);
            c.drawTextWithShadow(textRenderer, "Add a keyframed track in Studio first.", 12, top + 32, 0xFF8796A0);
        }
    }

    private void drawChannelKeys(DrawContext c, CurveChannels.Channel channel, int y) {
        int cy = y + ROW_H / 2;
        for (int i = 0; i < channel.size(); i++) {
            JsonObject key = channel.key(i);
            int x = tickX(channel.tick(i));
            if (x < LEFT - 8 || x > width + 8) continue;
            boolean sel = selected.containsKey(key);
            int color = sel ? 0xFFFFD260 : 0xFFDDE7EC;
            c.fill(x - 1, cy - 6, x + 2, cy + 7, color);
            c.fill(x - 5, cy - 2, x + 6, cy + 3, color);
            keyHits.add(new KeyHit(channel, key, channel.keys(), x - 7, cy - 8, x + 8, cy + 9));
        }
    }

    private void drawPlayhead(DrawContext c, int rulerY) {
        double local = preview.currentTick() - element.startTick();
        int x = tickX(local);
        if (x >= LEFT && x <= width) {
            c.fill(x - 1, rulerY, x + 1, height, 0xFFFFD464);
            c.fill(x - 4, rulerY, x + 5, rulerY + 6, 0xFFFFD464);
        }
    }

    private void drawSelectionBox(DrawContext c) {
        if (!boxSelecting) return;
        int x1 = (int)Math.floor(Math.min(boxX1, boxX2));
        int y1 = (int)Math.floor(Math.min(boxY1, boxY2));
        int x2 = (int)Math.ceil(Math.max(boxX1, boxX2));
        int y2 = (int)Math.ceil(Math.max(boxY1, boxY2));
        x1 = Math.max(LEFT, x1); y1 = Math.max(TOP + RULER_H, y1);
        c.fill(x1, y1, x2, y2, 0x223FA8E8);
        c.fill(x1, y1, x2, y1 + 1, 0xFF72C5F2);
        c.fill(x1, y2 - 1, x2, y2, 0xFF72C5F2);
        c.fill(x1, y1, x1 + 1, y2, 0xFF72C5F2);
        c.fill(x2 - 1, y1, x2, y2, 0xFF72C5F2);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && my >= 6 && my <= 28) {
            if (mx >= 144 && mx <= 192) { closeEditor(); return true; }
            if (mx >= 198 && mx <= 238) { fit(); return true; }
            if (mx >= 244 && mx <= 306) { if (client != null) client.setScreen(new CurveEditorScreen(this, project, element, preview, history)); return true; }
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (RowHit hit : rowHits) if (hit.contains(my)) {
                if (!collapsedGroups.add(hit.row.label)) collapsedGroups.remove(hit.row.label);
                return true;
            }
        }
        for (KeyHit hit : List.copyOf(keyHits)) {
            if (!hit.contains(mx, my)) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                boolean ctrl = ctrlDown();
                if (ctrl) {
                    if (selected.containsKey(hit.key)) selected.remove(hit.key); else selected.put(hit.key, hit.parent);
                } else if (!selected.containsKey(hit.key)) {
                    selected.clear(); selected.put(hit.key, hit.parent);
                }
                checkpoint();
                dragStartTicks.clear();
                for (JsonObject key : selected.keySet()) dragStartTicks.put(key, key.has("tick") ? key.get("tick").getAsDouble() : 0.0);
                dragMouseTick = tickAt(mx);
                draggingKeys = true;
                return true;
            }
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && my >= TOP + RULER_H && mx >= LEFT && altDown()) {
            boxSelecting = true; boxX1 = boxX2 = mx; boxY1 = boxY2 = my;
            if (!ctrlDown()) selected.clear();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && mx >= LEFT) {
            double localTick = Math.max(0.0, tickAt(mx));
            preview.setTick(client, element.startTick() + localTick);
            if (!ctrlDown()) selected.clear();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, dx, dy);
        if (boxSelecting) { boxX2 = click.x(); boxY2 = click.y(); return true; }
        if (!draggingKeys || dragStartTicks.isEmpty()) return super.mouseDragged(click, dx, dy);
        double delta = tickAt(click.x()) - dragMouseTick;
        if (ctrlDown()) delta = Math.round(delta);
        else delta = Math.round(delta * 4.0) / 4.0;
        for (Map.Entry<JsonObject, Double> entry : dragStartTicks.entrySet()) {
            entry.getKey().addProperty("tick", Math.max(0.0, entry.getValue() + delta));
        }
        changed();
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(click);
        if (boxSelecting) {
            double x1 = Math.min(boxX1, boxX2), x2 = Math.max(boxX1, boxX2);
            double y1 = Math.min(boxY1, boxY2), y2 = Math.max(boxY1, boxY2);
            for (KeyHit hit : keyHits) {
                if (hit.x2 < x1 || hit.x1 > x2 || hit.y2 < y1 || hit.y1 > y2) continue;
                selected.put(hit.key, hit.parent);
            }
            boxSelecting = false;
            return true;
        }
        if (draggingKeys) {
            for (CurveChannels.Channel channel : channels) channel.sort();
            draggingKeys = false; dragStartTicks.clear(); changed(); return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (mx < LEFT) {
            verticalScroll = Math.max(0.0, verticalScroll - verticalAmount * ROW_H * 2.0);
            return true;
        }
        if (shiftDown()) {
            startTick = Math.max(0.0, startTick - verticalAmount * 80.0 / Math.max(.05, pixelsPerTick));
            return true;
        }
        double anchor = tickAt(mx);
        double before = pixelsPerTick;
        pixelsPerTick = Math.max(.15, Math.min(80.0, pixelsPerTick * Math.pow(1.16, verticalAmount)));
        startTick = Math.max(0.0, anchor - (mx - LEFT) / pixelsPerTick);
        if (Math.abs(before - pixelsPerTick) > 1.0e-9) return true;
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { closeEditor(); return true; }
        if (input.key() == GLFW.GLFW_KEY_F) { fit(); return true; }
        if (input.key() == GLFW.GLFW_KEY_E && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (client != null) client.setScreen(new CurveEditorScreen(this, project, element, preview, history)); return true;
        }
        if (input.key() == GLFW.GLFW_KEY_A && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            selected.clear();
            for (CurveChannels.Channel channel : channels) for (int i = 0; i < channel.size(); i++) selected.put(channel.key(i), channel.keys());
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_DELETE && !selected.isEmpty()) {
            checkpoint(); boolean removed = false;
            for (Map.Entry<JsonObject, JsonArray> entry : List.copyOf(selected.entrySet())) {
                JsonArray parent = entry.getValue();
                if (parent == null || parent.size() <= 1) continue;
                for (int i = 0; i < parent.size(); i++) if (parent.get(i) == entry.getKey()) { parent.remove(i); removed = true; break; }
            }
            selected.clear();
            if (removed) changed();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_SPACE) { preview.togglePlay(client); return true; }
        return super.keyPressed(input);
    }

    private List<Row> rows() {
        LinkedHashMap<String, List<CurveChannels.Channel>> grouped = new LinkedHashMap<>();
        for (CurveChannels.Channel channel : channels) grouped.computeIfAbsent(group(channel.label()), ignored -> new ArrayList<>()).add(channel);
        ArrayList<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<CurveChannels.Channel>> entry : grouped.entrySet()) {
            rows.add(new Row(entry.getKey(), null, true));
            if (!collapsedGroups.contains(entry.getKey())) for (CurveChannels.Channel channel : entry.getValue()) rows.add(new Row(entry.getKey(), channel, false));
        }
        return rows;
    }

    private void fit() {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (CurveChannels.Channel channel : channels) for (int i = 0; i < channel.size(); i++) {
            min = Math.min(min, channel.tick(i)); max = Math.max(max, channel.tick(i));
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) { min = 0; max = Math.max(20, project.durationTicks); }
        double span = Math.max(20.0, max - min);
        int available = Math.max(100, width - LEFT - 24);
        pixelsPerTick = Math.max(.15, Math.min(80.0, available / (span * 1.12)));
        startTick = Math.max(0.0, min - span * .06);
        verticalScroll = 0.0;
    }

    private void checkpoint() { if (history != null) history.checkpoint(project); }
    private void changed() { project.dirty = true; preview.markDirty(); }
    private void closeEditor() { if (client != null) client.setScreen(parent); }
    private int tickX(double tick) { return LEFT + (int)Math.round((tick - startTick) * pixelsPerTick); }
    private double tickAt(double x) { return startTick + (x - LEFT) / Math.max(.0001, pixelsPerTick); }
    private boolean ctrlDown() { return keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL); }
    private boolean shiftDown() { return keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT); }
    private boolean altDown() { return keyDown(GLFW.GLFW_KEY_LEFT_ALT) || keyDown(GLFW.GLFW_KEY_RIGHT_ALT); }
    private boolean keyDown(int key) { return client != null && GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS; }
    private void button(DrawContext c,int mx,int my,int x,int y,int w,String text){boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+22;c.fill(x,y,x+w,y+22,hover?0xFF34414C:0xFF262E36);c.drawTextWithShadow(textRenderer,text,x+(w-textRenderer.getWidth(text))/2,y+7,0xFFE8EEF2);}
    private String trim(String value,int width){if(value==null)return"";return textRenderer.trimToWidth(value,width);}
    private static String group(String label) {
        if (label == null || label.isBlank()) return "Track";
        String out = label.replaceAll("\\.(X|Y|Z|A|R|G|B)$", "");
        int dot = out.lastIndexOf('.');
        String tail = dot >= 0 ? out.substring(dot + 1) : out;
        return switch (tail.toLowerCase(Locale.ROOT)) {
            case "translation", "position" -> "Position · " + out;
            case "rotation", "rotationdegrees" -> "Rotation · " + out;
            case "scale" -> "Scale · " + out;
            default -> out;
        };
    }
    private static String shortChannel(String label, String group) {
        if (label == null) return "Channel";
        int dot = label.lastIndexOf('.');
        String suffix = dot >= 0 ? label.substring(dot + 1) : label;
        return switch (suffix) { case "X", "Y", "Z", "A", "R", "G", "B" -> "  " + suffix; default -> "  " + label; };
    }
    private static double niceStep(double raw){double p=Math.pow(10,Math.floor(Math.log10(Math.max(raw,1e-9))));double n=raw/p;double m=n<=1?1:n<=2?2:n<=5?5:10;return m*p;}

    private record Row(String label, CurveChannels.Channel channel, boolean group) { }
    private record RowHit(Row row, int y1, int y2) { boolean contains(double y){return y>=y1&&y<=y2;} }
    private record KeyHit(CurveChannels.Channel channel, JsonObject key, JsonArray parent, int x1, int y1, int x2, int y2) {
        boolean contains(double x,double y){return x>=x1&&x<=x2&&y>=y1&&y<=y2;}
    }
}
