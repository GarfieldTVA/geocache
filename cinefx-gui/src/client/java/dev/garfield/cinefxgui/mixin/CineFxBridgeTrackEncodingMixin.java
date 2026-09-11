package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.ColorTrack;
import dev.garfield.cinefx.api.CubicBezier;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.ScalarTrack;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/** Stops CineFX GUI from reflecting private track fields now that the public API exposes keyframes. */
@Mixin(value = CineFxBridge.class, remap = false)
public abstract class CineFxBridgeTrackEncodingMixin {
    @Inject(method = "encodeScalarTrack", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$encodeScalar(ScalarTrack track, double durationTicks, CallbackInfoReturnable<JsonObject> cir) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "ScalarTrack");
        JsonArray keys = new JsonArray();
        for (Keyframe<Double> key : track.keyframes()) {
            JsonObject item = new JsonObject();
            item.addProperty("tick", key.tick());
            item.addProperty("value", key.value());
            item.addProperty("easing", key.easingToNext().name());
            addBezier(item, key.bezierToNext());
            keys.add(item);
        }
        object.add("keys", keys);
        cir.setReturnValue(object);
    }

    @Inject(method = "encodeColorTrack", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$encodeColor(ColorTrack track, double durationTicks, CallbackInfoReturnable<JsonObject> cir) {
        JsonObject object = new JsonObject();
        object.addProperty("$kind", "ColorTrack");
        JsonArray keys = new JsonArray();
        for (Keyframe<Integer> key : track.keyframes()) {
            JsonObject item = new JsonObject();
            item.addProperty("tick", key.tick());
            item.addProperty("value", String.format(Locale.ROOT, "#%08X", key.value()));
            item.addProperty("easing", key.easingToNext().name());
            addBezier(item, key.bezierToNext());
            keys.add(item);
        }
        object.add("keys", keys);
        cir.setReturnValue(object);
    }

    private static void addBezier(JsonObject key, CubicBezier curve) {
        if (curve == null) return;
        JsonObject value = new JsonObject();
        value.addProperty("x1", curve.x1()); value.addProperty("y1", curve.y1());
        value.addProperty("x2", curve.x2()); value.addProperty("y2", curve.y2());
        key.add("bezier", value);
    }
}
