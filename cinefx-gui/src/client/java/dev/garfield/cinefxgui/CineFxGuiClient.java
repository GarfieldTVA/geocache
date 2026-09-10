package dev.garfield.cinefxgui;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class CineFxGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "cinefx_gui";
    private static KeyBinding openEditor;

    @Override
    public void onInitializeClient() {
        openEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cinefx_gui.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditor.wasPressed()) {
                if (client.currentScreen instanceof CineFxStudioScreen) client.setScreen(null);
                else if (client.world != null) client.setScreen(new CineFxStudioScreen());
            }
        });
    }
}
