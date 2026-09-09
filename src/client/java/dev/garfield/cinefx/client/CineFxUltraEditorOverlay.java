package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight in-game timeline/operator view. It intentionally stays HUD-only so servers can
 * use it in production without opening a heavyweight GUI or pausing the event.
 */
public final class CineFxUltraEditorOverlay {
    private static boolean enabled;

    private CineFxUltraEditorOverlay() { }

    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean enabled() { return enabled; }
    public static void clear() { enabled = false; }

    public static void render(DrawContext context, RenderTickCounter ignored) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.textRenderer == null) return;
        double absolute = CineFxRuntime.absoluteGameTick(client);
        List<ActiveScene> scenes = CineFxRuntime.INSTANCE.snapshot(absolute);
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int panelX = 10;
        int panelW = Math.max(260, Math.min(560, width - 20));
        int panelH = Math.min(height - 20, 56 + scenes.size() * 38);
        int panelY = Math.max(10, height - panelH - 10);

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC510131A);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF55E7FF);
        context.drawTextWithShadow(client.textRenderer,
                "CineFX ULTRA EDITOR  quality=" + AdaptiveQualityController.current()
                        + "  frame=" + String.format(Locale.ROOT, "%.1fms", AdaptiveQualityController.averageFrameMs()),
                panelX + 7, panelY + 7, 0xFFEAFBFF);
        context.drawTextWithShadow(client.textRenderer,
                "scenes=" + scenes.size() + "  markers=" + CineFxUltraState.markers().size(),
                panelX + 7, panelY + 19, 0xFF9ECAD2);

        int y = panelY + 34;
        for (ActiveScene scene : scenes) {
            if (y + 30 > panelY + panelH) break;
            double local = scene.localTick(absolute);
            double duration = Math.max(1.0, scene.definition().durationTicks());
            double progress = clamp01(local / duration);
            String label = scene.definition().id().toString();
            context.drawTextWithShadow(client.textRenderer,
                    label + "  " + String.format(Locale.ROOT, "%.1fs / %.1fs", local / 20.0, duration / 20.0),
                    panelX + 7, y, 0xFFFFFFFF);
            int barX = panelX + 7;
            int barY = y + 12;
            int barW = panelW - 14;
            context.fill(barX, barY, barX + barW, barY + 8, 0xFF202B32);
            context.fill(barX, barY, barX + (int)Math.round(barW * progress), barY + 8, 0xFF2C9FB5);
            // second markers make scrubbing and timing discussions much easier while authoring.
            for (double tick = 0; tick <= duration; tick += 20.0) {
                int x = barX + (int)Math.round(barW * tick / duration);
                context.fill(x, barY + 6, x + 1, barY + 8, 0x708CC7CF);
            }
            for (UltraBackend.EditorMarkerFrame marker : CineFxUltraState.markers()) {
                if (marker.sceneInstanceId() != scene.instanceId()) continue;
                double markerProgress = clamp01(marker.sceneTick() / duration);
                int x = barX + (int)Math.round(barW * markerProgress);
                context.fill(x - 1, barY - 2, x + 2, barY + 10, forceAlpha(marker.colorArgb(), 230));
                if (Math.abs(marker.sceneTick() - local) <= 30.0) {
                    context.drawTextWithShadow(client.textRenderer, marker.label(),
                            Math.min(barX + barW - 80, Math.max(barX, x + 4)), barY - 9, marker.colorArgb());
                }
            }
            int playhead = barX + (int)Math.round(barW * progress);
            context.fill(playhead, barY - 3, playhead + 2, barY + 11, 0xFFFFFFFF);
            y += 34;
        }
    }

    private static int forceAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
