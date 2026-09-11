package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.ViewportGizmo;
import dev.garfield.cinefxgui.editor.VisualPresetLibrary;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Universal visual authoring layer.
 *
 * The specialised Camera/Light/Animation cards stay the fastest path for common work. This inspector
 * covers everything else without JSON: every primitive becomes a visual control, tracks are sampled at
 * the playhead, arrays get add/duplicate/remove controls, enum-like fields cycle visually, and arbitrary
 * identifiers/text use the Studio text widget. The All Types palette exposes every API element type.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 1450)
public abstract class CineFxStudioUniversalVisualMixin {
    @Unique private static final int LEFT = 252;
    @Unique private static final int RIGHT = 364;
    @Unique private static final int TOP = 32;
    @Unique private static final int TIMELINE = 244;
    @Unique private static final int PANEL_W = 430;
    @Unique private static final int ROW_H = 29;
    @Unique private static final int PAGE_ROWS = 11;

    @Unique private static final int BG = 0xF0171D24;
    @Unique private static final int BG2 = 0xF01E2630;
    @Unique private static final int CONTROL = 0xF02A3541;
    @Unique private static final int HOVER = 0xF0384858;
    @Unique private static final int BORDER = 0xFF465666;
    @Unique private static final int ACCENT = 0xFF55A9FF;
    @Unique private static final int TEXT = 0xFFF2F6F8;
    @Unique private static final int DIM = 0xFFA7B4BE;
    @Unique private static final int MUTED = 0xFF74838E;
    @Unique private static final int GOOD = 0xFF70DFA1;
    @Unique private static final int WARN = 0xFFFFC66B;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private TextFieldWidget valueEditor;

    @Unique private boolean cinefxGui$visualOpen;
    @Unique private boolean cinefxGui$catalogOpen;
    @Unique private int cinefxGui$page;
    @Unique private int cinefxGui$catalogPage;
    @Unique private final List<VisualHit> cinefxGui$hits = new ArrayList<>();
    @Unique private final List<VisualSlider> cinefxGui$sliders = new ArrayList<>();
    @Unique private VisualSlider cinefxGui$activeSlider;
    @Unique private VisualField cinefxGui$textField;
    @Unique private boolean cinefxGui$changingText;
    @Unique private String cinefxGui$lastSelection;

