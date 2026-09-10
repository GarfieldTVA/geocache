package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.QualityTier;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CinematicBackend.AttachmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.InstanceBatchFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MegaEnvironmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.CinematicBackend.ShadowFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SkyFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.VolumeFrame;
import dev.garfield.cinefx.client.api.LightFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Low-priority, dependency-free visual fallback for renderer-specific CineFX channels.
 * It intentionally favors deterministic stylized proxy geometry over silently dropping an effect.
 * A higher-priority renderer can replace every channel independently.
 */
final class CineFxNativeVisualFallback {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final long STALE_NANOS = 350_000_000L;
    private static final Map<String, AttachedSound> ATTACHED_AUDIO = new HashMap<>();
    private static SkyFrame sky;
    private static long skySeenNanos;

    private CineFxNativeVisualFallback() { }

    static boolean renderMeshes(SceneRenderContext context, List<MeshFrame> frames) {
        if (frames.isEmpty()) return false;
        List<MeshFrame> visible = frames.stream().filter(frame -> frame.opacity() > 0.001).toList();
        if (visible.isEmpty()) return true;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (MeshFrame frame : visible) {
                int color = multiplyAlpha(frame.tintArgb(), frame.opacity());
                String shape = frame.parameters().getOrDefault("fallback_shape", shapeFor(frame.modelId()));
                if ("ship".equalsIgnoreCase(shape) || "wedge".equalsIgnoreCase(shape)) {
                    drawWedge(vertices, base, frame.worldMatrix(), context.cameraPosition(), color);
                } else if ("crystal".equalsIgnoreCase(shape) || "diamond".equalsIgnoreCase(shape)) {
                    drawDiamond(vertices, base, frame.worldMatrix(), context.cameraPosition(), color);
                } else {
                    drawBox(vertices, base, frame.worldMatrix(), context.cameraPosition(), color, 1.0);
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    static boolean renderInstances(SceneRenderContext context, List<InstanceBatchFrame> batches) {
        if (batches.isEmpty()) return false;
        QualityTier quality = AdaptiveQualityController.current();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (InstanceBatchFrame batch : batches) {
                int total = batch.instances().size();
                if (total == 0) continue;
                int budget = Math.max(1, (int)Math.ceil(total * quality.instanceFraction()));
                budget = Math.min(budget, quality == QualityTier.ULTRA ? 3000 : 2200);
                int stride = Math.max(1, (int)Math.ceil((double)total / budget));
                int emitted = 0;
                String shape = batch.parameters().getOrDefault("fallback_shape", shapeFor(batch.modelId()));
                for (int i = 0; i < total && emitted < budget; i += stride) {
                    ComplexElement.InstanceSpec spec = batch.instances().get(i);
                    double local = (batch.localTick() + spec.timeOffsetTicks()) * spec.timeScale();
                    Matrix4f matrix = new Matrix4f(batch.rootMatrix());
                    applyInstanceLocal(matrix, spec, local, batch.seed());
                    int color = multiplyAlpha(spec.tint().sample(local), spec.opacity().sample(local));
                    if ("crystal".equalsIgnoreCase(shape) || "diamond".equalsIgnoreCase(shape)) {
                        drawDiamond(vertices, base, matrix, context.cameraPosition(), color);
                    } else if ("ship".equalsIgnoreCase(shape) || "wedge".equalsIgnoreCase(shape)) {
                        drawWedge(vertices, base, matrix, context.cameraPosition(), color);
                    } else {
                        drawBox(vertices, base, matrix, context.cameraPosition(), color, 0.8);
                    }
                    emitted++;
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    static boolean renderShadows(SceneRenderContext context, List<ShadowFrame> shadows) {
        if (shadows.isEmpty()) return false;
        QualityTier quality = AdaptiveQualityController.current();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (ShadowFrame frame : shadows) {
                if (frame.radius() <= 0.0 || frame.opacity() <= 0.001) continue;
                ComplexElement.ShadowMode mode = frame.mode();
                if (mode == ComplexElement.ShadowMode.GEOMETRY && !quality.geometryShadows()) {
                    mode = ComplexElement.ShadowMode.PROJECTED;
                }
                Vec3d center = frame.worldPosition().add(0, 0.018, 0);
                Vec3d xAxis = horizontalAxis(frame.worldMatrix(), true);
                Vec3d zAxis = horizontalAxis(frame.worldMatrix(), false);
                double softness = clamp01(frame.softness());
                int color = withAlpha(0x000000, (int)Math.round(210.0 * clamp01(frame.opacity()) * (1.0 - 0.28 * softness)));
                if (mode == ComplexElement.ShadowMode.BLOB) {
                    drawEllipse(vertices, base, center, xAxis, zAxis, frame.radius(), frame.radius(), 28,
                            context.cameraPosition(), color);
                } else if (mode == ComplexElement.ShadowMode.PROJECTED) {
                    drawEllipse(vertices, base, center, xAxis, zAxis, frame.radius() * 1.45, frame.radius() * 0.72, 32,
                            context.cameraPosition(), color);
                } else {
                    int lobe = multiplyAlpha(color, 0.72);
                    drawEllipse(vertices, base, center, xAxis, zAxis, frame.radius() * 1.25, frame.radius() * 0.55, 32,
                            context.cameraPosition(), color);
                    drawEllipse(vertices, base, center.add(zAxis.multiply(frame.radius() * 0.38)), xAxis, zAxis,
                            frame.radius() * 0.72, frame.radius() * 0.38, 24, context.cameraPosition(), lobe);
                    drawEllipse(vertices, base, center.subtract(zAxis.multiply(frame.radius() * 0.32)), xAxis, zAxis,
                            frame.radius() * 0.62, frame.radius() * 0.34, 24, context.cameraPosition(), lobe);
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    static boolean renderVolumes(SceneRenderContext context, List<VolumeFrame> volumes) {
        if (volumes.isEmpty()) return false;
        QualityTier quality = AdaptiveQualityController.current();
        if (!quality.volumes()) return true;
        int shells = quality == QualityTier.ULTRA ? 3 : 2;
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (VolumeFrame frame : volumes) {
                double density = clamp01(frame.density());
                if (density <= 0.002) continue;
                int originalAlpha = (frame.colorArgb() >>> 24) & 255;
                if (originalAlpha == 0) originalAlpha = 128;
                int alpha = (int)Math.round(originalAlpha * Math.min(0.55, 0.13 + density * 0.38) / shells);
                int color = (frame.colorArgb() & 0x00FFFFFF) | (Math.max(2, alpha) << 24);
                for (int shell = 0; shell < shells; shell++) {
                    double scale = 0.58 + 0.42 * (shell + 1.0) / shells;
                    Matrix4f matrix = new Matrix4f(frame.worldMatrix()).scale((float)scale);
                    switch (frame.shape()) {
                        case BOX -> drawBox(vertices, base, matrix, context.cameraPosition(), color, 1.0);
                        case CYLINDER -> drawCylinder(vertices, base, matrix, context.cameraPosition(), color, 18);
                        case SPHERE -> drawSphere(vertices, base, matrix, context.cameraPosition(), color, 10, 7);
                    }
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    static boolean renderMegaEnvironments(SceneRenderContext context, List<MegaEnvironmentFrame> environments) {
        if (environments.isEmpty()) return false;
        QualityTier quality = AdaptiveQualityController.current();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f base = entry.getPositionMatrix();
            for (MegaEnvironmentFrame environment : environments) {
                int total = environment.cells().size();
                if (total == 0) continue;
                int budget = Math.max(1, (int)Math.ceil(total * Math.max(0.10, quality.instanceFraction())));
                budget = Math.min(budget, 700);
                int stride = Math.max(1, (int)Math.ceil((double)total / budget));
                for (int i = 0, emitted = 0; i < total && emitted < budget; i += stride, emitted++) {
                    AdvancedEventElement.EnvironmentCell cell = environment.cells().get(i);
                    if (cell.lodLevel() > quality.ordinal() + 1) continue;
                    Matrix4f matrix = new Matrix4f(environment.rootMatrix())
                            .translate((float)cell.offset().x, (float)cell.offset().y, (float)cell.offset().z)
                            .scale((float)cell.bounds().x, (float)cell.bounds().y, (float)cell.bounds().z);
                    int rgb = 0x303030 | (cell.modelId().hashCode() & 0x00BFCFCF);
                    int color = 0x40000000 | (rgb & 0x00FFFFFF);
                    drawBox(vertices, base, matrix, context.cameraPosition(), color, 1.0);
                }
            }
        });
        context.matrices().pop();
        return true;
    }

    static boolean renderAttachments(SceneRenderContext context, List<AttachmentFrame> attachments) {
        if (attachments.isEmpty()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        QualityTier quality = AdaptiveQualityController.current();
        ArrayList<LightFrame> lights = new ArrayList<>();
        ArrayList<AttachmentFrame> geometry = new ArrayList<>();
        long now = System.nanoTime();

        for (AttachmentFrame frame : attachments) {
            double local = frame.localTick();
            if (frame.payload() instanceof AdvancedEventElement.LightPayload light) {
                lights.add(new LightFrame(frame.sceneInstanceId(), frame.elementKey(), light.kind(), frame.worldPosition(),
                        light.direction(), light.color().sample(local), Math.max(0.0, light.intensity().sample(local)),
                        Math.max(0.0, light.radius().sample(local)), light.innerConeDegrees(), light.outerConeDegrees()));
            } else if (frame.payload() instanceof AdvancedEventElement.EmitterPayload emitter) {
                spawnAttachedParticles(client, frame, emitter, quality);
            } else if (frame.payload() instanceof AdvancedEventElement.AudioPayload audio) {
                updateAttachedAudio(client, frame, audio, now);
            } else {
                geometry.add(frame);
            }
        }
        if (!lights.isEmpty()) CineFxRuntime.INSTANCE.lightingBackends().apply(List.copyOf(lights));
        if (!geometry.isEmpty()) renderAttachmentGeometry(context, geometry);
        return true;
    }

    static boolean acceptSky(SkyFrame frame) {
        sky = frame;
        skySeenNanos = System.nanoTime();
        return true;
    }

    static void renderSkyHud(DrawContext context, RenderTickCounter ignored) {
        SkyFrame frame = sky;
        if (frame == null || System.nanoTime() - skySeenNanos > STALE_NANOS) return;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int bands = 10;
        int skyHeight = Math.max(1, (int)(height * 0.64));
        for (int i = 0; i < bands; i++) {
            double t = (i + 0.5) / bands;
            int mixed = lerpColor(frame.zenithColorArgb(), frame.horizonColorArgb(), t);
            double alphaScale = 0.11 + 0.08 * frame.eclipse() + 0.035 * frame.aurora();
            mixed = forceAlpha(mixed, (int)Math.round(255 * Math.min(0.30, alphaScale)));
            int top = skyHeight * i / bands;
            int bottom = skyHeight * (i + 1) / bands + 1;
            context.fill(0, top, width, bottom, mixed);
        }
        if (frame.eclipse() > 0.001) {
            context.fill(0, 0, width, height,
                    forceAlpha(0xFF000000, (int)Math.round(95 * clamp01(frame.eclipse()))));
        }
        if (frame.aurora() > 0.01) {
            int stripAlpha = (int)Math.round(28 * clamp01(frame.aurora()));
            int strips = 7;
            for (int i = 0; i < strips; i++) {
                int x = (int)((i + 0.25) * width / strips);
                int w = Math.max(3, width / 28);
                int color = forceAlpha((i & 1) == 0 ? 0xFF63FFD4 : 0xFF8E70FF, stripAlpha);
                context.fill(x, 0, Math.min(width, x + w), Math.max(1, skyHeight / 2), color);
            }
        }
    }

    static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        if (client.getSoundManager() != null) {
            ATTACHED_AUDIO.entrySet().removeIf(entry -> {
                AttachedSound sound = entry.getValue();
                if (now - sound.lastUpdateNanos <= STALE_NANOS) return false;
                client.getSoundManager().stop(sound);
                return true;
            });
        }
        if (now - skySeenNanos > STALE_NANOS) sky = null;
    }

    static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() != null) {
            for (AttachedSound sound : ATTACHED_AUDIO.values()) client.getSoundManager().stop(sound);
        }
        ATTACHED_AUDIO.clear();
        sky = null;
        skySeenNanos = 0L;
    }

    private static void renderAttachmentGeometry(SceneRenderContext context, List<AttachmentFrame> geometry) {
        MinecraftClient client = MinecraftClient.getInstance();
        for (AttachmentFrame frame : geometry) {
            double local = frame.localTick();
            if (frame.payload() instanceof AdvancedEventElement.TextPayload text) {
                renderAttachedText(client, context, frame, text, local);
            } else {
                context.matrices().push();
                context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
                    if (frame.payload() instanceof AdvancedEventElement.BeamPayload beam) {
                        int color = beam.color().sample(local);
                        drawBeam(vertices, entry.getPositionMatrix(), frame.worldMatrix(), beam.from(), beam.to(),
                                Math.max(0.002, beam.width().sample(local)), context.cameraPosition(), color);
                    } else if (frame.payload() instanceof AdvancedEventElement.CustomPayload custom) {
                        int color = 0xC0000000 | (custom.type().hashCode() & 0x00FFFFFF);
                        drawDiamond(vertices, entry.getPositionMatrix(), frame.worldMatrix(), context.cameraPosition(), color);
                    }
                });
                context.matrices().pop();
            }
        }
    }

    private static void renderAttachedText(MinecraftClient client, SceneRenderContext context, AttachmentFrame frame,
                                           AdvancedEventElement.TextPayload text, double local) {
        if (client.textRenderer == null || text.textTemplate().isEmpty()) return;
        StyleSpriteSource.Font font = new StyleSpriteSource.Font(text.fontId());
        Text value = Text.literal(text.textTemplate()).styled(style -> style.withFont(font));
        int width = client.textRenderer.getWidth(value);
        float scale = (float)(0.025 * Math.max(0.001, text.scale().sample(local)));
        context.matrices().push();
        Vec3d pos = frame.worldPosition();
        context.matrices().translate(pos.x - context.cameraPosition().x, pos.y - context.cameraPosition().y,
                pos.z - context.cameraPosition().z);
        if (text.billboard()) context.matrices().multiply(context.camera().getRotation());
        context.matrices().scale(scale, -scale, scale);
        RenderCommandQueue textQueue = context.commandQueue().getBatchingQueue(2);
        textQueue.submitText(context.matrices(), -width / 2.0f, 0.0f, value.asOrderedText(), false,
                text.seeThrough() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                FULL_BRIGHT, text.color().sample(local), 0, 0);
        context.matrices().pop();
    }

    private static void spawnAttachedParticles(MinecraftClient client, AttachmentFrame frame,
                                               AdvancedEventElement.EmitterPayload emitter, QualityTier quality) {
        if (client.world == null) return;
        var raw = Registries.PARTICLE_TYPE.get(emitter.particleId());
        if (!(raw instanceof SimpleParticleType particle)) return;
        double local = frame.localTick();
        int max = Math.max(1, (int)Math.round(emitter.maxParticlesPerFrame() * quality.particleScale()));
        int count = Math.min(max, (int)Math.ceil(Math.max(0.0, emitter.ratePerSecond().sample(local)) / 60.0
                * quality.particleScale()));
        if (count <= 0) return;
        double spread = Math.max(0.0, emitter.spread().sample(local));
        double speed = Math.max(0.0, emitter.speed().sample(local));
        java.util.Random random = new java.util.Random(frame.seed() ^ Double.doubleToLongBits(local) ^ frame.elementKey().hashCode());
        for (int i = 0; i < count; i++) {
            Vec3d dir = randomUnit(random);
            Vec3d pos = frame.worldPosition().add(dir.multiply(spread * random.nextDouble()));
            Vec3d velocity = dir.multiply(speed);
            client.world.addParticleClient(particle, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void updateAttachedAudio(MinecraftClient client, AttachmentFrame frame,
                                            AdvancedEventElement.AudioPayload audio, long now) {
        if (client.getSoundManager() == null) return;
        String key = frame.sceneInstanceId() + ":" + frame.elementKey();
        AttachedSound sound = ATTACHED_AUDIO.get(key);
        if (sound == null || !sound.matches(audio)) {
            if (sound != null) client.getSoundManager().stop(sound);
            sound = new AttachedSound(audio, frame);
            ATTACHED_AUDIO.put(key, sound);
            client.getSoundManager().play(sound);
        }
        sound.update(audio, frame, now);
    }

    private static void applyInstanceLocal(Matrix4f matrix, ComplexElement.InstanceSpec spec, double local, long seed) {
        AdvancedTransform advanced = spec.transform().sample(local);
        Transform motion = spec.motion().sample(local, seed);
        Vec3d translation = spec.baseOffset().add(advanced.translation()).add(motion.translation());
        Vec3d rotation = advanced.rotationDegrees().add(motion.rotationDegrees());
        Vec3d scale = new Vec3d(advanced.scale().x * motion.scale().x,
                advanced.scale().y * motion.scale().y, advanced.scale().z * motion.scale().z);
        Vec3d pivot = advanced.pivot();
        matrix.translate((float)translation.x, (float)translation.y, (float)translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)pivot.x, (float)pivot.y, (float)pivot.z);
        if (rotation.x != 0.0) matrix.rotateX((float)Math.toRadians(rotation.x));
        if (rotation.y != 0.0) matrix.rotateY((float)Math.toRadians(rotation.y));
        if (rotation.z != 0.0) matrix.rotateZ((float)Math.toRadians(rotation.z));
        matrix.scale((float)scale.x, (float)scale.y, (float)scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);
    }

    private static void drawBox(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera,
                                int color, double size) {
        double h = size * 0.5;
        Vec3d[] p = new Vec3d[] {
                wp(transform, -h,-h,-h,camera), wp(transform, h,-h,-h,camera),
                wp(transform, h,h,-h,camera), wp(transform, -h,h,-h,camera),
                wp(transform, -h,-h,h,camera), wp(transform, h,-h,h,camera),
                wp(transform, h,h,h,camera), wp(transform, -h,h,h,camera)
        };
        quad(vertices, base, p[0],p[1],p[2],p[3],color);
        quad(vertices, base, p[5],p[4],p[7],p[6],color);
        quad(vertices, base, p[4],p[0],p[3],p[7],shade(color,0.82));
        quad(vertices, base, p[1],p[5],p[6],p[2],shade(color,0.92));
        quad(vertices, base, p[3],p[2],p[6],p[7],shade(color,1.08));
        quad(vertices, base, p[4],p[5],p[1],p[0],shade(color,0.72));
    }

    private static void drawWedge(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera, int color) {
        Vec3d noseTop = wp(transform,0,0.22,0.72,camera);
        Vec3d noseBottom = wp(transform,0,-0.18,0.68,camera);
        Vec3d lt = wp(transform,-0.52,0.22,-0.48,camera);
        Vec3d rt = wp(transform,0.52,0.22,-0.48,camera);
        Vec3d lb = wp(transform,-0.43,-0.22,-0.48,camera);
        Vec3d rb = wp(transform,0.43,-0.22,-0.48,camera);
        quad(vertices,base,lt,rt,noseTop,noseTop,color);
        quad(vertices,base,rb,lb,noseBottom,noseBottom,shade(color,0.65));
        quad(vertices,base,lt,noseTop,noseBottom,lb,shade(color,0.80));
        quad(vertices,base,noseTop,rt,rb,noseBottom,shade(color,0.92));
        quad(vertices,base,rt,lt,lb,rb,shade(color,0.72));
    }

    private static void drawDiamond(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera, int color) {
        Vec3d top = wp(transform,0,0.62,0,camera);
        Vec3d bottom = wp(transform,0,-0.62,0,camera);
        Vec3d[] e = {
                wp(transform,0.48,0,0,camera), wp(transform,0,0,0.48,camera),
                wp(transform,-0.48,0,0,camera), wp(transform,0,0,-0.48,camera)
        };
        for (int i=0;i<4;i++) {
            Vec3d a=e[i], b=e[(i+1)%4];
            quad(vertices,base,top,a,b,top,shade(color,0.90+0.05*i));
            quad(vertices,base,b,a,bottom,bottom,shade(color,0.66+0.05*i));
        }
    }

    private static void drawCylinder(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera,
                                     int color, int segments) {
        for (int i=0;i<segments;i++) {
            double a0=Math.PI*2*i/segments, a1=Math.PI*2*(i+1)/segments;
            Vec3d b0=wp(transform,Math.cos(a0)*0.5,-0.5,Math.sin(a0)*0.5,camera);
            Vec3d b1=wp(transform,Math.cos(a1)*0.5,-0.5,Math.sin(a1)*0.5,camera);
            Vec3d t1=wp(transform,Math.cos(a1)*0.5,0.5,Math.sin(a1)*0.5,camera);
            Vec3d t0=wp(transform,Math.cos(a0)*0.5,0.5,Math.sin(a0)*0.5,camera);
            quad(vertices,base,b0,b1,t1,t0,color);
            Vec3d cb=wp(transform,0,-0.5,0,camera), ct=wp(transform,0,0.5,0,camera);
            quad(vertices,base,cb,b1,b0,cb,color);
            quad(vertices,base,ct,t0,t1,ct,color);
        }
    }

    private static void drawSphere(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d camera,
                                   int color, int longitude, int latitude) {
        for (int y=0;y<latitude;y++) {
            double p0=-Math.PI/2 + Math.PI*y/latitude;
            double p1=-Math.PI/2 + Math.PI*(y+1)/latitude;
            for (int x=0;x<longitude;x++) {
                double a0=Math.PI*2*x/longitude, a1=Math.PI*2*(x+1)/longitude;
                Vec3d v00=spherePoint(p0,a0), v01=spherePoint(p0,a1), v11=spherePoint(p1,a1), v10=spherePoint(p1,a0);
                quad(vertices,base,wp(transform,v00,camera),wp(transform,v01,camera),
                        wp(transform,v11,camera),wp(transform,v10,camera),color);
            }
        }
    }

    private static Vec3d spherePoint(double pitch, double yaw) {
        double cp=Math.cos(pitch)*0.5;
        return new Vec3d(Math.cos(yaw)*cp, Math.sin(pitch)*0.5, Math.sin(yaw)*cp);
    }

    private static void drawEllipse(VertexConsumer vertices, Matrix4f base, Vec3d center, Vec3d xAxis, Vec3d zAxis,
                                    double rx, double rz, int segments, Vec3d camera, int color) {
        Vec3d c=center.subtract(camera);
        for (int i=0;i<segments;i++) {
            double a0=Math.PI*2*i/segments, a1=Math.PI*2*(i+1)/segments;
            Vec3d p0=center.add(xAxis.multiply(Math.cos(a0)*rx)).add(zAxis.multiply(Math.sin(a0)*rz)).subtract(camera);
            Vec3d p1=center.add(xAxis.multiply(Math.cos(a1)*rx)).add(zAxis.multiply(Math.sin(a1)*rz)).subtract(camera);
            quad(vertices,base,c,p0,p1,c,color);
        }
    }

    private static void drawBeam(VertexConsumer vertices, Matrix4f base, Matrix4fc transform, Vec3d from, Vec3d to,
                                 double width, Vec3d camera, int color) {
        Vec3d a=point(transform,from), b=point(transform,to);
        Vec3d direction=b.subtract(a);
        if (direction.lengthSquared()<1.0e-8) return;
        Vec3d side=direction.crossProduct(new Vec3d(0,1,0));
        if (side.lengthSquared()<1.0e-8) side=direction.crossProduct(new Vec3d(1,0,0));
        side=side.normalize().multiply(width*0.5);
        Vec3d up=direction.normalize().crossProduct(side).normalize().multiply(width*0.5);
        a=a.subtract(camera); b=b.subtract(camera);
        quad(vertices,base,a.subtract(side),a.add(side),b.add(side),b.subtract(side),color);
        quad(vertices,base,a.subtract(up),a.add(up),b.add(up),b.subtract(up),color);
    }

    private static Vec3d horizontalAxis(Matrix4fc matrix, boolean x) {
        Vec3d axis = x ? new Vec3d(matrix.m00(),0,matrix.m02()) : new Vec3d(matrix.m20(),0,matrix.m22());
        if (axis.lengthSquared()<1.0e-8) axis=x?new Vec3d(1,0,0):new Vec3d(0,0,1);
        return axis.normalize();
    }

    private static Vec3d wp(Matrix4fc matrix, double x, double y, double z, Vec3d camera) {
        return point(matrix,new Vec3d(x,y,z)).subtract(camera);
    }
    private static Vec3d wp(Matrix4fc matrix, Vec3d local, Vec3d camera) { return point(matrix,local).subtract(camera); }
    private static Vec3d point(Matrix4fc m, Vec3d p) {
        return new Vec3d(m.m00()*p.x+m.m10()*p.y+m.m20()*p.z+m.m30(),
                m.m01()*p.x+m.m11()*p.y+m.m21()*p.z+m.m31(),
                m.m02()*p.x+m.m12()*p.y+m.m22()*p.z+m.m32());
    }

    private static void quad(VertexConsumer v, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        v.vertex(m,(float)a.x,(float)a.y,(float)a.z).color(color);
        v.vertex(m,(float)b.x,(float)b.y,(float)b.z).color(color);
        v.vertex(m,(float)c.x,(float)c.y,(float)c.z).color(color);
        v.vertex(m,(float)d.x,(float)d.y,(float)d.z).color(color);
    }

    private static Vec3d randomUnit(java.util.Random random) {
        double y=random.nextDouble()*2.0-1.0, angle=random.nextDouble()*Math.PI*2.0;
        double xz=Math.sqrt(Math.max(0.0,1.0-y*y));
        return new Vec3d(Math.cos(angle)*xz,y,Math.sin(angle)*xz);
    }

    private static String shapeFor(Identifier model) {
        String path=model==null?"":model.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("ship")||path.contains("fleet")||path.contains("armada")) return "ship";
        if (path.contains("core")||path.contains("crystal")||path.contains("relic")||path.contains("portal")) return "crystal";
        return "box";
    }

    private static int shade(int argb, double factor) {
        int a=(argb>>>24)&255, r=(argb>>>16)&255, g=(argb>>>8)&255, b=argb&255;
        r=(int)Math.max(0,Math.min(255,Math.round(r*factor)));
        g=(int)Math.max(0,Math.min(255,Math.round(g*factor)));
        b=(int)Math.max(0,Math.min(255,Math.round(b*factor)));
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
    private static int multiplyAlpha(int argb, double opacity) {
        int a=(argb>>>24)&255;
        return (argb&0x00FFFFFF)|((int)Math.round(a*clamp01(opacity))<<24);
    }
    private static int forceAlpha(int argb, int alpha) { return (argb&0x00FFFFFF)|(Math.max(0,Math.min(255,alpha))<<24); }
    private static int withAlpha(int rgb, int alpha) { return (rgb&0x00FFFFFF)|(Math.max(0,Math.min(255,alpha))<<24); }
    private static double clamp01(double value) { return Math.max(0.0,Math.min(1.0,value)); }
    private static int lerpColor(int a, int b, double t) {
        t=clamp01(t);
        int ar=(a>>>16)&255, ag=(a>>>8)&255, ab=a&255;
        int br=(b>>>16)&255, bg=(b>>>8)&255, bb=b&255;
        int r=(int)Math.round(ar+(br-ar)*t), g=(int)Math.round(ag+(bg-ag)*t), bl=(int)Math.round(ab+(bb-ab)*t);
        return 0xFF000000|(r<<16)|(g<<8)|bl;
    }

    private static SoundCategory soundCategory(String raw) {
        try { return SoundCategory.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return SoundCategory.MASTER; }
    }

    private static final class AttachedSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final String soundId;
        private final boolean looping;
        private final boolean spatial;
        private final SoundCategory category;
        private long lastUpdateNanos;
        private boolean done;

        AttachedSound(AdvancedEventElement.AudioPayload payload, AttachmentFrame frame) {
            super(SoundEvent.of(payload.soundId()), soundCategory(payload.category()), Random.create());
            this.soundId=payload.soundId().toString();
            this.looping=payload.looping();
            this.spatial=payload.spatial();
            this.category=soundCategory(payload.category());
            this.attenuationType=spatial?SoundInstance.AttenuationType.LINEAR:SoundInstance.AttenuationType.NONE;
            this.repeat=looping;
            this.repeatDelay=0;
            this.relative=!spatial;
            update(payload,frame,System.nanoTime());
        }
        boolean matches(AdvancedEventElement.AudioPayload p) {
            return soundId.equals(p.soundId().toString())&&looping==p.looping()&&spatial==p.spatial()
                    && category==soundCategory(p.category());
        }
        void update(AdvancedEventElement.AudioPayload p, AttachmentFrame frame, long now) {
            lastUpdateNanos=now; done=false;
            volume=(float)Math.max(0.0,Math.min(4.0,p.volume().sample(frame.localTick())));
            pitch=(float)Math.max(0.01,Math.min(4.0,p.pitch().sample(frame.localTick())));
            Vec3d pos=frame.worldPosition(); x=pos.x; y=pos.y; z=pos.z;
        }
        @Override public void tick() { if (System.nanoTime()-lastUpdateNanos>STALE_NANOS) { done=true; volume=0; } }
        @Override public boolean isDone() { return done; }
        @Override public boolean canPlay() { return !done; }
    }
}
