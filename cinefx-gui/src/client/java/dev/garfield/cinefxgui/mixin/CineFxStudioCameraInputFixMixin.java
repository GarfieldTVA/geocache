package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps RMB fly-navigation reliable even when the cursor is over a CineFX object.
 *
 * The viewport context-menu UX added an object RMB handler that consumed the click and cleared
 * rightLook. That made WASD appear broken whenever an object happened to be under the cursor.
 * A held RMB must always win for navigation; a short RMB click may still leave the context menu open.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 2000)
public abstract class CineFxStudioCameraInputFixMixin {
    private static final int LEFT_W = 252;
    private static final int RIGHT_W = 364;
    private static final int TOP_H = 32;
    private static final int TIMELINE_H = 244;

    @Shadow private boolean rightLook;

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void cinefxGui$alwaysEnableRmbFly(Click click, boolean doubled,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        double x = click.x();
        double y = click.y();
        int right = client.getWindow().getScaledWidth() - RIGHT_W;
        int bottom = client.getWindow().getScaledHeight() - TIMELINE_H;
        if (x >= LEFT_W && x < right && y >= TOP_H && y < bottom) rightLook = true;
    }

    /**
     * While RMB fly mode is held, movement keys are input, not editor shortcuts. In particular S
     * must not switch the gizmo to Scale while the user is trying to move backwards.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$consumeFlyKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!rightLook) return;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S
                || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E) {
            cir.setReturnValue(true);
        }
    }
}
