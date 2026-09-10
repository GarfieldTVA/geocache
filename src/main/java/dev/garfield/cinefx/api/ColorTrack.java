package dev.garfield.cinefx.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** ARGB color keyframes, interpolated channel by channel. */
public final class ColorTrack {
    private final List<Keyframe<Integer>> keys;

    private ColorTrack(List<Keyframe<Integer>> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("A color track needs at least one keyframe");
        ArrayList<Keyframe<Integer>> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Keyframe::tick));
        this.keys = List.copyOf(sorted);
    }

    @SafeVarargs
    public static ColorTrack of(Keyframe<Integer>... keys) {
        return new ColorTrack(Arrays.asList(keys));
    }

    public static ColorTrack constant(int argb) {
        return of(Keyframe.at(0.0, argb));
    }

    /** Immutable ordered keyframe view for tooling, serializers and editors. */
    public List<Keyframe<Integer>> keyframes() { return keys; }

    public int sample(double tick) {
        if (keys.size() == 1 || tick <= keys.getFirst().tick()) return keys.getFirst().value();
        if (tick >= keys.getLast().tick()) return keys.getLast().value();
        int low = 0;
        int high = keys.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (keys.get(mid).tick() <= tick) low = mid;
            else high = mid;
        }
        Keyframe<Integer> a = keys.get(low);
        Keyframe<Integer> b = keys.get(high);
        double span = b.tick() - a.tick();
        double raw = span <= 0.0 ? 1.0 : (tick - a.tick()) / span;
        double t = a.easingToNext().apply(raw);
        return lerpArgb(a.value(), b.value(), t);
    }

    private static int lerpArgb(int a, int b, double t) {
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        int oa = (int)Math.round(aa + (ba - aa) * t);
        int or = (int)Math.round(ar + (br - ar) * t);
        int og = (int)Math.round(ag + (bg - ag) * t);
        int ob = (int)Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
