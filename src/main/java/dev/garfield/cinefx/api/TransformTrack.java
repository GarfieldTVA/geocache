package dev.garfield.cinefx.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Immutable transform keyframe track. Sampling is O(log n). */
public final class TransformTrack {
    private final List<Keyframe<Transform>> keys;

    private TransformTrack(List<Keyframe<Transform>> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("A transform track needs at least one keyframe");
        ArrayList<Keyframe<Transform>> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Keyframe::tick));
        this.keys = List.copyOf(sorted);
    }

    @SafeVarargs
    public static TransformTrack of(Keyframe<Transform>... keys) {
        return new TransformTrack(Arrays.asList(keys));
    }

    public static TransformTrack constant(Transform transform) {
        return of(Keyframe.at(0.0, transform));
    }

    public static TransformTrack identity() {
        return constant(Transform.IDENTITY);
    }

    public List<Keyframe<Transform>> keyframes() {
        return keys;
    }

    public Transform sample(double tick) {
        if (keys.size() == 1 || tick <= keys.getFirst().tick()) return keys.getFirst().value();
        if (tick >= keys.getLast().tick()) return keys.getLast().value();
        int low = 0;
        int high = keys.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (keys.get(mid).tick() <= tick) low = mid;
            else high = mid;
        }
        Keyframe<Transform> a = keys.get(low);
        Keyframe<Transform> b = keys.get(high);
        double span = b.tick() - a.tick();
        double raw = span <= 0.0 ? 1.0 : (tick - a.tick()) / span;
        return Transform.lerp(a.value(), b.value(), a.easingToNext().apply(raw));
    }
}
