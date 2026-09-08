package dev.garfield.cinefx.api;

/**
 * Analytic motion sampled directly from scene time. There is no per-object physics tick,
 * making hundreds of visuals deterministic and cheap.
 */
@FunctionalInterface
public interface MotionCurve {
    Transform sample(double localTick, long sceneSeed);

    default MotionCurve and(MotionCurve other) {
        return (tick, seed) -> sample(tick, seed).combine(other.sample(tick, seed));
    }

    static MotionCurve none() {
        return (tick, seed) -> Transform.IDENTITY;
    }
}
