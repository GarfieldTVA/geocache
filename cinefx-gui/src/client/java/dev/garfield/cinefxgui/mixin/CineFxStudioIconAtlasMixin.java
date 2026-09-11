package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Draws the CineFX generated icon atlas over the viewport-first editor.
 *
 * This mixin is deliberately visual-only: click targets and authoring behavior stay owned by the
 * viewport-first workspace, so introducing the icon set cannot change scene editing semantics.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 900)
public abstract class CineFxStudioIconAtlasMixin {
    @Unique private static final Identifier CINEFX_ICONS =
            Identifier.of("cinefx_gui", "textures/gui/icons/cinefx_icons.png");
    @Unique private static final int ATLAS_W = 128;
    @Unique private static final int ATLAS_H = 64;
    @Unique private static final int CELL = 32;
    @Unique private static final int VIEW_MARGIN = 8;
    @Unique private static final int TOP = 32;
    @Unique private static final int TIMELINE_H = 112;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;

    @Unique
    private enum CineFxAtlasIcon {
        CAMERA(0, 0), LIGHT(1, 0), AUDIO(2, 0), FX(3, 0),
        PORTAL(0, 1), ATMOSPHERE(1, 1), POST(2, 1), ANIMATION(3, 1);

        final int u;
        final int v;
        CineFxAtlasIcon(int column, int row) {
            this.u = column * CELL;
            this.v = row * CELL;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$drawGeneratedIcons(DrawContext context, int mouseX, int mouseY,
                                               float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || project == null || preview == null) return;

        cinefxGui$drawWorldElementIcons(context);

        String panel = cinefxGui$panelName();
        if ("SCENE".equals(panel)) cinefxGui$drawSceneListIcons(context);
        else if ("ADD".equals(panel)) cinefxGui$drawAddMenuIcons(context);
        else if ("QUICK".equals(panel)) cinefxGui$drawQuickEditIcon(context);

        if (preview.sceneCameraPreview()) {
            int sw = client.getWindow().getScaledWidth();
            cinefxGui$drawIcon(context, CineFxAtlasIcon.CAMERA, sw / 2 - 118, 40, 18);
        }
    }

    @Unique
    private void cinefxGui$drawSceneListIcons(DrawContext context) {
        int x = 10, y = 38;
        int bottom = cinefxGui$timelineTop();
        int rows = Math.max(4, Math.min(11, (bottom - y - 72) / 24));
        int page = Math.max(0, cinefxGui$readIntField("cinefxGui$scenePage", 0));
        int pages = Math.max(1, (project.elements.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int start = page * rows;
        int yy = y + 32;

        for (int i = start; i < Math.min(project.elements.size(), start + rows); i++) {
            EditorModel.Element element = project.elements.get(i);
            CineFxAtlasIcon icon = cinefxGui$iconFor(element);
            if (icon != null) {
                context.fill(x + 8, yy + 2, x + 35, yy + 20, 0xE71D252D);
                cinefxGui$drawIcon(context, icon, x + 12, yy + 3, 17);
            }
            yy += 24;
        }
    }

    @Unique
    private void cinefxGui$drawAddMenuIcons(DrawContext context) {
        int x = 74, y = 38;
        int bx = x + 12, by = y + 45;

        by += 32;
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.CAMERA, bx, by);
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.LIGHT, bx + 146, by);

        by += 32;
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.LIGHT, bx, by);
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.FX, bx + 146, by);

