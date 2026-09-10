package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonObject;
import dev.garfield.cinefxgui.editor.AnimationJson;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes gizmo-created keys sample the same eased value that CineFX renders at that tick.
 * The underlying CineFX runtime remains untouched; only editor-side JSON sampling is replaced.
 */
@Mixin(value = SceneManipulator.class, remap = false)
public abstract class SceneManipulatorSamplingMixin {
    @Inject(method = "sampleTransformValue", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$sampleTransform(JsonObject track, double tick, CallbackInfoReturnable<JsonObject> cir) {
        cir.setReturnValue(AnimationJson.sampleTransform(track, tick));
    }

    @Inject(method = "sampleVec3TrackValue", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$sampleVec3(JsonObject track, double tick, CallbackInfoReturnable<JsonObject> cir) {
        cir.setReturnValue(AnimationJson.sampleVec3(track, tick));
    }
}
