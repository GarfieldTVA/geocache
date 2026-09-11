package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Viewport-first CineFX Studio.
 *
 * Minecraft is the workspace. Persistent Blender-style side docks are intentionally removed in
 * favour of small contextual overlays: Scene, Add, right-click actions and Quick Edit. Advanced
 * timeline/curve/event screens still exist, but the common path is select -> right click -> edit.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 1900)
public abstract class CineFxStudioViewportFirstMixin {
    @Unique private static final int VIEW_MARGIN = 8;
    @Unique private static final int TOP = 32;
    @Unique private static final int TIMELINE_H = 112;
    @Unique private static final int CONTEXT_W = 164;
    @Unique private static final int CONTEXT_ROW = 21;

    @Unique private static final int BG = 0xEE151B22;
    @Unique private static final int BG_SOFT = 0xD91A222B;
    @Unique private static final int CONTROL = 0xEE26323E;
    @Unique private static final int HOVER = 0xF1374858;
    @Unique private static final int BORDER = 0xE54A5B69;
    @Unique private static final int ACCENT = 0xFF57AFFF;
    @Unique private static final int TEXT = 0xFFF2F6F8;
    @Unique private static final int DIM = 0xFFA6B4BE;
    @Unique private static final int MUTED = 0xFF71818D;
    @Unique private static final int GOOD = 0xFF6DDB9B;
    @Unique private static final int WARN = 0xFFFFC86C;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private SceneManipulator.Tool tool;
    @Shadow private boolean rightLook;
    @Shadow private double cameraSpeed;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;

    @Unique private CineFxSimplePanel cinefxGui$panel = CineFxSimplePanel.NONE;
    @Unique private boolean cinefxGui$chromeHidden;
    @Unique private boolean cinefxGui$allTypes;
    @Unique private int cinefxGui$scenePage;
    @Unique private int cinefxGui$addPage;
    @Unique private String cinefxGui$contextTarget;
    @Unique private int cinefxGui$contextX;
    @Unique private int cinefxGui$contextY;
    @Unique private double cinefxGui$rmbStartX;
    @Unique private double cinefxGui$rmbStartY;
    @Unique private final List<CineFxUiHit> cinefxGui$hits = new ArrayList<>();
    @Unique private final List<CineFxIconHit> cinefxGui$icons = new ArrayList<>();
    @Unique private final List<CineFxQuickSlider> cinefxGui$sliders = new ArrayList<>();
    @Unique private CineFxQuickSlider cinefxGui$activeSlider;

