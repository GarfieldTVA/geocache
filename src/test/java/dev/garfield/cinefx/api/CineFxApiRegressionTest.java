package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class CineFxApiRegressionTest {
    private static final double EPS = 1.0e-6;

    @Test
    void linearBezierTracksNormalizedTime() {
        CubicBezier linear = CubicBezier.linear();
        assertEquals(0.0, linear.apply(0.0), EPS);
        assertEquals(0.25, linear.apply(0.25), EPS);
        assertEquals(0.5, linear.apply(0.5), EPS);
        assertEquals(0.75, linear.apply(0.75), EPS);
        assertEquals(1.0, linear.apply(1.0), EPS);
    }

    @Test
    void bezierOverridesLegacyEasingWhenPresent() {
        Keyframe<Double> key = new Keyframe<>(0.0, 1.0, Easing.EASE_IN_QUAD, CubicBezier.linear());
        assertEquals(0.5, key.interpolate(0.5), EPS);

        Keyframe<Double> legacy = new Keyframe<>(0.0, 1.0, Easing.EASE_IN_QUAD);
        assertEquals(0.25, legacy.interpolate(0.5), EPS);
    }

    @Test
    void bezierRejectsNonMonotonicTimeHandlesButAllowsValueOvershoot() {
        assertThrows(IllegalArgumentException.class, () -> new CubicBezier(-0.01, 0.0, 0.5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new CubicBezier(0.5, 0.0, 1.01, 1.0));
        assertDoesNotThrow(() -> new CubicBezier(0.2, -0.4, 0.35, 1.6));
    }

    @Test
    void colorTrackClampsBezierOvershootPerArgbChannel() {
        CubicBezier overshoot = new CubicBezier(0.2, 0.0, 0.3, 1.6);
        double overshootTick = -1.0;
        for (int i = 1; i < 100; i++) {
            double raw = i / 100.0;
            if (overshoot.apply(raw) > 1.0) {
                overshootTick = raw * 10.0;
                break;
            }
        }
        assertTrue(overshootTick > 0.0, "test curve should overshoot above 1");

        ColorTrack track = ColorTrack.of(
                new Keyframe<>(0.0, 0xFF000000, Easing.LINEAR, overshoot),
                Keyframe.at(10.0, 0xFFFF0000));
        int sampled = track.sample(overshootTick);

        assertEquals(255, (sampled >>> 24) & 255, "alpha must not be polluted by red overflow");
        assertEquals(255, (sampled >>> 16) & 255, "red must clamp to 255");
        assertEquals(0, (sampled >>> 8) & 255);
        assertEquals(0, sampled & 255);
    }

    @Test
    void declarativeProgramCompilesAndHotReloadRegistryReplacesAtomically() {
        Identifier id = Identifier.of("cinefx_test", "program_" + Long.toUnsignedString(System.nanoTime()));
        EventProgramSpec first = EventProgramSpec.starter(id);
        assertDoesNotThrow(first::compile);

        try {
            EventProgramSpec.Registry.replace(first);
            assertEquals(first, EventProgramSpec.Registry.find(id).orElseThrow());

            EventProgramSpec replacement = new EventProgramSpec(
                    id,
                    "only",
                    List.of(new EventProgramSpec.PhaseSpec("only", List.of(), List.of(), List.of())),
                    Map.of("revision", "2"));
            EventProgramSpec.Registry.replace(replacement);
            assertEquals("2", EventProgramSpec.Registry.find(id).orElseThrow().metadata().get("revision"));
        } finally {
            EventProgramSpec.Registry.remove(id);
        }
    }

    @Test
    void declarativeProgramRejectsTransitionToUnknownPhase() {
        Identifier id = Identifier.of("cinefx_test", "invalid_" + Long.toUnsignedString(System.nanoTime()));
        EventProgramSpec.PhaseSpec phase = new EventProgramSpec.PhaseSpec(
                "start",
                List.of(),
                List.of(),
                List.of(new EventProgramSpec.TransitionSpec(
                        "missing",
                        0,
                        new EventProgramSpec.ConditionSpec.Always(),
                        List.of())));
        EventProgramSpec spec = new EventProgramSpec(id, "start", List.of(phase), Map.of());
        assertThrows(IllegalArgumentException.class, spec::compile);
    }
}
