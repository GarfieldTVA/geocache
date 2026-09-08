package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.LightFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Samples all active light elements once per rendered frame and hands them to the highest-priority integration backend. */
final class CineFxLightingBridge {
    private static final double CULL_DISTANCE_SQUARED = 640.0 * 640.0;

    private CineFxLightingBridge() { }

    static void render(WorldRenderContext ignored) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        ArrayList<LightFrame> lights = new ArrayList<>();
        VisualClaims claims = new VisualClaims();

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            for (SceneElement element : scene.elementsByPriority()) {
                if (!(element instanceof SceneLight light) || !light.activeAt(sceneTick)) continue;
                if (!claims.claim("light:" + light.key(), light.conflictPolicy())) continue;
                double localTick = sceneTick - light.startTick();
                Transform transform = light.transform().sample(localTick)
                        .combine(light.motion().sample(localTick, scene.options().seed()));
                Vec3d position = scene.options().anchor().add(light.baseOffset()).add(transform.translation());
                if (position.squaredDistanceTo(cameraPos) > CULL_DISTANCE_SQUARED) continue;
                Vec3d direction = rotate(light.direction(), transform.rotationDegrees()).normalize();
                lights.add(new LightFrame(
                        scene.instanceId(), light.key(), light.kind(), position, direction,
                        light.color().sample(localTick),
                        Math.max(0.0, light.intensity().sample(localTick)),
                        Math.max(0.0, light.radius().sample(localTick)),
                        light.innerConeDegrees(), light.outerConeDegrees()));
            }
        }

        CineFxRuntime.INSTANCE.lightingBackends().apply(List.copyOf(lights));
    }

    private static Vec3d rotate(Vec3d input, Vec3d degrees) {
        double x = input.x, y = input.y, z = input.z;
        double rx = Math.toRadians(degrees.x);
        double ry = Math.toRadians(degrees.y);
        double rz = Math.toRadians(degrees.z);

        double cos = Math.cos(rx), sin = Math.sin(rx);
        double y1 = y * cos - z * sin, z1 = y * sin + z * cos;
        y = y1; z = z1;

        cos = Math.cos(ry); sin = Math.sin(ry);
        double x1 = x * cos + z * sin, z2 = -x * sin + z * cos;
        x = x1; z = z2;

        cos = Math.cos(rz); sin = Math.sin(rz);
        double x2 = x * cos - y * sin, y2 = x * sin + y * cos;
        return new Vec3d(x2, y2, z);
    }
}
