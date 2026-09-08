package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Higher-level cinematic primitives used by large live events.
 * They remain immutable scene data; the client decides how to render/consume them.
 */
public final class EventElement {
    private EventElement() { }

    public enum CameraMode { ADDITIVE, ANCHOR_ABSOLUTE }
    public enum EmitterShape { POINT, SPHERE, DISC, CONE }

    /**
     * Real camera motion without spawning a camera entity.
     * ADDITIVE offsets the player's current view; ANCHOR_ABSOLUTE turns the scene anchor into a camera rig.
     */
    public record Camera(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            CameraMode mode,
            Vec3d baseOffset,
            TransformTrack transform,
            MotionCurve motion,
            ScalarTrack shakeTranslation,
            ScalarTrack shakeRotationDegrees,
            ScalarTrack shakeFrequency,
            Vec3d lookAtOffset
    ) implements SceneElement {
        public Camera {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            mode = mode == null ? CameraMode.ADDITIVE : mode;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            shakeTranslation = shakeTranslation == null ? ScalarTrack.constant(0.0) : shakeTranslation;
            shakeRotationDegrees = shakeRotationDegrees == null ? ScalarTrack.constant(0.0) : shakeRotationDegrees;
            shakeFrequency = shakeFrequency == null ? ScalarTrack.constant(1.0) : shakeFrequency;
        }
    }

    /**
     * Scene-wide sky/fog/weather request. A renderer backend can map this to Iris/Sodium/custom shaders.
     * The values are sampled every rendered frame and never mutate biome/chunk data.
     */
    public record Atmosphere(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            ColorTrack skyTint,
            ColorTrack fogColor,
            ScalarTrack fogDensity,
            ScalarTrack fogNear,
            ScalarTrack fogFar,
            ScalarTrack cloudOpacity,
            ScalarTrack starBrightness,
            ScalarTrack windStrength
    ) implements SceneElement {
        public Atmosphere {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            skyTint = skyTint == null ? ColorTrack.constant(0x00FFFFFF) : skyTint;
            fogColor = fogColor == null ? ColorTrack.constant(0x00FFFFFF) : fogColor;
            fogDensity = fogDensity == null ? ScalarTrack.constant(0.0) : fogDensity;
            fogNear = fogNear == null ? ScalarTrack.constant(0.0) : fogNear;
            fogFar = fogFar == null ? ScalarTrack.constant(256.0) : fogFar;
            cloudOpacity = cloudOpacity == null ? ScalarTrack.constant(1.0) : cloudOpacity;
            starBrightness = starBrightness == null ? ScalarTrack.constant(1.0) : starBrightness;
            windStrength = windStrength == null ? ScalarTrack.constant(0.0) : windStrength;
        }
    }

    /**
     * Batched particle emitter. Simple vanilla particle ids have a built-in fallback;
     * custom/parameterized particles can be consumed by a high-performance backend.
     */
    public record Emitter(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            Identifier particleId,
            EmitterShape shape,
            Vec3d baseOffset,
            TransformTrack transform,
            MotionCurve motion,
            ScalarTrack ratePerSecond,
            ScalarTrack spread,
            ScalarTrack speed,
            ScalarTrack size,
            ColorTrack color,
            int maxParticlesPerFrame
    ) implements SceneElement {
        public Emitter {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (particleId == null) throw new IllegalArgumentException("particleId is required");
            shape = shape == null ? EmitterShape.POINT : shape;
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            transform = transform == null ? TransformTrack.identity() : transform;
            motion = motion == null ? MotionCurve.none() : motion;
            ratePerSecond = ratePerSecond == null ? ScalarTrack.constant(20.0) : ratePerSecond;
            spread = spread == null ? ScalarTrack.constant(0.25) : spread;
            speed = speed == null ? ScalarTrack.constant(0.1) : speed;
            size = size == null ? ScalarTrack.constant(1.0) : size;
            color = color == null ? ColorTrack.constant(0xFFFFFFFF) : color;
            if (maxParticlesPerFrame < 1 || maxParticlesPerFrame > 16384) {
                throw new IllegalArgumentException("maxParticlesPerFrame must be 1..16384");
            }
        }
    }

    /** One-shot sound cue. endTick is the late-arrival grace window. */
    public record AudioCue(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            Identifier soundId,
            Vec3d baseOffset,
            float volume,
            float pitch,
            boolean spatial,
            String category
    ) implements SceneElement {
        public AudioCue {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (soundId == null) throw new IllegalArgumentException("soundId is required");
            baseOffset = baseOffset == null ? Vec3d.ZERO : baseOffset;
            if (!Float.isFinite(volume) || volume < 0.0F) throw new IllegalArgumentException("volume must be >= 0");
            if (!Float.isFinite(pitch) || pitch <= 0.0F) throw new IllegalArgumentException("pitch must be > 0");
            category = category == null || category.isBlank() ? "master" : category;
        }
    }

    /** Full-screen event layer: flash, fade, letterbox/cinematic bars, etc. */
    public record Overlay(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            ColorTrack color,
            ScalarTrack opacity,
            ScalarTrack letterbox,
            ScalarTrack blurHint,
            ScalarTrack chromaticAberrationHint
    ) implements SceneElement {
        public Overlay {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            color = color == null ? ColorTrack.constant(0xFF000000) : color;
            opacity = opacity == null ? ScalarTrack.constant(0.0) : opacity;
            letterbox = letterbox == null ? ScalarTrack.constant(0.0) : letterbox;
            blurHint = blurHint == null ? ScalarTrack.constant(0.0) : blurHint;
            chromaticAberrationHint = chromaticAberrationHint == null ? ScalarTrack.constant(0.0) : chromaticAberrationHint;
        }
    }

    /** Timeline marker delivered once to interested client mods. */
    public record Marker(
            String key,
            double startTick,
            double endTick,
            int priority,
            ConflictPolicy conflictPolicy,
            String name,
            Map<String, String> parameters
    ) implements SceneElement {
        public Marker {
            requireRange(key, startTick, endTick);
            conflictPolicy = defaultPolicy(conflictPolicy);
            if (name == null || name.isBlank()) throw new IllegalArgumentException("marker name is required");
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
}
