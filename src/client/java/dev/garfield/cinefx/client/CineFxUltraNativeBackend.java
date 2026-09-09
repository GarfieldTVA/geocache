package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.QualityTier;
import dev.garfield.cinefx.api.SceneLight;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.LightFrame;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

/** Lowest-priority built-in Ultra implementation. Premium shader/physics/audio backends can replace it channel-by-channel. */
final class CineFxUltraNativeBackend implements UltraBackend {
    @Override
    public boolean postProcess(PostProcessFrame frame) {
        return CineFxUltraPostOverlay.accept(frame);
    }

    @Override
    public boolean lightRigs(SceneRenderContext context, List<LightRigFrame> frames) {
        CineFxUltraState.acceptLights(frames);
        ArrayList<LightFrame> lights = new ArrayList<>();
        for (LightRigFrame rig : frames) {
            int index = 0;
            for (SampledRigLight light : rig.lights()) {
                SceneLight.Kind kind = light.kind() == UltraEventElement.LightKind.POINT
                        ? SceneLight.Kind.POINT : SceneLight.Kind.SPOT;
                lights.add(new LightFrame(rig.sceneInstanceId(), rig.elementKey() + "#" + index++, kind,
                        light.position(), light.direction(), light.colorArgb(), light.intensity(), light.radius(),
                        light.innerConeDegrees(), light.outerConeDegrees()));
            }
        }
        if (!lights.isEmpty()) CineFxRuntime.INSTANCE.lightingBackends().apply(List.copyOf(lights));
        renderVolumetrics(context, frames);
        return true;
    }

