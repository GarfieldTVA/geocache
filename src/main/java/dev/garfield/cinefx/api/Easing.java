package dev.garfield.cinefx.api;

/** Common interpolation curves used by every keyframed property. */
public enum Easing {
    LINEAR,
    SMOOTH_STEP,
    SMOOTHER_STEP,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_IN_SINE,
    EASE_OUT_SINE,
    EASE_IN_OUT_SINE,
    EASE_OUT_BACK;

    public double apply(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH_STEP -> t * t * (3.0 - 2.0 * t);
            case SMOOTHER_STEP -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT_QUAD -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1.0 - Math.pow(1.0 - t, 3.0);
            case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case EASE_IN_SINE -> 1.0 - Math.cos((t * Math.PI) / 2.0);
            case EASE_OUT_SINE -> Math.sin((t * Math.PI) / 2.0);
            case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case EASE_OUT_BACK -> {
                double c1 = 1.70158;
                double c3 = c1 + 1.0;
                yield 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
            }
        };
    }
}
