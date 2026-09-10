package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyJson;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Makes Studio markers/focus/path previews follow the same parent graph composition as CineFX runtime. */
@Mixin(value = SceneManipulator.class, remap = false)
public abstract class SceneManipulatorHierarchyMixin {
    @Inject(method = "pivotLocal", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$hierarchyPivot(EditorModel.Element element, double localTick,
                                                  CallbackInfoReturnable<Vec3d> cir) {
        EditorModel.Project project = HierarchyJson.project();
        if (project == null || element == null) return;
        Vec3d resolved = HierarchyJson.worldRelativePivot(project, element, element.startTick() + localTick);
        if (resolved != null) cir.setReturnValue(resolved);
    }

    @Inject(method = "animationPathLocal", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$hierarchyPath(EditorModel.Element element, CallbackInfoReturnable<List<Vec3d>> cir) {
        EditorModel.Project project = HierarchyJson.project();
        if (project == null || element == null) return;
        List<Vec3d> resolved = HierarchyJson.resolvedAnimationPath(project, element);
        if (resolved.size() > 1) cir.setReturnValue(resolved);
    }
}
