package dev.garfield.cinefx.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Immutable scalar keyframe track for alpha, grade strength, scale and similar properties. */
public final class ScalarTrack {
    private final List<Keyframe<Double>> keys;

    private ScalarTrack(List<Keyframe<Double>> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("A scalar track needs at least one keyframe");
        ArrayList<Keyframe<Double>> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Keyframe::tick));
        this.keys = List.copyOf(sorted);
    }

    @SafeVarargs
    public static ScalarTrack of(Keyframe<Double>... keys) {
        return new ScalarTrack(Arrays.asList(keys));
    }

    public static ScalarTrack constant(double value) {
        return of(Keyframe.at(0.0, value));
    }

    /** Immutable ordered keyframe view for tooling, serializers and editors. */
    public List<Keyframe<Double>> keyframes() { return keys; }

    public double sample(double tick) {
        if (keys.size() == 1 || tick <= keys.getFirst().tick()) return keys.getFirst().value();
        if (tick >= keys.getLast().tick()) return keys.getLast().value();
        int low = 0;
        int high = keys.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (keys.get(mid).tick() <= tick) low = mid;
            else high = mid;
        }
        Keyframe<Double> a = keys.get(low);
        Keyframe<Double> b = keys.get(high);
        double span = b.tick() - a.tick();
        double raw = span <= 0.0 ? 1.0 : (tick - a.tick()) / span;
        double t = a.easingToNext().apply(raw);
        return a.value() + (b.value() - a.value()) * t;
    }
}
