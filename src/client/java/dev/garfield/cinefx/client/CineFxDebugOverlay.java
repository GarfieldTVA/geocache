package dev.garfield.cinefx.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Lightweight developer overlay for scrubbing/profiling large events without external tools. */
public final class CineFxDebugOverlay {
    private static boolean enabled;

    private CineFxDebugOverlay() { }

    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean enabled() { return enabled; }

    public static void render(DrawContext context, RenderTickCounter ignored) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.textRenderer == null) return;
        double absolute = CineFxRuntime.absoluteGameTick(client);
        var scenes = CineFxRuntime.INSTANCE.snapshot(absolute);
        int y = 8;
        context.drawTextWithShadow(client.textRenderer,
                "CineFX DEBUG  quality=" + AdaptiveQualityController.current()
                        + "  frame=" + String.format(java.util.Locale.ROOT, "%.1fms", AdaptiveQualityController.averageFrameMs()),
                8, y, 0xFFFFFFFF);
        y += 11;
        context.drawTextWithShadow(client.textRenderer, "active scenes=" + scenes.size(), 8, y, 0xFFE8E8E8);
        y += 11;
        for (ActiveScene scene : scenes) {
            double local = scene.localTick(absolute);
            int active = scene.activeElementsAt(local).size();
            String line = scene.definition().id() + "  t="
                    + String.format(java.util.Locale.ROOT, "%.1f/%.1f", local, scene.definition().durationTicks())
                    + "  active=" + active + "/" + scene.allElementsByPriority().size();
            context.drawTextWithShadow(client.textRenderer, line, 8, y, 0xFFD0FFD0);
            y += 11;
            if (y > context.getScaledWindowHeight() - 20) break;
        }
    }
}