    @Unique private enum RowKind { VALUE, ARRAY, MAP, SECTION }
    @Unique private record VisualHit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }
    @Unique private record Range(double min, double max, double step) { }
    @Unique private record VisualSlider(int x1, int y1, int x2, int y2, VisualField field, Range range) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
        double valueAt(double x) {
            double t = Math.max(0.0, Math.min(1.0, (x - x1) / Math.max(1.0, x2 - x1)));
            double raw = range.min + (range.max - range.min) * t;
            return range.step > 0 ? Math.round(raw / range.step) * range.step : raw;
        }
    }
    @Unique private static final class VisualField {
        final String label;
        final String path;
        JsonElement value;
        final int depth;
        final RowKind kind;
        final Consumer<JsonElement> setter;
        final JsonObject owner;
        final String ownerKey;

        VisualField(String label, String path, JsonElement value, int depth, RowKind kind,
                    Consumer<JsonElement> setter, JsonObject owner, String ownerKey) {
            this.label = label; this.path = path; this.value = value; this.depth = depth; this.kind = kind;
            this.setter = setter; this.owner = owner; this.ownerKey = ownerKey;
        }
        void set(JsonElement next) { if (setter != null) setter.accept(next); value = next; }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cinefxGui$installUniversalTextBridge(CallbackInfo ci) {
        if (valueEditor == null) return;
        valueEditor.setChangedListener(text -> {
            if (cinefxGui$textField == null) {
                ((CineFxStudioAccessMixin)(Object)this).cinefxGui$onEditorTextChanged(text);
                return;
            }
            if (cinefxGui$changingText) return;
            try {
                JsonElement parsed = cinefxGui$parseLike(cinefxGui$textField.value, text);
                cinefxGui$textField.set(parsed);
                ((CineFxStudioAccessMixin)(Object)this).cinefxGui$markChanged();
            } catch (RuntimeException ignored) { }
        });
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$renderUniversal(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
        cinefxGui$hits.clear();
        cinefxGui$sliders.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        String selection = selectedId == null ? "" : selectedId;
        if (!selection.equals(cinefxGui$lastSelection)) {
            cinefxGui$lastSelection = selection;
            cinefxGui$page = 0;
            cinefxGui$textField = null;
        }

        int chipX = Math.max(LEFT + 8, client.getWindow().getScaledWidth() - RIGHT - 192);
        int chipY = TOP + 35;
        cinefxGui$button(c, mx, my, chipX, chipY, 88, cinefxGui$visualOpen ? "VISUAL ✓" : "VISUAL [I]", () -> {
            cinefxGui$visualOpen = !cinefxGui$visualOpen;
            cinefxGui$catalogOpen = false;
            cinefxGui$page = 0;
        });
        cinefxGui$button(c, mx, my, chipX + 94, chipY, 98, "ALL TYPES", () -> {
            cinefxGui$catalogOpen = !cinefxGui$catalogOpen;
            cinefxGui$visualOpen = false;
        });

        if (cinefxGui$catalogOpen) cinefxGui$drawCatalog(c, mx, my);
        else if (cinefxGui$visualOpen) cinefxGui$drawInspector(c, mx, my);

        if (cinefxGui$textField != null && valueEditor != null && valueEditor.isVisible()) {
            String hint = "EDITING  " + cinefxGui$short(cinefxGui$textField.path, 280) + "  ·  type in the value box →";
            int x = Math.max(8, client.getWindow().getScaledWidth() - RIGHT - client.textRenderer.getWidth(hint) - 8);
            int y = Math.max(TOP + 62, client.getWindow().getScaledHeight() - TIMELINE - 25);
            c.fill(x - 6, y - 4, x + client.textRenderer.getWidth(hint) + 6, y + 14, 0xE81B222A);
            c.drawTextWithShadow(client.textRenderer, hint, x, y, WARN);
        }
    }

    @Unique
    private void cinefxGui$drawInspector(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int x = Math.max(LEFT + 12, screenW - RIGHT - PANEL_W - 12);
        int y = TOP + 63;
        int maxH = Math.max(250, screenH - TIMELINE - y - 10);
        int h = Math.min(446, maxH);
        cinefxGui$box(c, x, y, PANEL_W, h, BG, BORDER);

        EditorModel.Element selected = cinefxGui$selected();
        if (selected == null) {
            c.drawTextWithShadow(client.textRenderer, "VISUAL INSPECTOR", x + 12, y + 11, TEXT);
            c.drawTextWithShadow(client.textRenderer, "Select an object, effect, light, camera or event.", x + 12, y + 39, DIM);
            c.drawTextWithShadow(client.textRenderer, "Every CineFX type is available from ALL TYPES.", x + 12, y + 56, MUTED);
            cinefxGui$button(c,mx,my,x+12,y+82,132,"Open All Types",()->{cinefxGui$catalogOpen=true;cinefxGui$visualOpen=false;});
            return;
        }

        String typeName = cinefxGui$simpleType(selected.apiClass);
        c.drawTextWithShadow(client.textRenderer, typeName.toUpperCase(Locale.ROOT), x + 12, y + 10, TEXT);
        c.drawTextWithShadow(client.textRenderer, cinefxGui$family(selected.apiClass), x + 12, y + 24, MUTED);
        cinefxGui$button(c,mx,my,x+PANEL_W-84,y+6,72,"CLOSE",()->cinefxGui$visualOpen=false);

        int actionsY = y + 43;
        cinefxGui$button(c,mx,my,x+12,actionsY,72,"Focus",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$focusSelected());
        cinefxGui$button(c,mx,my,x+89,actionsY,82,"Duplicate",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$duplicateSelected());
        cinefxGui$button(c,mx,my,x+176,actionsY,72,"Key now",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        cinefxGui$button(c,mx,my,x+253,actionsY,72,"Delete",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$deleteSelected());
        cinefxGui$button(c,mx,my,x+330,actionsY,86,"Advanced",()->cinefxGui$toast("The right Inspector still exposes the complete schema without JSON"));

        int presetY = actionsY + 29;
        List<String> presets = VisualPresetLibrary.presets(selected);
        if (!presets.isEmpty()) {
            c.drawTextWithShadow(client.textRenderer, "PRESETS", x + 12, presetY + 5, DIM);
            int px = x + 74;
            for (String preset : presets) {
                int w = Math.min(96, client.textRenderer.getWidth(preset) + 18);
                final String chosen = preset;
                cinefxGui$button(c,mx,my,px,presetY,w,preset,()->cinefxGui$applyPreset(selected,chosen));
                px += w + 5;
            }
            presetY += 30;
        }

        List<VisualField> fields = cinefxGui$fields(selected);
        int totalPages = Math.max(1, (fields.size() + PAGE_ROWS - 1) / PAGE_ROWS);
        cinefxGui$page = Math.max(0, Math.min(cinefxGui$page, totalPages - 1));
        int start = cinefxGui$page * PAGE_ROWS;
        int end = Math.min(fields.size(), start + PAGE_ROWS);
        int rowY = presetY + 4;
        for (int i = start; i < end && rowY + ROW_H <= y + h - 35; i++) {
            cinefxGui$drawField(c,mx,my,x+10,rowY,PANEL_W-20,fields.get(i),selected);
            rowY += ROW_H;
        }

        int footerY = y + h - 29;
        c.fill(x+8,footerY-4,x+PANEL_W-8,footerY-3,0x77475665);
        cinefxGui$button(c,mx,my,x+12,footerY,64,"◀ Prev",()->cinefxGui$page=Math.max(0,cinefxGui$page-1));
        String pageText = (cinefxGui$page+1)+" / "+totalPages+"  ·  "+fields.size()+" controls";
        c.drawTextWithShadow(client.textRenderer,pageText,x+93,footerY+7,DIM);
        cinefxGui$button(c,mx,my,x+PANEL_W-78,footerY,66,"Next ▶",()->cinefxGui$page=Math.min(totalPages-1,cinefxGui$page+1));
    }

    @Unique
    private void cinefxGui$drawField(DrawContext c, int mx, int my, int x, int y, int w,
                                     VisualField field, EditorModel.Element selected) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hover = cinefxGui$hover(mx,my,x,y,w,ROW_H-2);
        c.fill(x,y,x+w,y+ROW_H-2,hover?0xDD26323D:0xC91C252E);
        int indent = Math.min(42, field.depth * 10);
        String label = cinefxGui$pretty(field.label);
        c.drawTextWithShadow(client.textRenderer,cinefxGui$short(label,145-indent),x+8+indent,y+9,field.kind==RowKind.SECTION?ACCENT:DIM);

        if (field.kind == RowKind.SECTION) {
            c.drawTextWithShadow(client.textRenderer,"GROUP",x+w-48,y+9,MUTED);
            return;
        }
        if (field.kind == RowKind.ARRAY) {
            JsonArray array = field.value != null && field.value.isJsonArray() ? field.value.getAsJsonArray() : new JsonArray();
            c.drawTextWithShadow(client.textRenderer,array.size()+" item"+(array.size()==1?"":"s"),x+w-180,y+9,TEXT);
            cinefxGui$mini(c,mx,my,x+w-111,y+4,30,"+",()->cinefxGui$addArray(field,selected));
            cinefxGui$mini(c,mx,my,x+w-77,y+4,30,"Dup",()->cinefxGui$duplicateLast(field));
            cinefxGui$mini(c,mx,my,x+w-43,y+4,30,"−",()->cinefxGui$removeLast(field));
            return;
        }
        if (field.kind == RowKind.MAP) {
            JsonObject map = field.value != null && field.value.isJsonObject() ? field.value.getAsJsonObject() : new JsonObject();
            c.drawTextWithShadow(client.textRenderer,map.size()+" entries",x+w-135,y+9,TEXT);
            cinefxGui$mini(c,mx,my,x+w-65,y+4,52,"+ entry",()->cinefxGui$addMapEntry(field));
            return;
        }
        if (field.value == null || field.value.isJsonNull()) {
            cinefxGui$mini(c,mx,my,x+w-94,y+4,81,"Create",()->cinefxGui$createNull(field));
            return;
        }
        if (!field.value.isJsonPrimitive()) return;
        JsonPrimitive primitive = field.value.getAsJsonPrimitive();

        if (primitive.isBoolean()) {
            boolean on = primitive.getAsBoolean();
            int bx = x+w-92;
            cinefxGui$pill(c,bx,y+4,79,20,on?"ON":"OFF",on?GOOD:MUTED);
            cinefxGui$hits.add(new VisualHit(bx,y+4,bx+79,y+24,()->cinefxGui$setField(field,new JsonPrimitive(!on))));
            return;
        }

        if (primitive.isNumber()) {
            double value = cinefxGui$number(primitive,0);
            Range range = cinefxGui$range(field.label,value);
            if (range != null) {
                int sx=x+w-178, sw=102;
                c.fill(sx,y+12,sx+sw,y+16,0xFF34414D);
                double t=(value-range.min)/Math.max(1e-9,range.max-range.min); t=Math.max(0,Math.min(1,t));
                int knob=sx+(int)Math.round(sw*t);
                c.fill(sx,y+12,knob,y+16,ACCENT); c.fill(knob-2,y+7,knob+3,y+21,0xFFFFFFFF);
                VisualSlider slider=new VisualSlider(sx,y+5,sx+sw,y+23,field,range); cinefxGui$sliders.add(slider);
                String val=cinefxGui$formatNumber(value);
                cinefxGui$mini(c,mx,my,x+w-70,y+4,57,val,()->cinefxGui$beginText(field));
            } else {
                double step=cinefxGui$step(field.label,value);
                cinefxGui$mini(c,mx,my,x+w-150,y+4,28,"−",()->cinefxGui$adjust(field,-step));
                cinefxGui$mini(c,mx,my,x+w-118,y+4,73,cinefxGui$formatNumber(value),()->cinefxGui$beginText(field));
                cinefxGui$mini(c,mx,my,x+w-41,y+4,28,"+",()->cinefxGui$adjust(field,step));
            }
            return;
        }

        String current = primitive.getAsString();
        List<String> options = cinefxGui$options(selected,field.label,current);
        if (!options.isEmpty()) {
            cinefxGui$mini(c,mx,my,x+w-178,y+4,28,"‹",()->cinefxGui$cycle(field,options,-1));
            cinefxGui$mini(c,mx,my,x+w-146,y+4,101,cinefxGui$short(current,92),()->cinefxGui$cycle(field,options,1));
            cinefxGui$mini(c,mx,my,x+w-41,y+4,28,"›",()->cinefxGui$cycle(field,options,1));
        } else if (cinefxGui$isColor(current)) {
            c.fill(x+w-171,y+5,x+w-149,y+23,cinefxGui$parseColor(current));
            cinefxGui$mini(c,mx,my,x+w-143,y+4,130,cinefxGui$short(current,120),()->cinefxGui$cycleColor(field));
        } else {
            cinefxGui$mini(c,mx,my,x+w-178,y+4,165,cinefxGui$short(current,154),()->cinefxGui$beginText(field));
        }
    }

    @Unique
    private void cinefxGui$drawCatalog(DrawContext c, int mx, int my) {
        MinecraftClient client=MinecraftClient.getInstance();
        List<CineFxBridge.ElementType> types=new ArrayList<>(CineFxBridge.elementTypes());
        types.sort(Comparator.comparing(t->cinefxGui$family(t.apiClass().getName())+"/"+t.displayName()));
        int perPage=18, pages=Math.max(1,(types.size()+perPage-1)/perPage);
        cinefxGui$catalogPage=Math.max(0,Math.min(cinefxGui$catalogPage,pages-1));
        int w=Math.min(760,client.getWindow().getScaledWidth()-40);
        int h=Math.min(430,client.getWindow().getScaledHeight()-TIMELINE-55);
        int x=(client.getWindow().getScaledWidth()-w)/2;
        int y=TOP+62;
        cinefxGui$box(c,x,y,w,h,0xF51A2028,BORDER);
        c.drawTextWithShadow(client.textRenderer,"ADD EVERYTHING · EVERY CINEFX TYPE",x+14,y+12,TEXT);
        c.drawTextWithShadow(client.textRenderer,"No API/JSON knowledge required · created at the playhead and near your editor camera",x+14,y+27,MUTED);
        cinefxGui$button(c,mx,my,x+w-76,y+7,62,"CLOSE",()->cinefxGui$catalogOpen=false);
        int start=cinefxGui$catalogPage*perPage,end=Math.min(types.size(),start+perPage);
        int cols=3, gap=8, bw=(w-28-gap*(cols-1))/cols;
        int row=0;
        for(int i=start;i<end;i++){
            CineFxBridge.ElementType type=types.get(i); int col=(i-start)%cols; if(col==0&&i>start)row++;
            int bx=x+14+col*(bw+gap), by=y+52+row*49;
            boolean hover=cinefxGui$hover(mx,my,bx,by,bw,42);
            c.fill(bx,by,bx+bw,by+42,hover?0xF03A4958:0xEC27313C);
            c.fill(bx,by,bx+bw,by+1,hover?ACCENT:0xAA465666);
            c.drawTextWithShadow(client.textRenderer,cinefxGui$short(type.displayName(),bw-14),bx+7,by+8,hover?0xFFFFFFFF:TEXT);
            c.drawTextWithShadow(client.textRenderer,cinefxGui$short(cinefxGui$family(type.apiClass().getName()),bw-14),bx+7,by+23,MUTED);
            cinefxGui$hits.add(new VisualHit(bx,by,bx+bw,by+42,()->cinefxGui$addType(type)));
        }
        int fy=y+h-28;
        cinefxGui$button(c,mx,my,x+14,fy,70,"◀ Prev",()->cinefxGui$catalogPage=Math.max(0,cinefxGui$catalogPage-1));
        c.drawTextWithShadow(client.textRenderer,(cinefxGui$catalogPage+1)+" / "+pages+"  ·  "+types.size()+" element types",x+101,fy+7,DIM);
        cinefxGui$button(c,mx,my,x+w-84,fy,70,"Next ▶",()->cinefxGui$catalogPage=Math.min(pages-1,cinefxGui$catalogPage+1));
    }

    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void cinefxGui$universalKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir){
        if(input.key()==GLFW.GLFW_KEY_I && input.modifiers()==0){cinefxGui$visualOpen=!cinefxGui$visualOpen;cinefxGui$catalogOpen=false;cir.setReturnValue(true);return;}
        if(input.key()==GLFW.GLFW_KEY_A && (input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0 && (input.modifiers()&GLFW.GLFW_MOD_CONTROL)==0){cinefxGui$catalogOpen=!cinefxGui$catalogOpen;cinefxGui$visualOpen=false;cir.setReturnValue(true);return;}
        if(input.key()==GLFW.GLFW_KEY_ESCAPE && (cinefxGui$visualOpen||cinefxGui$catalogOpen||cinefxGui$textField!=null)){
            if(cinefxGui$textField!=null){cinefxGui$finishText();}else{cinefxGui$visualOpen=false;cinefxGui$catalogOpen=false;} cir.setReturnValue(true);
        }
    }

    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void cinefxGui$universalClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir){
        double mx=click.x(),my=click.y();
        for(int i=cinefxGui$sliders.size()-1;i>=0;i--){
            VisualSlider s=cinefxGui$sliders.get(i); if(click.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT||!s.contains(mx,my))continue;
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint(); cinefxGui$activeSlider=s; cinefxGui$setNumeric(s.field,s.valueAt(mx)); cir.setReturnValue(true);return;
        }
        for(int i=cinefxGui$hits.size()-1;i>=0;i--){VisualHit hit=cinefxGui$hits.get(i);if(!hit.contains(mx,my))continue;
            if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT||click.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){hit.action.run();cir.setReturnValue(true);return;}}
        if(cinefxGui$catalogOpen){cir.setReturnValue(true);return;}
        if(cinefxGui$visualOpen){
            MinecraftClient client=MinecraftClient.getInstance(); int x=Math.max(LEFT+12,client.getWindow().getScaledWidth()-RIGHT-PANEL_W-12),y=TOP+63;
            int h=Math.min(446,Math.max(250,client.getWindow().getScaledHeight()-TIMELINE-y-10));
            if(mx>=x&&mx<x+PANEL_W&&my>=y&&my<y+h){cir.setReturnValue(true);}
        }
    }

    @Inject(method="mouseDragged",at=@At("HEAD"),cancellable=true)
    private void cinefxGui$universalDrag(Click click,double dx,double dy,CallbackInfoReturnable<Boolean> cir){
        if(cinefxGui$activeSlider==null||click.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return;
        cinefxGui$setNumeric(cinefxGui$activeSlider.field,cinefxGui$activeSlider.valueAt(click.x()));cir.setReturnValue(true);
    }
    @Inject(method="mouseReleased",at=@At("HEAD"))
    private void cinefxGui$universalRelease(Click click,CallbackInfoReturnable<Boolean> cir){if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT)cinefxGui$activeSlider=null;}

    @Inject(method="drawViewport",at=@At("TAIL"))
    private void cinefxGui$drawUniversalWorldHelpers(DrawContext c,int mx,int my,CallbackInfo ci){
        EditorModel.Element e=cinefxGui$selected(); if(e==null||!cinefxGui$visualOpen)return;
        Vec3d local=SceneManipulator.pivotLocal(e,Math.max(0,preview.currentTick()-e.startTick())); if(local==null)return;
        MinecraftClient client=MinecraftClient.getInstance(); if(client==null)return;
        int right=client.getWindow().getScaledWidth()-RIGHT,bottom=client.getWindow().getScaledHeight()-TIMELINE;
        Vec3d world=project.anchor().add(local);
        ViewportGizmo.ScreenPoint center=ViewportGizmo.project(world,preview.editorCameraPosition(),preview.editorCameraYaw(),preview.editorCameraPitch(),LEFT,TOP,right,bottom);
        if(!center.visible())return;
        double radius=cinefxGui$findScalar(e.data,"radius",Math.max(0,preview.currentTick()-e.startTick()),Double.NaN);
        if(Double.isFinite(radius)){
            int r=(int)Math.max(10,Math.min(90,radius*2.2)),cx=(int)center.x(),cy=(int)center.y(); double lx=cx+r,ly=cy;
            for(int i=1;i<=28;i++){double a=Math.PI*2*i/28,nx=cx+Math.cos(a)*r,ny=cy+Math.sin(a)*r;ViewportGizmo.drawLine(c,lx,ly,nx,ny,0x8855A9FF,1);lx=nx;ly=ny;}
        }
        JsonElement dirRaw=cinefxGui$findNamed(e.data,"direction");
        if(dirRaw!=null&&dirRaw.isJsonObject()){
            Vec3d dir=cinefxGui$vec(dirRaw,new Vec3d(0,1,0)); ViewportGizmo.ScreenPoint tip=ViewportGizmo.project(world.add(dir.multiply(6)),preview.editorCameraPosition(),preview.editorCameraYaw(),preview.editorCameraPitch(),LEFT,TOP,right,bottom);
            if(tip.visible())ViewportGizmo.drawLine(c,center.x(),center.y(),tip.x(),tip.y(),0xCC55A9FF,2);
        }
    }

    @Unique private List<VisualField> cinefxGui$fields(EditorModel.Element selected){
        ArrayList<VisualField> out=new ArrayList<>();
        cinefxGui$flattenObject(selected.data,"",0,out,selected);
        return out;
    }

    @Unique private void cinefxGui$flattenObject(JsonObject object,String path,int depth,List<VisualField> out,EditorModel.Element selected){
        for(Map.Entry<String,JsonElement> entry:object.entrySet()){
            String key=entry.getKey(); if("$type".equals(key)||"$kind".equals(key)||"key".equals(key)||"startTick".equals(key)||"endTick".equals(key))continue;
            String p=path.isEmpty()?key:path+"."+key; JsonElement value=entry.getValue();
            Consumer<JsonElement> setter=v->object.add(key,v==null?JsonNull.INSTANCE:v);
            if(value==null||value.isJsonNull()||value.isJsonPrimitive()){out.add(new VisualField(key,p,value,depth,RowKind.VALUE,setter,object,key));continue;}
            if(value.isJsonArray()){
                out.add(new VisualField(key,p,value,depth,RowKind.ARRAY,setter,object,key));
                JsonArray a=value.getAsJsonArray(); int limit=Math.min(a.size(),6);
                for(int i=0;i<limit;i++){JsonElement child=a.get(i);final int index=i;String ip=p+"["+i+"]";Consumer<JsonElement> iset=v->a.set(index,v==null?JsonNull.INSTANCE:v);
                    if(child.isJsonPrimitive()||child.isJsonNull())out.add(new VisualField("["+i+"]",ip,child,depth+1,RowKind.VALUE,iset,null,null));
                    else if(child.isJsonObject()){
                        out.add(new VisualField("#"+(i+1),ip,child,depth+1,RowKind.SECTION,null,null,null)); cinefxGui$flattenObject(child.getAsJsonObject(),ip,depth+2,out,selected);
                    }
                }
                if(a.size()>limit)out.add(new VisualField("… "+(a.size()-limit)+" more",p+".more",JsonNull.INSTANCE,depth+1,RowKind.SECTION,null,null,null));
                continue;
            }
            JsonObject child=value.getAsJsonObject();
            String kind=cinefxGui$string(child.get("$kind"),"");
            if(kind.endsWith("Track")&&child.has("keys")&&child.get("keys").isJsonArray()){
                cinefxGui$flattenTrack(key,p,child,depth,out,selected); continue;
            }
            if(cinefxGui$isVec(child)){
                out.add(new VisualField(key,p,child,depth,RowKind.SECTION,null,null,null));
                for(String axis:List.of("x","y","z")){JsonElement axisVal=child.get(axis);Consumer<JsonElement> aset=v->child.add(axis,v);out.add(new VisualField(key+" "+axis.toUpperCase(Locale.ROOT),p+"."+axis,axisVal,depth+1,RowKind.VALUE,aset,child,axis));}
                continue;
            }
            if(cinefxGui$isMapField(key))out.add(new VisualField(key,p,child,depth,RowKind.MAP,setter,object,key));
            else out.add(new VisualField(key,p,child,depth,RowKind.SECTION,null,null,null));
            cinefxGui$flattenObject(child,p,depth+1,out,selected);
        }
    }

    @Unique private void cinefxGui$flattenTrack(String label,String path,JsonObject track,int depth,List<VisualField> out,EditorModel.Element selected){
        JsonArray keys=track.getAsJsonArray("keys"); double local=Math.max(0,preview.currentTick()-selected.startTick());
        JsonObject key=cinefxGui$nearest(keys,local);
        if(key==null){out.add(new VisualField(label,path+".keys",keys,depth,RowKind.ARRAY,v->track.add("keys",v),track,"keys"));return;}
        JsonElement value=key.get("value"); Consumer<JsonElement> setter=v->key.add("value",v==null?JsonNull.INSTANCE:v);
        if(value==null||value.isJsonNull()||value.isJsonPrimitive())out.add(new VisualField(label,path+"@playhead",value,depth,RowKind.VALUE,setter,key,"value"));
        else if(value.isJsonObject()&&cinefxGui$isVec(value.getAsJsonObject())){
            JsonObject vec=value.getAsJsonObject();out.add(new VisualField(label,path+"@playhead",vec,depth,RowKind.SECTION,null,null,null));
            for(String axis:List.of("x","y","z")){JsonElement av=vec.get(axis);Consumer<JsonElement> aset=v->vec.add(axis,v);out.add(new VisualField(label+" "+axis.toUpperCase(Locale.ROOT),path+"@."+axis,av,depth+1,RowKind.VALUE,aset,vec,axis));}
        }
        if(key.has("easing"))out.add(new VisualField(label+" easing",path+".easing",key.get("easing"),depth+1,RowKind.VALUE,v->key.add("easing",v),key,"easing"));
        out.add(new VisualField(label+" keys",path+".keys",keys,depth+1,RowKind.ARRAY,v->track.add("keys",v),track,"keys"));
    }

    @Unique private void cinefxGui$applyPreset(EditorModel.Element element,String preset){
        CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();
        double local=Math.max(0,preview.currentTick()-element.startTick());
        if(VisualPresetLibrary.apply(element,preset,local)){access.cinefxGui$markChanged();access.cinefxGui$toast(preset+" preset applied");}
        else access.cinefxGui$toast("Preset unavailable for this element");
    }

    @Unique private void cinefxGui$setField(VisualField field,JsonElement value){CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();field.set(value);access.cinefxGui$markChanged();}
    @Unique private void cinefxGui$setNumeric(VisualField field,double value){field.set(new JsonPrimitive(value));((CineFxStudioAccessMixin)(Object)this).cinefxGui$markChanged();}
    @Unique private void cinefxGui$adjust(VisualField field,double delta){double value=cinefxGui$number(field.value,0);cinefxGui$setField(field,new JsonPrimitive(value+delta));}
    @Unique private void cinefxGui$cycle(VisualField field,List<String> options,int direction){String current=cinefxGui$string(field.value,"");int index=Math.max(0,options.indexOf(current));cinefxGui$setField(field,new JsonPrimitive(options.get(Math.floorMod(index+direction,options.size()))));}
    @Unique private void cinefxGui$cycleColor(VisualField field){String[] colors={"#FFFFFFFF","#FFFFC46A","#FF67D9FF","#FF9B70FF","#FFFF6666","#FF65DF98","#FF11151C"};String current=cinefxGui$string(field.value,colors[0]);int idx=0;for(int i=0;i<colors.length;i++)if(colors[i].equalsIgnoreCase(current))idx=i;cinefxGui$setField(field,new JsonPrimitive(colors[(idx+1)%colors.length]));}

    @Unique private void cinefxGui$beginText(VisualField field){
        if(field.setter==null||valueEditor==null)return;((CineFxStudioAccessMixin)(Object)this).cinefxGui$checkpoint();cinefxGui$textField=field;cinefxGui$changingText=true;
        valueEditor.setText(cinefxGui$editText(field.value));cinefxGui$changingText=false;valueEditor.setVisible(true);valueEditor.setFocused(true);valueEditor.setCursorToEnd(false);
    }
    @Unique private void cinefxGui$finishText(){cinefxGui$textField=null;if(valueEditor!=null){valueEditor.setFocused(false);valueEditor.setVisible(false);}}

    @Unique private void cinefxGui$addArray(VisualField field,EditorModel.Element selected){
        if(field.owner==null||field.ownerKey==null)return;CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();
        JsonArray a=field.value!=null&&field.value.isJsonArray()?field.value.getAsJsonArray():new JsonArray();field.owner.add(field.ownerKey,a);
        if(!a.isEmpty())a.add(a.get(a.size()-1).deepCopy());
        else if("keys".equals(field.ownerKey)){
            JsonObject k=new JsonObject();k.addProperty("tick",Math.max(0,preview.currentTick()-selected.startTick()));k.addProperty("value",0.0);k.addProperty("easing","SMOOTH_STEP");a.add(k);
        } else if(!Set.of("forces","goals","lights","animations","boneOverrides","morphs","agents","instances","links","cells","points").contains(field.ownerKey)){
            access.cinefxGui$toast("This list has no generic seed; use its specialised preset or duplicate an existing item");return;
        } else VisualPresetLibrary.appendListItem(field.owner,field.ownerKey,Math.max(0,preview.currentTick()-selected.startTick()));
        access.cinefxGui$markChanged();access.cinefxGui$toast("Added "+field.ownerKey+" item");
    }
    @Unique private void cinefxGui$duplicateLast(VisualField field){if(field.value==null||!field.value.isJsonArray()||field.value.getAsJsonArray().isEmpty()){cinefxGui$toast("Nothing to duplicate");return;}CineFxStudioAccessMixin a=(CineFxStudioAccessMixin)(Object)this;a.cinefxGui$checkpoint();JsonArray arr=field.value.getAsJsonArray();arr.add(arr.get(arr.size()-1).deepCopy());a.cinefxGui$markChanged();}
    @Unique private void cinefxGui$removeLast(VisualField field){if(field.value==null||!field.value.isJsonArray()||field.value.getAsJsonArray().isEmpty())return;CineFxStudioAccessMixin a=(CineFxStudioAccessMixin)(Object)this;a.cinefxGui$checkpoint();JsonArray arr=field.value.getAsJsonArray();arr.remove(arr.size()-1);a.cinefxGui$markChanged();}
    @Unique private void cinefxGui$addMapEntry(VisualField field){if(field.value==null||!field.value.isJsonObject())return;JsonObject map=field.value.getAsJsonObject();CineFxStudioAccessMixin a=(CineFxStudioAccessMixin)(Object)this;a.cinefxGui$checkpoint();String base="param",key=base;int i=2;while(map.has(key))key=base+i++;if("effects".equals(field.ownerKey)){key="BLOOM";if(map.has(key))key="VIGNETTE";JsonObject track=new JsonObject();track.addProperty("$kind","ScalarTrack");JsonArray keys=new JsonArray();JsonObject k=new JsonObject();k.addProperty("tick",0);k.addProperty("value",0.5);k.addProperty("easing","SMOOTH_STEP");keys.add(k);track.add("keys",keys);map.add(key,track);}else map.addProperty(key,"value");a.cinefxGui$markChanged();}
    @Unique private void cinefxGui$createNull(VisualField field){cinefxGui$setField(field,new JsonPrimitive(""));}

    @Unique private void cinefxGui$addType(CineFxBridge.ElementType type){
        CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();EditorModel.Element element=CineFxBridge.newDraft(type,project.durationTicks,project.elements.size());
        element.setKey(cinefxGui$uniqueKey(type.displayName()));element.setStartTick(preview.currentTick());element.setEndTick(project.durationTicks);
        Vec3d spawn=preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(4)).subtract(project.anchor());SceneManipulator.setBaseOffset(element,spawn);
        project.elements.add(element);access.cinefxGui$select(element);access.cinefxGui$markChanged();access.cinefxGui$toast("Added "+type.displayName());cinefxGui$catalogOpen=false;cinefxGui$visualOpen=true;cinefxGui$page=0;
    }

    @Unique private EditorModel.Element cinefxGui$selected(){return selectedId==null?null:project.find(selectedId);}
    @Unique private String cinefxGui$uniqueKey(String raw){String base=(raw==null?"element":raw).replaceAll("[^a-zA-Z0-9_]+","_").toLowerCase(Locale.ROOT);if(base.isBlank())base="element";String key=base;int i=2;boolean found;do{found=false;for(EditorModel.Element e:project.elements)if(key.equals(e.key())){found=true;break;}if(found)key=base+"_"+i++;}while(found);return key;}
    @Unique private Vec3d cinefxGui$cameraForward(){double y=Math.toRadians(preview.editorCameraYaw()),p=Math.toRadians(preview.editorCameraPitch());return new Vec3d(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p)).normalize();}

    @Unique private List<String> cinefxGui$options(EditorModel.Element element,String label,String current){
        String l=label.toLowerCase(Locale.ROOT),type=element.apiClass==null?"":element.apiClass;
        if(l.contains("easing"))return List.of("LINEAR","SMOOTH_STEP","SMOOTHER_STEP","EASE_IN_QUAD","EASE_OUT_QUAD","EASE_IN_OUT_QUAD","EASE_IN_CUBIC","EASE_OUT_CUBIC","EASE_IN_OUT_CUBIC","EASE_IN_SINE","EASE_OUT_SINE","EASE_IN_OUT_SINE","EASE_OUT_BACK");
        if(l.equals("conflictpolicy"))return List.of("REPLACE_LOWER","SKIP_IF_OCCUPIED","BLEND","STACK");
        if(l.equals("reverb"))return List.of("NONE","ROOM","HALL","CAVE","ARENA","UNDERWATER","SPACE","CUSTOM");
        if(l.equals("blendmode"))return List.of("OVERRIDE","ADDITIVE","MULTIPLY");
        if(l.equals("inheritmode"))return List.of("FULL","TRANSLATION_ROTATION","TRANSLATION_ONLY","NONE");
        if(l.equals("interpolation"))return List.of("LINEAR","CATMULL_ROM","BEZIER");
        if(l.equals("shape")){if(type.endsWith("EventElement$Emitter"))return List.of("POINT","SPHERE","DISC","CONE");return List.of("SPHERE","BOX","CYLINDER");}
        if(l.equals("kind")){if(type.endsWith("ComplexElement$Actor")||type.endsWith("AdvancedEventElement$Crowd"))return List.of("PLAYER","ENTITY","CUSTOM_MODEL");if(type.contains("Light"))return List.of("POINT","SPOT","AREA","TUBE");}
        if(l.equals("mode")){
            if(type.endsWith("UltraEventElement$Fracture"))return List.of("RADIAL","VORONOI","GRID","DIRECTIONAL","PREBAKED");
            if(type.endsWith("UltraEventElement$SoftBody"))return List.of("CLOTH","ROPE","CHAIN","SPRING","RAGDOLL","TENTACLE");
            if(type.endsWith("UltraEventElement$MaterialEffect"))return List.of("DISSOLVE","CORRUPTION","FREEZE","BURN","HOLOGRAM","SCAN","PHASE","CLOAK");
            if(type.endsWith("UltraEventElement$WorldDeform"))return List.of("LIFT","SINK","CRACK","FISSURE","WAVE","PULSE","GROW","REBUILD","BIOME_ILLUSION");
            if(type.endsWith("UltraEventElement$PortalSurface"))return List.of("PORTAL","MIRROR","CAMERA_FEED","DIMENSION_VIEW","KALEIDOSCOPE");
            if(type.endsWith("UltraEventElement$CameraRig"))return List.of("DOLLY","CRANE","ORBIT","RAIL","HANDHELD","FOLLOW","LOCKED","FREE");
            if(type.endsWith("ComplexElement$Shadow"))return List.of("BLOB","PROJECTED","GEOMETRY");
            if(type.endsWith("ComplexElement$Trail"))return List.of("RIBBON","TUBE","STREAK");
            if(type.endsWith("EventElement$Camera"))return List.of("ADDITIVE","ANCHOR_ABSOLUTE");
        }
        if(l.equals("preset"))return List.of("NONE","SPIN","LEVITATE","ORBIT","GRAVITY","BALLISTIC","JITTER","BAKED");
        return List.of();
    }

    @Unique private Range cinefxGui$range(String label,double value){String l=label.toLowerCase(Locale.ROOT);
        if(l.contains("opacity")||l.contains("amount")||l.contains("progress")||l.contains("weight")||l.contains("softness")||l.contains("mix")||l.contains("falloff")||l.contains("volumetric")||l.contains("eclipse")||l.contains("aurora")||l.contains("emissive"))return new Range(0,1,0.01);
        if(l.contains("fov"))return new Range(20,120,1); if(l.contains("cone")||l.contains("angle"))return new Range(0,180,1);
        if(l.contains("intensity"))return new Range(0,12,0.05); if(l.contains("radius"))return new Range(0,64,0.1);
        if(l.contains("density"))return new Range(0,2,0.01); if(l.contains("damping")||l.contains("drag"))return new Range(0,1,0.001);
        if(l.contains("gravity"))return new Range(0,0.2,0.001); if(l.contains("pitch")||l.contains("speed")||l.contains("scale"))return new Range(0,4,0.01);
        if(l.contains("shake"))return new Range(0,2,0.01); if(l.contains("roll")||l.contains("rotation")||l.endsWith(" yaw")||l.endsWith(" pitch"))return new Range(-180,180,1);
        return null;}
    @Unique private double cinefxGui$step(String label,double value){String l=label.toLowerCase(Locale.ROOT);if(l.contains("tick")||l.contains("count")||l.contains("iterations")||l.contains("depth")||l.contains("variant")||l.contains("points")||l.startsWith("max"))return 1;double a=Math.abs(value);return a>=100?10:a>=10?1:a>=1?0.1:0.01;}

    @Unique private JsonObject cinefxGui$nearest(JsonArray keys,double tick){JsonObject best=null;double dist=Double.POSITIVE_INFINITY;for(JsonElement raw:keys)if(raw.isJsonObject()){JsonObject k=raw.getAsJsonObject();double d=Math.abs(cinefxGui$number(k.get("tick"),0)-tick);if(d<dist){dist=d;best=k;}}return best;}
    @Unique private boolean cinefxGui$isVec(JsonObject o){return o.has("x")&&o.has("y")&&o.has("z")&&o.get("x").isJsonPrimitive()&&o.get("y").isJsonPrimitive()&&o.get("z").isJsonPrimitive();}
    @Unique private boolean cinefxGui$isMapField(String key){return "parameters".equals(key)||"appearance".equals(key)||"effects".equals(key)||"variables".equals(key)||"metadata".equals(key);}
    @Unique private boolean cinefxGui$isColor(String s){return s!=null&&s.matches("#[0-9a-fA-F]{8}");}
    @Unique private int cinefxGui$parseColor(String s){try{return(int)Long.parseLong(s.substring(1),16);}catch(Exception e){return 0xFFFFFFFF;}}
    @Unique private JsonElement cinefxGui$parseLike(JsonElement original,String text){if(original==null||original.isJsonNull())return new JsonPrimitive(text);if(!original.isJsonPrimitive())return new JsonPrimitive(text);JsonPrimitive p=original.getAsJsonPrimitive();if(p.isBoolean())return new JsonPrimitive(Boolean.parseBoolean(text));if(p.isNumber())return new JsonPrimitive(new BigDecimal(text));return new JsonPrimitive(text);}
    @Unique private String cinefxGui$editText(JsonElement value){if(value==null||value.isJsonNull())return "";return value.isJsonPrimitive()?value.getAsString():value.toString();}
    @Unique private double cinefxGui$findScalar(JsonElement root,String name,double tick,double fallback){JsonElement found=cinefxGui$findNamed(root,name);if(found==null)return fallback;if(found.isJsonPrimitive())return cinefxGui$number(found,fallback);if(found.isJsonObject()){JsonObject o=found.getAsJsonObject();if(o.has("keys")&&o.get("keys").isJsonArray()){JsonObject k=cinefxGui$nearest(o.getAsJsonArray("keys"),tick);return k==null?fallback:cinefxGui$number(k.get("value"),fallback);}}return fallback;}
    @Unique private JsonElement cinefxGui$findNamed(JsonElement root,String name){if(root==null||root.isJsonNull())return null;if(root.isJsonObject()){JsonObject o=root.getAsJsonObject();if(o.has(name))return o.get(name);for(Map.Entry<String,JsonElement> e:o.entrySet()){JsonElement v=cinefxGui$findNamed(e.getValue(),name);if(v!=null)return v;}}else if(root.isJsonArray())for(JsonElement v:root.getAsJsonArray()){JsonElement f=cinefxGui$findNamed(v,name);if(f!=null)return f;}return null;}
    @Unique private Vec3d cinefxGui$vec(JsonElement v,Vec3d f){if(v==null||!v.isJsonObject())return f;JsonObject o=v.getAsJsonObject();return new Vec3d(cinefxGui$number(o.get("x"),f.x),cinefxGui$number(o.get("y"),f.y),cinefxGui$number(o.get("z"),f.z));}

    @Unique private String cinefxGui$family(String api){if(api==null)return "CINEFX";if(api.contains("UltraEventElement"))return "ULTRA / VFX";if(api.contains("AdvancedEventElement"))return "ADVANCED EVENT";if(api.contains("ComplexElement"))return "3D / SCENE GRAPH";if(api.contains("EventElement"))return "EVENT";if(api.contains("SceneElement"))return "SCENE";return "CINEFX";}
    @Unique private String cinefxGui$simpleType(String api){if(api==null)return "Element";int i=Math.max(api.lastIndexOf('$'),api.lastIndexOf('.'));return i>=0?api.substring(i+1):api;}
    @Unique private String cinefxGui$pretty(String raw){if(raw==null)return "";String s=raw.replace('_',' ');StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(i>0&&Character.isUpperCase(ch)&&Character.isLowerCase(s.charAt(i-1)))b.append(' ');b.append(ch);}return b.toString();}
    @Unique private String cinefxGui$short(String text,int width){MinecraftClient c=MinecraftClient.getInstance();return c==null||c.textRenderer==null?(text==null?"":text):c.textRenderer.trimToWidth(text==null?"":text,Math.max(8,width));}
    @Unique private String cinefxGui$formatNumber(double v){if(Math.abs(v)>=1000)return String.format(Locale.ROOT,"%.0f",v);if(Math.abs(v)>=100)return String.format(Locale.ROOT,"%.1f",v);if(Math.abs(v)>=1)return String.format(Locale.ROOT,"%.2f",v);return String.format(Locale.ROOT,"%.3f",v);}
    @Unique private double cinefxGui$number(JsonElement v,double f){try{return v==null||v.isJsonNull()?f:v.getAsDouble();}catch(RuntimeException e){return f;}}
    @Unique private String cinefxGui$string(JsonElement v,String f){try{return v==null||v.isJsonNull()?f:v.getAsString();}catch(RuntimeException e){return f;}}
    @Unique private boolean cinefxGui$hover(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Unique private void cinefxGui$toast(String text){((CineFxStudioAccessMixin)(Object)this).cinefxGui$toast(text);}

    @Unique private void cinefxGui$button(DrawContext c,int mx,int my,int x,int y,int w,String text,Runnable action){boolean h=cinefxGui$hover(mx,my,x,y,w,22);cinefxGui$box(c,x,y,w,22,h?HOVER:CONTROL,h?ACCENT:0xAA465666);c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,cinefxGui$short(text,w-12),x+6,y+7,h?0xFFFFFFFF:TEXT);cinefxGui$hits.add(new VisualHit(x,y,x+w,y+22,action));}
    @Unique private void cinefxGui$mini(DrawContext c,int mx,int my,int x,int y,int w,String text,Runnable action){boolean h=cinefxGui$hover(mx,my,x,y,w,20);c.fill(x,y,x+w,y+20,h?HOVER:CONTROL);c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,cinefxGui$short(text,w-8),x+4,y+6,h?0xFFFFFFFF:TEXT);cinefxGui$hits.add(new VisualHit(x,y,x+w,y+20,action));}
    @Unique private void cinefxGui$pill(DrawContext c,int x,int y,int w,int h,String text,int color){c.fill(x,y,x+w,y+h,0xEC26313B);c.fill(x,y,x+3,y+h,color);c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,text,x+10,y+6,color);}
    @Unique private void cinefxGui$box(DrawContext c,int x,int y,int w,int h,int fill,int border){c.fill(x,y,x+w,y+h,fill);c.fill(x,y,x+w,y+1,border);c.fill(x,y+h-1,x+w,y+h,border);c.fill(x,y,x+1,y+h,border);c.fill(x+w-1,y,x+w,y+h,border);}
}
