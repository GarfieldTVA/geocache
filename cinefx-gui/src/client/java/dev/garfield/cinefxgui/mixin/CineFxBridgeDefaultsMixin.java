package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;
import java.util.Locale;

/** Supplies constructor-safe defaults for fields where zero/blank is not a useful CineFX draft value. */
@Mixin(value = CineFxBridge.class, remap = false)
public abstract class CineFxBridgeDefaultsMixin {
    @Inject(method = "defaultValue", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$advancedDefaults(Type targetType, String name, double durationTicks, int serial,
                                                    CallbackInfoReturnable<JsonElement> cir) {
        if (!(targetType instanceof Class<?> type)) return;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            if (n.equals("maxpoints")) { cir.setReturnValue(new JsonPrimitive(128)); return; }
            if (n.equals("maxparticlesperframe")) { cir.setReturnValue(new JsonPrimitive(512)); return; }
            if (n.equals("maxparticles")) { cir.setReturnValue(new JsonPrimitive(10000)); return; }
            if (n.equals("solveriterations")) { cir.setReturnValue(new JsonPrimitive(8)); return; }
        }

        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            if (n.equals("lifetimeticks")) { cir.setReturnValue(new JsonPrimitive(40.0)); return; }
            if (n.equals("minsampledistance")) { cir.setReturnValue(new JsonPrimitive(0.05)); return; }
            if (n.equals("timescale")) { cir.setReturnValue(new JsonPrimitive(1.0)); return; }
            if (n.equals("tolerance")) { cir.setReturnValue(new JsonPrimitive(0.01)); return; }
            if (n.equals("audienceradius")) { cir.setReturnValue(new JsonPrimitive(64.0)); return; }
        }

        if (type == Identifier.class) {
            if (n.equals("modelid")) { cir.setReturnValue(new JsonPrimitive("minecraft:stone")); return; }
            if (n.equals("clipid")) { cir.setReturnValue(new JsonPrimitive("minecraft:idle")); return; }
            if (n.equals("type")) { cir.setReturnValue(new JsonPrimitive("cinefx_gui:custom")); return; }
        }

        if (type == String.class) {
            if (n.equals("bone") || n.equals("endbone")) { cir.setReturnValue(new JsonPrimitive("root")); return; }
            if (n.equals("name")) { cir.setReturnValue(new JsonPrimitive("value")); return; }
            if (n.equals("profileprefix")) { cir.setReturnValue(new JsonPrimitive("CineFx")); return; }
            if (n.equals("category")) { cir.setReturnValue(new JsonPrimitive("master")); return; }
        }

        if (type == AdvancedEventElement.AttachmentPayload.class) {
            cir.setReturnValue(defaultTextPayload());
        }
    }

    private static JsonObject defaultTextPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("$type", AdvancedEventElement.TextPayload.class.getName());
        payload.addProperty("textTemplate", "CineFX");
        payload.addProperty("fontId", "minecraft:default");
        payload.add("color", colorTrack("#FFFFFFFF"));
        payload.add("scale", scalarTrack(1.0));
        payload.addProperty("billboard", true);
        payload.addProperty("seeThrough", false);
        return payload;
    }

    private static JsonObject scalarTrack(double value) {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "ScalarTrack");
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.addProperty("value", value);
        key.addProperty("easing", "LINEAR");
        keys.add(key);
        track.add("keys", keys);
        return track;
    }

    private static JsonObject colorTrack(String value) {
        JsonObject track = new JsonObject();
        track.addProperty("$kind", "ColorTrack");
        JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject();
        key.addProperty("tick", 0.0);
        key.addProperty("value", value);
        key.addProperty("easing", "LINEAR");
        keys.add(key);
        track.add("keys", keys);
        return track;
    }
}
