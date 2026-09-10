package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the actual configured Minecraft FOV instead of the editor's old fixed 70 degree projection. */
@Mixin(value = ViewportGizmo.class, remap = false)
public abstract class ViewportGizmoProjectionMixin {
    @Inject(method = "project", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$projectWithConfiguredFov(Vec3d world, Vec3d camera, float yaw, float pitch,
                                                            int left, int top, int right, int bottom,
                                                            CallbackInfoReturnable<ViewportGizmo.ScreenPoint> cir) {
        if (world == null || camera == null) {
            cir.setReturnValue(new ViewportGizmo.ScreenPoint(0, 0, -1, false));
            return;
        }
        Vec3d rel = world.subtract(camera);
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d screenRight = new Vec3d(Math.cos(ry), 0, Math.sin(ry));
        Vec3d screenUp = forward.crossProduct(screenRight).normalize();
        double depth = rel.dotProduct(forward);
        if (depth <= 0.03) {
            cir.setReturnValue(new ViewportGizmo.ScreenPoint(0, 0, depth, false));
            return;
        }

        double fov = 70.0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            try { fov = client.options.getFov().getValue(); }
            catch (RuntimeException ignored) { }
        }
        fov = Math.max(1.0, Math.min(179.0, fov));
        double viewportH = Math.max(1, bottom - top);
        double focal = viewportH / (2.0 * Math.tan(Math.toRadians(fov) / 2.0));
        double cx = (left + right) * 0.5, cy = (top + bottom) * 0.5;
        double x = cx + rel.dotProduct(screenRight) / depth * focal;
        double y = cy - rel.dotProduct(screenUp) / depth * focal;
        boolean visible = x >= left - 120 && x <= right + 120 && y >= top - 120 && y <= bottom + 120;
        cir.setReturnValue(new ViewportGizmo.ScreenPoint(x, y, depth, visible));
    }
}
