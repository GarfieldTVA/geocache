package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * Ultra-tier event primitives. These stay renderer-neutral: CineFX samples/synchronizes them,
 * the built-in client provides safe approximations, and premium backends can replace channels.
 */
public final class UltraEventElement {
    private UltraEventElement() { }

    public enum PostEffect {
        BLOOM, DEPTH_OF_FIELD, MOTION_BLUR, CHROMATIC_ABERRATION, VIGNETTE,
        FILM_GRAIN, LENS_DIRT, HEAT_HAZE, UNDERWATER_REFRACTION, GLITCH, TRANSITION
    }

    public record PostProcess(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            Map<PostEffect, ScalarTrack> effects, ColorTrack tint, ScalarTrack focusDistance,
            ScalarTrack focusRange, Map<String, String> parameters
    ) implements SceneElement {
        public PostProcess {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            effects = effects == null ? Map.of() : Map.copyOf(effects);
            tint = tint == null ? ColorTrack.constant(0x00000000) : tint;
            focusDistance = focusDistance == null ? ScalarTrack.constant(8.0) : focusDistance;
            focusRange = focusRange == null ? ScalarTrack.constant(4.0) : focusRange;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum LightKind { POINT, SPOT, AREA, TUBE }

    public record RigLight(
            LightKind kind, Vec3d offset, Vec3d direction, ColorTrack color,
            ScalarTrack intensity, ScalarTrack radius, ScalarTrack innerConeDegrees,
            ScalarTrack outerConeDegrees, boolean castShadow, ScalarTrack volumetric
    ) {
        public RigLight {
            kind = kind == null ? LightKind.POINT : kind;
            offset = offset == null ? Vec3d.ZERO : offset;
            direction = direction == null ? new Vec3d(0, -1, 0) : direction;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            intensity = intensity == null ? ScalarTrack.constant(1.0) : intensity;
            radius = radius == null ? ScalarTrack.constant(12.0) : radius;
            innerConeDegrees = innerConeDegrees == null ? ScalarTrack.constant(20.0) : innerConeDegrees;
            outerConeDegrees = outerConeDegrees == null ? ScalarTrack.constant(38.0) : outerConeDegrees;
            volumetric = volumetric == null ? ScalarTrack.constant(0.0) : volumetric;
        }
    }

    public record LightRig(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            List<RigLight> lights, ScalarTrack globalIntensity, double cullDistance,
            Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public LightRig {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            lights = lights == null ? List.of() : List.copyOf(lights);
            globalIntensity = globalIntensity == null ? ScalarTrack.constant(1.0) : globalIntensity;
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum FractureMode { RADIAL, VORONOI, GRID, DIRECTIONAL, PREBAKED }

    public record Fracture(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier modelId, FractureMode mode, int shardCount,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            Vec3d impulse, ScalarTrack force, ScalarTrack gravity, ScalarTrack drag,
            ScalarTrack angularSpeed, ColorTrack tint, ScalarTrack opacity,
            boolean collideGround, boolean reverse, double cullDistance,
            Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public Fracture {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            if (modelId == null) throw new IllegalArgumentException("modelId is required");
            mode = mode == null ? FractureMode.VORONOI : mode;
            shardCount = Math.max(1, Math.min(8192, shardCount));
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            impulse = impulse == null ? new Vec3d(0, 1, 0) : impulse;
            force = force == null ? ScalarTrack.constant(1.0) : force;
            gravity = gravity == null ? ScalarTrack.constant(0.04) : gravity;
            drag = drag == null ? ScalarTrack.constant(0.985) : drag;
            angularSpeed = angularSpeed == null ? ScalarTrack.constant(3.0) : angularSpeed;
            tint = tint == null ? ColorTrack.constant(0xFFFFFFFF) : tint;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum SoftBodyMode { CLOTH, ROPE, CHAIN, SPRING, RAGDOLL, TENTACLE }

    public record SoftPoint(Vec3d offset, double inverseMass, boolean pinned) {
        public SoftPoint { offset = offset == null ? Vec3d.ZERO : offset; }
    }

    public record SoftLink(int a, int b, double restLength, double stiffness) { }

    public record SoftBody(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, SoftBodyMode mode, List<SoftPoint> points, List<SoftLink> links,
            Vec3d baseOffset, AdvancedTransformTrack transform, MotionCurve motion,
            ScalarTrack gravity, ScalarTrack wind, ScalarTrack damping, ScalarTrack thickness,
            ColorTrack color, boolean collideGround, int solverIterations, double cullDistance,
            Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public SoftBody {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            mode = mode == null ? SoftBodyMode.ROPE : mode;
            points = points == null ? List.of() : List.copyOf(points);
            links = links == null ? List.of() : List.copyOf(links);
            if (points.size() > 4096 || links.size() > 16384) throw new IllegalArgumentException("SoftBody budget exceeded");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            gravity = gravity == null ? ScalarTrack.constant(0.035) : gravity;
            wind = wind == null ? ScalarTrack.constant(0.0) : wind;
            damping = damping == null ? ScalarTrack.constant(0.985) : damping;
            thickness = thickness == null ? ScalarTrack.constant(0.04) : thickness;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            solverIterations = Math.max(1, Math.min(32, solverIterations));
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum IkMode { LOOK_AT, TWO_BONE, CCD, FABRIK, FOOT_PLANT, AIM, TENTACLE }

    public record IkGoal(
            String chain, IkMode mode, String endBone, String poleBone, Vec3d targetOffset,
            String targetElementKey, ScalarTrack weight, int iterations, double tolerance,
            Map<String, String> parameters
    ) {
        public IkGoal {
            chain = chain == null ? "default" : chain;
            mode = mode == null ? IkMode.LOOK_AT : mode;
            endBone = endBone == null ? "" : endBone;
            poleBone = poleBone == null ? "" : poleBone;
            targetOffset = targetOffset == null ? Vec3d.ZERO : targetOffset;
            targetElementKey = targetElementKey == null || targetElementKey.isBlank() ? null : targetElementKey;
            weight = weight == null ? ScalarTrack.constant(1.0) : weight;
            iterations = Math.max(1, Math.min(64, iterations));
            tolerance = Double.isFinite(tolerance) && tolerance > 0 ? tolerance : 0.01;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record ProceduralRig(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String actorKey, List<IkGoal> goals, ScalarTrack globalWeight,
            Map<String, String> parameters
    ) implements SceneElement {
        public ProceduralRig {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            if (actorKey == null || actorKey.isBlank()) throw new IllegalArgumentException("actorKey is required");
            goals = goals == null ? List.of() : List.copyOf(goals);
            globalWeight = globalWeight == null ? ScalarTrack.constant(1.0) : globalWeight;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum ForceKind { DIRECTIONAL, ATTRACTOR, REPELLER, VORTEX, TURBULENCE, DRAG, EXPLOSION }

    public record Force(
            ForceKind kind, Vec3d offset, Vec3d direction, ScalarTrack strength, ScalarTrack radius,
            ScalarTrack falloff, long seed
    ) {
        public Force {
            kind = kind == null ? ForceKind.DIRECTIONAL : kind;
            offset = offset == null ? Vec3d.ZERO : offset;
            direction = direction == null ? new Vec3d(0, 1, 0) : direction;
            strength = strength == null ? ScalarTrack.constant(1.0) : strength;
            radius = radius == null ? ScalarTrack.constant(8.0) : radius;
            falloff = falloff == null ? ScalarTrack.constant(1.0) : falloff;
        }
    }

    public record ParticleField(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier particleId, Vec3d baseOffset, AdvancedTransformTrack transform,
            MotionCurve motion, ScalarTrack spawnRate, ScalarTrack lifetimeTicks, ScalarTrack speed,
            ScalarTrack size, ColorTrack color, List<Force> forces, int maxParticles,
            boolean collideGround, boolean trails, double cullDistance, Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public ParticleField {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            if (particleId == null) throw new IllegalArgumentException("particleId is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            spawnRate = spawnRate == null ? ScalarTrack.constant(100.0) : spawnRate;
            lifetimeTicks = lifetimeTicks == null ? ScalarTrack.constant(40.0) : lifetimeTicks;
            speed = speed == null ? ScalarTrack.constant(0.2) : speed;
            size = size == null ? ScalarTrack.constant(1.0) : size;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            forces = forces == null ? List.of() : List.copyOf(forces);
            maxParticles = Math.max(1, Math.min(250000, maxParticles));
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum CameraRigMode { DOLLY, CRANE, ORBIT, RAIL, HANDHELD, FOLLOW, LOCKED, FREE }

    public record CameraRig(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            CameraRigMode mode, PathTrack path, Vec3d lookAtOffset, String lookAtElementKey,
            ScalarTrack rollDegrees, ScalarTrack fovDegrees, ScalarTrack focusDistance,
            ScalarTrack focusRange, ScalarTrack shakeTranslation, ScalarTrack shakeRotation,
            ScalarTrack shakeFrequency, boolean collideWorld, Map<String, String> parameters
    ) implements SceneElement {
        public CameraRig {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            mode = mode == null ? CameraRigMode.RAIL : mode;
            lookAtOffset = lookAtOffset == null ? Vec3d.ZERO : lookAtOffset;
            lookAtElementKey = lookAtElementKey == null || lookAtElementKey.isBlank() ? null : lookAtElementKey;
            rollDegrees = rollDegrees == null ? ScalarTrack.constant(0.0) : rollDegrees;
            fovDegrees = fovDegrees == null ? ScalarTrack.constant(-1.0) : fovDegrees;
            focusDistance = focusDistance == null ? ScalarTrack.constant(8.0) : focusDistance;
            focusRange = focusRange == null ? ScalarTrack.constant(4.0) : focusRange;
            shakeTranslation = shakeTranslation == null ? ScalarTrack.constant(0.0) : shakeTranslation;
            shakeRotation = shakeRotation == null ? ScalarTrack.constant(0.0) : shakeRotation;
            shakeFrequency = shakeFrequency == null ? ScalarTrack.constant(1.0) : shakeFrequency;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum ReverbPreset { NONE, ROOM, HALL, CAVE, ARENA, UNDERWATER, SPACE, CUSTOM }

    public record SpatialAudio(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, Identifier soundId, Vec3d baseOffset, AdvancedTransformTrack transform,
            MotionCurve motion, ScalarTrack volume, ScalarTrack pitch, ScalarTrack radius,
            ScalarTrack lowPass, ReverbPreset reverb, ScalarTrack reverbMix,
            boolean looping, boolean doppler, boolean occlusion, Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public SpatialAudio {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            if (soundId == null) throw new IllegalArgumentException("soundId is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            volume = volume == null ? ScalarTrack.constant(1.0) : volume;
            pitch = pitch == null ? ScalarTrack.constant(1.0) : pitch;
            radius = radius == null ? ScalarTrack.constant(32.0) : radius;
            lowPass = lowPass == null ? ScalarTrack.constant(0.0) : lowPass;
            reverb = reverb == null ? ReverbPreset.NONE : reverb;
            reverbMix = reverbMix == null ? ScalarTrack.constant(0.0) : reverbMix;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum MaterialMode { DISSOLVE, CORRUPTION, FREEZE, BURN, HOLOGRAM, SCAN, PHASE, CLOAK }

    public record MaterialEffect(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String targetElementKey, MaterialMode mode, ScalarTrack amount, ScalarTrack edgeWidth,
            ColorTrack edgeColor, ScalarTrack noiseScale, ScalarTrack speed, Vec3d direction,
            Map<String, String> parameters
    ) implements SceneElement {
        public MaterialEffect {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            if (targetElementKey == null || targetElementKey.isBlank()) throw new IllegalArgumentException("targetElementKey is required");
            mode = mode == null ? MaterialMode.DISSOLVE : mode;
            amount = amount == null ? ScalarTrack.constant(0.0) : amount;
            edgeWidth = edgeWidth == null ? ScalarTrack.constant(0.08) : edgeWidth;
            edgeColor = edgeColor == null ? ColorTrack.constant(0xFFFFFFFF) : edgeColor;
            noiseScale = noiseScale == null ? ScalarTrack.constant(1.0) : noiseScale;
            speed = speed == null ? ScalarTrack.constant(1.0) : speed;
            direction = direction == null ? new Vec3d(0, 1, 0) : direction;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum DeformMode { LIFT, SINK, CRACK, FISSURE, WAVE, PULSE, GROW, REBUILD, BIOME_ILLUSION }

    public record WorldDeform(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            Vec3d centerOffset, DeformMode mode, ScalarTrack radius, ScalarTrack amplitude,
            ScalarTrack frequency, ScalarTrack progress, Identifier materialId,
            ColorTrack color, boolean affectVirtualBlocks, Map<String, String> parameters
    ) implements SceneElement {
        public WorldDeform {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            centerOffset = centerOffset == null ? Vec3d.ZERO : centerOffset;
            mode = mode == null ? DeformMode.LIFT : mode;
            radius = radius == null ? ScalarTrack.constant(12.0) : radius;
            amplitude = amplitude == null ? ScalarTrack.constant(3.0) : amplitude;
            frequency = frequency == null ? ScalarTrack.constant(1.0) : frequency;
            progress = progress == null ? ScalarTrack.constant(1.0) : progress;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public enum PortalMode { PORTAL, MIRROR, CAMERA_FEED, DIMENSION_VIEW, KALEIDOSCOPE }

    public record PortalSurface(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String parentKey, PortalMode mode, Vec3d baseOffset, AdvancedTransformTrack transform,
            MotionCurve motion, Vec3d size, Identifier targetSceneId, Vec3d targetOffset,
            ScalarTrack opacity, ColorTrack rimColor, ScalarTrack distortion,
            int recursionDepth, double cullDistance, Map<String, String> parameters
    ) implements ComplexElement.Transformable {
        public PortalSurface {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            parentKey = parent(parentKey);
            mode = mode == null ? PortalMode.PORTAL : mode;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? AdvancedTransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            size = size == null ? new Vec3d(4, 6, 0.1) : size;
            targetOffset = targetOffset == null ? Vec3d.ZERO : targetOffset;
            opacity = opacity == null ? ScalarTrack.constant(1.0) : opacity;
            rimColor = rimColor == null ? ColorTrack.constant(0xFFFFFFFF) : rimColor;
            distortion = distortion == null ? ScalarTrack.constant(0.1) : distortion;
            recursionDepth = Math.max(0, Math.min(4, recursionDepth));
            cullDistance = cull(cullDistance);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** Timeline/editor marker consumed by development tools and ignored by normal renderers. */
    public record EditorMarker(
            String key, double startTick, double endTick, int priority, ConflictPolicy conflictPolicy,
            String label, int colorArgb, Map<String, String> parameters
    ) implements SceneElement {
        public EditorMarker {
            requireRange(key, startTick, endTick);
            conflictPolicy = policy(conflictPolicy);
            label = label == null ? key : label;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    private static void requireRange(String key, double start, double end) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("element key is required");
        if (!Double.isFinite(start) || Double.isNaN(end) || end < start) throw new IllegalArgumentException("invalid element time range");
    }

    private static ConflictPolicy policy(ConflictPolicy value) {
        return value == null ? ConflictPolicy.REPLACE_LOWER : value;
    }

    private static String parent(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static double cull(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 512.0;
    }
}
