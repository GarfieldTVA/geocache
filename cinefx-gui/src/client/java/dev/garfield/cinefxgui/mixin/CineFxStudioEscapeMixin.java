package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Escape behaves like a 3D editor: cancel the interaction being edited before closing the whole
 * Studio. The next mouse release finishes the base screen's internal drag-state cleanup.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioEscapeMixin {
    @Shadow private EditorModel.Project project;
    @Shadow private EditorModel.History history;
    @Shadow private PreviewController preview;
    @Shadow private TextFieldWidget searchField;
    @Shadow private TextFieldWidget valueEditor;
    @Shadow private boolean rightLook;
    @Shadow private SceneManipulator.Session gizmoSession;
    @Shadow private EditorModel.Element dragElement;
    @Shadow private JsonObject dragKeyframe;
    @Shadow private JsonArray dragKeyParent;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$escapeCancelsFirst(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() != GLFW.GLFW_KEY_ESCAPE) return;

        boolean consumed = false;
        if (valueEditor != null && valueEditor.isFocused()) {
            valueEditor.setFocused(false);
            valueEditor.setVisible(false);
            consumed = true;
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            consumed = true;
        }
        if (rightLook) {
            rightLook = false;
            consumed = true;
        }

        if (gizmoSession != null || dragElement != null || dragKeyframe != null) {
            EditorModel.Project restored = history.undo(project);
            if (restored != project) {
                project = restored;
                preview.setProject(restored);
            }
            gizmoSession = null;
            dragElement = null;
            dragKeyframe = null;
            dragKeyParent = null;
            consumed = true;
        }

        if (consumed) cir.setReturnValue(true);
    }
}
