package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projects against Minecraft's actual full-screen world camera. The Studio panels are overlays, not a
 * smaller render target, so using the center panel dimensions for projection made gizmos drift away
 * from the object as panels/resolution/FOV changed.
 */
@Mixin(value = ViewportGizmo.class, remap = false)
public abstract class ViewportGizmoProjectionMixin {
    @Inject(method = "project", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$projectWithGameViewport(Vec3d world, Vec3d camera, float yaw, float pitch,
                                                           int left, int top, int right, int bottom,
                                                           CallbackInfoReturnable<ViewportGizmo.ScreenPoint> cir) {
        if (world == null || camera == null) {
            cir.setReturnValue(new ViewportGizmo.ScreenPoint(0, 0, -1, false));
            return;
        }
        Vec3d rel = world.subtract(camera);
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));

        // Minecraft yaw 0 looks toward +Z, where camera-right is -X. The old +X basis mirrored
        // horizontal projection and made the overlay disagree with what the player actually saw.
        Vec3d screenRight = new Vec3d(-Math.cos(ry), 0, -Math.sin(ry));
        Vec3d screenUp = screenRight.crossProduct(forward).normalize();
        double depth = rel.dotProduct(forward);
        if (depth <= 0.03) {
            cir.setReturnValue(new ViewportGizmo.ScreenPoint(0, 0, depth, false));
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double fov = 70.0;
        int screenW = Math.max(1, right - left);
        int screenH = Math.max(1, bottom - top);
        if (client != null) {
            if (client.options != null) {
                try { fov = client.options.getFov().getValue(); }
                catch (RuntimeException ignored) { }
            }
            if (client.getWindow() != null) {
                screenW = Math.max(1, client.getWindow().getScaledWidth());
                screenH = Math.max(1, client.getWindow().getScaledHeight());
            }
        }
        fov = Math.max(1.0, Math.min(179.0, fov));

        // Minecraft renders the world behind the GUI across the entire window. Only clipping uses the
        // visible Studio center panel; projection itself must stay centered on the actual game window.
        double focal = screenH / (2.0 * Math.tan(Math.toRadians(fov) / 2.0));
        double cx = screenW * 0.5;
        double cy = screenH * 0.5;
        double x = cx + rel.dotProduct(screenRight) / depth * focal;
        double y = cy - rel.dotProduct(screenUp) / depth * focal;
        boolean visible = x >= left - 120 && x <= right + 120 && y >= top - 120 && y <= bottom + 120;
        cir.setReturnValue(new ViewportGizmo.ScreenPoint(x, y, depth, visible));
    }
}
