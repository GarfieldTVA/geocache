package dev.garfield.cinefx.mixin.client;

import dev.garfield.cinefx.client.CineFxDebugOverlay;
import dev.garfield.cinefx.client.CineFxHudRenderer;
import dev.garfield.cinefx.client.CineFxPlayerControlState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla HUD during a cutscene while preserving CineFX overlays/subtitles/debug layers. */
@Mixin(InGameHud.class)
public abstract class CineFxHudControlMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void cinefx$hideVanillaHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!CineFxPlayerControlState.hideHud()) return;
        CineFxHudRenderer.render(context, tickCounter);
        CineFxDebugOverlay.render(context, tickCounter);
        ci.cancel();
    }
}
