package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonObject;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.TransformSpaceController;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SceneManipulator.Session.class, remap = false)
public abstract class SceneManipulatorSessionTransformSpaceMixin {
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
