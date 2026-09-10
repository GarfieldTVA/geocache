package dev.garfield.cinefx.mixin.client;

import dev.garfield.cinefx.client.CineFxCameraController;
import dev.garfield.cinefx.client.CineFxPlayerControlState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Built-in cinematic FOV and first-person hand control for Minecraft 1.21.11. */
@Mixin(GameRenderer.class)
public abstract class CineFxFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true, require = 0)
    private void cinefx$overrideFov(Camera camera, float tickDelta, boolean useFovSetting,
                                    CallbackInfoReturnable<Double> cir) {
        double ultra = CineFxCameraController.ultraFovDegrees();
        if (ultra > 1.0 && Double.isFinite(ultra)) {
            cir.setReturnValue(ultra);
            return;
        }
        double requested = CineFxPlayerControlState.fovDegrees();
        if (requested > 1.0 && Double.isFinite(requested)) cir.setReturnValue(requested);
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void cinefx$hideHand(CallbackInfo ci) {
        if (CineFxPlayerControlState.hideHand() || CineFxCameraController.hideHand()) ci.cancel();
    }
}
