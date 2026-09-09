package dev.garfield.cinefx.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/** Local authoring controls; no server permission or packet is required to show/hide the editor. */
final class CineFxUltraClientCommands {
    private static boolean registered;

    private CineFxUltraClientCommands() { }

    static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("cinefxeditor")
                        .executes(context -> {
                            boolean next = !CineFxUltraEditorOverlay.enabled();
                            CineFxUltraEditorOverlay.setEnabled(next);
                            context.getSource().sendFeedback(Text.literal("CineFX Ultra editor: " + (next ? "ON" : "OFF")));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("on").executes(context -> {
                            CineFxUltraEditorOverlay.setEnabled(true);
                            context.getSource().sendFeedback(Text.literal("CineFX Ultra editor: ON"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            CineFxUltraEditorOverlay.setEnabled(false);
                            context.getSource().sendFeedback(Text.literal("CineFX Ultra editor: OFF"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("debug").executes(context -> {
                            boolean next = !CineFxDebugOverlay.enabled();
                            CineFxDebugOverlay.setEnabled(next);
                            context.getSource().sendFeedback(Text.literal("CineFX debug overlay: " + (next ? "ON" : "OFF")));
                            return 1;
                        }))
        ));
    }
}
