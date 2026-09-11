package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EventAuthoringModel;
import dev.garfield.cinefxgui.editor.EventProgramEditorScreen;
import dev.garfield.cinefxgui.editor.EventWorkspaceStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Construction guards and honest draft/publish behavior for Event Studio. */
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
            EventWorkspaceStore.validateAndPublish(workspace);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getServer() != null) {
                // Integrated single-player/LAN server lives in the same JVM and therefore sees the
                // same hot-reloaded EventProgramSpec/AssetBundle registries immediately.
                status = "Published to integrated CineFX runtime";
            } else if (client.getNetworkHandler() != null) {
                // Never imply that a client-side editor silently rewrote a dedicated remote server.
                // A future remote publish path must be an explicit, permission-checked C2S protocol.
                status = "Published locally · remote server unchanged";
            } else {
                status = "Published to local CineFX registries";
            }
        } catch (RuntimeException exception) {
            status = "Publish failed: " + compact(exception);
        }
        statusUntil = System.currentTimeMillis() + 6000;
        ci.cancel();
    }

    private static String compact(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ');
    }
}
