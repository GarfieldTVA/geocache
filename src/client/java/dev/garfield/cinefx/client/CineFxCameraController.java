package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CinematicBackend.CameraFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Samples scene camera rigs analytically at render time; no camera entity is created. */
public final class CineFxCameraController {
    private CineFxCameraController() { }

    public record Sample(Vec3d position, float yaw, float pitch) { }

    public static Sample sample(Camera vanillaCamera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        Vec3d position = vanillaCamera.getCameraPos();
        double yaw = vanillaCamera.getYaw();
        double pitch = vanillaCamera.getPitch();
        boolean used = false;
        VisualClaims claims = new VisualClaims();

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            for (SceneElement raw : scene.elementsByPriority()) {
                if (!(raw instanceof EventElement.Camera camera) || !camera.activeAt(sceneTick)) continue;
                if (!claims.claim("camera", camera.conflictPolicy())) continue;

                double local = sceneTick - camera.startTick();
                Transform transform = camera.transform().sample(local)
                        .combine(camera.motion().sample(local, scene.options().seed()));
                Vec3d worldPosition = scene.options().anchor().add(camera.baseOffset()).add(transform.translation());
                Vec3d rotation = transform.rotationDegrees();

                double outYaw;
                double outPitch;
                if (camera.lookAtOffset() != null) {
                    Vec3d target = scene.options().anchor().add(camera.lookAtOffset());
                    Vec3d delta = target.subtract(worldPosition);
                    double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                    outYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
                    outPitch = Math.toDegrees(-Math.atan2(delta.y, horizontal));
                } else if (camera.mode() == EventElement.CameraMode.ANCHOR_ABSOLUTE) {
                    outYaw = rotation.y;
                    outPitch = rotation.x;
                } else {
                    outYaw = yaw + rotation.y;
                    outPitch = pitch + rotation.x;
                }

                Vec3d outPosition = camera.mode() == EventElement.CameraMode.ANCHOR_ABSOLUTE
                        ? worldPosition
                        : position.add(camera.baseOffset()).add(transform.translation());

                double shakeT = Math.max(0.0, camera.shakeTranslation().sample(local));
                double shakeR = Math.max(0.0, camera.shakeRotationDegrees().sample(local));
                double frequency = Math.max(0.01, camera.shakeFrequency().sample(local));
                long salt = scene.options().seed() ^ ((long) camera.key().hashCode() << 32) ^ scene.instanceId();
                double phase = absoluteTick * frequency * 0.73 + (salt & 0xFFFF) * 0.0137;
                Vec3d shakeOffset = new Vec3d(
                        Math.sin(phase * 1.37 + 0.4) * shakeT,
                        Math.sin(phase * 1.91 + 2.1) * shakeT * 0.72,
                        Math.cos(phase * 1.61 + 4.7) * shakeT);
                outPosition = outPosition.add(shakeOffset);
                outYaw += Math.sin(phase * 1.83 + 1.2) * shakeR;
                outPitch += Math.cos(phase * 1.47 + 3.8) * shakeR * 0.75;

                CameraFrame frame = new CameraFrame(scene.instanceId(), camera.key(), camera.mode(), outPosition,
                        new Vec3d(outPitch, outYaw, rotation.z),
                        camera.lookAtOffset() == null ? null : scene.options().anchor().add(camera.lookAtOffset()),
                        shakeT, shakeR, frequency);

                // A specialized camera mod can fully own this channel. Otherwise CineFX applies it natively.
                if (!CineFxRuntime.INSTANCE.cinematicBackends().camera(frame)) {
                    position = outPosition;
                    yaw = outYaw;
                    pitch = outPitch;
                }
                used = true;
            }
        }

        if (!used) return null;
        pitch = Math.max(-89.9, Math.min(89.9, pitch));
        return new Sample(position, (float) yaw, (float) pitch);
    }
}
