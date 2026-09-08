package dev.garfield.cinefx.api;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Short factories for common primitives. Instantiate the records directly for full control. */
public final class Fx {
    private static final double FOREVER = Double.POSITIVE_INFINITY;
    private Fx() { }

    public static SceneElement.Block block(String key, BlockState state, Vec3d baseOffset, TransformTrack animation) {
        return new SceneElement.Block(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                state, null, baseOffset, animation, MotionCurve.none(), false, 0, true);
    }

    public static SceneElement.Block movingBlock(String key, BlockState state, Vec3d baseOffset,
                                                  TransformTrack animation, MotionCurve motion) {
        return new SceneElement.Block(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                state, null, baseOffset, animation, motion, false, 0, true);
    }

    /** Samples anchorBlock + sampleOffset and draws an animated visual copy of that real block. */
    public static SceneElement.Block sampledBlock(String key, BlockPos sampleOffset,
                                                   TransformTrack animation, MotionCurve motion) {
        return new SceneElement.Block(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                null, sampleOffset, Vec3d.ZERO, animation, motion, false, 0, true);
    }

    public static SceneElement.BlockSkin skin(String key, BlockPos offset, BlockState replacement) {
        return new SceneElement.BlockSkin(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                offset, replacement, 1.003, false);
    }

    public static SceneElement.Beam beam(String key, Vec3d from, Vec3d to, double width, int argb) {
        return new SceneElement.Beam(key, 0, FOREVER, 0, ConflictPolicy.ALLOW,
                from, to, width, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(argb));
    }

    public static SceneElement.Ring ring(String key, Vec3d center, ScalarTrack radius, ScalarTrack thickness, int argb) {
        return new SceneElement.Ring(key, 0, FOREVER, 0, ConflictPolicy.ALLOW,
                center, radius, thickness, 48, TransformTrack.identity(), MotionCurve.none(), ColorTrack.constant(argb));
    }

    public static SceneElement.WorldText worldText(String key, String text, Identifier font,
                                                    Vec3d baseOffset, TransformTrack transform) {
        return new SceneElement.WorldText(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                text, font, baseOffset, transform, MotionCurve.none(), ColorTrack.constant(0xFFFFFFFF),
                0x50000000, true, false, true);
    }

    public static SceneElement.HudText hudText(String key, String text, Identifier font,
                                               double normalizedX, double normalizedY) {
        return new SceneElement.HudText(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                text, font, normalizedX, normalizedY, 0, 0, SceneElement.HorizontalAlign.CENTER,
                ScalarTrack.constant(1.0), ColorTrack.constant(0xFFFFFFFF), true);
    }

    public static SceneElement.ScreenGrade grade(String key, int tintArgb, double saturation,
                                                  double exposure, double contrast, double vignette) {
        return new SceneElement.ScreenGrade(key, 0, FOREVER, 0, ConflictPolicy.REPLACE_LOWER,
                ColorTrack.constant(tintArgb), ScalarTrack.constant(saturation), ScalarTrack.constant(exposure),
                ScalarTrack.constant(contrast), ScalarTrack.constant(vignette));
    }

    public static SceneElement.Custom custom(String key, Identifier rendererType, Map<String, String> parameters,
                                             Vec3d baseOffset, TransformTrack transform, MotionCurve motion) {
        return new SceneElement.Custom(key, 0, FOREVER, 0, ConflictPolicy.ALLOW,
                rendererType, parameters, baseOffset, transform, motion);
    }
}
