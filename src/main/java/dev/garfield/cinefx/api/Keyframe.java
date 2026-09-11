package dev.garfield.cinefx.api;

import java.util.Objects;

/** A keyframe. easingToNext or bezierToNext controls interpolation leaving this frame. */
public record Keyframe<T>(double tick, T value, Easing easingToNext, CubicBezier bezierToNext) {
    public Keyframe {
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite");
        Objects.requireNonNull(value, "value");
        easingToNext = easingToNext == null ? Easing.LINEAR : easingToNext;
    }

    /** Source-compatible constructor used by existing CineFX integrations. */
    public Keyframe(double tick, T value, Easing easingToNext) {
        this(tick, value, easingToNext, null);
    }

    public static <T> Keyframe<T> at(double tick, T value) {
        return new Keyframe<>(tick, value, Easing.LINEAR, null);
    }

    public static <T> Keyframe<T> at(double tick, T value, Easing easingToNext) {
        return new Keyframe<>(tick, value, easingToNext, null);
    }

    public static <T> Keyframe<T> bezier(double tick, T value, CubicBezier curveToNext) {
        return new Keyframe<>(tick, value, Easing.LINEAR, curveToNext);
    }

    /** Normalized interpolation progress for a raw segment fraction. */
    public double interpolate(double rawFraction) {
        return bezierToNext == null ? easingToNext.apply(rawFraction) : bezierToNext.apply(rawFraction);
    }
}
