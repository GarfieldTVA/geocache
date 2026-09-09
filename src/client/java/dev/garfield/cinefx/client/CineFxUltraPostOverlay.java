package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.HashMap;
import java.util.Map;

/** Renderer-safe approximation of Ultra post FX. Shader backends can override this channel. */
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
            if (alpha > 0) {
                tint = frame.tintArgb();
                tintWeight = Math.max(tintWeight, alpha / 255.0);
            }
        }

        if (tintWeight > 0.001) context.fill(0, 0, width, height, withAlpha(tint, (int)(90 * tintWeight)));

        double underwater = fx[UltraEventElement.PostEffect.UNDERWATER_REFRACTION.ordinal()];
        if (underwater > 0.001) context.fill(0, 0, width, height, argb((int)(42 * underwater), 20, 90, 145));

        double heat = fx[UltraEventElement.PostEffect.HEAT_HAZE.ordinal()];
        if (heat > 0.001) context.fill(0, 0, width, height, argb((int)(22 * heat), 255, 118, 45));

        double bloom = fx[UltraEventElement.PostEffect.BLOOM.ordinal()];
        if (bloom > 0.001) context.fill(0, 0, width, height, argb((int)(28 * bloom), 245, 250, 255));

        double motion = fx[UltraEventElement.PostEffect.MOTION_BLUR.ordinal()];
        if (motion > 0.001) {
            int a = (int)(16 * motion);
            context.fill(0, 0, width, height, argb(a, 210, 225, 255));
        }

        double chroma = fx[UltraEventElement.PostEffect.CHROMATIC_ABERRATION.ordinal()];
        if (chroma > 0.001) {
            int band = Math.max(1, (int)(width * 0.012 * chroma));
            context.fill(0, 0, band, height, argb((int)(90 * chroma), 255, 35, 70));
            context.fill(width - band, 0, width, height, argb((int)(90 * chroma), 30, 225, 255));
        }

        double dof = fx[UltraEventElement.PostEffect.DEPTH_OF_FIELD.ordinal()];
        if (dof > 0.001) {
            int edge = Math.max(1, (int)(height * 0.12 * dof));
            context.fill(0, 0, width, edge, argb((int)(42 * dof), 0, 0, 0));
            context.fill(0, height - edge, width, height, argb((int)(42 * dof), 0, 0, 0));
        }

        double vignette = fx[UltraEventElement.PostEffect.VIGNETTE.ordinal()];
        if (vignette > 0.001) drawVignette(context, width, height, vignette);

        double lens = fx[UltraEventElement.PostEffect.LENS_DIRT.ordinal()];
        if (lens > 0.001) drawLensDirt(context, width, height, lens);

        double grain = fx[UltraEventElement.PostEffect.FILM_GRAIN.ordinal()];
        if (grain > 0.001) drawGrain(context, width, height, grain, now);

        double glitch = fx[UltraEventElement.PostEffect.GLITCH.ordinal()];
        if (glitch > 0.001) drawGlitch(context, width, height, glitch, now);

        double transition = fx[UltraEventElement.PostEffect.TRANSITION.ordinal()];
        if (transition > 0.001) context.fill(0, 0, width, height, argb((int)(255 * transition), 0, 0, 0));
    }

    static synchronized void clear() { ACTIVE.clear(); }

    private static void drawVignette(DrawContext context, int width, int height, double amount) {
        int bands = 8;
        int maxX = Math.max(1, (int)(width * 0.12 * amount));
        int maxY = Math.max(1, (int)(height * 0.14 * amount));
        for (int i = 0; i < bands; i++) {
            double t = (i + 1.0) / bands;
            int alpha = (int)(58 * amount * t * t);
            int x = maxX * (bands - i) / bands;
            int y = maxY * (bands - i) / bands;
            context.fill(0, 0, x, height, argb(alpha, 0, 0, 0));
            context.fill(width - x, 0, width, height, argb(alpha, 0, 0, 0));
            context.fill(0, 0, width, y, argb(alpha, 0, 0, 0));
            context.fill(0, height - y, width, height, argb(alpha, 0, 0, 0));
        }
    }

    private static void drawLensDirt(DrawContext context, int width, int height, double amount) {
        int count = 9;
        for (int i = 0; i < count; i++) {
            long h = hash(i * 0x9E3779B97F4A7C15L);
            int x = (int)Math.floorMod(h, Math.max(1, width));
            int y = (int)Math.floorMod(h >>> 17, Math.max(1, height));
            int r = 2 + (int)Math.floorMod(h >>> 29, 8);
            context.fill(Math.max(0, x - r), Math.max(0, y - r), Math.min(width, x + r), Math.min(height, y + r),
                    argb((int)(20 * amount), 255, 235, 190));
        }
    }

    private static void drawGrain(DrawContext context, int width, int height, double amount, long now) {
        long frame = now / 33_000_000L;
        int count = Math.min(180, Math.max(20, width * height / 15000));
        for (int i = 0; i < count; i++) {
            long h = hash(frame * 1315423911L + i * 2654435761L);
            int x = (int)Math.floorMod(h, Math.max(1, width));
            int y = (int)Math.floorMod(h >>> 21, Math.max(1, height));
            int lum = ((h >>> 42) & 1L) == 0L ? 255 : 0;
            context.fill(x, y, Math.min(width, x + 1), Math.min(height, y + 1), argb((int)(34 * amount), lum, lum, lum));
        }
    }

    private static void drawGlitch(DrawContext context, int width, int height, double amount, long now) {
        long frame = now / 55_000_000L;
        int count = 2 + (int)(8 * amount);
        for (int i = 0; i < count; i++) {
            long h = hash(frame * 31 + i * 977);
            int y = (int)Math.floorMod(h, Math.max(1, height));
            int barH = 1 + (int)Math.floorMod(h >>> 12, Math.max(2, (int)(8 * amount) + 2));
            int x = (int)Math.floorMod(h >>> 27, Math.max(1, width / 3 + 1));
            int length = width / 3 + (int)Math.floorMod(h >>> 39, Math.max(1, width * 2 / 3));
            int color = ((i & 1) == 0) ? argb((int)(55 * amount), 255, 35, 90) : argb((int)(55 * amount), 25, 235, 255);
            context.fill(x, y, Math.min(width, x + length), Math.min(height, y + barH), color);
        }
    }

    private static int withAlpha(int argb, int alpha) { return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24); }
    private static int argb(int a, int r, int g, int b) { return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b); }
    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static long hash(long x) { x ^= x >>> 33; x *= 0xff51afd7ed558ccdl; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53l; return x ^ (x >>> 33); }
    private record TimedFrame(UltraBackend.PostProcessFrame frame, long seen) { }
}
