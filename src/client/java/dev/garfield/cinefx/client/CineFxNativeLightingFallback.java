package dev.garfield.cinefx.client;

import dev.garfield.cinefx.CineFx;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.client.api.LightFrame;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Cheap last-resort lighting preview for clients without a shader/dynamic-light backend.
 *
 * This deliberately does not touch Minecraft's block light engine. Higher priority integrations
 * still get first refusal; when nobody consumes the light frame this fallback makes the authored
 * colour/intensity/radius immediately visible as a camera-space illumination wash. That means the
 * Studio never presents dead light controls on a plain Fabric client.
 */
final class CineFxNativeLightingFallback {
    private static volatile List<LightFrame> lights = List.of();
    private static volatile long updatedNanos;

    private CineFxNativeLightingFallback() { }

    static void register() {
        CineFxRuntime.INSTANCE.lightingBackends().register(
                Identifier.of(CineFx.MOD_ID, "native_lighting_preview"),
                Integer.MIN_VALUE,
                CineFxNativeLightingFallback::accept);
        HudElementRegistry.addLast(
                Identifier.of(CineFx.MOD_ID, "native_lighting_preview"),
                CineFxNativeLightingFallback::render);
    }

    private static boolean accept(List<LightFrame> frame) {
        lights = frame == null ? List.of() : List.copyOf(frame);
        updatedNanos = System.nanoTime();
        return true;
    }

    static void clear() {
        lights = List.of();
        updatedNanos = 0L;
    }

    private static void render(DrawContext context, RenderTickCounter ignored) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.gameRenderer == null) return;
        if (System.nanoTime() - updatedNanos > 250_000_000L) return;
        List<LightFrame> snapshot = lights;
        if (snapshot.isEmpty()) return;

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        double total = 0.0;
        double red = 0.0, green = 0.0, blue = 0.0;

        for (LightFrame light : snapshot) {
            if (light == null || light.position() == null) continue;
            double radius = Math.max(0.001, light.radius());
            Vec3d toCamera = camera.subtract(light.position());
            double distance = Math.sqrt(toCamera.lengthSquared());
            if (distance >= radius) continue;

            double attenuation = 1.0 - distance / radius;
            attenuation *= attenuation;
            double influence = attenuation * Math.max(0.0, light.intensity());

            if (light.kind() == SceneLight.Kind.SPOT && light.direction() != null && distance > 1.0e-5) {
                Vec3d direction = light.direction().normalize();
                double cos = direction.dotProduct(toCamera.normalize());
                double outerCos = Math.cos(Math.toRadians(Math.max(0.0, light.outerConeDegrees())));
                double innerCos = Math.cos(Math.toRadians(Math.max(0.0, light.innerConeDegrees())));
                if (cos <= outerCos) continue;
                if (innerCos > outerCos && cos < innerCos) {
                    influence *= (cos - outerCos) / (innerCos - outerCos);
                }
            }

            int color = light.colorArgb();
            double alpha = ((color >>> 24) & 255) / 255.0;
            influence *= alpha;
            if (influence <= 0.0001) continue;
            total += influence;
            red += ((color >>> 16) & 255) * influence;
            green += ((color >>> 8) & 255) * influence;
            blue += (color & 255) * influence;
        }

        if (total <= 0.0001) return;
        int r = (int)Math.round(red / total);
        int g = (int)Math.round(green / total);
        int b = (int)Math.round(blue / total);
        // Enough to be obvious while authoring, intentionally capped so it cannot blind gameplay.
        int alpha = (int)Math.round(Math.min(82.0, 10.0 + Math.log1p(total) * 31.0));
        int color = (alpha << 24) | (r << 16) | (g << 8) | b;
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), color);
    }
}
