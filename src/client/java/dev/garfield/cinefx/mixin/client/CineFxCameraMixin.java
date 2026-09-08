package dev.garfield.cinefx.mixin.client;

import dev.garfield.cinefx.client.CineFxCameraController;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies CineFX camera rigs at the end of vanilla Camera.update(). */
@Mixin(Camera.class)
public abstract class CineFxCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void cinefx$applySceneCamera(CallbackInfo ci) {
        CineFxCameraController.Sample sample = CineFxCameraController.sample((Camera) (Object) this);
        if (sample == null) return;
        setPos(sample.position());
        setRotation(sample.yaw(), sample.pitch());
    }
}
