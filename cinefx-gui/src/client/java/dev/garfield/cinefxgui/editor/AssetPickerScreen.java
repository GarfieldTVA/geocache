package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Typed ResourceManager/registry browser for identifier-like CineFX element fields. */
public final class AssetPickerScreen extends Screen {
    public enum Kind { BLOCK, SOUND, PARTICLE, TEXTURE, MODEL, FONT, RESOURCE }

    public record Binding(String path, String fieldName, Kind kind, JsonObject owner) {
        public String current() {
            try { return owner.has(fieldName) && owner.get(fieldName).isJsonPrimitive() ? owner.get(fieldName).getAsString() : ""; }
            catch (RuntimeException ignored) { return ""; }
        }
        public void set(String value) { owner.addProperty(fieldName, value == null ? "" : value); }
    }

    private final Screen parent;
    private final EditorModel.Project project;
    private final EditorModel.Element element;
    private final PreviewController preview;
    private final EditorModel.History history;
    private final List<Binding> bindings;
    private final List<RowHit> hits = new ArrayList<>();
    private TextFieldWidget search;
    private int bindingIndex;
    private double leftScroll;
    private double rightScroll;
    private List<String> candidates = List.of();

    public AssetPickerScreen(Screen parent, EditorModel.Project project, EditorModel.Element element,
                             PreviewController preview, EditorModel.History history) {
        super(Text.literal("CineFX Asset Browser"));
        this.parent = parent;
        this.project = project;
        this.element = element;
        this.preview = preview;
        this.history = history;
        this.bindings = discover(element);
    }

    public static List<Binding> discover(EditorModel.Element element) {
        if (element == null || element.data == null) return List.of();
        ArrayList<Binding> out = new ArrayList<>();
        discoverObject(element.data, "", out);
        return List.copyOf(out);
    }

