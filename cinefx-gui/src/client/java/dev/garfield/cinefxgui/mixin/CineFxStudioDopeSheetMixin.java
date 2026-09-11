package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.CurveChannels;
import dev.garfield.cinefxgui.editor.DopeSheetScreen;
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

/** Ctrl+T opens a real channel Dope Sheet for the selected element. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioDopeSheetMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$openDopeSheet(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() != GLFW.GLFW_KEY_T || (input.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        EditorModel.Element selected = access.cinefxGui$getProject().find(access.cinefxGui$getSelectedId());
        if (selected == null || CurveChannels.discover(selected).isEmpty()) {
            access.cinefxGui$toast("Selection has no numeric animation channels");
            cir.setReturnValue(true);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new DopeSheetScreen((CineFxStudioScreen)(Object)this,
                access.cinefxGui$getProject(), selected, access.cinefxGui$getPreview(), access.cinefxGui$getHistory()));
        cir.setReturnValue(true);
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawDopeSheetHint(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        CineFxStudioAccessMixin access = (CineFxStudioAccessMixin)(Object)this;
        EditorModel.Element selected = access.cinefxGui$getProject().find(access.cinefxGui$getSelectedId());
        if (selected == null || CurveChannels.discover(selected).isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        int x = 261;
        int y = Math.max(45, client.getWindow().getScaledHeight() - 270);
        context.drawTextWithShadow(client.textRenderer, "Ctrl+T Dope Sheet", x, y, 0xCC9FC4D9);
    }
}
