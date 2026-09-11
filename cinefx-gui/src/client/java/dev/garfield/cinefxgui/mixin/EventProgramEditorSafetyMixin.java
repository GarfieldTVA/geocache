package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EventAuthoringModel;
import dev.garfield.cinefxgui.editor.EventProgramEditorScreen;
import dev.garfield.cinefxgui.editor.EventWorkspaceStore;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Construction guards and draft-safe persistence behavior for Event Studio. */
@Mixin(value = EventProgramEditorScreen.class, remap = false)
public abstract class EventProgramEditorSafetyMixin {
    @Shadow @Final private TextFieldWidget[] fields;
    @Shadow private EventAuthoringModel.Workspace workspace;
    @Shadow private String status;
    @Shadow private long statusUntil;

    @Inject(method = "configureFields", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$deferBindingsUntilInit(CallbackInfo ci) {
        if (fields == null || fields.length == 0 || fields[0] == null) ci.cancel();
    }

    @Inject(method = "saveWorkspace", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$saveDraftWithoutPublish(CallbackInfo ci) {
        try {
            EventWorkspaceStore.save(workspace, workspace.sourceName == null ? workspace.name : workspace.sourceName);
            status = "Draft saved";
        } catch (Exception exception) {
            String message = exception.getMessage();
            status = "Save failed: " + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.replace('\n', ' '));
        }
        statusUntil = System.currentTimeMillis() + 5000;
        ci.cancel();
    }
}