        by += 32;
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.AUDIO, bx, by);

        by += 32;
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.ATMOSPHERE, bx, by);
        cinefxGui$drawButtonIcon(context, CineFxAtlasIcon.POST, bx + 146, by);
    }

    @Unique
    private void cinefxGui$drawButtonIcon(DrawContext context, CineFxAtlasIcon icon, int x, int y) {
        context.fill(x + 3, y + 3, x + 25, y + 21, 0xEE26323E);
        cinefxGui$drawIcon(context, icon, x + 5, y + 4, 16);
    }

    @Unique
    private void cinefxGui$drawQuickEditIcon(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        EditorModel.Element selected = cinefxGui$selected();
        CineFxAtlasIcon icon = cinefxGui$iconFor(selected);
        if (icon == null) return;
        int sw = client.getWindow().getScaledWidth();
        int x = Math.max(10, sw - 286), y = 39, w = 276;
        cinefxGui$drawIcon(context, icon, x + w - 54, y + 5, 18);
    }

    @Unique
    private void cinefxGui$drawWorldElementIcons(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int sw = client.getWindow().getScaledWidth();
        int bottom = cinefxGui$timelineTop();
        int globalX = 12;
        int globalY = cinefxGui$chromeHidden() ? 34 : 38;

        for (EditorModel.Element element : project.elements) {
            if (element == null || element.hiddenInEditor) continue;
            CineFxAtlasIcon icon = cinefxGui$iconFor(element);
            if (icon == null) continue;

            Vec3d local = SceneManipulator.pivotLocal(
                    element, Math.max(0, preview.currentTick() - element.startTick()));
            if (local == null) {
                cinefxGui$drawChip(context, icon, globalX, globalY, 22);
                globalY += 24;
                continue;
            }

            Vec3d world = project.anchor().add(local);
            ViewportGizmo.ScreenPoint point = ViewportGizmo.project(
                    world,
                    preview.editorCameraPosition(),
                    preview.editorCameraYaw(),
                    preview.editorCameraPitch(),
                    VIEW_MARGIN, TOP, sw - VIEW_MARGIN, bottom);
            if (!point.visible()) continue;

            int px = (int) Math.round(point.x());
            int py = (int) Math.round(point.y());
            if (px < VIEW_MARGIN || px >= sw - VIEW_MARGIN || py < TOP || py >= bottom) continue;

            boolean selected = element.editorId.equals(selectedId);
            int size = selected ? 24 : 20;
            int left = px - size / 2;
            int top = py - size / 2;
            context.fill(left - 2, top - 2, left + size + 2, top + size + 2,
                    selected ? 0xE5245E86 : 0xC9121920);
            if (selected) {
                context.fill(left - 2, top - 2, left + size + 2, top, 0xFF57AFFF);
                context.fill(left - 2, top + size, left + size + 2, top + size + 2, 0xFF57AFFF);
            }
            cinefxGui$drawIcon(context, icon, left, top, size);
        }
    }

    @Unique
    private void cinefxGui$drawChip(DrawContext context, CineFxAtlasIcon icon, int x, int y, int size) {
        context.fill(x, y, x + size + 6, y + size + 2, 0xD51A222B);
        cinefxGui$drawIcon(context, icon, x + 3, y + 1, size);
    }

    @Unique
    private void cinefxGui$drawIcon(DrawContext context, CineFxAtlasIcon icon, int x, int y, int size) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                CINEFX_ICONS,
                x, y,
                (float) icon.u, (float) icon.v,
                size, size,
                CELL, CELL,
                ATLAS_W, ATLAS_H);
    }

    @Unique
    private CineFxAtlasIcon cinefxGui$iconFor(EditorModel.Element element) {
        if (element == null || element.apiClass == null) return null;
        String type = element.apiClass.toLowerCase(Locale.ROOT);
        if (type.contains("camera")) return CineFxAtlasIcon.CAMERA;
        if (type.contains("light")) return CineFxAtlasIcon.LIGHT;
        if (type.contains("audio") || type.contains("sound")) return CineFxAtlasIcon.AUDIO;
        if (type.contains("particle") || type.contains("emitter") || type.contains("trail") || type.contains("force")) {
            return CineFxAtlasIcon.FX;
        }
        if (type.contains("portal")) return CineFxAtlasIcon.PORTAL;
        if (type.contains("atmosphere") || type.contains("sky")) return CineFxAtlasIcon.ATMOSPHERE;
        if (type.contains("postprocess") || type.contains("overlay") || type.contains("grade")) {
            return CineFxAtlasIcon.POST;
        }
        if (type.contains("animation") || type.contains("rig")) return CineFxAtlasIcon.ANIMATION;
        return null;
    }

    @Unique
    private EditorModel.Element cinefxGui$selected() {
        return selectedId == null ? null : project.find(selectedId);
    }

    @Unique
    private int cinefxGui$timelineTop() {
        MinecraftClient client = MinecraftClient.getInstance();
        int h = client.getWindow().getScaledHeight();
        return cinefxGui$chromeHidden() ? h - VIEW_MARGIN : h - TIMELINE_H;
    }

    @Unique
    private boolean cinefxGui$chromeHidden() {
        Object value = cinefxGui$readField("cinefxGui$chromeHidden");
        return value instanceof Boolean hidden && hidden;
    }

    @Unique
    private String cinefxGui$panelName() {
        Object value = cinefxGui$readField("cinefxGui$panel");
        return value == null ? "NONE" : value.toString();
    }

    @Unique
    private int cinefxGui$readIntField(String name, int fallback) {
        Object value = cinefxGui$readField(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @Unique
    private Object cinefxGui$readField(String name) {
        try {
            Field field = ((Object) this).getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
