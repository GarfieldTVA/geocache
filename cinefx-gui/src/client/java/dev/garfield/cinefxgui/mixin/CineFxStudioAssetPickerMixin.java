package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.AssetPickerScreen;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ctrl+Shift+P opens the typed ResourceManager/registry browser for the selected element. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioAssetPickerMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$openAssetPicker(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() != GLFW.GLFW_KEY_P
                || (input.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0
                || (input.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) return;
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        EditorModel.Element selected = access.cinefxGui$getProject().find(access.cinefxGui$getSelectedId());
        if (selected == null) {
            access.cinefxGui$toast("Select an element first");
            cir.setReturnValue(true);
            return;
        }
        if (AssetPickerScreen.discover(selected).isEmpty()) {
            access.cinefxGui$toast("Selection has no asset-like fields");
            cir.setReturnValue(true);
            return;
        }
        MinecraftClient.getInstance().setScreen(new AssetPickerScreen((CineFxStudioScreen)(Object)this,
                access.cinefxGui$getProject(), selected, access.cinefxGui$getPreview(), access.cinefxGui$getHistory()));
        cir.setReturnValue(true);
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$assetHint(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        EditorModel.Element selected = access.cinefxGui$getProject().find(access.cinefxGui$getSelectedId());
        if (selected == null || AssetPickerScreen.discover(selected).isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        int y = Math.max(45, client.getWindow().getScaledHeight() - 284);
        context.drawTextWithShadow(client.textRenderer, "Ctrl+Shift+P Assets", 261, y, 0xCC9FC4D9);
    }
}
