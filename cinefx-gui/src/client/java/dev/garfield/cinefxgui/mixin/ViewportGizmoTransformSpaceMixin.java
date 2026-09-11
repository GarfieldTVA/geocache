package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.HierarchyJson;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.TransformSpaceController;
import dev.garfield.cinefxgui.editor.ViewportGizmo;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ViewportGizmo.class, remap = false)
public abstract class ViewportGizmoTransformSpaceMixin {
    @Inject(method = "layout", at = @At("RETURN"), cancellable = true)
    private static void cinefxGui$orientLayout(Vec3d pivot, Vec3d camera, float yaw, float pitch,
                                                int left, int top, int right, int bottom,
                                                CallbackInfoReturnable<ViewportGizmo.Layout> cir) {
        ViewportGizmo.Layout base = cir.getReturnValue();
        if (base == null) return;
        HierarchyJson.Axes axes = TransformSpaceController.gizmoAxes();
        double scale = base.worldScale();
        cir.setReturnValue(new ViewportGizmo.Layout(
                base.center(),
                ViewportGizmo.project(pivot.add(axes.x().multiply(scale)), camera, yaw, pitch, left, top, right, bottom),
                ViewportGizmo.project(pivot.add(axes.y().multiply(scale)), camera, yaw, pitch, left, top, right, bottom),
                ViewportGizmo.project(pivot.add(axes.z().multiply(scale)), camera, yaw, pitch, left, top, right, bottom),
                scale, pivot, camera, yaw, pitch, left, top, right, bottom));
    }

    @Inject(method = "ringPoint", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$orientedRing(ViewportGizmo.Layout layout, SceneManipulator.Axis axis, double angle,
                                                CallbackInfoReturnable<ViewportGizmo.ScreenPoint> cir) {
        if (layout == null) return;
        HierarchyJson.Axes axes = TransformSpaceController.gizmoAxes();
        Vec3d a;
        Vec3d b;
        switch (axis) {
            case X -> { a = axes.y(); b = axes.z(); }
            case Y -> { a = axes.x(); b = axes.z(); }
            case Z -> { a = axes.x(); b = axes.y(); }
            case CENTER -> { cir.setReturnValue(layout.center()); return; }
            default -> { return; }
        }
        double c = Math.cos(angle) * layout.worldScale();
        double s = Math.sin(angle) * layout.worldScale();
        Vec3d point = layout.pivot().add(a.multiply(c)).add(b.multiply(s));
        cir.setReturnValue(ViewportGizmo.project(point, layout.camera(), layout.yaw(), layout.pitch(),
                layout.left(), layout.top(), layout.right(), layout.bottom()));
    }

    @Inject(method = "rotationDirection", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$orientedRotationDirection(ViewportGizmo.Layout layout, SceneManipulator.Axis axis,
                                                             CallbackInfoReturnable<Double> cir) {
        if (layout == null || axis == SceneManipulator.Axis.CENTER) return;
        HierarchyJson.Axes axes = TransformSpaceController.gizmoAxes();
        Vec3d normal = switch (axis) {
            case X -> axes.x();
            case Y -> axes.y();
            case Z -> axes.z();
            case CENTER -> new Vec3d(0, 1, 0);
        };
        Vec3d toCamera = layout.camera().subtract(layout.pivot());
        if (toCamera.lengthSquared() < 1.0e-9) return;
        double dot = normal.dotProduct(toCamera.normalize());
        cir.setReturnValue(Math.abs(dot) < 0.035 ? 1.0 : Math.signum(dot));
    }
}
