package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyJson;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import dev.garfield.cinefxgui.editor.TransformSpaceController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SceneManipulator.class, remap = false)
public abstract class SceneManipulatorBeginTransformSpaceMixin {
    @Inject(method = "begin", at = @At("HEAD"))
    private static void cinefxGui$prepareTransformContext(EditorModel.Element element, SceneManipulator.Tool tool,
                                                           SceneManipulator.Axis axis, double localTick,
                                                           CallbackInfoReturnable<SceneManipulator.Session> cir) {
        EditorModel.Project project = HierarchyJson.project();
        if (project != null && element != null) {
            TransformSpaceController.setContext(project, element, element.startTick() + Math.max(0.0, localTick));
        }
    }
}
