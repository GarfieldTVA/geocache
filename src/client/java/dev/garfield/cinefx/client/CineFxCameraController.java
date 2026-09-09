package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.CinematicBackend.CameraFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Samples scene camera rigs analytically at camera-update time; no camera entity is created. */
public final class CineFxCameraController {
    private static volatile double ultraFovDegrees = -1.0;

    private CineFxCameraController() { }

    public record Sample(Vec3d position, float yaw, float pitch) { }

    public static double ultraFovDegrees() { return ultraFovDegrees; }

    public static Sample sample(Camera vanillaCamera) {
        MinecraftClient client = MinecraftClient.getInstance();
        ultraFovDegrees = -1.0;
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
                if (!raw.activeAt(sceneTick)) continue;

                if (raw instanceof UltraEventElement.CameraRig rig) {
                    if (!claims.claim("camera", rig.conflictPolicy())) continue;
                    double local = sceneTick - rig.startTick();
                    Vec3d target = scene.options().anchor().add(rig.lookAtOffset());
                    Vec3d pathLocal = rig.path() == null ? Vec3d.ZERO : rig.path().sample(local).position();
                    Vec3d desired;
                    switch (rig.mode()) {
                        case ORBIT -> {
                            double radius = parse(rig.parameters(), "radius", 8.0);
                            double height = parse(rig.parameters(), "height", 2.0);
                            double speed = parse(rig.parameters(), "speed", 0.7);
                            if (rig.path() != null) desired = scene.options().anchor().add(pathLocal);
                            else {
                                double angle = Math.toRadians(parse(rig.parameters(), "start_angle", 0.0) + local * speed);
                                desired = target.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                            }
                        }
                        case HANDHELD, FOLLOW, FREE -> desired = position.add(pathLocal);
                        case DOLLY, CRANE, RAIL, LOCKED -> desired = scene.options().anchor().add(pathLocal);
                        default -> desired = scene.options().anchor().add(pathLocal);
                    }
                    if (rig.collideWorld()) desired = collideCamera(client, target, desired);

                    double[] angles = lookAngles(desired, target, yaw, pitch);
                    double outYaw = angles[0];
                    double outPitch = angles[1];
                    double shakeT = Math.max(0.0, rig.shakeTranslation().sample(local));
                    double shakeR = Math.max(0.0, rig.shakeRotation().sample(local));
                    double frequency = Math.max(0.01, rig.shakeFrequency().sample(local));
                    long salt = scene.options().seed() ^ ((long)rig.key().hashCode() << 32) ^ scene.instanceId();
                    double phase = absoluteTick * frequency * 0.73 + (salt & 0xFFFF) * 0.0137;
                    desired = desired.add(
                            Math.sin(phase * 1.37 + 0.4) * shakeT,
                            Math.sin(phase * 1.91 + 2.1) * shakeT * 0.72,
                            Math.cos(phase * 1.61 + 4.7) * shakeT);
                    outYaw += Math.sin(phase * 1.83 + 1.2) * shakeR;
                    outPitch += Math.cos(phase * 1.47 + 3.8) * shakeR * 0.75;

                    double requestedFov = rig.fovDegrees().sample(local);
                    if (requestedFov > 1.0 && Double.isFinite(requestedFov)) ultraFovDegrees = requestedFov;
                    position = desired;
                    yaw = outYaw;
                    pitch = outPitch;
                    used = true;
                    continue;
                }

                if (!(raw instanceof EventElement.Camera camera)) continue;
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
                    double[] angles = lookAngles(worldPosition, target, yaw, pitch);
                    outYaw = angles[0];
                    outPitch = angles[1];
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

    private static Vec3d collideCamera(MinecraftClient client, Vec3d target, Vec3d desired) {
        if (client.world == null) return desired;
        Vec3d delta = desired.subtract(target);
        double distance = delta.length();
        if (distance < 0.001) return desired;
        int samples = Math.max(8, Math.min(96, (int)Math.ceil(distance * 5.0)));
        Vec3d lastFree = target;
        for (int i = 1; i <= samples; i++) {
            double t = i / (double)samples;
            Vec3d p = target.add(delta.multiply(t));
            BlockPos block = BlockPos.ofFloored(p.x, p.y, p.z);
            if (!client.world.getBlockState(block).isAir()) {
                Vec3d back = lastFree.subtract(target);
                if (back.lengthSquared() > 0.0001) return lastFree.add(back.normalize().multiply(-0.12));
                return lastFree;
            }
            lastFree = p;
        }
        return desired;
    }

    private static double[] lookAngles(Vec3d position, Vec3d target, double fallbackYaw, double fallbackPitch) {
        Vec3d delta = target.subtract(position);
        if (delta.lengthSquared() < 1.0e-10) return new double[]{fallbackYaw, fallbackPitch};
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double pitch = Math.toDegrees(-Math.atan2(delta.y, horizontal));
        return new double[]{yaw, pitch};
    }

    private static double parse(Map<String, String> values, String key, double fallback) {
        String raw = values.get(key);
        if (raw == null) return fallback;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
