package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PresetStore;
import dev.garfield.cinefxgui.editor.PreviewController;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fast recovery path for rolling preset backups. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioBackupMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private EditorModel.History history;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$restoreBackup(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!ctrl || !shift || input.key() != GLFW.GLFW_KEY_B) return;
        if (project == null || project.sourcePreset == null || project.sourcePreset.isBlank()) {
            cir.setReturnValue(true);
            return;
        }
        try {
            EditorModel.Project restored = PresetStore.loadLatestBackup(project.sourcePreset);
            if (restored != null) {
                history.checkpoint(project);
                project = restored;
                preview.setProject(project);
            }
        } catch (Exception ignored) { }
        cir.setReturnValue(true);
    }
}
