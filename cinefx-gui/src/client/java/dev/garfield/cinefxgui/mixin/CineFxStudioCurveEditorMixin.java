package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.CurveChannels;
import dev.garfield.cinefxgui.editor.CurveEditorScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
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
public abstract class CineFxStudioCurveEditorMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private EditorModel.History history;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$openCurveEditor(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (!ctrl || input.key() != GLFW.GLFW_KEY_E) return;
        if ((searchField != null && searchField.isFocused()) || (valueEditor != null && valueEditor.isFocused())) return;
        EditorModel.Element selected = selectedId == null ? null : project.find(selectedId);
        if (selected == null || CurveChannels.discover(selected).isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(new CurveEditorScreen((Screen)(Object)this, project, selected, preview, history));
        cir.setReturnValue(true);
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawCurveHint(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        EditorModel.Element selected = selectedId == null ? null : project.find(selectedId);
        if (selected == null || CurveChannels.discover(selected).isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        String text = "Ctrl+E  Curves";
        context.drawTextWithShadow(client.textRenderer, text, 261, 68, 0xFFC69AF2);
    }
}
