package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * High-complexity scene graph primitives. These are still immutable scene data,
 * not Minecraft entities. Client renderers receive them as resolved/batched frames.
 */
public final class ComplexElement {
    private ComplexElement() { }

    public enum ActorKind { PLAYER, ENTITY, CUSTOM_MODEL }
    public enum BlendMode { OVERRIDE, ADDITIVE, MULTIPLY }
    public enum ShadowMode { BLOB, PROJECTED, GEOMETRY }
    public enum TrailMode { RIBBON, TUBE, STREAK }
    public enum VolumeShape { SPHERE, BOX, CYLINDER }

    /** Elements that participate in the parent/child transform graph. */
    public interface Transformable extends SceneElement {
        String parentKey();
        Vec3d baseOffset();
        AdvancedTransformTrack transform();
        MotionCurve motion();
    }

    /** One animation clip layer. Backends may map clipId to vanilla/custom animation data. */
    public record AnimationLayer(
            Identifier clipId,
            ScalarTrack weight,
            ScalarTrack speed,
            double timeOffsetTicks,
            boolean looping,
            BlendMode blendMode,
            Map<String, String> parameters
    ) {
        public AnimationLayer {
            if (clipId == null) throw new IllegalArgumentException("clipId is required");
            weight = weight == null ? ScalarTrack.constant(1.0) : weight;
            speed = speed == null ? ScalarTrack.constant(1.0) : speed;
            blendMode = blendMode == null ? BlendMode.OVERRIDE : blendMode;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** Direct per-bone transform override, useful for procedural aiming/IK-style integrations. */
    public record BoneTrack(String bone, AdvancedTransformTrack transform, ScalarTrack weight, BlendMode blendMode) {
        public BoneTrack {
            if (bone == null || bone.isBlank()) throw new IllegalArgumentException("bone is required");
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            weight = weight == null ? ScalarTrack.constant(1.0) : weight;
            blendMode = blendMode == null ? BlendMode.OVERRIDE : blendMode;
        }
    }

    /** Named morph/blend-shape weight. Ignored by renderers that do not support morph targets. */
    public record MorphTrack(String name, ScalarTrack weight) {
        public MorphTrack {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("morph name is required");
            weight = weight == null ? ScalarTrack.constant(0.0) : weight;
        }
    }

    /** Empty transform node. Use it as a moving parent for whole sub-assemblies. */
    public record Node(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            boolean visible
    ) implements Transformable {
        public Node {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
        }
    }

    /** Render-only fake player, vanilla entity or custom rigged model. */
    public record Actor(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, ActorKind kind, Identifier resourceId, String profileName, Identifier skinTexture,
            Map<String, String> appearance,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            List<AnimationLayer> animations, List<BoneTrack> boneOverrides, List<MorphTrack> morphs,
            Vec3d lookAtOffset, ColorTrack tint, ScalarTrack opacity, ScalarTrack emissive,
            boolean castShadow, double cullDistance
    ) implements Transformable {
        public Actor {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            kind = kind == null ? ActorKind.CUSTOM_MODEL : kind;
            if (kind != ActorKind.PLAYER && resourceId == null) {
                throw new IllegalArgumentException("resourceId is required for ENTITY/CUSTOM_MODEL actors");
            }
            appearance = appearance == null ? Map.of() : Map.copyOf(appearance);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            animations = animations == null ? List.of() : List.copyOf(animations);
            boneOverrides = boneOverrides == null ? List.of() : List.copyOf(boneOverrides);
            morphs = morphs == null ? List.of() : List.copyOf(morphs);
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            emissive = emissive == null ? ScalarTrack.constant(0.0) : emissive;
            cullDistance = validCull(cullDistance);
        }
    }

    /** Arbitrary static or skinned 3D asset, independent of Minecraft entity renderers. */
    public record Mesh(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier modelId, Identifier materialId,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ColorTrack tint, ScalarTrack opacity, ScalarTrack emissive,
            boolean castShadow, double cullDistance, Map<String, String> parameters
    ) implements Transformable {
        public Mesh {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            if (modelId == null) throw new IllegalArgumentException("modelId is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            emissive = emissive == null ? ScalarTrack.constant(0.0) : emissive;
            cullDistance = validCull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** One logical instance in an InstanceBatch. It can still have independent animation and motion. */
    public record InstanceSpec(
            String key,
            Vec3d baseOffset,
            AdvancedTransformTrack transform,
            MotionCurve motion,
            ColorTrack tint,
            ScalarTrack opacity,
            double timeOffsetTicks,
            double timeScale,
            int variant
    ) {
        public InstanceSpec {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("instance key is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            if (!Double.isFinite(timeScale) || timeScale == 0.0) timeScale = 1.0;
        }
    }

    /** Thousands of copies of one model submitted as one logical batch. */
    public record InstanceBatch(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier modelId, Identifier materialId,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            List<InstanceSpec> instances, boolean castShadow, double cullDistance,
            int lodGroup, Map<String, String> parameters
    ) implements Transformable {
        public InstanceBatch {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            if (modelId == null) throw new IllegalArgumentException("modelId is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            instances = instances == null ? List.of() : List.copyOf(instances);
            if (instances.size() > 100_000) throw new IllegalArgumentException("one batch is limited to 100000 instances");
            cullDistance = validCull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** Shadow request. BLOB has a cheap built-in fallback; PROJECTED/GEOMETRY are backend channels. */
    public record Shadow(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, ShadowMode mode, Identifier texture,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ScalarTrack opacity, ScalarTrack softness, ScalarTrack radius,
            double cullDistance
    ) implements Transformable {
        public Shadow {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            mode = mode == null ? ShadowMode.BLOB : mode;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            opacity = opacity == null ? ScalarTrack.constant(0.45) : opacity;
            softness = softness == null ? ScalarTrack.constant(0.5) : softness;
            radius = radius == null ? ScalarTrack.constant(1.0) : radius;
            cullDistance = validCull(cullDistance);
        }
    }

    /** Persistent ribbon/tube history generated from a moving source point. */
    public record Trail(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, TrailMode mode, Vec3d sourceOffset,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ScalarTrack width, ColorTrack color, ScalarTrack opacity,
            int maxPoints, double lifetimeTicks, double minSampleDistance,
            double cullDistance
    ) implements Transformable {
        public Trail {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            mode = mode == null ? TrailMode.RIBBON : mode;
            sourceOffset = sourceOffset == null ? Vec3d.ZERO : sourceOffset;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            width = width == null ? ScalarTrack.constant(0.15) : width;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            if (maxPoints < 2 || maxPoints > 8192) throw new IllegalArgumentException("maxPoints must be 2..8192");
            if (!Double.isFinite(lifetimeTicks) || lifetimeTicks <= 0.0) throw new IllegalArgumentException("lifetimeTicks must be > 0");
            if (!Double.isFinite(minSampleDistance) || minSampleDistance < 0.0) throw new IllegalArgumentException("minSampleDistance must be >= 0");
            cullDistance = validCull(cullDistance);
        }
    }

    /** Projected texture/colour on nearby geometry or a flat fallback plane. */
    public record Decal(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier texture,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            Vec3Track size, ColorTrack tint, ScalarTrack opacity, ScalarTrack projectionDepth,
            boolean conformToSurface, double cullDistance
    ) implements Transformable {
        public Decal {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            size = size == null ? Vec3Track.constant(1.0, 1.0, 1.0) : size;
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            projectionDepth = projectionDepth == null ? ScalarTrack.constant(1.5) : projectionDepth;
            cullDistance = validCull(cullDistance);
        }
    }

    /** Fog/smoke/energy volume. High-end backends can raymarch it; fallback may approximate it. */
    public record Volume(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, VolumeShape shape, Identifier materialId,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ColorTrack color, ScalarTrack density, ScalarTrack noiseScale,
            ScalarTrack distortion, ScalarTrack emissive, double cullDistance,
            Map<String, String> parameters
    ) implements Transformable {
        public Volume {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            parentKey = normalizeParent(parentKey);
            shape = shape == null ? VolumeShape.SPHERE : shape;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            color = color == null ? ColorTrack.constant(0x80FFFFFF) : color;
            density = density == null ? ScalarTrack.constant(0.2) : density;
            noiseScale = noiseScale == null ? ScalarTrack.constant(1.0) : noiseScale;
            distortion = distortion == null ? ScalarTrack.constant(0.0) : distortion;
            emissive = emissive == null ? ScalarTrack.constant(0.0) : emissive;
            cullDistance = validCull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    private static void requireRange(String key, double start, double end) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("element key is required");
        if (!Double.isFinite(start) || Double.isNaN(end) || end < start) {
            throw new IllegalArgumentException("invalid element time range");
        }
    }

    private static ConflictPolicy defaultPolicy(ConflictPolicy policy) {
        return policy == null ? ConflictPolicy.REPLACE_LOWER : policy;
    }

    private static String normalizeParent(String parentKey) {
        return parentKey == null || parentKey.isBlank() ? null : parentKey;
    }

    private static double validCull(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 512.0;
    }
}
