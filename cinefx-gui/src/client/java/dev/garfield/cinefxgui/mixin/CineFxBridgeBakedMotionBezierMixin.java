package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.CubicBezier;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefx.api.Keyframe;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefxgui.editor.CineFxBridge;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Mixin(value = CineFxBridge.class, remap = false)
public abstract class CineFxBridgeBakedMotionBezierMixin {
    @Inject(method = "decodeBakedMotion", at = @At("HEAD"), cancellable = true)
    private static void cinefxGui$decodeBakedBezier(JsonObject object, CallbackInfoReturnable<MotionCurve> cir) {
        JsonArray array = object == null ? null : object.getAsJsonArray("keys");
        ArrayList<Keyframe<Transform>> keys = new ArrayList<>();
        if (array != null) {
            for (JsonElement raw : array) {
                if (!raw.isJsonObject()) continue;
                JsonObject key = raw.getAsJsonObject();
                keys.add(new Keyframe<>(number(key,"tick",0), transform(key.get("value")), easing(key), bezier(key)));
            }
        }
        if (keys.isEmpty()) { cir.setReturnValue(MotionCurve.none()); return; }
        keys.sort(Comparator.comparingDouble(Keyframe::tick));
        List<Keyframe<Transform>> fixed = List.copyOf(keys);
        cir.setReturnValue((tick, seed) -> {
            if (tick <= fixed.getFirst().tick()) return fixed.getFirst().value();
            if (tick >= fixed.getLast().tick()) return fixed.getLast().value();
            int low=0,high=fixed.size()-1;
            while(low+1<high){int mid=(low+high)>>>1;if(fixed.get(mid).tick()<=tick)low=mid;else high=mid;}
            Keyframe<Transform> a=fixed.get(low),b=fixed.get(high);
            double span=b.tick()-a.tick();
            double rawFraction=span<=0?1:(tick-a.tick())/span;
            return Transform.lerp(a.value(),b.value(),a.interpolate(rawFraction));
        });
    }

    private static Transform transform(JsonElement raw){
        if(raw==null||!raw.isJsonObject())return Transform.IDENTITY;
        JsonObject o=raw.getAsJsonObject();
        return new Transform(vec(o.get("translation"),Vec3d.ZERO),vec(o.get("rotationDegrees"),Vec3d.ZERO),vec(o.get("scale"),new Vec3d(1,1,1)));
    }
    private static Vec3d vec(JsonElement raw,Vec3d fallback){
        if(raw==null||!raw.isJsonObject())return fallback;JsonObject o=raw.getAsJsonObject();
        try{return new Vec3d(o.get("x").getAsDouble(),o.get("y").getAsDouble(),o.get("z").getAsDouble());}catch(RuntimeException ignored){return fallback;}
    }
    private static Easing easing(JsonObject key){try{return Easing.valueOf(text(key,"easing","LINEAR").toUpperCase(Locale.ROOT));}catch(RuntimeException ignored){return Easing.LINEAR;}}
    private static CubicBezier bezier(JsonObject key){
        if(key==null||!key.has("bezier")||!key.get("bezier").isJsonObject())return null;JsonObject b=key.getAsJsonObject("bezier");
        try{return new CubicBezier(number(b,"x1",.33),number(b,"y1",.33),number(b,"x2",.67),number(b,"y2",.67));}catch(RuntimeException ignored){return null;}
    }
    private static double number(JsonObject o,String k,double fallback){try{return o!=null&&o.has(k)?o.get(k).getAsDouble():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static String text(JsonObject o,String k,String fallback){try{return o!=null&&o.has(k)?o.get(k).getAsString():fallback;}catch(RuntimeException ignored){return fallback;}}
}