    private static void discoverObject(JsonObject object, String path, List<Binding> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("$")) continue;
            JsonElement value = entry.getValue();
            String next = path.isBlank() ? key : path + "." + key;
            if (value != null && value.isJsonObject()) {
                discoverObject(value.getAsJsonObject(), next, out);
                continue;
            }
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
            Kind kind = kindFor(key, next);
            if (kind != null) out.add(new Binding(next, key, kind, object));
        }
    }

    private static Kind kindFor(String field, String path) {
        String name = (field + " " + path).toLowerCase(Locale.ROOT);
        if (name.contains("blockid") || name.endsWith(" block") || name.contains("block_id")) return Kind.BLOCK;
        if (name.contains("sound") || name.contains("audio")) return Kind.SOUND;
        if (name.contains("particle")) return Kind.PARTICLE;
        if (name.contains("texture") || name.contains("sprite")) return Kind.TEXTURE;
        if (name.contains("font")) return Kind.FONT;
        if (name.contains("model") || name.contains("mesh") || name.contains("gltf") || name.contains("glb")) return Kind.MODEL;
        if (name.contains("resource") || name.contains("asset")) return Kind.RESOURCE;
        return null;
    }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, 310, 8, Math.max(120, width - 320), 20, Text.literal("Search assets"));
        search.setMaxLength(256);
        search.setChangedListener(value -> { rightScroll = 0; rebuildCandidates(); });
        addDrawableChild(search);
        rebuildCandidates();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override
    public void render(DrawContext c, int mx, int my, float deltaTicks) {
        hits.clear();
        c.fill(0, 0, width, height, 0xFF11161B);
        c.fill(0, 0, 300, height, 0xFF171C22);
        c.fill(0, 0, width, 34, 0xFF1C2229);
        c.drawTextWithShadow(textRenderer, "CineFX Asset Browser", 10, 12, 0xFFF0F4F7);
        button(c, mx, my, 160, 6, 48, "Back");
        Binding active = binding();
        if (active != null) {
            c.drawTextWithShadow(textRenderer, active.kind().name() + " · " + trim(active.path(), width - 390), 310, 40, 0xFFC5D2D9);
            c.drawTextWithShadow(textRenderer, "Current: " + trim(active.current(), width - 390), 310, 55, 0xFF7F919D);
        }
        drawBindings(c, mx, my);
        drawCandidates(c, mx, my);
        super.render(c, mx, my, deltaTicks);
    }

    private void drawBindings(DrawContext c, int mx, int my) {
        c.drawTextWithShadow(textRenderer, trim(element.key(), 276), 10, 42, 0xFFC9D5DC);
        c.drawTextWithShadow(textRenderer, "ASSET FIELDS · " + bindings.size(), 10, 58, 0xFF748695);
        int y = 78 - (int)leftScroll;
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            if (y + 20 >= 70 && y < height) {
                boolean selected = i == bindingIndex;
                boolean hover = mx >= 6 && mx <= 294 && my >= y - 2 && my <= y + 18;
                c.fill(6, y - 2, 294, y + 18, selected ? 0xFF304D62 : hover ? 0xFF27333D : 0xFF1B2228);
                c.drawTextWithShadow(textRenderer, binding.kind().name().substring(0, 1), 12, y + 4, 0xFF79BCE7);
                c.drawTextWithShadow(textRenderer, trim(binding.path(), 252), 28, y + 4, selected ? 0xFFF0F5F8 : 0xFFB8C5CD);
                hits.add(new RowHit(Type.BINDING, i, y - 2, y + 18));
            }
            y += 21;
        }
        if (bindings.isEmpty()) {
            c.drawTextWithShadow(textRenderer, "No asset-like string fields found", 10, 84, 0xFFFFA07F);
            c.drawTextWithShadow(textRenderer, "Block elements still use the Blocks tab.", 10, 100, 0xFF8796A0);
        }
    }

    private void drawCandidates(DrawContext c, int mx, int my) {
        int y = 78 - (int)rightScroll;
        for (int i = 0; i < candidates.size(); i++) {
            String value = candidates.get(i);
            if (y + 20 >= 70 && y < height) {
                boolean chosen = binding() != null && value.equals(binding().current());
                boolean hover = mx >= 306 && mx <= width - 8 && my >= y - 2 && my <= y + 18;
                c.fill(306, y - 2, width - 8, y + 18, chosen ? 0xFF314B3B : hover ? 0xFF29343D : 0xFF171E24);
                c.drawTextWithShadow(textRenderer, chosen ? "●" : "·", 314, y + 4, chosen ? 0xFF7BD896 : 0xFF6F838F);
                c.drawTextWithShadow(textRenderer, trim(value, Math.max(30, width - 354)), 332, y + 4, 0xFFD6E0E6);
                hits.add(new RowHit(Type.CANDIDATE, i, y - 2, y + 18));
            }
            y += 21;
        }
        if (binding() != null && candidates.isEmpty()) c.drawTextWithShadow(textRenderer, "No matching resources", 314, 86, 0xFFFFA07F);
    }

    private void rebuildCandidates() {
        Binding binding = binding();
        if (binding == null || client == null) { candidates = List.of(); return; }
        Set<String> values = new LinkedHashSet<>();
        switch (binding.kind()) {
            case BLOCK -> Registries.BLOCK.getIds().forEach(id -> values.add(id.toString()));
            case SOUND -> Registries.SOUND_EVENT.getIds().forEach(id -> values.add(id.toString()));
            case PARTICLE -> Registries.PARTICLE_TYPE.getIds().forEach(id -> values.add(id.toString()));
            case TEXTURE -> findResources(values, "textures", ".png");
            case MODEL -> {
                findResources(values, "models", ".json");
                findResources(values, "models", ".gltf");
                findResources(values, "models", ".glb");
                findResources(values, "cinefx", ".gltf");
                findResources(values, "cinefx", ".glb");
            }
            case FONT -> findResources(values, "font", ".json");
            case RESOURCE -> {
                findResources(values, "textures", "");
                findResources(values, "models", "");
                findResources(values, "sounds", "");
                findResources(values, "font", "");
            }
        }
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        ArrayList<String> sorted = new ArrayList<>();
        for (String value : values) if (query.isBlank() || value.toLowerCase(Locale.ROOT).contains(query)) sorted.add(value);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        candidates = List.copyOf(sorted);
    }

    private void findResources(Set<String> out, String root, String suffix) {
        try {
            client.getResourceManager().findResources(root, id -> suffix.isBlank() || id.getPath().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .keySet().forEach(id -> out.add(id.toString()));
        } catch (RuntimeException ignored) { }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.y() >= 6 && click.y() <= 28 && click.x() >= 160 && click.x() <= 208) {
            closeEditor(); return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        for (RowHit hit : List.copyOf(hits)) {
            if (!hit.contains(click.y())) continue;
            if (hit.type == Type.BINDING) {
                bindingIndex = hit.index; rightScroll = 0; if (search != null) search.setText(""); rebuildCandidates(); return true;
            }
            if (hit.type == Type.CANDIDATE && binding() != null && hit.index >= 0 && hit.index < candidates.size()) {
                if (history != null) history.checkpoint(project);
                binding().set(candidates.get(hit.index));
                project.dirty = true; preview.markDirty();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (mx < 300) leftScroll = Math.max(0, leftScroll - verticalAmount * 40);
        else rightScroll = Math.max(0, rightScroll - verticalAmount * 40);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { closeEditor(); return true; }
        return super.keyPressed(input);
    }

    private Binding binding() { return bindings.isEmpty() || bindingIndex < 0 || bindingIndex >= bindings.size() ? null : bindings.get(bindingIndex); }
    private void closeEditor() { if (client != null) client.setScreen(parent); }
    private void button(DrawContext c,int mx,int my,int x,int y,int w,String text){boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+22;c.fill(x,y,x+w,y+22,hover?0xFF34414C:0xFF262E36);c.drawTextWithShadow(textRenderer,text,x+(w-textRenderer.getWidth(text))/2,y+7,0xFFE8EEF2);}
    private String trim(String value,int width){return value==null?"":textRenderer.trimToWidth(value,width);}
    private enum Type { BINDING, CANDIDATE }
    private record RowHit(Type type,int index,int y1,int y2){boolean contains(double y){return y>=y1&&y<=y2;}}
}
