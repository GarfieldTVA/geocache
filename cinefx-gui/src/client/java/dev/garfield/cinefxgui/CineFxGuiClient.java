package dev.garfield.cinefxgui;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EventStudioRemotePublisher;
import dev.garfield.cinefxgui.mixin.CineFxStudioAccessMixin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;

public final class CineFxGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "cinefx_gui";
    private static KeyBinding openEditor;
    private static CineFxStudioScreen persistentStudio;
    private static ClientWorld studioWorld;

    @Override
    public void onInitializeClient() {
        EventStudioRemotePublisher.initializeClient();
        openEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cinefx_gui.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // A Studio session belongs to one loaded world. Closing only the GUI keeps the authored
            // preview alive; changing/leaving worlds performs the real runtime cleanup.
            if (client.world != studioWorld) {
                if (persistentStudio != null) {
                    ((CineFxStudioAccessMixin)(Object)persistentStudio).cinefxGui$getPreview().shutdown();
                    persistentStudio = null;
                }
                studioWorld = client.world;
            }

            while (openEditor.wasPressed()) {
                if (client.currentScreen instanceof CineFxStudioScreen) {
                    client.setScreen(null);
                } else if (client.world != null) {
                    if (persistentStudio == null) persistentStudio = new CineFxStudioScreen();
                    client.setScreen(persistentStudio);
                }
            }
        });
    }
}
