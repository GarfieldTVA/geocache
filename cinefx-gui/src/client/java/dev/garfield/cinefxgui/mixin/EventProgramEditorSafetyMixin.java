package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EventAuthoringModel;
import dev.garfield.cinefxgui.editor.EventProgramEditorScreen;
import dev.garfield.cinefxgui.editor.EventStudioRemotePublisher;
import dev.garfield.cinefxgui.editor.EventWorkspaceStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Construction guards and explicit draft/local/remote publish behavior for Event Studio. */
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
            status = "Save failed: " + compact(exception);
        }
        statusUntil = System.currentTimeMillis() + 5000;
        ci.cancel();
    }

    @Inject(method = "publishWorkspace", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$publishWithRuntimeScope(CallbackInfo ci) {
        try {
            // Always keep the local editor/runtime registries in sync first. Validation is complete
            // before mutation, so a rejected remote request still leaves a valid local preview.
            EventWorkspaceStore.validateAndPublish(workspace);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getServer() != null) {
                status = "Published to integrated CineFX runtime";
                statusUntil = System.currentTimeMillis() + 6000;
            } else if (client.getNetworkHandler() != null) {
                if (!EventStudioRemotePublisher.canPublishRemote()) {
                    status = "Published locally · server has no CineFX authoring endpoint";
                    statusUntil = System.currentTimeMillis() + 6000;
                } else {
                    status = "Publishing to remote CineFX server…";
                    statusUntil = System.currentTimeMillis() + 10000;
                    EventStudioRemotePublisher.publish(workspace, true, result -> {
                        status = result.success()
                                ? result.message()
                                : "Remote publish rejected: " + result.message();
                        statusUntil = System.currentTimeMillis() + 7000;
                    });
                }
            } else {
                status = "Published to local CineFX registries";
                statusUntil = System.currentTimeMillis() + 6000;
            }
        } catch (RuntimeException exception) {
            status = "Publish failed: " + compact(exception);
            statusUntil = System.currentTimeMillis() + 6000;
        }
        ci.cancel();
    }

    private static String compact(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ');
    }
}
