package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.UltraEventElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Map;

/**
 * Optional premium bridge for effects that go beyond vanilla rendering. Every channel is independent.
 * The built-in backend provides renderer-safe approximations; shader/renderer mods can override them.
 */
public interface UltraBackend {
    default boolean postProcess(PostProcessFrame frame) { return false; }
    default boolean lightRigs(CinematicBackend.SceneRenderContext context, List<LightRigFrame> frames) { return false; }
    default boolean fractures(CinematicBackend.SceneRenderContext context, List<FractureFrame> frames) { return false; }
    default boolean softBodies(CinematicBackend.SceneRenderContext context, List<SoftBodyFrame> frames) { return false; }
    default boolean proceduralRigs(List<ProceduralRigFrame> frames) { return false; }
    default boolean particleFields(CinematicBackend.SceneRenderContext context, List<ParticleFieldFrame> frames) { return false; }
    default boolean cameraRigs(List<CameraRigFrame> frames) { return false; }
    default boolean spatialAudio(List<SpatialAudioFrame> frames) { return false; }
    default boolean materialEffects(List<MaterialEffectFrame> frames) { return false; }
    default boolean worldDeforms(CinematicBackend.SceneRenderContext context, List<WorldDeformFrame> frames) { return false; }
    default boolean portals(CinematicBackend.SceneRenderContext context, List<PortalFrame> frames) { return false; }
    default void editorMarkers(List<EditorMarkerFrame> frames) { }

    record PostProcessFrame(
            long sceneInstanceId, String elementKey, Map<UltraEventElement.PostEffect, Double> effects,
            int tintArgb, double focusDistance, double focusRange, Map<String, String> parameters,
            double localTick
    ) { }

    record SampledRigLight(
            UltraEventElement.LightKind kind, Vec3d position, Vec3d direction, int colorArgb,
            double intensity, double radius, double innerConeDegrees, double outerConeDegrees,
            boolean castShadow, double volumetric
    ) { }

    record LightRigFrame(
            long sceneInstanceId, String elementKey, Matrix4fc worldMatrix, Vec3d worldPosition,
            List<SampledRigLight> lights, Map<String, String> parameters, double localTick
    ) { }

    record FractureFrame(
            long sceneInstanceId, String elementKey, Identifier modelId, UltraEventElement.FractureMode mode,
            Matrix4fc worldMatrix, Vec3d worldPosition, int shardCount, Vec3d impulse, double force,
            double gravity, double drag, double angularSpeed, int tintArgb, double opacity,
            boolean collideGround, boolean reverse, Map<String, String> parameters, long seed, double localTick
    ) { }

    record SoftBodyFrame(
            long sceneInstanceId, String elementKey, UltraEventElement.SoftBodyMode mode,
            Matrix4fc worldMatrix, Vec3d worldPosition, List<UltraEventElement.SoftPoint> points,
            List<UltraEventElement.SoftLink> links, double gravity, double wind, double damping,
            double thickness, int colorArgb, boolean collideGround, int solverIterations,
            Map<String, String> parameters, long seed, double localTick
    ) { }

    record SampledIkGoal(
            String chain, UltraEventElement.IkMode mode, String endBone, String poleBone,
            Vec3d targetWorld, String targetElementKey, double weight, int iterations,
            double tolerance, Map<String, String> parameters
    ) { }

    record ProceduralRigFrame(
            long sceneInstanceId, String elementKey, String actorKey, List<SampledIkGoal> goals,
            double globalWeight, Map<String, String> parameters, double localTick
    ) { }

    record SampledForce(
            UltraEventElement.ForceKind kind, Vec3d position, Vec3d direction,
            double strength, double radius, double falloff, long seed
    ) { }

    record ParticleFieldFrame(
            long sceneInstanceId, String elementKey, Identifier particleId, Matrix4fc worldMatrix,
            Vec3d worldPosition, double spawnRate, double lifetimeTicks, double speed, double size,
            int colorArgb, List<SampledForce> forces, int maxParticles, boolean collideGround,
            boolean trails, Map<String, String> parameters, long seed, double localTick
    ) { }

    record CameraRigFrame(
            long sceneInstanceId, String elementKey, UltraEventElement.CameraRigMode mode,
            Vec3d position, Vec3d lookAt, double rollDegrees, double fovDegrees,
            double focusDistance, double focusRange, double shakeTranslation,
            double shakeRotation, double shakeFrequency, boolean collideWorld,
            Map<String, String> parameters, double localTick
    ) { }

    record SpatialAudioFrame(
            long sceneInstanceId, String elementKey, Identifier soundId, Vec3d position,
            double volume, double pitch, double radius, double lowPass,
            UltraEventElement.ReverbPreset reverb, double reverbMix, boolean looping,
            boolean doppler, boolean occlusion, Map<String, String> parameters, double localTick
    ) { }

    record MaterialEffectFrame(
            long sceneInstanceId, String elementKey, String targetElementKey,
            UltraEventElement.MaterialMode mode, double amount, double edgeWidth, int edgeColorArgb,
            double noiseScale, double speed, Vec3d direction, Map<String, String> parameters,
            double localTick
    ) { }

    record WorldDeformFrame(
            long sceneInstanceId, String elementKey, Vec3d center,
            UltraEventElement.DeformMode mode, double radius, double amplitude, double frequency,
            double progress, Identifier materialId, int colorArgb, boolean affectVirtualBlocks,
            Map<String, String> parameters, long seed, double localTick
    ) { }

    record PortalFrame(
            long sceneInstanceId, String elementKey, UltraEventElement.PortalMode mode,
            Matrix4fc worldMatrix, Vec3d worldPosition, Vec3d size, Identifier targetSceneId,
            Vec3d targetPosition, double opacity, int rimColorArgb, double distortion,
            int recursionDepth, Map<String, String> parameters, double localTick
    ) { }

    record EditorMarkerFrame(
            long sceneInstanceId, String elementKey, String label, int colorArgb,
            Map<String, String> parameters, double sceneTick
    ) { }
}
