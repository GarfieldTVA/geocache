package dev.garfield.cinefx.api;

import java.util.Objects;

/** A keyframe. easingToNext controls interpolation leaving this frame. */
public record Keyframe<T>(double tick, T value, Easing easingToNext) {
    public Keyframe {
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite");
        Objects.requireNonNull(value, "value");
        easingToNext = easingToNext == null ? Easing.LINEAR : easingToNext;
    }

    public static <T> Keyframe<T> at(double tick, T value) {
        return new Keyframe<>(tick, value, Easing.LINEAR);
    }

    public static <T> Keyframe<T> at(double tick, T value, Easing easingToNext) {
        return new Keyframe<>(tick, value, easingToNext);
    }
}
