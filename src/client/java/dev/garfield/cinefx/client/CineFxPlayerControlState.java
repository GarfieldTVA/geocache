package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend.PlayerControlFrame;
import net.minecraft.client.MinecraftClient;

/** Safe built-in subset of cutscene player control. Specialized backends may override the channel. */
public final class CineFxPlayerControlState {
    private static final long STALE_NANOS = 300_000_000L;
    private static PlayerControlFrame current;
    private static long updatedNanos;
    private static boolean lookCaptured;
    private static float lockedYaw;
    private static float lockedPitch;

    private CineFxPlayerControlState() { }

    static boolean apply(PlayerControlFrame frame) {
        if (frame == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean enteringLookLock = frame.lockLook() && (!active() || current == null || !current.lockLook());
        current = frame;
        updatedNanos = System.nanoTime();
        if (enteringLookLock && client.player != null) {
            lockedYaw = client.player.getYaw();
            lockedPitch = client.player.getPitch();
            lookCaptured = true;
        }
        if (!frame.lockLook()) lookCaptured = false;
        return true;
    }

    public static void tick(MinecraftClient client) {
        if (!active() || current == null || client.player == null) {
            if (!active()) clear();
            return;
        }
        PlayerControlFrame frame = current;
        if (frame.lockMovement() || frame.movementScale() <= 0.001) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
        }
        if (!frame.allowJump()) client.options.jumpKey.setPressed(false);
        if (!frame.allowInventory()) client.options.inventoryKey.setPressed(false);
        if (frame.lockLook() && lookCaptured) {
            client.player.setYaw(lockedYaw);
            client.player.setPitch(lockedPitch);
        }
    }

    public static boolean active() {
        return current != null && System.nanoTime() - updatedNanos <= STALE_NANOS;
    }

    public static double fovDegrees() {
        return active() && current != null ? current.fovDegrees() : -1.0;
    }

    public static boolean hideHud() { return active() && current != null && current.hideHud(); }
    public static boolean hideHand() { return active() && current != null && current.hideHand(); }

    public static void clear() {
        current = null;
        updatedNanos = 0L;
        lookCaptured = false;
    }
}
