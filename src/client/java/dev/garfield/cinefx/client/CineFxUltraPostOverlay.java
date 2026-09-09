package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.HashMap;
import java.util.Map;

/**
 * Renderer-safe approximation of Ultra post FX.
 *
 * The native path deliberately avoids fake rectangular DOF/chromatic/glitch overlays: those looked
 * like broken UI rather than framebuffer effects. Shader backends can still implement every Ultra
 * post channel. The built-in fallback keeps only effects that remain visually coherent in HUD-space.
 */
final class CineFxUltraPostOverlay {
    private static final long STALE_NANOS = 350_000_000L;
    private static final Map<String, TimedFrame> ACTIVE = new HashMap<>();

    private CineFxUltraPostOverlay() { }

    static synchronized boolean accept(UltraBackend.PostProcessFrame frame) {
        ACTIVE.put(frame.sceneInstanceId() + ":" + frame.elementKey(), new TimedFrame(frame, System.nanoTime()));
        return true;
    }

    static synchronized void render(DrawContext context, RenderTickCounter ignored) {
        long now = System.nanoTime();
        ACTIVE.entrySet().removeIf(entry -> now - entry.getValue().seen > STALE_NANOS);
        if (ACTIVE.isEmpty()) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        double[] fx = new double[UltraEventElement.PostEffect.values().length];
        int tint = 0;
        double tintWeight = 0.0;

        for (TimedFrame timed : ACTIVE.values()) {
            UltraBackend.PostProcessFrame frame = timed.frame;
            for (Map.Entry<UltraEventElement.PostEffect, Double> e : frame.effects().entrySet()) {
                fx[e.getKey().ordinal()] = Math.max(fx[e.getKey().ordinal()], clamp01(e.getValue()));
            }
            int alpha = (frame.tintArgb() >>> 24) & 255;
            if (alpha > 0 && alpha / 255.0 >= tintWeight) {
                tint = frame.tintArgb();
                tintWeight = alpha / 255.0;
            }
        }

        // Safe colour washes. Keep them intentionally subtle: this is not a shader framebuffer pass.
        if (tintWeight > 0.001) {
            context.fill(0, 0, width, height, withAlpha(tint, (int)(54 * tintWeight)));
        }

        double underwater = fx[UltraEventElement.PostEffect.UNDERWATER_REFRACTION.ordinal()];
        if (underwater > 0.001) {
            context.fill(0, 0, width, height, argb((int)(24 * underwater), 15, 82, 132));
        }

        double heat = fx[UltraEventElement.PostEffect.HEAT_HAZE.ordinal()];
        if (heat > 0.001) {
            context.fill(0, 0, width, height, argb((int)(12 * heat), 255, 115, 42));
        }

        double bloom = fx[UltraEventElement.PostEffect.BLOOM.ordinal()];
        if (bloom > 0.001) {
            context.fill(0, 0, width, height, argb((int)(10 * bloom), 245, 250, 255));
        }

        double vignette = fx[UltraEventElement.PostEffect.VIGNETTE.ordinal()];
        if (vignette > 0.001) drawVignette(context, width, height, vignette);

        // TRANSITION is the only full-opacity screen effect in the native fallback and is intentional.
        double transition = fx[UltraEventElement.PostEffect.TRANSITION.ordinal()];
        if (transition > 0.001) {
            context.fill(0, 0, width, height, argb((int)(255 * clamp01(transition)), 0, 0, 0));
        }

        // DEPTH_OF_FIELD, MOTION_BLUR, CHROMATIC_ABERRATION, FILM_GRAIN, LENS_DIRT and GLITCH
        // require actual framebuffer/shader work to look correct. They remain available to UltraBackend
        // implementations, but the native fallback intentionally does not fake them with HUD rectangles.
    }

    static synchronized void clear() { ACTIVE.clear(); }

    private static void drawVignette(DrawContext context, int width, int height, double amount) {
        int bands = 7;
        int maxX = Math.max(1, (int)(width * 0.08 * amount));
        int maxY = Math.max(1, (int)(height * 0.10 * amount));
        for (int i = 0; i < bands; i++) {
            double t = (i + 1.0) / bands;
            int alpha = (int)(38 * amount * t * t);
            int x = maxX * (bands - i) / bands;
            int y = maxY * (bands - i) / bands;
            context.fill(0, 0, x, height, argb(alpha, 0, 0, 0));
            context.fill(width - x, 0, width, height, argb(alpha, 0, 0, 0));
            context.fill(0, 0, width, y, argb(alpha, 0, 0, 0));
            context.fill(0, height - y, width, height, argb(alpha, 0, 0, 0));
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private record TimedFrame(UltraBackend.PostProcessFrame frame, long seen) { }
}
