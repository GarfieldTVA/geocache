package dev.garfield.cinefx.client.api;

import dev.garfield.cinefx.api.EventElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Optional high-end renderer bridge for cinematic camera, atmosphere and particle batches.
 * A backend may consume only the channels it owns by returning true from those methods.
 */
public interface CinematicBackend {
    default boolean applyCamera(CameraFrame frame) { return false; }
    default boolean applyAtmosphere(AtmosphereFrame frame) { return false; }
    default boolean emitParticles(List<ParticleFrame> particles) { return false; }

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
}
