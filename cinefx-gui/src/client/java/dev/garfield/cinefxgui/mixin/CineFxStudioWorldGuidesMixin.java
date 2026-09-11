package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/** Visual editor-only helpers for non-solid camera/light elements. */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 1700)
public abstract class CineFxStudioWorldGuidesMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$guides(DrawContext c, int mx, int my, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || project == null) return;
        int left = 8, top = 32;
        int right = client.getWindow().getScaledWidth() - 8;
        int bottom = client.getWindow().getScaledHeight() - 112;
        double tick = preview.currentTick();

        for (EditorModel.Element e : project.elements) {
            if (e == null || e.hiddenInEditor || e.apiClass == null) continue;
            String type = e.apiClass.toLowerCase(Locale.ROOT);
            boolean light = type.endsWith("scenelight") || type.contains("lightrig");
            boolean camera = type.contains("camera");
            if (!light && !camera) continue;
            Vec3d local = SceneManipulator.pivotLocal(e, Math.max(0.0, tick - e.startTick()));
            if (local == null) continue;
            Vec3d world = project.anchor().add(local);
            ViewportGizmo.ScreenPoint center = ViewportGizmo.project(world,
                    preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                    left, top, right, bottom);
            if (!center.visible()) continue;

            if (light) cinefxGui$lightGuide(c, e, world, center, left, top, right, bottom);
            if (camera) cinefxGui$cameraGuide(c, e, world, center, left, top, right, bottom);
        }
    }

    @Unique
    private void cinefxGui$lightGuide(DrawContext c, EditorModel.Element e, Vec3d world,
                                      ViewportGizmo.ScreenPoint center, int left, int top, int right, int bottom) {
        double local = Math.max(0.0, preview.currentTick() - e.startTick());
        double radius = cinefxGui$scalar(e, "radius", local, 8.0);
        int r = (int)Math.max(12, Math.min(92, radius * 2.8));
        int segments = 28;
        double lx = center.x() + r, ly = center.y();
        for (int i = 1; i <= segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            double nx = center.x() + Math.cos(a) * r;
            double ny = center.y() + Math.sin(a) * r;
            ViewportGizmo.drawLine(c, lx, ly, nx, ny, 0x72FFD45F, 1);
            lx = nx; ly = ny;
        }

        String kind = cinefxGui$string(e.data.get("kind"), "POINT");
        if ("SPOT".equalsIgnoreCase(kind)) {
            Vec3d direction = cinefxGui$vec(e.data.get("direction"), new Vec3d(0, -1, 0));
            if (direction.lengthSquared() > 1.0e-8) direction = direction.normalize();
            ViewportGizmo.ScreenPoint tip = ViewportGizmo.project(world.add(direction.multiply(Math.min(12.0, radius))),
                    preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                    left, top, right, bottom);
            if (tip.visible()) {
                ViewportGizmo.drawLine(c, center.x(), center.y(), tip.x(), tip.y(), 0xDDFFD45F, 2);
                ViewportGizmo.drawLine(c, tip.x() - 4, tip.y() - 4, tip.x(), tip.y(), 0xDDFFD45F, 2);
                ViewportGizmo.drawLine(c, tip.x() + 4, tip.y() - 4, tip.x(), tip.y(), 0xDDFFD45F, 2);
            }
        }
    }

    @Unique
    private void cinefxGui$cameraGuide(DrawContext c, EditorModel.Element e, Vec3d world,
                                       ViewportGizmo.ScreenPoint center, int left, int top, int right, int bottom) {
        JsonElement raw = e.data.get("lookAtOffset");
        if (raw == null || !raw.isJsonObject()) return;
        Vec3d target = project.anchor().add(cinefxGui$vec(raw, Vec3d.ZERO));
        ViewportGizmo.ScreenPoint p = ViewportGizmo.project(target,
                preview.editorCameraPosition(), preview.editorCameraYaw(), preview.editorCameraPitch(),
                left, top, right, bottom);
        if (!p.visible()) return;
        ViewportGizmo.drawLine(c, center.x(), center.y(), p.x(), p.y(), 0xA865B7FF, 1);
        c.fill((int)p.x() - 3, (int)p.y() - 3, (int)p.x() + 4, (int)p.y() + 4, 0xDD65B7FF);
    }

    @Unique
    private double cinefxGui$scalar(EditorModel.Element e, String field, double tick, double fallback) {
        if (!e.data.has(field) || !e.data.get(field).isJsonObject()) return fallback;
        JsonObject track = e.data.getAsJsonObject(field);
        if (!track.has("keys") || !track.get("keys").isJsonArray()) return fallback;
        JsonArray keys = track.getAsJsonArray("keys");
        double bestDistance = Double.POSITIVE_INFINITY, result = fallback;
        for (JsonElement raw : keys) {
            if (!raw.isJsonObject()) continue;
            JsonObject key = raw.getAsJsonObject();
            double kt = cinefxGui$number(key.get("tick"), 0.0);
            double d = Math.abs(kt - tick);
            if (d < bestDistance) { bestDistance = d; result = cinefxGui$number(key.get("value"), fallback); }
        }
        return result;
    }

    @Unique
    private Vec3d cinefxGui$vec(JsonElement raw, Vec3d fallback) {
        if (raw == null || !raw.isJsonObject()) return fallback;
        JsonObject o = raw.getAsJsonObject();
        return new Vec3d(cinefxGui$number(o.get("x"), fallback.x),
                cinefxGui$number(o.get("y"), fallback.y), cinefxGui$number(o.get("z"), fallback.z));
    }

    @Unique private static double cinefxGui$number(JsonElement e, double fallback) {
        try { return e == null || e.isJsonNull() ? fallback : e.getAsDouble(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    @Unique private static String cinefxGui$string(JsonElement e, String fallback) {
        try { return e == null || e.isJsonNull() ? fallback : e.getAsString(); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
