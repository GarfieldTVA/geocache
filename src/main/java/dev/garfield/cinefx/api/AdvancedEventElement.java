package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** Additional orchestration/render primitives needed by large server events. */
public final class AdvancedEventElement {
    private AdvancedEventElement() { }

    public enum InheritMode { FULL, TRANSLATION_ROTATION, TRANSLATION_ONLY, NONE }

    public interface AttachmentPayload { }

    public record LightPayload(
            SceneLight.Kind kind, Vec3d direction, ColorTrack color, ScalarTrack intensity,
            ScalarTrack radius, double innerConeDegrees, double outerConeDegrees
    ) implements AttachmentPayload {
        public LightPayload {
            kind = kind == null ? SceneLight.Kind.POINT : kind;
            direction = direction == null ? new Vec3d(0, -1, 0) : direction;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            intensity = intensity == null ? ScalarTrack.constant(1.0) : intensity;
            radius = radius == null ? ScalarTrack.constant(8.0) : radius;
        }
    }

    public record EmitterPayload(
            Identifier particleId, EventElement.EmitterShape shape, ScalarTrack ratePerSecond,
            ScalarTrack spread, ScalarTrack speed, ScalarTrack size, ColorTrack color,
            int maxParticlesPerFrame
    ) implements AttachmentPayload {
        public EmitterPayload {
            if (particleId == null) throw new IllegalArgumentException("particleId is required");
            shape = shape == null ? EventElement.EmitterShape.POINT : shape;
            ratePerSecond = ratePerSecond == null ? ScalarTrack.constant(30.0) : ratePerSecond;
            spread = spread == null ? ScalarTrack.constant(0.25) : spread;
            speed = speed == null ? ScalarTrack.constant(0.1) : speed;
            size = size == null ? ScalarTrack.constant(1.0) : size;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            maxParticlesPerFrame = Math.max(1, Math.min(16384, maxParticlesPerFrame));
        }
    }

    public record BeamPayload(Vec3d from, Vec3d to, ScalarTrack width, ColorTrack color) implements AttachmentPayload {
        public BeamPayload {
            from = from == null ? Vec3d.ZERO : from;
            to = to == null ? new Vec3d(0, 0, 1) : to;
            width = width == null ? ScalarTrack.constant(0.08) : width;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
        }
    }

    public record AudioPayload(Identifier soundId, ScalarTrack volume, ScalarTrack pitch,
                               boolean spatial, boolean looping, String category) implements AttachmentPayload {
        public AudioPayload {
            if (soundId == null) throw new IllegalArgumentException("soundId is required");
            volume = volume == null ? ScalarTrack.constant(1.0) : volume;
            pitch = pitch == null ? ScalarTrack.constant(1.0) : pitch;
            category = category == null || category.isBlank() ? "MASTER" : category;
        }
    }

    public record TextPayload(String textTemplate, Identifier fontId, ColorTrack color,
                              ScalarTrack scale, boolean billboard, boolean seeThrough) implements AttachmentPayload {
        public TextPayload {
            textTemplate = textTemplate == null ? "" : textTemplate;
            fontId = fontId == null ? Identifier.of("minecraft", "default") : fontId;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            scale = scale == null ? ScalarTrack.constant(1.0) : scale;
        }
    }

