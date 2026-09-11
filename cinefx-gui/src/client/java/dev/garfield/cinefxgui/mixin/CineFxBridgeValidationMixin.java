package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxBridge;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.ProjectDiagnostics;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/** Adds editor-level structural diagnostics to the normal CineFX constructor validation. */
@Mixin(value = CineFxBridge.class, remap = false)
public abstract class CineFxBridgeValidationMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true)
    private static void cinefxGui$validateProject(EditorModel.Project project, Identifier sceneId, boolean includeAudio,
                                                   CallbackInfoReturnable<CineFxBridge.BuildResult> cir) {
        CineFxBridge.BuildResult original = cir.getReturnValue();
        if (original == null) return;
        ArrayList<CineFxBridge.BuildError> errors = new ArrayList<>(original.errors());
        errors.addAll(ProjectDiagnostics.validate(project));
        cir.setReturnValue(new CineFxBridge.BuildResult(original.scene(), java.util.List.copyOf(errors)));
    }
}
