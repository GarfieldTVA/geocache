package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EventProgramEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioEventEditorMixin {
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$openEventEditor(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!ctrl || !shift || input.key() != GLFW.GLFW_KEY_E) return;
        if ((searchField != null && searchField.isFocused()) || (valueEditor != null && valueEditor.isFocused())) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(new EventProgramEditorScreen((Screen)(Object)this));
        cir.setReturnValue(true);
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawEventHint(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        context.drawTextWithShadow(client.textRenderer, "Ctrl+Shift+E  Event Studio", 261, 82, 0xFF8FD4B4);
    }
}
