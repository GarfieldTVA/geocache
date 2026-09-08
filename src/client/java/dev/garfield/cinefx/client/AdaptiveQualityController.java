package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.QualityTier;

/**
 * Tiny frame-time governor. It reacts slowly to avoid oscillation and can be forced by a server/mod UI.
 */
public final class AdaptiveQualityController {
    private static QualityTier current = QualityTier.HIGH;
    private static QualityTier forced;
    private static long previousNanos;
    private static double emaMs = 16.0;
    private static int stableFrames;
    private static int cooldownFrames;

    private AdaptiveQualityController() { }

    public static void onFrame() {
        long now = System.nanoTime();
        if (previousNanos != 0L) {
            double ms = Math.max(0.0, Math.min(250.0, (now - previousNanos) / 1_000_000.0));
            emaMs = emaMs * 0.94 + ms * 0.06;
            stableFrames++;
            if (cooldownFrames > 0) cooldownFrames--;
            if (forced == null && cooldownFrames == 0) adapt();
        }
        previousNanos = now;
    }

    private static void adapt() {
        if (stableFrames < 45) return;
        if (emaMs > 32.0) {
            current = current.lower();
            stableFrames = 0;
            cooldownFrames = 90;
        } else if (emaMs > 23.0 && current.ordinal() > QualityTier.LOW.ordinal()) {
            current = current.lower();
            stableFrames = 0;
            cooldownFrames = 120;
        } else if (emaMs < 13.5 && stableFrames > 420) {
            current = current.higher();
            stableFrames = 0;
            cooldownFrames = 240;
        }
    }

    public static QualityTier current() { return forced == null ? current : forced; }
    public static double averageFrameMs() { return emaMs; }

    public static void force(QualityTier tier) { forced = tier; }
    public static void automatic() { forced = null; }

    public static void reset() {
        current = QualityTier.HIGH;
        forced = null;
        previousNanos = 0L;
        emaMs = 16.0;
        stableFrames = 0;
        cooldownFrames = 0;
    }
}