    public record CustomPayload(Identifier type, Map<String, String> parameters) implements AttachmentPayload {
        public CustomPayload {
            if (type == null) throw new IllegalArgumentException("type is required");
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /**
     * Universal attachment. parentKey can target a Node/Actor/Mesh/etc. boneName is optional;
     * skeletal backends can resolve it after CineFX resolves the actor's root matrix.
     */
    public record Attachment(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, String boneName, InheritMode inheritMode,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            AttachmentPayload payload, double cullDistance
    ) implements ComplexElement.Transformable {
        public Attachment {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            if (parentKey == null || parentKey.isBlank()) throw new IllegalArgumentException("Attachment parentKey is required");
            boneName = boneName == null || boneName.isBlank() ? null : boneName;
            inheritMode = inheritMode == null ? InheritMode.FULL : inheritMode;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            if (payload == null) throw new IllegalArgumentException("Attachment payload is required");
            cullDistance = cull(cullDistance);
        }
    }

    /** Multi-layer continuous music/ambience stem. High-end audio backends can crossfade stems sample-accurately. */
    public record AudioLayer(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            Identifier soundId, ScalarTrack volume, ScalarTrack pitch, ScalarTrack lowPass,
            boolean looping, boolean music, double fadeInTicks, double fadeOutTicks,
            Map<String, String> parameters
    ) implements SceneElement {
        public AudioLayer {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            if (soundId == null) throw new IllegalArgumentException("soundId is required");
            volume = volume == null ? ScalarTrack.constant(1.0) : volume;
            pitch = pitch == null ? ScalarTrack.constant(1.0) : pitch;
            lowPass = lowPass == null ? ScalarTrack.constant(0.0) : lowPass;
            fadeInTicks = Math.max(0.0, fadeInTicks);
            fadeOutTicks = Math.max(0.0, fadeOutTicks);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** Custom sky/celestial layer for eclipse, alien planets, auroras and full skyboxes. */
    public record Sky(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            Identifier skyboxId, ColorTrack horizonColor, ColorTrack zenithColor,
            ScalarTrack sunBrightness, ScalarTrack moonBrightness, ScalarTrack eclipse,
            ScalarTrack aurora, ScalarTrack rotationDegrees, Map<String, String> parameters
    ) implements SceneElement {
        public Sky {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            horizonColor = horizonColor == null ? ColorTrack.constant(0xFF92B7FF) : horizonColor;
            zenithColor = zenithColor == null ? ColorTrack.constant(0xFF3156A6) : zenithColor;
            sunBrightness = sunBrightness == null ? ScalarTrack.constant(1.0) : sunBrightness;
            moonBrightness = moonBrightness == null ? ScalarTrack.constant(1.0) : moonBrightness;
            eclipse = eclipse == null ? ScalarTrack.constant(0.0) : eclipse;
            aurora = aurora == null ? ScalarTrack.constant(0.0) : aurora;
            rotationDegrees = rotationDegrees == null ? ScalarTrack.constant(0.0) : rotationDegrees;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** Client cutscene-control request; integrations decide how strictly to lock player input. */
    public record PlayerControl(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            boolean hideHud, boolean hideHand, boolean lockMovement, boolean lockLook,
            ScalarTrack movementScale, ScalarTrack mouseScale, ScalarTrack fovDegrees,
            boolean allowJump, boolean allowInventory
    ) implements SceneElement {
        public PlayerControl {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            movementScale = movementScale == null ? ScalarTrack.constant(lockMovement ? 0.0 : 1.0) : movementScale;
            mouseScale = mouseScale == null ? ScalarTrack.constant(lockLook ? 0.0 : 1.0) : mouseScale;
            fovDegrees = fovDegrees == null ? ScalarTrack.constant(-1.0) : fovDegrees;
        }
    }

    public record CrowdAgent(Vec3d offset, double yawDegrees, double timeOffsetTicks, int variant) {
        public CrowdAgent { offset = offset == null ? Vec3d.ZERO : offset; }
    }

    /** Large decorative crowd. Backends can GPU-instance it; built-ins may expand a quality-limited subset. */
    public record Crowd(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, ComplexElement.ActorKind kind, Identifier resourceId,
            String profilePrefix, List<CrowdAgent> agents, List<ComplexElement.AnimationLayer> animations,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ColorTrack tint, ScalarTrack opacity, boolean castShadow, double cullDistance,
            Map<String, String> appearance
    ) implements ComplexElement.Transformable {
        public Crowd {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey;
            kind = kind == null ? ComplexElement.ActorKind.PLAYER : kind;
            if (kind != ComplexElement.ActorKind.PLAYER && resourceId == null) throw new IllegalArgumentException("resourceId required for non-player crowds");
            profilePrefix = profilePrefix == null ? "CineFx" : profilePrefix;
            agents = agents == null ? List.of() : List.copyOf(agents);
            if (agents.size() > 5000) throw new IllegalArgumentException("Crowd is limited to 5000 agents; use custom GPU backends for larger crowds");
            animations = animations == null ? List.of() : List.copyOf(animations);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            cullDistance = cull(cullDistance);
            appearance = appearance == null ? Map.of() : Map.copyOf(appearance);
        }
    }

    public record EnvironmentCell(Identifier modelId, Vec3d offset, Vec3d bounds, int lodLevel, int variant) {
        public EnvironmentCell {
            if (modelId == null) throw new IllegalArgumentException("modelId is required");
            offset = offset == null ? Vec3d.ZERO : offset;
            bounds = bounds == null ? new Vec3d(16, 16, 16) : bounds;
        }
    }

    /** Chunked virtual mega-environment for cities, islands, ships or temporary arenas. */
    public record MegaEnvironment(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier materialSet, List<EnvironmentCell> cells,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            double cullDistance, Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public MegaEnvironment {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey;
            cells = cells == null ? List.of() : List.copyOf(cells);
            if (cells.size() > 16384) throw new IllegalArgumentException("MegaEnvironment is limited to 16384 cells");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    private static void requireRange(String key, double start, double end) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("element key is required");
        if (!Double.isFinite(start) || Double.isNaN(end) || end < start) throw new IllegalArgumentException("invalid element time range");
    }

    private static ConflictPolicy policy(ConflictPolicy value) { return value == null ? ConflictPolicy.REPLACE_LOWER : value; }
    private static double cull(double value) { return Double.isFinite(value) && value > 0.0 ? value : 512.0; }
}
