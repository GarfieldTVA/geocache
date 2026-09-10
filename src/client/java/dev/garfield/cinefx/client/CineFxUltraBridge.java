package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Samples Ultra primitives from synchronized scene time and dispatches renderer-ready frames. */
public final class CineFxUltraBridge {
    private CineFxUltraBridge() { }

    public static void render(WorldRenderContext worldContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        MatrixStack matrices = worldContext.matrices();
        OrderedRenderCommandQueue queue = worldContext.commandQueue();
        if (matrices == null || queue == null) return;

        Camera camera = worldContext.gameRenderer().getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        SceneRenderContext renderContext = new SceneRenderContext(worldContext, matrices, queue, camera, cameraPos, absoluteTick);
        UltraBackendRegistry backends = UltraBackendRegistry.INSTANCE;

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            List<SceneElement> active = scene.activeElementsAt(sceneTick);
            if (active.isEmpty()) continue;

            HashMap<String, ComplexElement.Transformable> graph = new HashMap<>();
            for (SceneElement raw : active) if (raw instanceof ComplexElement.Transformable t) graph.put(t.key(), t);
            HashMap<String, Matrix4f> resolved = new HashMap<>();
            for (ComplexElement.Transformable t : graph.values()) {
                double local = sceneTick - t.startTick();
                resolve(scene, t, graph, resolved, new HashSet<>(), local);
            }
            HashMap<String, Vec3d> positions = new HashMap<>();
            for (Map.Entry<String, Matrix4f> entry : resolved.entrySet()) positions.put(entry.getKey(), point(entry.getValue(), Vec3d.ZERO));

            ArrayList<UltraBackend.LightRigFrame> lights = new ArrayList<>();
            ArrayList<UltraBackend.FractureFrame> fractures = new ArrayList<>();
            ArrayList<UltraBackend.SoftBodyFrame> softBodies = new ArrayList<>();
            ArrayList<UltraBackend.ProceduralRigFrame> rigs = new ArrayList<>();
            ArrayList<UltraBackend.ParticleFieldFrame> particles = new ArrayList<>();
            ArrayList<UltraBackend.CameraRigFrame> cameras = new ArrayList<>();
            ArrayList<UltraBackend.SpatialAudioFrame> audio = new ArrayList<>();
            ArrayList<UltraBackend.MaterialEffectFrame> materials = new ArrayList<>();
            ArrayList<UltraBackend.WorldDeformFrame> deforms = new ArrayList<>();
            ArrayList<UltraBackend.PortalFrame> portals = new ArrayList<>();
            ArrayList<UltraBackend.EditorMarkerFrame> markers = new ArrayList<>();

            for (SceneElement raw : active) {
                double local = sceneTick - raw.startTick();
                if (raw instanceof UltraEventElement.PostProcess post) {
                    EnumMap<UltraEventElement.PostEffect, Double> effects = new EnumMap<>(UltraEventElement.PostEffect.class);
                    for (Map.Entry<UltraEventElement.PostEffect, dev.garfield.cinefx.api.ScalarTrack> e : post.effects().entrySet()) {
                        effects.put(e.getKey(), e.getValue().sample(local));
                    }
                    backends.post(new UltraBackend.PostProcessFrame(scene.instanceId(), post.key(), Map.copyOf(effects),
                            post.tint().sample(local), post.focusDistance().sample(local), post.focusRange().sample(local),
                            post.parameters(), local));
                } else if (raw instanceof UltraEventElement.LightRig rig) {
                    Matrix4f matrix = matrix(resolved, rig.key(), scene);
                    Vec3d worldPos = point(matrix, Vec3d.ZERO);
                    if (culled(worldPos, cameraPos, rig.cullDistance())) continue;
                    double global = Math.max(0.0, rig.globalIntensity().sample(local));
                    ArrayList<UltraBackend.SampledRigLight> sampled = new ArrayList<>();
                    for (UltraEventElement.RigLight light : rig.lights()) {
                        Vec3d p = point(matrix, light.offset());
                        Vec3d d = direction(matrix, light.direction());
                        sampled.add(new UltraBackend.SampledRigLight(light.kind(), p, d, light.color().sample(local),
                                Math.max(0.0, light.intensity().sample(local)) * global,
                                Math.max(0.0, light.radius().sample(local)), light.innerConeDegrees().sample(local),
                                light.outerConeDegrees().sample(local), light.castShadow(),
                                Math.max(0.0, light.volumetric().sample(local))));
                    }
                    lights.add(new UltraBackend.LightRigFrame(scene.instanceId(), rig.key(), new Matrix4f(matrix), worldPos,
                            List.copyOf(sampled), rig.parameters(), local));
                } else if (raw instanceof UltraEventElement.Fracture fracture) {
                    Matrix4f matrix = matrix(resolved, fracture.key(), scene);
                    Vec3d worldPos = point(matrix, Vec3d.ZERO);
                    if (culled(worldPos, cameraPos, fracture.cullDistance())) continue;
                    fractures.add(new UltraBackend.FractureFrame(scene.instanceId(), fracture.key(), fracture.modelId(),
                            fracture.mode(), new Matrix4f(matrix), worldPos, fracture.shardCount(), fracture.impulse(),
                            fracture.force().sample(local), fracture.gravity().sample(local), fracture.drag().sample(local),
                            fracture.angularSpeed().sample(local), fracture.tint().sample(local), clamp01(fracture.opacity().sample(local)),
                            fracture.collideGround(), fracture.reverse(), fracture.parameters(), scene.options().seed(), local));
                } else if (raw instanceof UltraEventElement.SoftBody body) {
                    Matrix4f matrix = matrix(resolved, body.key(), scene);
                    Vec3d worldPos = point(matrix, Vec3d.ZERO);
                    if (culled(worldPos, cameraPos, body.cullDistance())) continue;
                    softBodies.add(new UltraBackend.SoftBodyFrame(scene.instanceId(), body.key(), body.mode(),
                            new Matrix4f(matrix), worldPos, body.points(), body.links(), body.gravity().sample(local),
                            body.wind().sample(local), body.damping().sample(local), Math.max(0.001, body.thickness().sample(local)),
                            body.color().sample(local), body.collideGround(), body.solverIterations(), body.parameters(),
                            scene.options().seed(), local));
                } else if (raw instanceof UltraEventElement.ProceduralRig rig) {
                    Vec3d actorPos = positions.getOrDefault(rig.actorKey(), scene.options().anchor());
                    ArrayList<UltraBackend.SampledIkGoal> goals = new ArrayList<>();
                    for (UltraEventElement.IkGoal goal : rig.goals()) {
                        Vec3d target = goal.targetElementKey() == null
                                ? scene.options().anchor().add(goal.targetOffset())
                                : positions.getOrDefault(goal.targetElementKey(), actorPos).add(goal.targetOffset());
                        goals.add(new UltraBackend.SampledIkGoal(goal.chain(), goal.mode(), goal.endBone(), goal.poleBone(),
                                target, goal.targetElementKey(), clamp01(goal.weight().sample(local)), goal.iterations(),
                                goal.tolerance(), goal.parameters()));
                    }
                    rigs.add(new UltraBackend.ProceduralRigFrame(scene.instanceId(), rig.key(), rig.actorKey(),
                            List.copyOf(goals), clamp01(rig.globalWeight().sample(local)), rig.parameters(), local));
                } else if (raw instanceof UltraEventElement.ParticleField field) {
                    Matrix4f matrix = matrix(resolved, field.key(), scene);
                    Vec3d worldPos = point(matrix, Vec3d.ZERO);
                    if (culled(worldPos, cameraPos, field.cullDistance())) continue;
                    ArrayList<UltraBackend.SampledForce> forces = new ArrayList<>();
                    for (UltraEventElement.Force force : field.forces()) {
                        forces.add(new UltraBackend.SampledForce(force.kind(), point(matrix, force.offset()),
                                direction(matrix, force.direction()), force.strength().sample(local),
                                Math.max(0.0, force.radius().sample(local)), Math.max(0.0, force.falloff().sample(local)), force.seed()));
                    }
                    particles.add(new UltraBackend.ParticleFieldFrame(scene.instanceId(), field.key(), field.particleId(),
                            new Matrix4f(matrix), worldPos, Math.max(0.0, field.spawnRate().sample(local)),
                            Math.max(1.0, field.lifetimeTicks().sample(local)), field.speed().sample(local),
                            Math.max(0.001, field.size().sample(local)), field.color().sample(local), List.copyOf(forces),
                            field.maxParticles(), field.collideGround(), field.trails(), field.parameters(), scene.options().seed(), local));
                } else if (raw instanceof UltraEventElement.CameraRig rig) {
                    Vec3d pathPosition = scene.options().anchor();
                    if (rig.path() != null) pathPosition = scene.options().anchor().add(rig.path().sample(local).position());
                    Vec3d lookAt = rig.lookAtElementKey() == null
                            ? scene.options().anchor().add(rig.lookAtOffset())
                            : positions.getOrDefault(rig.lookAtElementKey(), scene.options().anchor()).add(rig.lookAtOffset());
                    cameras.add(new UltraBackend.CameraRigFrame(scene.instanceId(), rig.key(), rig.mode(), pathPosition, lookAt,
                            rig.rollDegrees().sample(local), rig.fovDegrees().sample(local), rig.focusDistance().sample(local),
                            rig.focusRange().sample(local), rig.shakeTranslation().sample(local), rig.shakeRotation().sample(local),
                            rig.shakeFrequency().sample(local), rig.collideWorld(), rig.parameters(), local));
                } else if (raw instanceof UltraEventElement.SpatialAudio sound) {
                    Matrix4f matrix = matrix(resolved, sound.key(), scene);
                    audio.add(new UltraBackend.SpatialAudioFrame(scene.instanceId(), sound.key(), sound.soundId(),
                            point(matrix, Vec3d.ZERO), Math.max(0.0, sound.volume().sample(local)), sound.pitch().sample(local),
                            Math.max(0.1, sound.radius().sample(local)), clamp01(sound.lowPass().sample(local)), sound.reverb(),
                            clamp01(sound.reverbMix().sample(local)), sound.looping(), sound.doppler(), sound.occlusion(),
                            sound.parameters(), local));
                } else if (raw instanceof UltraEventElement.MaterialEffect effect) {
                    materials.add(new UltraBackend.MaterialEffectFrame(scene.instanceId(), effect.key(), effect.targetElementKey(),
                            effect.mode(), clamp01(effect.amount().sample(local)), Math.max(0.0, effect.edgeWidth().sample(local)),
                            effect.edgeColor().sample(local), Math.max(0.0001, effect.noiseScale().sample(local)),
                            effect.speed().sample(local), effect.direction(), withTarget(effect.parameters(), positions.get(effect.targetElementKey())), local));
                } else if (raw instanceof UltraEventElement.WorldDeform deform) {
                    Vec3d center = scene.options().anchor().add(deform.centerOffset());
                    deforms.add(new UltraBackend.WorldDeformFrame(scene.instanceId(), deform.key(), center, deform.mode(),
                            Math.max(0.0, deform.radius().sample(local)), deform.amplitude().sample(local),
                            Math.max(0.0, deform.frequency().sample(local)), clamp01(deform.progress().sample(local)),
                            deform.materialId(), deform.color().sample(local), deform.affectVirtualBlocks(), deform.parameters(),
                            scene.options().seed(), local));
                } else if (raw instanceof UltraEventElement.PortalSurface portal) {
                    Matrix4f matrix = matrix(resolved, portal.key(), scene);
                    Vec3d worldPos = point(matrix, Vec3d.ZERO);
                    if (culled(worldPos, cameraPos, portal.cullDistance())) continue;
                    portals.add(new UltraBackend.PortalFrame(scene.instanceId(), portal.key(), portal.mode(), new Matrix4f(matrix),
                            worldPos, portal.size(), portal.targetSceneId(), scene.options().anchor().add(portal.targetOffset()),
                            clamp01(portal.opacity().sample(local)), portal.rimColor().sample(local),
                            Math.max(0.0, portal.distortion().sample(local)), portal.recursionDepth(), portal.parameters(), local));
                } else if (raw instanceof UltraEventElement.EditorMarker marker) {
                    markers.add(new UltraBackend.EditorMarkerFrame(scene.instanceId(), marker.key(), marker.label(),
                            marker.colorArgb(), marker.parameters(), sceneTick));
                }
            }

            if (!lights.isEmpty()) backends.lights(renderContext, List.copyOf(lights));
            if (!fractures.isEmpty()) backends.fractures(renderContext, List.copyOf(fractures));
            if (!softBodies.isEmpty()) backends.softBodies(renderContext, List.copyOf(softBodies));
            if (!rigs.isEmpty()) backends.procedural(List.copyOf(rigs));
            if (!particles.isEmpty()) backends.particles(renderContext, List.copyOf(particles));
            if (!cameras.isEmpty()) backends.cameras(List.copyOf(cameras));
            if (!audio.isEmpty()) backends.audio(List.copyOf(audio));
            if (!materials.isEmpty()) backends.materials(List.copyOf(materials));
            if (!deforms.isEmpty()) backends.deforms(renderContext, List.copyOf(deforms));
            if (!portals.isEmpty()) backends.portals(renderContext, List.copyOf(portals));
            if (!markers.isEmpty()) backends.markers(List.copyOf(markers));
        }
    }

    private static Map<String, String> withTarget(Map<String, String> source, Vec3d target) {
        if (target == null) return source;
        HashMap<String, String> out = new HashMap<>(source);
        out.put("targetX", Double.toString(target.x));
        out.put("targetY", Double.toString(target.y));
        out.put("targetZ", Double.toString(target.z));
        return Map.copyOf(out);
    }

    private static Matrix4f matrix(Map<String, Matrix4f> resolved, String key, ActiveScene scene) {
        Matrix4f value = resolved.get(key);
        if (value != null) return value;
        Vec3d anchor = scene.options().anchor();
        return new Matrix4f().translation((float) anchor.x, (float) anchor.y, (float) anchor.z);
    }

    private static Matrix4f resolve(ActiveScene scene, ComplexElement.Transformable element,
                                    Map<String, ComplexElement.Transformable> graph,
                                    Map<String, Matrix4f> cache, Set<String> resolving, double localTick) {
        Matrix4f cached = cache.get(element.key());
        if (cached != null) return cached;
        if (!resolving.add(element.key())) return root(scene);

        Matrix4f matrix;
        ComplexElement.Transformable parent = element.parentKey() == null ? null : graph.get(element.parentKey());
        if (parent == null) matrix = root(scene);
        else {
            double sceneTick = scene.localTick(CineFxRuntime.absoluteGameTick(MinecraftClient.getInstance()));
            matrix = new Matrix4f(resolve(scene, parent, graph, cache, resolving, sceneTick - parent.startTick()));
        }

        AdvancedTransform advanced = element.transform().sample(localTick);
        Transform motion = element.motion().sample(localTick, scene.options().seed());
        Vec3d translation = element.baseOffset().add(advanced.translation()).add(motion.translation());
        Vec3d rotation = advanced.rotationDegrees().add(motion.rotationDegrees());
        Vec3d scale = new Vec3d(advanced.scale().x * motion.scale().x,
                advanced.scale().y * motion.scale().y, advanced.scale().z * motion.scale().z);
        Vec3d pivot = advanced.pivot();

        matrix.translate((float) translation.x, (float) translation.y, (float) translation.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float) pivot.x, (float) pivot.y, (float) pivot.z);
        if (rotation.x != 0.0) matrix.rotateX((float) Math.toRadians(rotation.x));
        if (rotation.y != 0.0) matrix.rotateY((float) Math.toRadians(rotation.y));
        if (rotation.z != 0.0) matrix.rotateZ((float) Math.toRadians(rotation.z));
        matrix.scale((float) scale.x, (float) scale.y, (float) scale.z);
        if (!pivot.equals(Vec3d.ZERO)) matrix.translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
        resolving.remove(element.key());
        Matrix4f frozen = new Matrix4f(matrix);
        cache.put(element.key(), frozen);
        return frozen;
    }

    private static Matrix4f root(ActiveScene scene) {
        Vec3d anchor = scene.options().anchor();
        return new Matrix4f().translation((float) anchor.x, (float) anchor.y, (float) anchor.z);
    }

    private static Vec3d point(Matrix4f matrix, Vec3d point) {
        Vector3f out = matrix.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3d(out.x, out.y, out.z);
    }

    private static Vec3d direction(Matrix4f matrix, Vec3d direction) {
        Vector3f out = matrix.transformDirection(new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        Vec3d result = new Vec3d(out.x, out.y, out.z);
        return result.lengthSquared() < 1.0e-10 ? new Vec3d(0, 1, 0) : result.normalize();
    }

    private static boolean culled(Vec3d position, Vec3d camera, double distance) {
        return distance > 0.0 && position.squaredDistanceTo(camera) > distance * distance;
    }

    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
