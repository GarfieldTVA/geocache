package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AdvancedEventElement;
import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.QualityTier;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CinematicBackend.AnimationSample;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AttachmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AudioLayerFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.CrowdFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MegaEnvironmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.PlayerControlFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.CinematicBackend.SkyFrame;
import dev.garfield.cinefx.client.api.LightFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Resolves the v1 advanced channels without creating Minecraft world entities. */
public final class CineFxAdvancedEventBridge {
    private CineFxAdvancedEventBridge() { }

    public static void render(WorldRenderContext worldContext) {
        AdaptiveQualityController.onFrame();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        MatrixStack matrices = worldContext.matrices();
        OrderedRenderCommandQueue queue = worldContext.commandQueue();
        if (matrices == null || queue == null) return;

        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        Vec3d cameraPos = worldContext.gameRenderer().getCamera().getCameraPos();
        SceneRenderContext renderContext = new SceneRenderContext(worldContext, matrices, queue,
                worldContext.gameRenderer().getCamera(), cameraPos, absoluteTick);
        QualityTier quality = AdaptiveQualityController.current();

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            List<SceneElement> active = scene.activeElementsAt(sceneTick);
            if (active.isEmpty()) continue;

            HashMap<String, ComplexElement.Transformable> graph = new HashMap<>();
            for (SceneElement raw : active) if (raw instanceof ComplexElement.Transformable value) graph.put(value.key(), value);
            HashMap<String, Matrix4f> resolved = new HashMap<>();
            HashMap<String, Boolean> visibility = new HashMap<>();
            VisualClaims claims = new VisualClaims();

            ArrayList<AttachmentFrame> attachments = new ArrayList<>();
            ArrayList<CrowdFrame> crowds = new ArrayList<>();
            ArrayList<MegaEnvironmentFrame> environments = new ArrayList<>();
            ArrayList<AudioLayerFrame> audio = new ArrayList<>();

            for (SceneElement raw : active) {
                double local = sceneTick - raw.startTick();
                if (raw instanceof AdvancedEventElement.Sky sky && claims.claim("advanced-sky", sky.conflictPolicy())) {
                    CineFxRuntime.INSTANCE.cinematicBackends().sky(new SkyFrame(scene.instanceId(), sky.key(), sky.skyboxId(),
                            sky.horizonColor().sample(local), sky.zenithColor().sample(local),
                            sky.sunBrightness().sample(local), sky.moonBrightness().sample(local),
                            clamp01(sky.eclipse().sample(local)), clamp01(sky.aurora().sample(local)),
                            sky.rotationDegrees().sample(local), sky.parameters()));
                    continue;
                }
                if (raw instanceof AdvancedEventElement.AudioLayer layer) {
                    double duration = Math.max(1.0, layer.endTick() - layer.startTick());
                    double fadeIn = layer.fadeInTicks() <= 0.0 ? 1.0 : clamp01(local / layer.fadeInTicks());
                    double remaining = duration - local;
                    double fadeOut = layer.fadeOutTicks() <= 0.0 ? 1.0 : clamp01(remaining / layer.fadeOutTicks());
                    audio.add(new AudioLayerFrame(scene.instanceId(), layer.key(), layer.soundId(),
                            Math.max(0.0, layer.volume().sample(local)) * Math.min(fadeIn, fadeOut),
                            Math.max(0.01, layer.pitch().sample(local)), clamp01(layer.lowPass().sample(local)),
                            layer.looping(), layer.music(), fadeIn, fadeOut, layer.parameters(), local));
                    continue;
                }
                if (raw instanceof AdvancedEventElement.PlayerControl control
                        && claims.claim("advanced-player-control", control.conflictPolicy())) {
                    CineFxRuntime.INSTANCE.cinematicBackends().playerControl(new PlayerControlFrame(
                            scene.instanceId(), control.key(), control.hideHud(), control.hideHand(),
                            control.lockMovement(), control.lockLook(), clamp01(control.movementScale().sample(local)),
                            clamp01(control.mouseScale().sample(local)), control.fovDegrees().sample(local),
                            control.allowJump(), control.allowInventory(), local));
                    continue;
                }
                if (!(raw instanceof ComplexElement.Transformable transformable)) continue;
                if (!(raw instanceof AdvancedEventElement.Attachment
                        || raw instanceof AdvancedEventElement.Crowd
                        || raw instanceof AdvancedEventElement.MegaEnvironment)) continue;
                if (!isVisible(transformable, graph, visibility, new HashSet<>())) continue;

                Matrix4f matrix = resolve(scene, transformable, graph, resolved, new HashSet<>(), sceneTick);
                Vec3d worldPos = point(matrix, Vec3d.ZERO);

                if (raw instanceof AdvancedEventElement.Attachment attachment) {
                    if (culled(worldPos, cameraPos, attachment.cullDistance())) continue;
                    Matrix4f inherited = applyInheritance(scene, attachment, matrix, graph, resolved, sceneTick);
                    worldPos = point(inherited, Vec3d.ZERO);
                    attachments.add(new AttachmentFrame(scene.instanceId(), attachment.key(), attachment.parentKey(),
                            attachment.boneName(), attachment.inheritMode(), new Matrix4f(inherited), worldPos,
                            attachment.payload(), scene.options().seed(), local));
                } else if (raw instanceof AdvancedEventElement.Crowd crowd) {
                    if (culled(worldPos, cameraPos, crowd.cullDistance())) continue;
                    ArrayList<AnimationSample> samples = new ArrayList<>();
                    for (ComplexElement.AnimationLayer layer : crowd.animations()) {
                        double weight = clamp01(layer.weight().sample(local));
                        if (weight <= 0.0001) continue;
                        double speed = layer.speed().sample(local);
                        samples.add(new AnimationSample(layer.clipId(), weight, speed,
                                (local + layer.timeOffsetTicks()) * speed, layer.looping(), layer.blendMode(), layer.parameters()));
                    }
                    crowds.add(new CrowdFrame(scene.instanceId(), crowd.key(), crowd.kind(), crowd.resourceId(),
                            crowd.profilePrefix(), new Matrix4f(matrix), worldPos, crowd.agents(), List.copyOf(samples),
                            crowd.tint().sample(local), clamp01(crowd.opacity().sample(local)), crowd.castShadow(),
                            crowd.appearance(), scene.options().seed(), local));
                } else if (raw instanceof AdvancedEventElement.MegaEnvironment environment) {
                    if (culled(worldPos, cameraPos, environment.cullDistance())) continue;
                    environments.add(new MegaEnvironmentFrame(scene.instanceId(), environment.key(), environment.materialSet(),
                            new Matrix4f(matrix), worldPos, environment.cells(), environment.parameters(), local));
                }
            }

            if (!audio.isEmpty()) CineFxRuntime.INSTANCE.cinematicBackends().audioLayers(List.copyOf(audio));
            if (!attachments.isEmpty() && !CineFxRuntime.INSTANCE.cinematicBackends().attachments(renderContext, List.copyOf(attachments))) {
                fallbackAttachments(client, renderContext, scene, attachments, quality);
            }
            if (!crowds.isEmpty() && !CineFxRuntime.INSTANCE.cinematicBackends().crowds(renderContext, List.copyOf(crowds))) {
                fallbackCrowds(renderContext, crowds, quality);
            }
            if (!environments.isEmpty()) CineFxRuntime.INSTANCE.cinematicBackends().megaEnvironments(renderContext, List.copyOf(environments));
        }
    }

    private static void fallbackCrowds(SceneRenderContext context, List<CrowdFrame> crowds, QualityTier quality) {
        ArrayList<ActorFrame> actors = new ArrayList<>();
        int globalBudget = quality.maxCrowdActors();
        for (CrowdFrame crowd : crowds) {
            if (globalBudget <= 0 || crowd.opacity() <= 0.001) break;
            int count = crowd.agents().size();
            if (count == 0) continue;
            int take = Math.min(globalBudget, count);
            int stride = Math.max(1, (int)Math.ceil((double)count / take));
            int emitted = 0;
            for (int i = 0; i < count && emitted < take; i += stride) {
                AdvancedEventElement.CrowdAgent agent = crowd.agents().get(i);
                Matrix4f matrix = new Matrix4f(crowd.rootMatrix())
                        .translate((float)agent.offset().x, (float)agent.offset().y, (float)agent.offset().z)
                        .rotateY((float)Math.toRadians(agent.yawDegrees()));
                Vec3d pos = point(matrix, Vec3d.ZERO);
                String key = crowd.elementKey() + "#" + i;
                String profile = crowd.profilePrefix() + (i % 10000);
                ArrayList<AnimationSample> shifted = new ArrayList<>(crowd.animations().size());
                for (AnimationSample sample : crowd.animations()) {
                    shifted.add(new AnimationSample(sample.clipId(), sample.weight(), sample.speed(),
                            sample.timeTicks() + agent.timeOffsetTicks(), sample.looping(), sample.blendMode(), sample.parameters()));
                }
                actors.add(new ActorFrame(crowd.sceneInstanceId(), key, crowd.kind(), crowd.resourceId(), profile,
                        null, crowd.appearance(), matrix, pos, null, crowd.tintArgb(), crowd.opacity(), 0.0,
                        List.copyOf(shifted), List.of(), List.of(), crowd.castShadow(), crowd.localTick()));
                emitted++;
            }
            globalBudget -= emitted;
        }
        if (!actors.isEmpty()) CineFxRuntime.INSTANCE.cinematicBackends().actors(context, List.copyOf(actors));
    }

    private static void fallbackAttachments(MinecraftClient client, SceneRenderContext context, ActiveScene scene,
                                            List<AttachmentFrame> attachments, QualityTier quality) {
        ArrayList<LightFrame> lights = new ArrayList<>();
        Random random = new Random(scene.options().seed() ^ Double.doubleToLongBits(context.absoluteGameTick()));
        for (AttachmentFrame frame : attachments) {
            double local = frame.localTick();
            if (frame.payload() instanceof AdvancedEventElement.LightPayload light) {
                lights.add(new LightFrame(frame.sceneInstanceId(), frame.elementKey(), light.kind(), frame.worldPosition(),
                        light.direction(), light.color().sample(local), Math.max(0.0, light.intensity().sample(local)),
                        Math.max(0.0, light.radius().sample(local)), light.innerConeDegrees(), light.outerConeDegrees()));
            } else if (frame.payload() instanceof AdvancedEventElement.EmitterPayload emitter && client.world != null) {
                var rawType = Registries.PARTICLE_TYPE.get(emitter.particleId());
                if (!(rawType instanceof SimpleParticleType particle)) continue;
                int count = Math.min((int)Math.ceil(emitter.ratePerSecond().sample(local) / 60.0 * quality.particleScale()),
                        Math.max(1, (int)Math.round(emitter.maxParticlesPerFrame() * quality.particleScale())));
                double spread = Math.max(0.0, emitter.spread().sample(local));
                double speed = Math.max(0.0, emitter.speed().sample(local));
                for (int i = 0; i < count; i++) {
                    Vec3d dir = randomUnit(random);
                    Vec3d pos = frame.worldPosition().add(dir.multiply(spread * random.nextDouble()));
                    Vec3d velocity = dir.multiply(speed);
                    client.world.addParticleClient(particle, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
                }
            } else if (frame.payload() instanceof AdvancedEventElement.BeamPayload beam) {
                renderBeam(context, frame, beam, local);
            }
        }
        if (!lights.isEmpty()) CineFxRuntime.INSTANCE.lightingBackends().apply(List.copyOf(lights));
    }

    private static void renderBeam(SceneRenderContext context, AttachmentFrame frame,
                                   AdvancedEventElement.BeamPayload beam, double local) {
        final Vec3d a = point(frame.worldMatrix(), beam.from());
        final Vec3d b = point(frame.worldMatrix(), beam.to());
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1.0e-8) return;
        double half = Math.max(0.001, beam.width().sample(local)) * 0.5;
        Vec3d rawSide = direction.crossProduct(new Vec3d(0, 1, 0));
        if (rawSide.lengthSquared() < 1.0e-8) rawSide = direction.crossProduct(new Vec3d(1, 0, 0));
        final Vec3d side = rawSide.normalize().multiply(half);
        final Vec3d up = direction.normalize().crossProduct(side).normalize().multiply(half);
        final int color = beam.color().sample(local);
        final Vec3d camera = context.cameraPosition();
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            quad(vertices, matrix, a.subtract(side).subtract(camera), a.add(side).subtract(camera),
                    b.add(side).subtract(camera), b.subtract(side).subtract(camera), color);
            quad(vertices, matrix, a.subtract(up).subtract(camera), a.add(up).subtract(camera),
                    b.add(up).subtract(camera), b.subtract(up).subtract(camera), color);
        });
        context.matrices().pop();
    }

    private static Matrix4f applyInheritance(ActiveScene scene, AdvancedEventElement.Attachment attachment,
                                             Matrix4f fullyResolved, Map<String, ComplexElement.Transformable> graph,
                                             Map<String, Matrix4f> cache, double sceneTick) {
        if (attachment.inheritMode() == AdvancedEventElement.InheritMode.FULL) return fullyResolved;
        ComplexElement.Transformable parent = graph.get(attachment.parentKey());
        Matrix4f base = root(scene);
        if (parent != null && attachment.inheritMode() != AdvancedEventElement.InheritMode.NONE) {
            Matrix4f parentMatrix = resolve(scene, parent, graph, cache, new HashSet<>(), sceneTick);
            Vec3d parentPosition = point(parentMatrix, Vec3d.ZERO);
            base.translate((float)(parentPosition.x - scene.options().anchor().x),
                    (float)(parentPosition.y - scene.options().anchor().y),
                    (float)(parentPosition.z - scene.options().anchor().z));
            if (attachment.inheritMode() == AdvancedEventElement.InheritMode.TRANSLATION_ROTATION) {
                float sx = length(parentMatrix.m00(), parentMatrix.m01(), parentMatrix.m02());
                float sy = length(parentMatrix.m10(), parentMatrix.m11(), parentMatrix.m12());
                float sz = length(parentMatrix.m20(), parentMatrix.m21(), parentMatrix.m22());
                Matrix4f rotation = new Matrix4f(parentMatrix);
                if (sx > 1.0e-6f && sy > 1.0e-6f && sz > 1.0e-6f) rotation.scale(1.0f / sx, 1.0f / sy, 1.0f / sz);
                rotation.m30(0); rotation.m31(0); rotation.m32(0);
                base.mul(rotation);
            }
        }
        applyLocal(base, attachment, sceneTick - attachment.startTick(), scene.options().seed());
        return base;
    }

    private static Matrix4f resolve(ActiveScene scene, ComplexElement.Transformable element,
                                    Map<String, ComplexElement.Transformable> graph,
                                    Map<String, Matrix4f> cache, Set<String> resolving, double sceneTick) {
        Matrix4f cached = cache.get(element.key());
        if (cached != null) return cached;
        if (!resolving.add(element.key())) return root(scene);
        Matrix4f matrix;
        ComplexElement.Transformable parent = element.parentKey() == null ? null : graph.get(element.parentKey());
        if (parent == null) matrix = root(scene);
        else matrix = new Matrix4f(resolve(scene, parent, graph, cache, resolving, sceneTick));
        applyLocal(matrix, element, sceneTick - element.startTick(), scene.options().seed());
        resolving.remove(element.key());
        Matrix4f frozen = new Matrix4f(matrix);
        cache.put(element.key(), frozen);
        return frozen;
    }

    private static void applyLocal(Matrix4f matrix, ComplexElement.Transformable element, double local, long seed) {
        AdvancedTransform advanced = element.transform().sample(local);
        Transform motion = element.motion().sample(local, seed);
        Vec3d translation = element.baseOffset().add(advanced.translation()).add(motion.translation());
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

    private static Matrix4f root(ActiveScene scene) {
        Vec3d anchor = scene.options().anchor();
        return new Matrix4f().translation((float)anchor.x, (float)anchor.y, (float)anchor.z);
    }

    private static boolean isVisible(ComplexElement.Transformable element,
                                     Map<String, ComplexElement.Transformable> graph,
                                     Map<String, Boolean> cache, Set<String> resolving) {
        Boolean cached = cache.get(element.key());
        if (cached != null) return cached;
        if (!resolving.add(element.key())) return true;
        boolean visible = !(element instanceof ComplexElement.Node node) || node.visible();
        if (visible && element.parentKey() != null) {
            ComplexElement.Transformable parent = graph.get(element.parentKey());
            if (parent != null) visible = isVisible(parent, graph, cache, resolving);
        }
        resolving.remove(element.key());
        cache.put(element.key(), visible);
        return visible;
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d local) {
        return new Vec3d(
                matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30(),
                matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31(),
                matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32());
    }

    private static boolean culled(Vec3d world, Vec3d camera, double distance) {
        return world.squaredDistanceTo(camera) > distance * distance;
    }

    private static Vec3d randomUnit(Random random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double xz = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3d(Math.cos(angle) * xz, y, Math.sin(angle) * xz);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        vertices.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(color);
        vertices.vertex(matrix, (float)b.x, (float)b.y, (float)b.z).color(color);
        vertices.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(color);
        vertices.vertex(matrix, (float)d.x, (float)d.y, (float)d.z).color(color);
    }

    private static float length(float x, float y, float z) { return (float)Math.sqrt(x*x + y*y + z*z); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
