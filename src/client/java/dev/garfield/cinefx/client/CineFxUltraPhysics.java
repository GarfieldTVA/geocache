package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.QualityTier;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Stateful, client-only visual physics. It never creates or synchronizes Minecraft entities. */
final class CineFxUltraPhysics {
    private static final long STALE_NANOS = 500_000_000L;
    private static final Map<String, SoftState> SOFT = new HashMap<>();
    private static final Map<String, ParticleState> PARTICLES = new HashMap<>();

    private CineFxUltraPhysics() { }

    static boolean softBodies(SceneRenderContext context, List<UltraBackend.SoftBodyFrame> frames) {
        if (frames.isEmpty()) return false;
        long now = System.nanoTime();
        ArrayList<RenderedSoft> rendered = new ArrayList<>();
        for (UltraBackend.SoftBodyFrame frame : frames) {
            String key = key(frame.sceneInstanceId(), frame.elementKey());
            SoftState state = SOFT.computeIfAbsent(key, ignored -> new SoftState());
            state.update(frame, now);
            rendered.add(new RenderedSoft(frame, state.snapshot()));
        }
        SOFT.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_NANOS);
        renderSoft(context, rendered);
        return true;
    }

    static boolean particleFields(SceneRenderContext context, List<UltraBackend.ParticleFieldFrame> frames) {
        if (frames.isEmpty()) return false;
        long now = System.nanoTime();
        QualityTier quality = AdaptiveQualityController.current();
        ArrayList<RenderedParticles> rendered = new ArrayList<>();
        for (UltraBackend.ParticleFieldFrame frame : frames) {
            String key = key(frame.sceneInstanceId(), frame.elementKey());
            ParticleState state = PARTICLES.computeIfAbsent(key, ignored -> new ParticleState(frame.seed() ^ frame.elementKey().hashCode()));
            state.update(frame, quality, now);
            rendered.add(new RenderedParticles(frame, state.snapshot()));
        }
        PARTICLES.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_NANOS);
        renderParticles(context, rendered, quality);
        return true;
    }

    static void tick() {
        long now = System.nanoTime();
        SOFT.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_NANOS);
        PARTICLES.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_NANOS);
    }

    static void clear() {
        SOFT.clear();
        PARTICLES.clear();
    }

    private static void renderSoft(SceneRenderContext context, List<RenderedSoft> batches) {
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            for (RenderedSoft batch : batches) {
                UltraBackend.SoftBodyFrame frame = batch.frame;
                List<Vec3d> points = batch.points;
                double width = Math.max(0.008, frame.thickness());
                int color = frame.colorArgb();
                for (UltraEventElement.SoftLink link : frame.links()) {
                    if (link.a() < 0 || link.b() < 0 || link.a() >= points.size() || link.b() >= points.size()) continue;
                    drawBeam(vertices, matrix, context.cameraPosition(), points.get(link.a()), points.get(link.b()), width, color);
                }
                if (frame.mode() == UltraEventElement.SoftBodyMode.CLOTH && points.size() >= 4) {
                    // A faint membrane makes cloth readable even when the author supplied only structural links.
                    int membrane = multiplyAlpha(color, 0.18);
                    for (int i = 0; i + 3 < points.size(); i += 4) {
                        drawQuad(vertices, matrix, context.cameraPosition(), points.get(i), points.get(i + 1), points.get(i + 2), points.get(i + 3), membrane);
                    }
                }
            }
        });
        context.matrices().pop();
    }

    private static void renderParticles(SceneRenderContext context, List<RenderedParticles> fields, QualityTier quality) {
        context.matrices().push();
        context.commandQueue().submitCustom(context.matrices(), RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            for (RenderedParticles rendered : fields) {
                UltraBackend.ParticleFieldFrame frame = rendered.frame;
                List<VisualParticle> particles = rendered.particles;
                int stride = quality == QualityTier.SAFE ? 4 : quality == QualityTier.LOW ? 3 : quality == QualityTier.MEDIUM ? 2 : 1;
                double half = Math.max(0.006, 0.025 * frame.size());
                for (int i = 0; i < particles.size(); i += stride) {
                    VisualParticle particle = particles.get(i);
                    double life = 1.0 - particle.age / Math.max(1.0, particle.lifetime);
                    int color = multiplyAlpha(frame.colorArgb(), Math.max(0.0, life));
                    drawBillboard(vertices, matrix, context.cameraPosition(), particle.position, half, color);
                    if (frame.trails() && particle.previous != null) {
                        drawBeam(vertices, matrix, context.cameraPosition(), particle.previous, particle.position,
                                Math.max(0.003, half * 0.35), multiplyAlpha(color, 0.45));
                    }
                }
            }
        });
        context.matrices().pop();
    }

    private static final class SoftState {
        private Vec3d[] positions = new Vec3d[0];
        private Vec3d[] previous = new Vec3d[0];
        private double lastLocal = Double.NaN;
        private long lastSeen;

        void update(UltraBackend.SoftBodyFrame frame, long now) {
            lastSeen = now;
            if (positions.length != frame.points().size()) initialize(frame);
            double dt = Double.isNaN(lastLocal) ? 0.0 : clamp(frame.localTick() - lastLocal, 0.0, 3.0);
            lastLocal = frame.localTick();
            if (dt <= 0.00001) {
                pin(frame);
                return;
            }

            double damping = clamp(frame.damping(), 0.0, 1.0);
            double gravity = frame.gravity();
            double wind = frame.wind();
            for (int i = 0; i < positions.length; i++) {
                UltraEventElement.SoftPoint point = frame.points().get(i);
                if (point.pinned() || point.inverseMass() <= 0.0) continue;
                Vec3d current = positions[i];
                Vec3d velocity = current.subtract(previous[i]).multiply(damping);
                previous[i] = current;
                double phase = frame.localTick() * 0.08 + i * 1.618 + (frame.seed() & 255) * 0.01;
                Vec3d acceleration = new Vec3d(Math.sin(phase) * wind * 0.012, -gravity, Math.cos(phase * 0.83) * wind * 0.012);
                positions[i] = current.add(velocity).add(acceleration.multiply(dt * dt));
            }

            pin(frame);
            int iterations = Math.max(1, frame.solverIterations());
            for (int iteration = 0; iteration < iterations; iteration++) {
                for (UltraEventElement.SoftLink link : frame.links()) solveLink(frame, link);
                pin(frame);
                if (frame.collideGround()) collideGround(frame);
            }
        }

        private void initialize(UltraBackend.SoftBodyFrame frame) {
            positions = new Vec3d[frame.points().size()];
            previous = new Vec3d[frame.points().size()];
            for (int i = 0; i < positions.length; i++) {
                Vec3d p = point(frame.worldMatrix(), frame.points().get(i).offset());
                positions[i] = p;
                previous[i] = p;
            }
            lastLocal = frame.localTick();
        }

        private void pin(UltraBackend.SoftBodyFrame frame) {
            for (int i = 0; i < positions.length; i++) {
                UltraEventElement.SoftPoint p = frame.points().get(i);
                if (!p.pinned() && p.inverseMass() > 0.0) continue;
                Vec3d target = point(frame.worldMatrix(), p.offset());
                positions[i] = target;
                previous[i] = target;
            }
        }

        private void solveLink(UltraBackend.SoftBodyFrame frame, UltraEventElement.SoftLink link) {
            int a = link.a(), b = link.b();
            if (a < 0 || b < 0 || a >= positions.length || b >= positions.length || a == b) return;
            Vec3d delta = positions[b].subtract(positions[a]);
            double distance = delta.length();
            if (distance < 1.0e-7) return;
            double rest = link.restLength() > 0.0 ? link.restLength()
                    : point(frame.worldMatrix(), frame.points().get(b).offset()).distanceTo(point(frame.worldMatrix(), frame.points().get(a).offset()));
            double stiffness = clamp(link.stiffness(), 0.0, 1.0);
            double error = (distance - rest) / distance;
            UltraEventElement.SoftPoint pa = frame.points().get(a), pb = frame.points().get(b);
            double wa = pa.pinned() ? 0.0 : Math.max(0.0, pa.inverseMass());
            double wb = pb.pinned() ? 0.0 : Math.max(0.0, pb.inverseMass());
            double total = wa + wb;
            if (total <= 1.0e-9) return;
            Vec3d correction = delta.multiply(error * stiffness);
            if (wa > 0.0) positions[a] = positions[a].add(correction.multiply(wa / total));
            if (wb > 0.0) positions[b] = positions[b].subtract(correction.multiply(wb / total));
        }

        private void collideGround(UltraBackend.SoftBodyFrame frame) {
            double floor = frame.worldPosition().y;
            for (int i = 0; i < positions.length; i++) {
                if (positions[i].y < floor) positions[i] = new Vec3d(positions[i].x, floor, positions[i].z);
            }
        }

        List<Vec3d> snapshot() { return List.of(positions.clone()); }
    }

    private static final class ParticleState {
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final long seed;
        private long serial;
        private double spawnAccumulator;
        private double lastLocal = Double.NaN;
        private long lastSeen;

        ParticleState(long seed) { this.seed = seed; }

        void update(UltraBackend.ParticleFieldFrame frame, QualityTier quality, long now) {
            lastSeen = now;
            double dt = Double.isNaN(lastLocal) ? 0.0 : clamp(frame.localTick() - lastLocal, 0.0, 3.0);
            lastLocal = frame.localTick();
            if (dt <= 0.00001) return;

            int hardCap = switch (quality) {
                case SAFE -> 1200;
                case LOW -> 2600;
                case MEDIUM -> 5200;
                case HIGH -> 9000;
                case ULTRA -> 16000;
            };
            int capacity = Math.min(frame.maxParticles(), hardCap);
            spawnAccumulator += frame.spawnRate() * quality.particleScale() * dt / 20.0;
            int spawn = Math.min(capacity - particles.size(), (int)Math.floor(spawnAccumulator));
            spawnAccumulator -= Math.floor(spawnAccumulator);
            double spawnRadius = parse(frame.parameters().get("spawn_radius"), 0.35);
            for (int i = 0; i < spawn; i++) particles.add(spawn(frame, spawnRadius));

            Iterator<Particle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                particle.previous = particle.position;
                Vec3d acceleration = Vec3d.ZERO;
                double velocityScale = 1.0;
                for (UltraBackend.SampledForce force : frame.forces()) {
                    ForceResult result = force(particle, force, frame.localTick());
                    acceleration = acceleration.add(result.acceleration);
                    velocityScale *= result.velocityScale;
                }
                particle.velocity = particle.velocity.multiply(velocityScale).add(acceleration.multiply(dt));
                particle.position = particle.position.add(particle.velocity.multiply(dt));
                if (frame.collideGround() && particle.position.y < frame.worldPosition().y) {
                    particle.position = new Vec3d(particle.position.x, frame.worldPosition().y, particle.position.z);
                    particle.velocity = new Vec3d(particle.velocity.x * 0.72, Math.abs(particle.velocity.y) * 0.42, particle.velocity.z * 0.72);
                }
                particle.age += dt;
                if (particle.age >= particle.lifetime) iterator.remove();
            }
        }

        private Particle spawn(UltraBackend.ParticleFieldFrame frame, double radius) {
            long h = mix(seed + serial++ * 0x9E3779B97F4A7C15L);
            Vec3d random = unit(h);
            double radial = radius * (0.25 + unit01(h >>> 9) * 0.75);
            Vec3d position = frame.worldPosition().add(random.multiply(radial));
            Vec3d velocity = unit(mix(h ^ 0xD1B54A32D192ED03L)).multiply(frame.speed() * (0.45 + unit01(h >>> 27)));
            double lifetime = Math.max(1.0, frame.lifetimeTicks() * (0.7 + unit01(h >>> 41) * 0.6));
            return new Particle(position, position, velocity, 0.0, lifetime);
        }

        private ForceResult force(Particle particle, UltraBackend.SampledForce force, double localTick) {
            double strength = force.strength();
            Vec3d delta = force.position().subtract(particle.position);
            double distance = Math.max(1.0e-5, delta.length());
            double radius = Math.max(1.0e-5, force.radius());
            double normalized = Math.max(0.0, 1.0 - distance / radius);
            double falloff = Math.pow(normalized, Math.max(0.01, force.falloff()));
            return switch (force.kind()) {
                case DIRECTIONAL -> new ForceResult(force.direction().multiply(strength * 0.02), 1.0);
                case ATTRACTOR -> new ForceResult(delta.normalize().multiply(strength * falloff * 0.035), 1.0);
                case REPELLER, EXPLOSION -> new ForceResult(delta.normalize().multiply(-strength * falloff * 0.04), 1.0);
                case VORTEX -> {
                    Vec3d axis = force.direction().lengthSquared() < 1.0e-9 ? new Vec3d(0, 1, 0) : force.direction().normalize();
                    Vec3d radial = particle.position.subtract(force.position());
                    Vec3d tangent = axis.crossProduct(radial);
                    if (tangent.lengthSquared() < 1.0e-9) tangent = new Vec3d(1, 0, 0);
                    yield new ForceResult(tangent.normalize().multiply(strength * falloff * 0.035), 1.0);
                }
                case TURBULENCE -> {
                    double p = localTick * 0.11 + particle.position.x * 0.7 + particle.position.z * 0.43 + (force.seed() & 255) * 0.01;
                    Vec3d turbulence = new Vec3d(Math.sin(p * 1.7), Math.cos(p * 1.13), Math.sin(p * 0.83 + 2.0));
                    yield new ForceResult(turbulence.multiply(strength * 0.018), 1.0);
                }
                case DRAG -> new ForceResult(Vec3d.ZERO, Math.max(0.0, 1.0 - Math.abs(strength) * 0.025));
            };
        }

        List<VisualParticle> snapshot() {
            ArrayList<VisualParticle> out = new ArrayList<>(particles.size());
            for (Particle p : particles) out.add(new VisualParticle(p.position, p.previous, p.age, p.lifetime));
            return List.copyOf(out);
        }
    }

    private static void drawBillboard(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d center, double half, int color) {
        Vec3d forward = camera.subtract(center);
        if (forward.lengthSquared() < 1.0e-8) forward = new Vec3d(0, 0, 1);
        else forward = forward.normalize();
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(forward);
        if (right.lengthSquared() < 1.0e-8) right = new Vec3d(1, 0, 0);
        else right = right.normalize();
        Vec3d up = forward.crossProduct(right).normalize();
        Vec3d a = center.subtract(right.multiply(half)).subtract(up.multiply(half));
        Vec3d b = center.add(right.multiply(half)).subtract(up.multiply(half));
        Vec3d c = center.add(right.multiply(half)).add(up.multiply(half));
        Vec3d d = center.subtract(right.multiply(half)).add(up.multiply(half));
        drawQuad(vertices, matrix, camera, a, b, c, d, color);
    }

    private static void drawBeam(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d a, Vec3d b, double width, int color) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1.0e-10) return;
        direction = direction.normalize();
        Vec3d view = camera.subtract(a.add(b).multiply(0.5));
        if (view.lengthSquared() < 1.0e-10) view = new Vec3d(0, 1, 0);
        else view = view.normalize();
        Vec3d side = direction.crossProduct(view);
        if (side.lengthSquared() < 1.0e-10) side = direction.crossProduct(new Vec3d(0, 1, 0));
        if (side.lengthSquared() < 1.0e-10) side = new Vec3d(1, 0, 0);
        side = side.normalize().multiply(width);
        drawQuad(vertices, matrix, camera, a.add(side), b.add(side), b.subtract(side), a.subtract(side), color);
        Vec3d side2 = direction.crossProduct(side).normalize().multiply(width);
        drawQuad(vertices, matrix, camera, a.add(side2), b.add(side2), b.subtract(side2), a.subtract(side2), color);
    }

    private static void drawQuad(VertexConsumer vertices, Matrix4f matrix, Vec3d camera,
                                 Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        emit(vertices, matrix, a.subtract(camera), color);
        emit(vertices, matrix, b.subtract(camera), color);
        emit(vertices, matrix, c.subtract(camera), color);
        emit(vertices, matrix, d.subtract(camera), color);
    }

    private static void emit(VertexConsumer vertices, Matrix4f matrix, Vec3d p, int color) {
        vertices.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(color);
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d value) {
        Vector3f out = matrix.transformPosition(new Vector3f((float)value.x, (float)value.y, (float)value.z));
        return new Vec3d(out.x, out.y, out.z);
    }

    private static int multiplyAlpha(int argb, double scale) {
        int alpha = (argb >>> 24) & 255;
        if (alpha == 0) alpha = 255;
        alpha = (int)Math.round(alpha * clamp(scale, 0.0, 1.0));
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static double parse(String raw, double fallback) {
        if (raw == null) return fallback;
        try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static Vec3d unit(long h) {
        double z = unit01(h) * 2.0 - 1.0;
        double angle = unit01(h >>> 21) * Math.PI * 2.0;
        double radius = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3d(Math.cos(angle) * radius, z, Math.sin(angle) * radius);
    }

    private static double unit01(long h) { return (mix(h) >>> 11) * 0x1.0p-53; }
    private static long mix(long x) { x ^= x >>> 33; x *= 0xff51afd7ed558ccdl; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53l; return x ^ (x >>> 33); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static String key(long scene, String element) { return scene + ":" + element; }

    private record RenderedSoft(UltraBackend.SoftBodyFrame frame, List<Vec3d> points) { }
    private record RenderedParticles(UltraBackend.ParticleFieldFrame frame, List<VisualParticle> particles) { }
    private record VisualParticle(Vec3d position, Vec3d previous, double age, double lifetime) { }
    private record ForceResult(Vec3d acceleration, double velocityScale) { }
    private static final class Particle {
        Vec3d position;
        Vec3d previous;
        Vec3d velocity;
        double age;
        final double lifetime;
        Particle(Vec3d position, Vec3d previous, Vec3d velocity, double age, double lifetime) {
            this.position = position; this.previous = previous; this.velocity = velocity; this.age = age; this.lifetime = lifetime;
        }
    }
}
