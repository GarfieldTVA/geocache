package dev.garfield.cinefx.api;

/**
 * CSS-style cubic Bezier timing function. X control points are constrained to [0,1] so time stays
 * monotonic; Y may overshoot to support anticipation/back style motion. The curve maps normalized
 * segment time x to normalized interpolation progress y.
 */
public record CubicBezier(double x1, double y1, double x2, double y2) {
    public CubicBezier {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) {
            throw new IllegalArgumentException("Bezier control points must be finite");
        }
        if (x1 < 0.0 || x1 > 1.0 || x2 < 0.0 || x2 > 1.0) {
            throw new IllegalArgumentException("Bezier x control points must be in [0,1]");
        }
    }

    public static CubicBezier linear() { return new CubicBezier(0.0, 0.0, 1.0, 1.0); }
    public static CubicBezier ease() { return new CubicBezier(0.25, 0.1, 0.25, 1.0); }
    public static CubicBezier easeIn() { return new CubicBezier(0.42, 0.0, 1.0, 1.0); }
    public static CubicBezier easeOut() { return new CubicBezier(0.0, 0.0, 0.58, 1.0); }
    public static CubicBezier easeInOut() { return new CubicBezier(0.42, 0.0, 0.58, 1.0); }

    public double apply(double progress) {
        double x = clamp01(progress);
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;

        // Newton converges quickly for normal editor curves. Keep a bracket and fall back to
        // bisection around very flat handles so pathological but valid curves stay deterministic.
        double t = x;
        double low = 0.0, high = 1.0;
        for (int i = 0; i < 8; i++) {
            double current = coordinate(t, x1, x2);
            double error = current - x;
            if (Math.abs(error) < 1.0e-7) return coordinate(t, y1, y2);
            if (error > 0.0) high = t; else low = t;
            double derivative = derivative(t, x1, x2);
            double next = Math.abs(derivative) > 1.0e-7 ? t - error / derivative : (low + high) * 0.5;
            if (!(next > low && next < high) || !Double.isFinite(next)) next = (low + high) * 0.5;
            t = next;
        }
        for (int i = 0; i < 18; i++) {
            t = (low + high) * 0.5;
            double current = coordinate(t, x1, x2);
            if (current > x) high = t; else low = t;
        }
        return coordinate((low + high) * 0.5, y1, y2);
    }

    private static double coordinate(double t, double a, double b) {
        double u = 1.0 - t;
        return 3.0 * u * u * t * a + 3.0 * u * t * t * b + t * t * t;
    }

    private static double derivative(double t, double a, double b) {
        double u = 1.0 - t;
        return 3.0 * u * u * a + 6.0 * u * t * (b - a) + 3.0 * t * t * (1.0 - b);
    }

    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
