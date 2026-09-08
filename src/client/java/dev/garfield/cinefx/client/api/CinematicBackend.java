package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.EventElement;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Map;

/**
 * Optional high-end renderer bridge for cinematic camera, atmosphere, particles and
 * the resolved complex scene graph. Each channel is independently consumable.
 */
public interface CinematicBackend {
    default boolean applyCamera(CameraFrame frame) { return false; }
    default boolean applyAtmosphere(AtmosphereFrame frame) { return false; }
    default boolean emitParticles(List<ParticleFrame> particles) { return false; }

    default boolean renderActors(SceneRenderContext context, List<ActorFrame> actors) { return false; }
    default boolean renderMeshes(SceneRenderContext context, List<MeshFrame> meshes) { return false; }
    default boolean renderInstanceBatches(SceneRenderContext context, List<InstanceBatchFrame> batches) { return false; }
    default boolean renderShadows(SceneRenderContext context, List<ShadowFrame> shadows) { return false; }
    default boolean renderTrails(SceneRenderContext context, List<TrailFrame> trails) { return false; }
    default boolean renderDecals(SceneRenderContext context, List<DecalFrame> decals) { return false; }
    default boolean renderVolumes(SceneRenderContext context, List<VolumeFrame> volumes) { return false; }

    record SceneRenderContext(
            WorldRenderContext worldContext,
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            Camera camera,
            Vec3d cameraPosition,
            double absoluteGameTick
    ) { }

    record CameraFrame(
            long sceneInstanceId,
            String elementKey,
            EventElement.CameraMode mode,
            Vec3d position,
            Vec3d rotationDegrees,
            Vec3d lookAt,
            double shakeTranslation,
            double shakeRotationDegrees,
            double shakeFrequency
    ) { }

    record AtmosphereFrame(
            long sceneInstanceId,
            String elementKey,
            int skyTintArgb,
            int fogColorArgb,
            double fogDensity,
            double fogNear,
            double fogFar,
            double cloudOpacity,
            double starBrightness,
            double windStrength
    ) { }

    record ParticleFrame(
            long sceneInstanceId,
            String elementKey,
            Identifier particleId,
            EventElement.EmitterShape shape,
            Vec3d position,
            Vec3d rotationDegrees,
            double ratePerSecond,
            double spread,
            double speed,
            double size,
            int colorArgb,
            int maxParticlesPerFrame,
            long seed,
            double localTick
    ) { }

    record AnimationSample(
            Identifier clipId,
            double weight,
            double speed,
            double timeTicks,
            boolean looping,
            ComplexElement.BlendMode blendMode,
            Map<String, String> parameters
    ) { }

    record BoneSample(
            String bone,
            AdvancedTransform transform,
            double weight,
            ComplexElement.BlendMode blendMode
    ) { }

    record MorphSample(String name, double weight) { }

    record ActorFrame(
            long sceneInstanceId,
            String elementKey,
            ComplexElement.ActorKind kind,
            Identifier resourceId,
            String profileName,
            Identifier skinTexture,
            Map<String, String> appearance,
            Matrix4fc worldMatrix,
            Vec3d worldPosition,
            Vec3d lookAt,
            int tintArgb,
            double opacity,
            double emissive,
            List<AnimationSample> animations,
            List<BoneSample> boneOverrides,
            List<MorphSample> morphs,
            boolean castShadow,
            double localTick
    ) { }

    record MeshFrame(
            long sceneInstanceId,
            String elementKey,
            Identifier modelId,
            Identifier materialId,
            Matrix4fc worldMatrix,
            Vec3d worldPosition,
            int tintArgb,
            double opacity,
            double emissive,
            boolean castShadow,
            Map<String, String> parameters,
            double localTick
    ) { }

    /**
     * The backend receives a resolved root matrix but the immutable InstanceSpecs stay compact.
     * GPU backends can evaluate them in one batch instead of receiving tens of thousands of Java objects.
     */
    record InstanceBatchFrame(
            long sceneInstanceId,
            String elementKey,
            Identifier modelId,
            Identifier materialId,
            Matrix4fc rootMatrix,
            Vec3d worldPosition,
            List<ComplexElement.InstanceSpec> instances,
            boolean castShadow,
            int lodGroup,
            Map<String, String> parameters,
            long seed,
            double localTick
    ) { }

    record ShadowFrame(
            long sceneInstanceId,
            String elementKey,
            ComplexElement.ShadowMode mode,
            Identifier texture,
            Matrix4fc worldMatrix,
            Vec3d worldPosition,
            double opacity,
            double softness,
            double radius,
            double localTick
    ) { }

    record TrailPoint(Vec3d position, double age01) { }

    record TrailFrame(
            long sceneInstanceId,
            String elementKey,
            ComplexElement.TrailMode mode,
            List<TrailPoint> points,
            double width,
            int colorArgb,
            double opacity,
            double localTick
    ) { }

    record DecalFrame(
            long sceneInstanceId,
            String elementKey,
            Identifier texture,
            Matrix4fc worldMatrix,
            Vec3d worldPosition,
            Vec3d size,
            int tintArgb,
            double opacity,
            double projectionDepth,
            boolean conformToSurface,
            double localTick
    ) { }

    record VolumeFrame(
            long sceneInstanceId,
            String elementKey,
            ComplexElement.VolumeShape shape,
            Identifier materialId,
            Matrix4fc worldMatrix,
            Vec3d worldPosition,
            int colorArgb,
            double density,
            double noiseScale,
            double distortion,
            double emissive,
            Map<String, String> parameters,
            double localTick
    ) { }
}