    @Override
    public boolean fractures(SceneRenderContext context, List<FractureFrame> frames) {
        if (frames.isEmpty()) return false;
        QualityTier quality = AdaptiveQualityController.current();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (FractureFrame frame : frames) {
                int budget = Math.min(frame.shardCount(), switch (quality) {
                    case SAFE -> 80; case LOW -> 180; case MEDIUM -> 420; case HIGH -> 900; case ULTRA -> 1800;
                });
                int stride = Math.max(1, (int)Math.ceil(frame.shardCount() / (double)Math.max(1, budget)));
                double duration = parse(frame.parameters().get("fracture_duration"), 100.0);
                double t = clamp01(frame.localTick() / Math.max(1.0, duration));
                if (frame.reverse()) t = 1.0 - t;
                double seconds = t * duration / 20.0;
                int emitted = 0;
                for (int shard = 0; shard < frame.shardCount() && emitted < budget; shard += stride, emitted++) {
                    long h = mix(frame.seed() ^ (long)frame.elementKey().hashCode() * 31L ^ shard * 0x9E3779B97F4A7C15L);
                    Vec3d random = unit(h);
                    Vec3d impulse = frame.impulse().lengthSquared() < 1.0e-8 ? new Vec3d(0, 1, 0) : frame.impulse().normalize();
                    Vec3d direction = random.multiply(0.75).add(impulse.multiply(0.55));
                    if (direction.lengthSquared() < 1.0e-8) direction = new Vec3d(0, 1, 0);
                    else direction = direction.normalize();
                    double speed = Math.max(0.0, frame.force()) * (0.45 + unit01(h >>> 13) * 0.9);
                    Vec3d center = frame.worldPosition().add(direction.multiply(speed * seconds * 2.2))
                            .add(0, -Math.max(0.0, frame.gravity()) * seconds * seconds * 10.0, 0);
                    if (frame.collideGround() && center.y < frame.worldPosition().y) center = new Vec3d(center.x, frame.worldPosition().y, center.z);
                    double size = 0.06 + unit01(h >>> 29) * 0.24;
                    double spin = frame.angularSpeed() * seconds * (unit01(h >>> 41) * 2.0 - 1.0);
                    Matrix4f shardMatrix = new Matrix4f().translation((float)center.x, (float)center.y, (float)center.z)
                            .rotateXYZ((float)spin, (float)(spin * 0.7), (float)(spin * 1.3))
                            .scale((float)size, (float)(size * (0.55 + unit01(h >>> 7))), (float)size);
                    drawBox(vertices, base, shardMatrix, context.cameraPosition(), multiplyAlpha(frame.tintArgb(), frame.opacity()));
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    @Override
    public boolean softBodies(SceneRenderContext context, List<SoftBodyFrame> frames) {
        return CineFxUltraPhysics.softBodies(context, frames);
    }

    @Override
    public boolean proceduralRigs(List<ProceduralRigFrame> frames) {
        CineFxUltraState.acceptRigs(frames);
        return true;
    }

    @Override
    public boolean particleFields(SceneRenderContext context, List<ParticleFieldFrame> frames) {
        return CineFxUltraPhysics.particleFields(context, frames);
    }

    @Override
    public boolean cameraRigs(List<CameraRigFrame> frames) {
        CineFxUltraState.acceptCameras(frames);
        return true;
    }

    @Override
    public boolean spatialAudio(List<SpatialAudioFrame> frames) {
        return CineFxUltraSpatialAudio.apply(frames);
    }

    @Override
    public boolean materialEffects(List<MaterialEffectFrame> frames) {
        CineFxUltraState.acceptMaterials(frames);
        return true;
    }

    @Override
    public boolean worldDeforms(SceneRenderContext context, List<WorldDeformFrame> frames) {
        if (frames.isEmpty()) return false;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (WorldDeformFrame frame : frames) renderDeform(vertices, base, context.cameraPosition(), frame);
        });
        context.matrices().pop();
        return true;
    }

    @Override
    public boolean portals(SceneRenderContext context, List<PortalFrame> frames) {
        if (frames.isEmpty()) return false;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (PortalFrame frame : frames) renderPortal(vertices, base, context.cameraPosition(), frame);
        });
        context.matrices().pop();
        return true;
    }

    @Override
    public void editorMarkers(List<EditorMarkerFrame> frames) {
        CineFxUltraState.acceptMarkers(frames);
    }

    private static void renderVolumetrics(SceneRenderContext context, List<LightRigFrame> frames) {
        boolean any = frames.stream().flatMap(frame -> frame.lights().stream()).anyMatch(light -> light.volumetric() > 0.001);
        if (!any) return;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            for (LightRigFrame rig : frames) for (SampledRigLight light : rig.lights()) {
                double volume = clamp01(light.volumetric());
                if (volume <= 0.001 || light.intensity() <= 0.001) continue;
                double length = Math.max(1.0, light.radius());
                Vec3d end = light.position().add(light.direction().multiply(length));
                double width = Math.max(0.03, Math.tan(Math.toRadians(Math.max(1.0, light.outerConeDegrees()))) * length * 0.10);
                int color = multiplyAlpha(light.colorArgb(), Math.min(0.34, volume * 0.18 * Math.max(0.3, light.intensity())));
                drawBeam(vertices, matrix, context.cameraPosition(), light.position(), end, width, color);
                if (light.kind() == UltraEventElement.LightKind.AREA || light.kind() == UltraEventElement.LightKind.TUBE) {
                    drawBeam(vertices, matrix, context.cameraPosition(), light.position().add(0.18, 0, 0), end.add(0.18, 0, 0), width * 0.62, multiplyAlpha(color, 0.65));
                }
            }
        });
        context.matrices().pop();
    }

    private static void renderDeform(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, WorldDeformFrame frame) {
        double progress = clamp01(frame.progress());
        double radius = Math.max(0.05, frame.radius()) * progress;
        double amplitude = frame.amplitude() * progress;
        int color = multiplyAlpha(frame.colorArgb(), 0.55);
        switch (frame.mode()) {
            case CRACK, FISSURE -> {
                int rays = frame.mode() == UltraEventElement.DeformMode.FISSURE ? 18 : 11;
                for (int i = 0; i < rays; i++) {
                    long h = mix(frame.seed() + i * 0x9E3779B97F4A7C15L);
                    double angle = Math.PI * 2.0 * i / rays + (unit01(h) - 0.5) * 0.45;
                    Vec3d a = frame.center().add(Math.cos(angle) * radius * 0.08, 0.025, Math.sin(angle) * radius * 0.08);
                    Vec3d b = frame.center().add(Math.cos(angle) * radius * (0.55 + unit01(h >>> 19) * 0.45),
                            -Math.abs(amplitude) * 0.08 * unit01(h >>> 31),
                            Math.sin(angle) * radius * (0.55 + unit01(h >>> 19) * 0.45));
                    drawBeam(vertices, matrix, camera, a, b, Math.max(0.015, radius * 0.012), color);
                }
            }
            case LIFT, SINK, WAVE, PULSE -> {
                int rings = 7;
                for (int r = 1; r <= rings; r++) {
                    double rr = radius * r / rings;
                    double y = frame.mode() == UltraEventElement.DeformMode.SINK ? -amplitude * (1.0 - r / (double)rings)
                            : amplitude * Math.sin(frame.localTick() * 0.08 * frame.frequency() - r * 0.8) * (1.0 - r / (double)(rings + 1));
                    drawRing(vertices, matrix, camera, frame.center().add(0, y * 0.18 + 0.02, 0), rr,
                            Math.max(0.012, radius * 0.008), 48, multiplyAlpha(color, 1.0 - r * 0.08));
                }
            }
            case GROW, REBUILD -> {
                int pillars = 22;
                for (int i = 0; i < pillars; i++) {
                    long h = mix(frame.seed() + i * 811L);
                    double a = unit01(h) * Math.PI * 2.0;
                    double r = Math.sqrt(unit01(h >>> 18)) * radius;
                    double y = Math.abs(amplitude) * (0.3 + unit01(h >>> 37)) * progress;
                    Vec3d center = frame.center().add(Math.cos(a) * r, y * 0.5, Math.sin(a) * r);
                    Matrix4f box = new Matrix4f().translation((float)center.x, (float)center.y, (float)center.z)
                            .scale(0.15f, (float)Math.max(0.05, y), 0.15f);
                    drawBox(vertices, matrix, box, camera, multiplyAlpha(color, 0.65));
                }
            }
            case BIOME_ILLUSION -> drawDisc(vertices, matrix, camera, frame.center().add(0, 0.018, 0), radius, 64, multiplyAlpha(color, 0.28));
        }
    }

    private static void renderPortal(VertexConsumer vertices, Matrix4f base, Vec3d camera, PortalFrame frame) {
        double pulse = 0.5 + 0.5 * Math.sin(frame.localTick() * 0.12);
        int rim = multiplyAlpha(frame.rimColorArgb(), frame.opacity());
        int core = multiplyAlpha(frame.rimColorArgb(), frame.opacity() * (0.10 + 0.08 * pulse));
        Matrix4fc transform = frame.worldMatrix();
        double hx = Math.abs(frame.size().x) * 0.5;
        double hy = Math.abs(frame.size().y) * 0.5;
        Vec3d a = point(transform, new Vec3d(-hx, -hy, 0));
        Vec3d b = point(transform, new Vec3d(hx, -hy, 0));
        Vec3d c = point(transform, new Vec3d(hx, hy, 0));
        Vec3d d = point(transform, new Vec3d(-hx, hy, 0));
        drawQuad(vertices, base, camera, a, b, c, d, core);
        double edge = Math.max(0.025, Math.min(hx, hy) * 0.035 * (1.0 + frame.distortion() * 0.3));
        drawBeam(vertices, base, camera, a, b, edge, rim);
        drawBeam(vertices, base, camera, b, c, edge, rim);
        drawBeam(vertices, base, camera, c, d, edge, rim);
        drawBeam(vertices, base, camera, d, a, edge, rim);
        int layers = 4;
        for (int i = 1; i <= layers; i++) {
            double inset = i / (double)(layers + 1);
            double sx = hx * (1.0 - inset * 0.78);
            double sy = hy * (1.0 - inset * 0.78);
            double z = Math.sin(frame.localTick() * 0.08 + i) * 0.025 * frame.distortion();
            Vec3d p0 = point(transform, new Vec3d(-sx, -sy, z));
            Vec3d p1 = point(transform, new Vec3d(sx, -sy, z));
            Vec3d p2 = point(transform, new Vec3d(sx, sy, z));
            Vec3d p3 = point(transform, new Vec3d(-sx, sy, z));
            drawBeam(vertices, base, camera, p0, p1, edge * 0.35, multiplyAlpha(rim, 0.40));
            drawBeam(vertices, base, camera, p1, p2, edge * 0.35, multiplyAlpha(rim, 0.40));
            drawBeam(vertices, base, camera, p2, p3, edge * 0.35, multiplyAlpha(rim, 0.40));
            drawBeam(vertices, base, camera, p3, p0, edge * 0.35, multiplyAlpha(rim, 0.40));
        }
    }

    private static void drawRing(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d center,
                                 double radius, double thickness, int segments, int color) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3d p0 = center.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3d p1 = center.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            drawBeam(vertices, matrix, camera, p0, p1, thickness, color);
        }
    }

    private static void drawDisc(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d center,
                                 double radius, int segments, int color) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            drawQuad(vertices, matrix, camera, center,
                    center.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius),
                    center.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), center, color);
        }
    }

    private static void drawBeam(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d a, Vec3d b, double width, int color) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1.0e-10) return;
        direction = direction.normalize();
        Vec3d view = camera.subtract(a.add(b).multiply(0.5));
        if (view.lengthSquared() < 1.0e-10) view = new Vec3d(0, 1, 0); else view = view.normalize();
        Vec3d side = direction.crossProduct(view);
        if (side.lengthSquared() < 1.0e-10) side = direction.crossProduct(new Vec3d(0, 1, 0));
        if (side.lengthSquared() < 1.0e-10) side = new Vec3d(1, 0, 0);
        side = side.normalize().multiply(width);
        drawQuad(vertices, matrix, camera, a.add(side), b.add(side), b.subtract(side), a.subtract(side), color);
        Vec3d side2 = direction.crossProduct(side).normalize().multiply(width);
        drawQuad(vertices, matrix, camera, a.add(side2), b.add(side2), b.subtract(side2), a.subtract(side2), color);
    }

    private static void drawBox(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera, int color) {
        Vec3d[] p = new Vec3d[8];
        int n = 0;
        for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
            p[n++] = point(transform, new Vec3d(x - 0.5, y - 0.5, z - 0.5));
        }
        int[][] faces = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for (int[] f : faces) drawQuad(vertices, base, camera, p[f[0]], p[f[1]], p[f[2]], p[f[3]], color);
    }

    private static void drawQuad(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        emit(vertices, matrix, a.subtract(camera), color); emit(vertices, matrix, b.subtract(camera), color);
        emit(vertices, matrix, c.subtract(camera), color); emit(vertices, matrix, d.subtract(camera), color);
    }

    private static void emit(VertexConsumer vertices, Matrix4f matrix, Vec3d p, int color) {
        vertices.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(color);
    }

    private static Vec3d point(Matrix4fc m, Vec3d p) {
        return new Vec3d(m.m00()*p.x + m.m10()*p.y + m.m20()*p.z + m.m30(),
                m.m01()*p.x + m.m11()*p.y + m.m21()*p.z + m.m31(),
                m.m02()*p.x + m.m12()*p.y + m.m22()*p.z + m.m32());
    }

    private static int multiplyAlpha(int argb, double opacity) {
        int a = (argb >>> 24) & 255; if (a == 0) a = 255;
        a = (int)Math.round(a * clamp01(opacity));
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }
    private static Vec3d unit(long h) { double z = unit01(h)*2-1, a = unit01(h>>>19)*Math.PI*2, r=Math.sqrt(Math.max(0,1-z*z)); return new Vec3d(Math.cos(a)*r,z,Math.sin(a)*r); }
    private static double unit01(long h) { return (mix(h) >>> 11) * 0x1.0p-53; }
    private static long mix(long x) { x ^= x >>> 33; x *= 0xff51afd7ed558ccdl; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53l; return x ^ (x >>> 33); }
    private static double parse(String raw, double fallback) { if (raw == null) return fallback; try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) { return fallback; } }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