    @Unique private enum CineFxSimplePanel { NONE, SCENE, ADD, QUICK }
    @Unique private record CineFxUiHit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }
    @Unique private record CineFxIconHit(String editorId, int x1, int y1, int x2, int y2) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }
    @Unique private record CineFxQuickSlider(int x1, int y1, int x2, int y2, String editorId,
                                             String field, double min, double max) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
        double value(double x) {
            double t = Math.max(0.0, Math.min(1.0, (x - x1) / Math.max(1.0, x2 - x1)));
            return min + (max - min) * t;
        }
    }

    /* ---------- Turn the old docked editor into a nearly full-screen Minecraft viewport. ---------- */

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void cinefxGui$removeOldWorkspaceBackgrounds(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // drawTimeline owns its own background. The five old render() dock backgrounds are removed.
    }

    @Inject(method = "drawToolbar", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideOldToolbar(DrawContext c, int mx, int my, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "drawLeftPanel", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideOldLeft(DrawContext c, int mx, int my, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "drawInspector", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$hideOldInspector(DrawContext c, int mx, int my, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "drawTimeline", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$optionalTimeline(DrawContext c, int mx, int my, CallbackInfo ci) {
        if (cinefxGui$chromeHidden) ci.cancel();
    }

    @Inject(method = "timelineTop", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$compactTimeline(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        int h = client.getWindow().getScaledHeight();
        cir.setReturnValue(cinefxGui$chromeHidden ? Math.max(TOP + 80, h - VIEW_MARGIN) : Math.max(TOP + 150, h - TIMELINE_H));
    }

    @ModifyConstant(method = "drawViewport", constant = @Constant(intValue = 252))
    private int cinefxGui$viewportLeft(int old) { return VIEW_MARGIN; }

    @ModifyConstant(method = "drawViewport", constant = @Constant(intValue = 364))
    private int cinefxGui$viewportRight(int old) { return VIEW_MARGIN; }

    @ModifyConstant(method = "inViewport", constant = @Constant(intValue = 252))
    private int cinefxGui$inputLeft(int old) { return VIEW_MARGIN; }

    @ModifyConstant(method = "inViewport", constant = @Constant(intValue = 364))
    private int cinefxGui$inputRight(int old) { return VIEW_MARGIN; }

    @ModifyConstant(method = "mouseScrolled", constant = @Constant(intValue = 252))
    private int cinefxGui$scrollLeft(int old) { return VIEW_MARGIN; }

    @ModifyConstant(method = "mouseScrolled", constant = @Constant(intValue = 364))
    private int cinefxGui$scrollRight(int old) { return VIEW_MARGIN; }

    @Inject(method = "render", at = @At("HEAD"))
    private void cinefxGui$prepareViewportFirst(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
        if (searchField != null) searchField.setVisible(false);
        if (valueEditor != null) valueEditor.setVisible(false);
    }

    /* ---------- One compact toolbar + overlays. Drawn last so gizmos never cover menus. ---------- */

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$renderViewportFirst(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
        cinefxGui$hits.clear();
        cinefxGui$icons.clear();
        cinefxGui$sliders.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        cinefxGui$drawEditorIcons(c, mx, my);
        if (cinefxGui$chromeHidden) {
            String hint = "TAB  Interface CineFX   ·   RMB + WASD/QE  voler   ·   clic droit objet  modifier";
            int w = client.textRenderer.getWidth(hint) + 18;
            int x = Math.max(8, (client.getWindow().getScaledWidth() - w) / 2);
            cinefxGui$box(c, x, 7, w, 21, 0xC810151B, 0xA3465664);
            c.drawTextWithShadow(client.textRenderer, hint, x + 9, 14, 0xFFDDE6EB);
        } else {
            cinefxGui$drawToolbar(c, mx, my);
            switch (cinefxGui$panel) {
                case SCENE -> cinefxGui$drawScenePanel(c, mx, my);
                case ADD -> cinefxGui$drawAddPanel(c, mx, my);
                case QUICK -> cinefxGui$drawQuickPanel(c, mx, my);
                default -> { }
            }
        }

        if (preview.sceneCameraPreview()) cinefxGui$drawReturnToEditor(c, mx, my);
        if (cinefxGui$contextTarget != null) cinefxGui$drawContextMenu(c, mx, my);
    }

    @Unique
    private void cinefxGui$drawToolbar(DrawContext c, int mx, int my) {
        MinecraftClient client = MinecraftClient.getInstance();
        int sw = client.getWindow().getScaledWidth();
        c.fill(0, 0, sw, TOP, 0xC910151B);
        c.fill(0, TOP - 1, sw, TOP, 0x90435461);
        c.drawTextWithShadow(client.textRenderer, "CineFX", 9, 11, 0xFFF5F8FA);
        int x = 55;
        x = cinefxGui$toolbarButton(c,mx,my,x,"SCÈNE",()->cinefxGui$toggle(CineFxSimplePanel.SCENE));
        x = cinefxGui$toolbarButton(c,mx,my,x+4,"+ AJOUTER",()->{ cinefxGui$allTypes=false; cinefxGui$toggle(CineFxSimplePanel.ADD); });
        x += 7;
        x = cinefxGui$toolButton(c,mx,my,x,"G", SceneManipulator.Tool.MOVE);
        x = cinefxGui$toolButton(c,mx,my,x+3,"R", SceneManipulator.Tool.ROTATE);
        x = cinefxGui$toolButton(c,mx,my,x+3,"S", SceneManipulator.Tool.SCALE);
        x += 7;
        x = cinefxGui$toolbarButton(c,mx,my,x,preview.playing()?"PAUSE":"PLAY",()->preview.togglePlay(client));
        x = cinefxGui$toolbarButton(c,mx,my,x+4,"KEY",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        x = cinefxGui$toolbarButton(c,mx,my,x+4,preview.sceneCameraPreview()?"CAM ÉDITEUR":"CAM RÉAL.",()->preview.setSceneCameraPreview(client,!preview.sceneCameraPreview()));
        cinefxGui$toolbarButton(c,mx,my,x+4,"SAVE",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$saveProject());

        EditorModel.Element selected = cinefxGui$selected();
        if (selected != null) {
            String selectedText = cinefxGui$icon(selected) + "  " + cinefxGui$trim(selected.key(), 150);
            int w = client.textRenderer.getWidth(selectedText) + 18;
            int sx = Math.max(x + 82, sw - w - 10);
            cinefxGui$box(c,sx,5,w,22,0xD91D2630,selected.editorId.equals(selectedId)?ACCENT:BORDER);
            c.drawTextWithShadow(client.textRenderer, selectedText, sx+9, 12, 0xFFE5EDF2);
            cinefxGui$hits.add(new CineFxUiHit(sx,5,sx+w,27,()->cinefxGui$panel=CineFxSimplePanel.QUICK));
        }
    }

    @Unique
    private int cinefxGui$toolbarButton(DrawContext c,int mx,int my,int x,String text,Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.textRenderer.getWidth(text)+14;
        boolean hover=cinefxGui$inside(mx,my,x,5,w,22);
        cinefxGui$box(c,x,5,w,22,hover?HOVER:CONTROL,hover?0xFF607384:0xB8455360);
        c.drawTextWithShadow(client.textRenderer,text,x+7,12,hover?0xFFFFFFFF:0xFFDCE5EA);
        cinefxGui$hits.add(new CineFxUiHit(x,5,x+w,27,action));
        return x+w;
    }

    @Unique
    private int cinefxGui$toolButton(DrawContext c,int mx,int my,int x,String text,SceneManipulator.Tool candidate) {
        boolean active=tool==candidate;
        int w=28;
        cinefxGui$box(c,x,5,w,22,active?0xE5276089:cinefxGui$inside(mx,my,x,5,w,22)?HOVER:CONTROL,active?ACCENT:BORDER);
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,text,x+10,12,active?0xFFFFFFFF:DIM);
        cinefxGui$hits.add(new CineFxUiHit(x,5,x+w,27,()->tool=candidate));
        return x+w;
    }

    @Unique
    private void cinefxGui$toggle(CineFxSimplePanel p) { cinefxGui$panel = cinefxGui$panel == p ? CineFxSimplePanel.NONE : p; }

    /* ---------- Scene overlay ---------- */

    @Unique
    private void cinefxGui$drawScenePanel(DrawContext c,int mx,int my) {
        MinecraftClient client=MinecraftClient.getInstance();
        int x=10,y=38,w=232;
        int rows=Math.max(4,Math.min(11,(cinefxGui$timelineTop()-y-72)/24));
        int h=50+rows*24;
        cinefxGui$box(c,x,y,w,h,BG,BORDER);
        c.drawTextWithShadow(client.textRenderer,"SCÈNE  ·  "+project.elements.size()+" éléments",x+11,y+10,TEXT);
        cinefxGui$smallButton(c,mx,my,x+w-28,y+5,20,"×",()->cinefxGui$panel=CineFxSimplePanel.NONE);
        int pages=Math.max(1,(project.elements.size()+rows-1)/rows);
        cinefxGui$scenePage=Math.max(0,Math.min(cinefxGui$scenePage,pages-1));
        int start=cinefxGui$scenePage*rows;
        int yy=y+32;
        for(int i=start;i<Math.min(project.elements.size(),start+rows);i++) {
            EditorModel.Element e=project.elements.get(i);
            boolean selected=e.editorId.equals(selectedId);
            c.fill(x+6,yy,x+w-6,yy+21,selected?0xE72B526D:cinefxGui$inside(mx,my,x+6,yy,w-12,21)?0xE82A3540:0xB51D252D);
            c.drawTextWithShadow(client.textRenderer,cinefxGui$icon(e),x+12,yy+7,cinefxGui$iconColor(e));
            c.drawTextWithShadow(client.textRenderer,cinefxGui$trim(e.key(),150),x+39,yy+7,e.enabled?0xFFE5EDF2:0xFF77838C);
            final EditorModel.Element target=e;
            cinefxGui$hits.add(new CineFxUiHit(x+6,yy,x+w-6,yy+21,()->{
                ((CineFxStudioAccessMixin)(Object)this).cinefxGui$select(target); selectedId=target.editorId;
                cinefxGui$panel=CineFxSimplePanel.QUICK;
            }));
            yy+=24;
        }
        int fy=y+h-27;
        cinefxGui$smallButton(c,mx,my,x+8,fy,54,"◀",()->cinefxGui$scenePage=Math.max(0,cinefxGui$scenePage-1));
        c.drawTextWithShadow(client.textRenderer,(cinefxGui$scenePage+1)+" / "+pages,x+91,fy+6,DIM);
        cinefxGui$smallButton(c,mx,my,x+w-62,fy,54,"▶",()->cinefxGui$scenePage=Math.min(pages-1,cinefxGui$scenePage+1));
    }

    /* ---------- Add overlay ---------- */

    @Unique
    private void cinefxGui$drawAddPanel(DrawContext c,int mx,int my) {
        MinecraftClient client=MinecraftClient.getInstance();
        int x=74,y=38,w=304;
        if(!cinefxGui$allTypes) {
            int h=260;
            cinefxGui$box(c,x,y,w,h,BG,BORDER);
            c.drawTextWithShadow(client.textRenderer,"AJOUT RAPIDE",x+12,y+10,TEXT);
            c.drawTextWithShadow(client.textRenderer,"Ajouté devant ta caméra au temps actuel",x+12,y+24,MUTED);
            cinefxGui$smallButton(c,mx,my,x+w-28,y+5,20,"×",()->cinefxGui$panel=CineFxSimplePanel.NONE);
            int bx=x+12,by=y+45,bw=134;
            cinefxGui$quickAdd(c,mx,my,bx,by,bw,"■  Bloc","SceneElement$Block","Block");
            cinefxGui$quickAdd(c,mx,my,bx+146,by,bw,"T  Texte","WorldText","World Text");
            by+=32;
            cinefxGui$button(c,mx,my,bx,by,bw,"▣  Caméra",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$createCameraFromView());
            cinefxGui$button(c,mx,my,bx+146,by,bw,"☀  Point Light",()->cinefxGui$createLight("POINT"));
            by+=32;
            cinefxGui$button(c,mx,my,bx,by,bw,"◉  Spot Light",()->cinefxGui$createLight("SPOT"));
            cinefxGui$quickAdd(c,mx,my,bx+146,by,bw,"✦  Particules","EventElement$Emitter","Emitter");
            by+=32;
            cinefxGui$quickAdd(c,mx,my,bx,by,bw,"♪  Son","EventElement$AudioCue","Audio Cue");
            cinefxGui$quickAdd(c,mx,my,bx+146,by,bw,"◆  Marker","EventElement$Marker","Marker");
            by+=32;
            cinefxGui$quickAdd(c,mx,my,bx,by,bw,"☁  Atmosphère","EventElement$Atmosphere","Atmosphere");
            cinefxGui$quickAdd(c,mx,my,bx+146,by,bw,"FX  Post Process","UltraEventElement$PostProcess","Post Process");
            by+=39;
            cinefxGui$button(c,mx,my,bx,by,w-24,"Tous les types CineFX…",()->{cinefxGui$allTypes=true;cinefxGui$addPage=0;});
        } else {
            List<CineFxBridge.ElementType> types=CineFxBridge.elementTypes();
            int rows=9,h=36+rows*26+32;
            cinefxGui$box(c,x,y,w,h,BG,BORDER);
            c.drawTextWithShadow(client.textRenderer,"TOUS LES TYPES",x+12,y+10,TEXT);
            cinefxGui$smallButton(c,mx,my,x+w-76,y+5,46,"←",()->cinefxGui$allTypes=false);
            cinefxGui$smallButton(c,mx,my,x+w-26,y+5,18,"×",()->cinefxGui$panel=CineFxSimplePanel.NONE);
            int pages=Math.max(1,(types.size()+rows-1)/rows);
            cinefxGui$addPage=Math.max(0,Math.min(cinefxGui$addPage,pages-1));
            int yy=y+31,start=cinefxGui$addPage*rows;
            for(int i=start;i<Math.min(types.size(),start+rows);i++) {
                CineFxBridge.ElementType type=types.get(i);
                final CineFxBridge.ElementType target=type;
                cinefxGui$button(c,mx,my,x+10,yy,w-20,cinefxGui$trim(type.displayName(),w-42),()->cinefxGui$addType(target));
                yy+=26;
            }
            int fy=y+h-27;
            cinefxGui$smallButton(c,mx,my,x+10,fy,52,"◀",()->cinefxGui$addPage=Math.max(0,cinefxGui$addPage-1));
            c.drawTextWithShadow(client.textRenderer,(cinefxGui$addPage+1)+" / "+pages,x+132,fy+6,DIM);
            cinefxGui$smallButton(c,mx,my,x+w-62,fy,52,"▶",()->cinefxGui$addPage=Math.min(pages-1,cinefxGui$addPage+1));
        }
    }

    @Unique
    private void cinefxGui$quickAdd(DrawContext c,int mx,int my,int x,int y,int w,String text,String classHint,String displayHint) {
        cinefxGui$button(c,mx,my,x,y,w,text,()->{
            CineFxBridge.ElementType type=cinefxGui$type(classHint,displayHint);
            if(type==null){cinefxGui$toast(displayHint+" indisponible");return;}
            cinefxGui$addType(type);
        });
    }

    /* ---------- Quick edit: only the useful controls for the selected object. ---------- */

    @Unique
    private void cinefxGui$drawQuickPanel(DrawContext c,int mx,int my) {
        EditorModel.Element e=cinefxGui$selected();
        if(e==null){cinefxGui$panel=CineFxSimplePanel.NONE;return;}
        MinecraftClient client=MinecraftClient.getInstance();
        int sw=client.getWindow().getScaledWidth();
        int x=Math.max(10,sw-286),y=39,w=276;
        boolean light=cinefxGui$isLight(e),camera=cinefxGui$isCamera(e);
        int h=light?306:camera?245:225;
        cinefxGui$box(c,x,y,w,h,BG,BORDER);
        c.drawTextWithShadow(client.textRenderer,cinefxGui$icon(e)+"  "+cinefxGui$trim(e.key(),176),x+12,y+10,TEXT);
        c.drawTextWithShadow(client.textRenderer,cinefxGui$simpleType(e.apiClass),x+12,y+24,MUTED);
        cinefxGui$smallButton(c,mx,my,x+w-28,y+5,20,"×",()->cinefxGui$panel=CineFxSimplePanel.NONE);
        int yy=y+43;
        cinefxGui$button(c,mx,my,x+10,yy,78,"Déplacer",()->tool=SceneManipulator.Tool.MOVE);
        cinefxGui$button(c,mx,my,x+94,yy,78,"Tourner",()->tool=SceneManipulator.Tool.ROTATE);
        cinefxGui$button(c,mx,my,x+178,yy,88,"Taille",()->tool=SceneManipulator.Tool.SCALE);
        yy+=31;
        cinefxGui$button(c,mx,my,x+10,yy,78,"Keyframe",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
        cinefxGui$button(c,mx,my,x+94,yy,78,"Focus",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$focusSelected());
        cinefxGui$button(c,mx,my,x+178,yy,88,e.hiddenInEditor?"Afficher":"Masquer",()->{e.hiddenInEditor=!e.hiddenInEditor;cinefxGui$changed("Visibilité éditeur");});
        yy+=36;

        if(light) {
            String kind=cinefxGui$string(e.data.get("kind"),"POINT");
            cinefxGui$button(c,mx,my,x+10,yy,122,"Type : "+kind,()->{e.data.addProperty("kind","SPOT".equalsIgnoreCase(kind)?"POINT":"SPOT");cinefxGui$changed("Type de lumière");});
            cinefxGui$button(c,mx,my,x+138,yy,128,"Orienter avec vue",()->{e.data.add("direction",cinefxGui$vecJson(cinefxGui$cameraForward()));cinefxGui$changed("Direction lumière");});
            yy+=32;
            double local=Math.max(0,preview.currentTick()-e.startTick());
            yy=cinefxGui$slider(c,x+10,yy,w-20,"Intensité",cinefxGui$scalar(e,"intensity",local,1.0),0,12,e,"intensity",mx,my);
            yy=cinefxGui$slider(c,x+10,yy,w-20,"Rayon",cinefxGui$scalar(e,"radius",local,8.0),0.5,64,e,"radius",mx,my);
            c.drawTextWithShadow(client.textRenderer,"COULEUR",x+12,yy+5,DIM);
            int[] colors={0xFFFFFFFF,0xFFFFD39A,0xFFB8D8FF,0xFFFF655D,0xFF70E69A,0xFF766CFF};
            int sx=x+70;
            for(int color:colors){
                c.fill(sx,yy,sx+23,yy+19,color); c.fill(sx,yy,sx+23,yy+1,0xFFFFFFFF);
                final int chosen=color;
                cinefxGui$hits.add(new CineFxUiHit(sx,yy,sx+23,yy+19,()->cinefxGui$setColor(e,"color",chosen)));
                sx+=29;
            }
            yy+=27;
            c.drawTextWithShadow(client.textRenderer,"✓ Aperçu lumière natif actif",x+12,yy,GOOD);
            c.drawTextWithShadow(client.textRenderer,"Un shader compatible peut remplacer ce fallback.",x+12,yy+13,MUTED);
        } else if(camera) {
            cinefxGui$button(c,mx,my,x+10,yy,122,preview.sceneCameraPreview()?"← Cam éditeur":"Voir caméra",()->preview.setSceneCameraPreview(client,!preview.sceneCameraPreview()));
            cinefxGui$button(c,mx,my,x+138,yy,128,"Key caméra",()->((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead());
            yy+=34;
            if(e.data.has("fovDegrees")) {
                double local=Math.max(0,preview.currentTick()-e.startTick());
                cinefxGui$slider(c,x+10,yy,w-20,"FOV",cinefxGui$scalar(e,"fovDegrees",local,70),20,120,e,"fovDegrees",mx,my);
            }
            c.drawTextWithShadow(client.textRenderer,"C ou Échap revient toujours à la caméra éditeur.",x+12,y+h-23,GOOD);
        } else {
            cinefxGui$drawCommonQuickScalars(c,e,x+10,yy,w-20,mx,my);
            c.drawTextWithShadow(client.textRenderer,"Clic droit dans la vue = actions rapides",x+12,y+h-23,MUTED);
        }
    }

    @Unique
    private void cinefxGui$drawCommonQuickScalars(DrawContext c,EditorModel.Element e,int x,int y,int w,int mx,int my) {
        String[] candidates={"opacity","size","speed","ratePerSecond","spawnRate","volume","pitch","density","amount","radius","progress"};
        int count=0;
        double local=Math.max(0,preview.currentTick()-e.startTick());
        for(String field:candidates) {
            if(count>=3 || !e.data.has(field) || !e.data.get(field).isJsonObject()) continue;
            double min=0,max=1;
            if(field.contains("rate")||field.equals("spawnRate")){max=2000;}
            else if(field.equals("size")){max=10;}
            else if(field.equals("speed")||field.equals("pitch")){max=4;}
            else if(field.equals("volume")){max=2;}
            else if(field.equals("radius")){max=64;}
            y=cinefxGui$slider(c,x,y,w,cinefxGui$friendly(field),cinefxGui$scalar(e,field,local,0),min,max,e,field,mx,my);
            count++;
        }
        if(count==0) c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,"Les actions principales sont juste au-dessus.",x,y+5,DIM);
    }

    /* ---------- Right click directly on preview ---------- */

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$viewportClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client==null)return;
        double mx=click.x(),my=click.y();

        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for(int i=cinefxGui$sliders.size()-1;i>=0;i--){
                CineFxQuickSlider s=cinefxGui$sliders.get(i);
                if(s.contains(mx,my)){cinefxGui$activeSlider=s;cinefxGui$applySlider(s,mx);cir.setReturnValue(true);return;}
            }
            for(int i=cinefxGui$hits.size()-1;i>=0;i--){
                CineFxUiHit hit=cinefxGui$hits.get(i);
                if(hit.contains(mx,my)){hit.action.run();cir.setReturnValue(true);return;}
            }
        }

        if(cinefxGui$contextTarget!=null && cinefxGui$handleContext(mx,my,click.button())) {
            cir.setReturnValue(true);return;
        }

        for(int i=cinefxGui$icons.size()-1;i>=0;i--){
            CineFxIconHit icon=cinefxGui$icons.get(i);
            if(!icon.contains(mx,my))continue;
            EditorModel.Element target=project.find(icon.editorId());
            if(target==null)continue;
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$select(target);selectedId=target.editorId;
            if(click.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){cinefxGui$openContext(target,mx,my);rightLook=true;cinefxGui$rmbStartX=mx;cinefxGui$rmbStartY=my;}
            else cinefxGui$panel=CineFxSimplePanel.QUICK;
            cir.setReturnValue(true);return;
        }

        if(!cinefxGui$inViewport(mx,my))return;
        EditorModel.Element hovered=cinefxGui$pick(mx,my);
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){
            cinefxGui$rmbStartX=mx;cinefxGui$rmbStartY=my;rightLook=true;
            if(hovered!=null){
                ((CineFxStudioAccessMixin)(Object)this).cinefxGui$select(hovered);selectedId=hovered.editorId;
                cinefxGui$openContext(hovered,mx,my);
            } else cinefxGui$contextTarget=null;
            cir.setReturnValue(true);return;
        }
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT && doubled && hovered!=null){
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$select(hovered);selectedId=hovered.editorId;
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$focusSelected();
            cir.setReturnValue(true);return;
        }
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT) cinefxGui$contextTarget=null;
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$sliderOrRmbDrag(Click click,double dx,double dy,CallbackInfoReturnable<Boolean> cir) {
        if(cinefxGui$activeSlider!=null && click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            cinefxGui$applySlider(cinefxGui$activeSlider,click.x());cir.setReturnValue(true);return;
        }
        if(rightLook && click.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if(Math.hypot(click.x()-cinefxGui$rmbStartX,click.y()-cinefxGui$rmbStartY)>3.0) cinefxGui$contextTarget=null;
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void cinefxGui$releaseSlider(Click click, CallbackInfoReturnable<Boolean> cir) {
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT) cinefxGui$activeSlider=null;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$simpleKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if(preview.sceneCameraPreview() && (input.key()==GLFW.GLFW_KEY_C || input.key()==GLFW.GLFW_KEY_ESCAPE)) {
            preview.setSceneCameraPreview(MinecraftClient.getInstance(),false);
            cinefxGui$toast("Caméra éditeur"); cir.setReturnValue(true); return;
        }
        if(input.key()==GLFW.GLFW_KEY_TAB && input.modifiers()==0) {
            cinefxGui$chromeHidden=!cinefxGui$chromeHidden; cinefxGui$panel=CineFxSimplePanel.NONE; cinefxGui$contextTarget=null;
            cir.setReturnValue(true); return;
        }
        if(rightLook) {
            int key=input.key();
            if(key==GLFW.GLFW_KEY_W||key==GLFW.GLFW_KEY_A||key==GLFW.GLFW_KEY_S||key==GLFW.GLFW_KEY_D||key==GLFW.GLFW_KEY_Q||key==GLFW.GLFW_KEY_E){cir.setReturnValue(true);}
        }
    }

    /** Correct free-fly directions; editor camera movement stays usable in the full viewport. */
    @Inject(method = "updateFreeCamera", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$freeCamera(CallbackInfo ci) {
        ci.cancel();
        MinecraftClient client=MinecraftClient.getInstance();
        if(preview.sceneCameraPreview()||!rightLook||client==null)return;
        long window=client.getWindow().getHandle();
        double boost=cinefxGui$pressed(window,GLFW.GLFW_KEY_LEFT_SHIFT)||cinefxGui$pressed(window,GLFW.GLFW_KEY_RIGHT_SHIFT)?4.0:1.0;
        double precise=cinefxGui$pressed(window,GLFW.GLFW_KEY_LEFT_ALT)||cinefxGui$pressed(window,GLFW.GLFW_KEY_RIGHT_ALT)?0.25:1.0;
        double speed=cameraSpeed*boost*precise;
        float yaw=preview.editorCameraYaw(),pitch=preview.editorCameraPitch();
        double ry=Math.toRadians(yaw),rp=Math.toRadians(pitch);
        Vec3d forward=new Vec3d(-Math.sin(ry)*Math.cos(rp),-Math.sin(rp),Math.cos(ry)*Math.cos(rp));
        Vec3d right=new Vec3d(-Math.cos(ry),0,-Math.sin(ry));
        Vec3d pos=preview.editorCameraPosition();boolean changed=false;
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_W)){pos=pos.add(forward.multiply(speed));changed=true;}
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_S)){pos=pos.subtract(forward.multiply(speed));changed=true;}
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_D)){pos=pos.add(right.multiply(speed));changed=true;}
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_A)){pos=pos.subtract(right.multiply(speed));changed=true;}
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_E)){pos=pos.add(0,speed,0);changed=true;}
        if(cinefxGui$pressed(window,GLFW.GLFW_KEY_Q)){pos=pos.add(0,-speed,0);changed=true;}
        if(changed)preview.setEditorCamera(pos,yaw,pitch);
    }

    /* ---------- Context menu ---------- */

    @Unique private void cinefxGui$openContext(EditorModel.Element e,double mx,double my){
        cinefxGui$contextTarget=e.editorId;cinefxGui$contextX=(int)Math.round(mx);cinefxGui$contextY=(int)Math.round(my);
    }

    @Unique
    private void cinefxGui$drawContextMenu(DrawContext c,int mx,int my) {
        EditorModel.Element e=project.find(cinefxGui$contextTarget);
        if(e==null){cinefxGui$contextTarget=null;return;}
        MinecraftClient client=MinecraftClient.getInstance();
        int sw=client.getWindow().getScaledWidth(),bottom=cinefxGui$timelineTop();
        String[] rows={"Modifier…","Déplacer [G]","Tourner [R]","Taille [S]","Keyframe maintenant","Focus","Dupliquer",e.hiddenInEditor?"Afficher":"Masquer","Supprimer"};
        int h=rows.length*CONTEXT_ROW;
        int x=Math.max(8,Math.min(sw-CONTEXT_W-8,cinefxGui$contextX));
        int y=Math.max(TOP+5,Math.min(bottom-h-5,cinefxGui$contextY));
        cinefxGui$contextX=x;cinefxGui$contextY=y;
        cinefxGui$box(c,x,y,CONTEXT_W,h,BG,ACCENT);
        for(int i=0;i<rows.length;i++){
            int yy=y+i*CONTEXT_ROW;
            if(cinefxGui$inside(mx,my,x,yy,CONTEXT_W,CONTEXT_ROW))c.fill(x+1,yy+1,x+CONTEXT_W-1,yy+CONTEXT_ROW-1,HOVER);
            c.drawTextWithShadow(client.textRenderer,rows[i],x+9,yy+7,i==rows.length-1?0xFFFF9D93:0xFFE5EDF2);
        }
    }

    @Unique
    private boolean cinefxGui$handleContext(double mx,double my,int button) {
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT && button!=GLFW.GLFW_MOUSE_BUTTON_RIGHT)return false;
        int x=cinefxGui$contextX,y=cinefxGui$contextY,h=CONTEXT_ROW*9;
        if(!cinefxGui$inside(mx,my,x,y,CONTEXT_W,h)){if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT)cinefxGui$contextTarget=null;return false;}
        EditorModel.Element e=project.find(cinefxGui$contextTarget);if(e==null){cinefxGui$contextTarget=null;return true;}
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$select(e);selectedId=e.editorId;
        int row=Math.max(0,Math.min(8,(int)((my-y)/CONTEXT_ROW)));
        switch(row){
            case 0->cinefxGui$panel=CineFxSimplePanel.QUICK;
            case 1->tool=SceneManipulator.Tool.MOVE;
            case 2->tool=SceneManipulator.Tool.ROTATE;
            case 3->tool=SceneManipulator.Tool.SCALE;
            case 4->((CineFxStudioAccessMixin)(Object)this).cinefxGui$addKeyframesAtPlayhead();
            case 5->((CineFxStudioAccessMixin)(Object)this).cinefxGui$focusSelected();
            case 6->((CineFxStudioAccessMixin)(Object)this).cinefxGui$duplicateSelected();
            case 7->{e.hiddenInEditor=!e.hiddenInEditor;cinefxGui$changed("Visibilité éditeur");}
            case 8->((CineFxStudioAccessMixin)(Object)this).cinefxGui$deleteSelected();
            default->{}
        }
        cinefxGui$contextTarget=null;return true;
    }

    /* ---------- Visible editor icons for things that are otherwise invisible. ---------- */

    @Unique
    private void cinefxGui$drawEditorIcons(DrawContext c,int mx,int my) {
        MinecraftClient client=MinecraftClient.getInstance();
        int right=client.getWindow().getScaledWidth()-VIEW_MARGIN,bottom=cinefxGui$timelineTop();
        int globalX=12,globalY=cinefxGui$chromeHidden?34:38;
        for(EditorModel.Element e:project.elements){
            if(e==null||e.hiddenInEditor||!cinefxGui$needsIcon(e))continue;
            Vec3d local=SceneManipulator.pivotLocal(e,Math.max(0,preview.currentTick()-e.startTick()));
            if(local==null){
                int w=client.textRenderer.getWidth(cinefxGui$icon(e)+" "+cinefxGui$trim(e.key(),100))+14;
                cinefxGui$box(c,globalX,globalY,w,20,0xD51A222B,cinefxGui$iconColor(e));
                c.drawTextWithShadow(client.textRenderer,cinefxGui$icon(e)+" "+cinefxGui$trim(e.key(),100),globalX+7,globalY+6,cinefxGui$iconColor(e));
                cinefxGui$icons.add(new CineFxIconHit(e.editorId,globalX,globalY,globalX+w,globalY+20));
                globalY+=23;continue;
            }
            Vec3d world=project.anchor().add(local);
            ViewportGizmo.ScreenPoint p=ViewportGizmo.project(world,preview.editorCameraPosition(),preview.editorCameraYaw(),preview.editorCameraPitch(),VIEW_MARGIN,TOP,right,bottom);
            if(!p.visible())continue;
            int px=(int)Math.round(p.x()),py=(int)Math.round(p.y());
            if(px<VIEW_MARGIN||px>=right||py<TOP||py>=bottom)continue;
            int color=cinefxGui$iconColor(e);String icon=cinefxGui$icon(e);
            int iw=Math.max(25,client.textRenderer.getWidth(icon)+12);
            c.fill(px-iw/2-1,py-11,px+iw/2+1,py+11,0xBC05080B);
            c.fill(px-iw/2,py-10,px+iw/2,py+10,0xD519222B);
            c.fill(px-iw/2,py-10,px+iw/2,py-8,color);
            c.drawTextWithShadow(client.textRenderer,icon,px-client.textRenderer.getWidth(icon)/2,py-4,color);
            if(e.editorId.equals(selectedId)||Math.hypot(mx-px,my-py)<24)c.drawTextWithShadow(client.textRenderer,cinefxGui$trim(e.key(),110),px+iw/2+5,py-4,color);
            cinefxGui$icons.add(new CineFxIconHit(e.editorId,px-iw/2-4,py-14,px+iw/2+5,py+14));
        }
    }

    @Unique private boolean cinefxGui$needsIcon(EditorModel.Element e){
        String t=e.apiClass==null?"":e.apiClass.toLowerCase(Locale.ROOT);
        return t.contains("camera")||t.contains("light")||t.contains("audio")||t.contains("sound")||t.contains("emitter")||t.contains("particle")
                ||t.contains("marker")||t.contains("atmosphere")||t.contains("sky")||t.contains("postprocess")||t.contains("overlay")
                ||t.contains("force")||t.contains("portal")||t.contains("worlddeform")||t.contains("proceduralrig")||t.contains("volume")||t.contains("trail");
    }

    @Unique private String cinefxGui$icon(EditorModel.Element e){
        String t=e.apiClass==null?"":e.apiClass.toLowerCase(Locale.ROOT);
        if(t.contains("camera"))return "CAM"; if(t.contains("light"))return "LIGHT"; if(t.contains("audio")||t.contains("sound"))return "SND";
        if(t.contains("particle")||t.contains("emitter"))return "FX"; if(t.contains("marker"))return "MARK"; if(t.contains("portal"))return "PORT";
        if(t.contains("atmosphere")||t.contains("sky"))return "SKY"; if(t.contains("postprocess")||t.contains("overlay"))return "POST";
        if(t.contains("proceduralrig"))return "IK"; if(t.contains("force"))return "FORCE"; if(t.contains("volume"))return "VOL"; if(t.contains("trail"))return "TRAIL";
        return "OBJ";
    }

    @Unique private int cinefxGui$iconColor(EditorModel.Element e){
        String i=cinefxGui$icon(e);return switch(i){case "CAM"->0xFF65B7FF;case "LIGHT"->0xFFFFD35F;case "SND"->0xFF7DDEA0;case "FX"->0xFFD887FF;case "PORT"->0xFFB777FF;case "POST"->0xFFFF8FB2;default->0xFF8FD1D9;};
    }

    /* ---------- Director/editor camera escape hatch ---------- */

    @Unique
    private void cinefxGui$drawReturnToEditor(DrawContext c,int mx,int my){
        MinecraftClient client=MinecraftClient.getInstance();
        String text="← RETOUR CAMÉRA ÉDITEUR   [C / Échap]";
        int w=client.textRenderer.getWidth(text)+24;
        int x=(client.getWindow().getScaledWidth()-w)/2,y=36;
        boolean hover=cinefxGui$inside(mx,my,x,y,w,26);
        cinefxGui$box(c,x,y,w,26,hover?0xF02D6085:0xEE173D59,hover?0xFFFFFFFF:0xFF65B7FF);
        c.drawTextWithShadow(client.textRenderer,text,x+12,y+9,0xFFFFFFFF);
        cinefxGui$hits.add(new CineFxUiHit(x,y,x+w,y+26,()->preview.setSceneCameraPreview(client,false)));
    }

    /* ---------- Creation and quick-value helpers ---------- */

    @Unique
    private void cinefxGui$addType(CineFxBridge.ElementType type){
        CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();
        EditorModel.Element e=CineFxBridge.newDraft(type,project.durationTicks,project.elements.size());
        e.setKey(cinefxGui$uniqueKey(cinefxGui$simpleType(type.apiClass().getName()).toLowerCase(Locale.ROOT)));
        e.setStartTick(preview.currentTick());e.setEndTick(project.durationTicks);
        Vec3d spawn=preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(4)).subtract(project.anchor());
        SceneManipulator.setBaseOffset(e,spawn);
        project.elements.add(e);access.cinefxGui$select(e);selectedId=e.editorId;access.cinefxGui$markChanged();
        cinefxGui$panel=CineFxSimplePanel.QUICK;cinefxGui$toast("Ajouté : "+type.displayName());
    }

    @Unique
    private void cinefxGui$createLight(String kind){
        CineFxBridge.ElementType type=cinefxGui$type("SceneLight","Scene Light");
        if(type==null){cinefxGui$toast("Scene Light indisponible");return;}
        CineFxStudioAccessMixin access=(CineFxStudioAccessMixin)(Object)this;access.cinefxGui$checkpoint();
        EditorModel.Element e=CineFxBridge.newDraft(type,project.durationTicks,project.elements.size());
        e.setKey(cinefxGui$uniqueKey("SPOT".equals(kind)?"spot_light":"point_light"));e.setStartTick(preview.currentTick());e.setEndTick(project.durationTicks);
        e.data.addProperty("kind",kind);e.data.add("direction",cinefxGui$vecJson(cinefxGui$cameraForward()));
        e.data.addProperty("innerConeDegrees",20.0);e.data.addProperty("outerConeDegrees",38.0);
        cinefxGui$setScalar(e,"intensity",3.0);cinefxGui$setScalar(e,"radius",12.0);cinefxGui$setColor(e,"color",0xFFFFE0B0,false);
        Vec3d spawn=preview.editorCameraPosition().add(cinefxGui$cameraForward().multiply(4)).subtract(project.anchor());SceneManipulator.setBaseOffset(e,spawn);
        project.elements.add(e);access.cinefxGui$select(e);selectedId=e.editorId;access.cinefxGui$markChanged();cinefxGui$panel=CineFxSimplePanel.QUICK;
        cinefxGui$toast("Lumière ajoutée · aperçu natif actif");
    }

    @Unique
    private CineFxBridge.ElementType cinefxGui$type(String classHint,String displayHint){
        String cn=classHint.toLowerCase(Locale.ROOT),dn=displayHint.toLowerCase(Locale.ROOT);
        for(CineFxBridge.ElementType t:CineFxBridge.elementTypes()){
            String c=t.apiClass().getName().toLowerCase(Locale.ROOT),d=t.displayName().toLowerCase(Locale.ROOT);
            if(c.endsWith(cn)||c.contains(cn)||d.equals(dn)||d.contains(dn))return t;
        }return null;
    }

    @Unique
    private int cinefxGui$slider(DrawContext c,int x,int y,int w,String label,double value,double min,double max,EditorModel.Element e,String field,int mx,int my){
        MinecraftClient client=MinecraftClient.getInstance();
        c.drawTextWithShadow(client.textRenderer,label,x,y+4,DIM);
        String v=String.format(Locale.ROOT,Math.abs(value)>=100?"%.0f":"%.2f",value);
        c.drawTextWithShadow(client.textRenderer,v,x+w-client.textRenderer.getWidth(v),y+4,TEXT);
        int sx=x+82,sw=Math.max(60,w-130),sy=y+5;
        c.fill(sx,sy+5,sx+sw,sy+9,0xFF2B3742);
        double t=Math.max(0,Math.min(1,(value-min)/Math.max(1e-9,max-min)));int px=sx+(int)Math.round(sw*t);
        c.fill(sx,sy+5,px,sy+9,ACCENT);c.fill(px-2,sy+1,px+3,sy+14,0xFFFFFFFF);
        cinefxGui$sliders.add(new CineFxQuickSlider(sx,sy-2,sx+sw,sy+17,e.editorId,field,min,max));
        return y+27;
    }

    @Unique private void cinefxGui$applySlider(CineFxQuickSlider s,double mouseX){
        EditorModel.Element e=project.find(s.editorId());if(e==null)return;
        cinefxGui$setScalar(e,s.field(),s.value(mouseX));((CineFxStudioAccessMixin)(Object)this).cinefxGui$markChanged();
    }

    @Unique
    private void cinefxGui$setScalar(EditorModel.Element e,String field,double value){
        JsonObject track=e.data.has(field)&&e.data.get(field).isJsonObject()?e.data.getAsJsonObject(field):new JsonObject();
        if(!track.has("$kind"))track.addProperty("$kind","ScalarTrack");
        JsonArray keys=track.has("keys")&&track.get("keys").isJsonArray()?track.getAsJsonArray("keys"):new JsonArray();
        double tick=Math.max(0,preview.currentTick()-e.startTick());JsonObject key=cinefxGui$keyAt(keys,tick);
        if(key==null){key=new JsonObject();key.addProperty("tick",tick);key.addProperty("easing",Easing.SMOOTH_STEP.name());keys.add(key);}
        key.addProperty("value",value);cinefxGui$sort(keys);track.add("keys",keys);e.data.add(field,track);
    }

    @Unique private double cinefxGui$scalar(EditorModel.Element e,String field,double tick,double fallback){
        if(e==null||!e.data.has(field)||!e.data.get(field).isJsonObject())return fallback;JsonObject t=e.data.getAsJsonObject(field);
        if(!t.has("keys")||!t.get("keys").isJsonArray())return fallback;JsonObject k=cinefxGui$nearest(t.getAsJsonArray("keys"),tick);return k==null?fallback:cinefxGui$number(k.get("value"),fallback);
    }

    @Unique private void cinefxGui$setColor(EditorModel.Element e,String field,int color){cinefxGui$setColor(e,field,color,true);}
    @Unique private void cinefxGui$setColor(EditorModel.Element e,String field,int color,boolean changed){
        JsonObject track=e.data.has(field)&&e.data.get(field).isJsonObject()?e.data.getAsJsonObject(field):new JsonObject();track.addProperty("$kind","ColorTrack");
        JsonArray keys=track.has("keys")&&track.get("keys").isJsonArray()?track.getAsJsonArray("keys"):new JsonArray();double tick=Math.max(0,preview.currentTick()-e.startTick());
        JsonObject key=cinefxGui$keyAt(keys,tick);if(key==null){key=new JsonObject();key.addProperty("tick",tick);key.addProperty("easing",Easing.SMOOTH_STEP.name());keys.add(key);}key.addProperty("value",String.format(Locale.ROOT,"#%08X",color));
        cinefxGui$sort(keys);track.add("keys",keys);e.data.add(field,track);if(changed)cinefxGui$changed("Couleur");
    }

    @Unique private JsonObject cinefxGui$keyAt(JsonArray a,double tick){for(JsonElement raw:a)if(raw.isJsonObject()){JsonObject k=raw.getAsJsonObject();if(Math.abs(cinefxGui$number(k.get("tick"),-1e9)-tick)<.001)return k;}return null;}
    @Unique private JsonObject cinefxGui$nearest(JsonArray a,double tick){JsonObject best=null;double d=Double.POSITIVE_INFINITY;for(JsonElement raw:a)if(raw.isJsonObject()){JsonObject k=raw.getAsJsonObject();double nd=Math.abs(cinefxGui$number(k.get("tick"),0)-tick);if(nd<d){d=nd;best=k;}}return best;}
    @Unique private void cinefxGui$sort(JsonArray a){ArrayList<JsonObject> list=new ArrayList<>();for(JsonElement raw:a)if(raw.isJsonObject())list.add(raw.getAsJsonObject());list.sort(Comparator.comparingDouble(k->cinefxGui$number(k.get("tick"),0)));while(!a.isEmpty())a.remove(a.size()-1);list.forEach(a::add);}

    /* ---------- Picking ---------- */

    @Unique private EditorModel.Element cinefxGui$pick(double mx,double my){
        int right=MinecraftClient.getInstance().getWindow().getScaledWidth()-VIEW_MARGIN,bottom=cinefxGui$timelineTop();EditorModel.Element best=null;double bd=16;
        for(EditorModel.Element e:project.elements){if(e==null||e.hiddenInEditor)continue;Vec3d local=SceneManipulator.pivotLocal(e,Math.max(0,preview.currentTick()-e.startTick()));if(local==null)continue;
            ViewportGizmo.ScreenPoint p=ViewportGizmo.project(project.anchor().add(local),preview.editorCameraPosition(),preview.editorCameraYaw(),preview.editorCameraPitch(),VIEW_MARGIN,TOP,right,bottom);if(!p.visible())continue;
            double d=Math.hypot(mx-p.x(),my-p.y());if(d<bd){bd=d;best=e;}}
        return best;
    }

    @Unique private boolean cinefxGui$inViewport(double x,double y){int sw=MinecraftClient.getInstance().getWindow().getScaledWidth();return x>=VIEW_MARGIN&&x<sw-VIEW_MARGIN&&y>=TOP&&y<cinefxGui$timelineTop();}
    @Unique private int cinefxGui$timelineTop(){int h=MinecraftClient.getInstance().getWindow().getScaledHeight();return cinefxGui$chromeHidden?h-VIEW_MARGIN:h-TIMELINE_H;}

    /* ---------- Small UI helpers ---------- */

    @Unique private void cinefxGui$button(DrawContext c,int mx,int my,int x,int y,int w,String text,Runnable action){boolean hover=cinefxGui$inside(mx,my,x,y,w,24);cinefxGui$box(c,x,y,w,24,hover?HOVER:CONTROL,hover?0xFF607384:BORDER);c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,cinefxGui$trim(text,w-14),x+7,y+8,hover?0xFFFFFFFF:0xFFE0E8ED);cinefxGui$hits.add(new CineFxUiHit(x,y,x+w,y+24,action));}
    @Unique private void cinefxGui$smallButton(DrawContext c,int mx,int my,int x,int y,int w,String text,Runnable action){boolean hover=cinefxGui$inside(mx,my,x,y,w,20);cinefxGui$box(c,x,y,w,20,hover?HOVER:CONTROL,BORDER);c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,cinefxGui$trim(text,w-8),x+4,y+6,0xFFE1E9EE);cinefxGui$hits.add(new CineFxUiHit(x,y,x+w,y+20,action));}
    @Unique private void cinefxGui$box(DrawContext c,int x,int y,int w,int h,int fill,int border){c.fill(x,y,x+w,y+h,fill);c.fill(x,y,x+w,y+1,border);c.fill(x,y+h-1,x+w,y+h,border);c.fill(x,y,x+1,y+h,border);c.fill(x+w-1,y,x+w,y+h,border);}
    @Unique private boolean cinefxGui$inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Unique private EditorModel.Element cinefxGui$selected(){return selectedId==null?null:project.find(selectedId);}
    @Unique private boolean cinefxGui$isLight(EditorModel.Element e){return e!=null&&e.apiClass!=null&&(e.apiClass.endsWith("SceneLight")||e.apiClass.endsWith("UltraEventElement$LightRig"));}
    @Unique private boolean cinefxGui$isCamera(EditorModel.Element e){return e!=null&&e.apiClass!=null&&e.apiClass.toLowerCase(Locale.ROOT).contains("camera");}
    @Unique private String cinefxGui$simpleType(String s){if(s==null)return "Element";int i=Math.max(s.lastIndexOf('.'),s.lastIndexOf('$'));return i>=0?s.substring(i+1):s;}
    @Unique private String cinefxGui$friendly(String s){return switch(s){case "ratePerSecond","spawnRate"->"Débit";case "opacity"->"Opacité";case "density"->"Densité";case "amount"->"Effet";case "volume"->"Volume";case "pitch"->"Pitch";case "radius"->"Rayon";case "progress"->"Progression";default->s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);};}
    @Unique private String cinefxGui$trim(String s,int width){return MinecraftClient.getInstance().textRenderer.trimToWidth(s==null?"":s,Math.max(4,width));}
    @Unique private Vec3d cinefxGui$cameraForward(){double y=Math.toRadians(preview.editorCameraYaw()),p=Math.toRadians(preview.editorCameraPitch());return new Vec3d(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p)).normalize();}
    @Unique private JsonObject cinefxGui$vecJson(Vec3d v){JsonObject o=new JsonObject();o.addProperty("x",v.x);o.addProperty("y",v.y);o.addProperty("z",v.z);return o;}
    @Unique private String cinefxGui$uniqueKey(String base){String b=(base==null||base.isBlank()?"element":base).replaceAll("[^a-zA-Z0-9_]+","_").toLowerCase(Locale.ROOT);String k=b;int i=2;boolean found;do{found=false;for(EditorModel.Element e:project.elements)if(k.equals(e.key())){found=true;break;}if(found)k=b+"_"+i++;}while(found);return k;}
    @Unique private void cinefxGui$changed(String message){CineFxStudioAccessMixin a=(CineFxStudioAccessMixin)(Object)this;a.cinefxGui$markChanged();a.cinefxGui$toast(message);}
    @Unique private void cinefxGui$toast(String message){((CineFxStudioAccessMixin)(Object)this).cinefxGui$toast(message);}
    @Unique private static double cinefxGui$number(JsonElement e,double fallback){try{return e==null||e.isJsonNull()?fallback:e.getAsDouble();}catch(RuntimeException ex){return fallback;}}
    @Unique private static String cinefxGui$string(JsonElement e,String fallback){try{return e==null||e.isJsonNull()?fallback:e.getAsString();}catch(RuntimeException ex){return fallback;}}
    @Unique private static boolean cinefxGui$pressed(long window,int key){return GLFW.glfwGetKey(window,key)==GLFW.GLFW_PRESS;}
}
