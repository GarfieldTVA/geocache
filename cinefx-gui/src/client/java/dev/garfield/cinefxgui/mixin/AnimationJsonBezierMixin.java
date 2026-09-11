package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.CubicBezier;
import dev.garfield.cinefxgui.editor.AnimationJson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnimationJson.class, remap = false)
public abstract class AnimationJsonBezierMixin {
    @Inject(method = "easedFraction", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$sampleBezier(JsonObject a, JsonObject b, double tick,
                                               CallbackInfoReturnable<Double> cir) {
        if (a == null || b == null || !a.has("bezier") || !a.get("bezier").isJsonObject()) return;
        JsonObject curve = a.getAsJsonObject("bezier");
        try {
            double ta = a.get("tick").getAsDouble();
            double tb = b.get("tick").getAsDouble();
            double raw = tb <= ta ? 1.0 : Math.max(0.0, Math.min(1.0, (tick - ta) / (tb - ta)));
            CubicBezier bezier = new CubicBezier(
                    curve.get("x1").getAsDouble(), curve.get("y1").getAsDouble(),
                    curve.get("x2").getAsDouble(), curve.get("y2").getAsDouble());
            cir.setReturnValue(bezier.apply(raw));
        } catch (RuntimeException ignored) { }
    }
}
