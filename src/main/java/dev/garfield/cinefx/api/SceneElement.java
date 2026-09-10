package dev.garfield.cinefx.api;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Immutable scene primitives. They are data, not Minecraft entities. */
public interface SceneElement {
    String key();
    double startTick();
    double endTick();
    int priority();
    ConflictPolicy conflictPolicy();

    default boolean activeAt(double sceneTick) { return sceneTick >= startTick() && sceneTick <= endTick(); }

    enum HorizontalAlign { LEFT, CENTER, RIGHT }

    /** A virtual block model. fixedState may be null only when sampleOffset is non-null. */
    record Block(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                 BlockState fixedState, BlockPos sampleOffset, Vec3d baseOffset, TransformTrack transform,
                 MotionCurve motion, boolean fullBright, int outlineColor, boolean claimOccupiedCell)
            implements SceneElement {
        public Block {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (fixedState == null && sampleOffset == null) throw new IllegalArgumentException("Block needs fixedState or sampleOffset");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
        }
        public boolean samplesWorld() { return fixedState == null; }
    }

    /** Visual-only world block replacement. The underlying server block is untouched. */
    record BlockSkin(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                     BlockPos offset, BlockState replacement, double inflate, boolean fullBright)
            implements SceneElement {
        public BlockSkin {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (offset == null || replacement == null) throw new IllegalArgumentException("BlockSkin needs offset and replacement");
            if (inflate < 1.0 || inflate > 1.08) throw new IllegalArgumentException("inflate should stay between 1.0 and 1.08");
        }
    }

    /** Camera-independent 3D beam/laser rendered as a thin cross prism. Offsets are scene-anchor-local. */
    record Beam(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                Vec3d fromOffset, Vec3d toOffset, double width, TransformTrack transform, MotionCurve motion,
                ColorTrack color)
            implements SceneElement {
        public Beam {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            fromOffset = fromOffset == null ? Vec3d.ZERO : fromOffset;
            toOffset = toOffset == null ? new Vec3d(0, 1, 0) : toOffset;
            if (!Double.isFinite(width) || width <= 0.0) throw new IllegalArgumentException("beam width must be > 0");
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        }
    }

    /** Flat annulus/shockwave in local XZ. Rotate its transform for a vertical portal ring. */
    record Ring(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                Vec3d baseOffset, ScalarTrack radius, ScalarTrack thickness, int segments,
                TransformTrack transform, MotionCurve motion, ColorTrack color)
            implements SceneElement {
        public Ring {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            radius = radius == null ? ScalarTrack.constant(1.0) : radius;
            thickness = thickness == null ? ScalarTrack.constant(0.1) : thickness;
            if (segments < 6 || segments > 256) throw new IllegalArgumentException("ring segments must be 6..256");
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        }
    }

    /** 3D text submitted to the world render command queue. Custom resource-pack fonts are supported. */
    record WorldText(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                     String textTemplate, Identifier fontId, Vec3d baseOffset, TransformTrack transform,
                     MotionCurve motion, ColorTrack color, int backgroundArgb, boolean billboard,
                     boolean seeThrough, boolean fullBright)
            implements SceneElement {
        public WorldText {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (textTemplate == null) textTemplate = "";
            fontId = fontId == null ? Identifier.of("minecraft", "default") : fontId;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        }
    }

    /** HUD text. x/y are normalized screen coordinates plus optional pixel offsets. */
    record HudText(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                   String textTemplate, Identifier fontId, double normalizedX, double normalizedY,
                   int pixelOffsetX, int pixelOffsetY, HorizontalAlign align, ScalarTrack scale,
                   ColorTrack color, boolean shadow)
            implements SceneElement {
        public HudText {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (textTemplate == null) textTemplate = "";
            fontId = fontId == null ? Identifier.of("minecraft", "default") : fontId;
            align = align == null ? HorizontalAlign.CENTER : align;
            scale = scale == null ? ScalarTrack.constant(1.0) : scale;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        }
    }

    /** Global scene grade. Full saturation/contrast is available to optional GPU backends. */
    record ScreenGrade(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                       ColorTrack tint, ScalarTrack saturation, ScalarTrack exposure,
                       ScalarTrack contrast, ScalarTrack vignette)
            implements SceneElement {
        public ScreenGrade {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            tint = tint == null ? ColorTrack.constant(0x00000000) : tint;
            saturation = saturation == null ? ScalarTrack.constant(1.0) : saturation;
            exposure = exposure == null ? ScalarTrack.constant(0.0) : exposure;
            contrast = contrast == null ? ScalarTrack.constant(1.0) : contrast;
            vignette = vignette == null ? ScalarTrack.constant(0.0) : vignette;
        }
    }

    /** Arbitrary renderer extension point registered by another mod on the client. */
    record Custom(String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
                  Identifier rendererType, Map<String, String> parameters, Vec3d baseOffset,
                  TransformTrack transform, MotionCurve motion)
            implements SceneElement {
        public Custom {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (rendererType == null) throw new IllegalArgumentException("rendererType is required");
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
        }
    }

    private static void requireRange(String key, double start, double end) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("element key is required");
        if (!Double.isFinite(start) || Double.isNaN(end) || end < start) throw new IllegalArgumentException("invalid element time range");
    }

    private static ConflictPolicy defaultPolicy(ConflictPolicy policy) {
        return policy == null ? ConflictPolicy.REPLACE_LOWER : policy;
    }
}
