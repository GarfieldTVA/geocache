package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.TransformSpaceController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
public abstract class CineFxStudioTransformSpaceMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;

    @Inject(method = "drawViewport", at = @At("HEAD"))
    private void cinefxGui$setTransformContext(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        EditorModel.Element selected = selectedId == null ? null : project.find(selectedId);
        TransformSpaceController.setContext(project, selected, preview.currentTick());
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$drawTransformSpace(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int width = client.getWindow().getScaledWidth();
        int right = width - 364;
        String label = "Transform " + TransformSpaceController.space().name() + " [X]";
        int color = TransformSpaceController.space() == TransformSpaceController.Space.WORLD ? 0xFF82C8F2 : 0xFFFFC86A;
        context.drawTextWithShadow(client.textRenderer, label, Math.max(258, right - client.textRenderer.getWidth(label) - 10), 53, color);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$toggleTransformSpace(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if ((valueEditor != null && valueEditor.isFocused()) || (searchField != null && searchField.isFocused())) return;
        if (input.key() == GLFW.GLFW_KEY_X && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
            TransformSpaceController.toggle();
            cir.setReturnValue(true);
        }
    }
}
