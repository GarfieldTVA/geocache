package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonObject;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyJson;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.TransformSpaceController;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps SceneManipulator's JSON vectors compatible while upgrading its transform-space math. */
public final class SceneManipulatorTransformSpaceMixin {
    private SceneManipulatorTransformSpaceMixin() { }

    @Mixin(value = SceneManipulator.class, remap = false)
    public abstract static class BeginContext {
        @Inject(method = "begin", at = @At("HEAD"))
        private static void cinefxGui$prepareTransformContext(EditorModel.Element element, SceneManipulator.Tool tool,
                                                               SceneManipulator.Axis axis, double localTick,
                                                               CallbackInfoReturnable<SceneManipulator.Session> cir) {
            EditorModel.Project project = HierarchyJson.project();
            if (project != null && element != null) {
                TransformSpaceController.setContext(project, element, element.startTick() + Math.max(0.0, localTick));
            }
        }
    }

    @Mixin(value = SceneManipulator.Session.class, remap = false)
    public abstract static class SessionMath {
        @Shadow @Final private SceneManipulator.Tool tool;
        @Shadow @Final private SceneManipulator.Axis axis;
        @Shadow @Final private JsonObject vector;
        @Shadow @Final private Vec3d original;

        @Inject(method = "applyAxis", at = @At("HEAD"), cancellable = true)
        private void cinefxGui$applyHierarchyAxis(double amount, CallbackInfo ci) {
            if (TransformSpaceController.applyAxis(tool, axis, vector, original, amount)) ci.cancel();
        }

        @Inject(method = "applyPlane", at = @At("HEAD"), cancellable = true)
        private void cinefxGui$applyHierarchyPlane(Vec3d delta, CallbackInfo ci) {
            if (TransformSpaceController.applyPlane(tool, vector, original, delta)) ci.cancel();
        }
    }
}
