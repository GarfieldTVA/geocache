package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.CinematicBackend.AtmosphereFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.ParticleFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Processes the large-event channels that are not normal world geometry. */
public final class CineFxEventBridge {
    private static final Set<String> FIRED = new HashSet<>();
    private static final Map<String, Double> PARTICLE_REMAINDER = new HashMap<>();
    private static double previousRenderTick = Double.NaN;

    private CineFxEventBridge() { }

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            FIRED.clear();
            PARTICLE_REMAINDER.clear();
            previousRenderTick = Double.NaN;
            return;
        }
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        List<ActiveScene> scenes = CineFxRuntime.INSTANCE.snapshot(absoluteTick);
        if (scenes.isEmpty()) {
            FIRED.clear();
            PARTICLE_REMAINDER.clear();
            return;
        }

        for (ActiveScene scene : scenes) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            long cycle = cycle(scene, absoluteTick);
            for (SceneElement raw : scene.elementsByPriority()) {
                if (raw instanceof EventElement.AudioCue cue && cue.activeAt(sceneTick)) {
                    String fireKey = fireKey(scene, cue.key(), cycle);
                    if (FIRED.add(fireKey)) play(client, scene, cue);
                } else if (raw instanceof EventElement.Marker marker && marker.activeAt(sceneTick)) {
                    String fireKey = fireKey(scene, marker.key(), cycle);
                    if (FIRED.add(fireKey)) {
                        CineFxRuntime.INSTANCE.fireMarker(scene.definition().id(), scene.instanceId(),
                                marker.name(), marker.parameters());
                    }
                }
            }
        }
    }

    public static void render(WorldRenderContext ignored) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        double deltaTicks = Double.isNaN(previousRenderTick) ? 0.0 : absoluteTick - previousRenderTick;
        previousRenderTick = absoluteTick;
        deltaTicks = Math.max(0.0, Math.min(2.0, deltaTicks));

        VisualClaims claims = new VisualClaims();
        ArrayList<ParticleFrame> particles = new ArrayList<>();

        for (ActiveScene scene : CineFxRuntime.INSTANCE.snapshot(absoluteTick)) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            for (SceneElement raw : scene.elementsByPriority()) {
                if (!raw.activeAt(sceneTick)) continue;
                double local = sceneTick - raw.startTick();

                if (raw instanceof EventElement.Atmosphere atmosphere
                        && claims.claim("atmosphere", atmosphere.conflictPolicy())) {
                    AtmosphereFrame frame = new AtmosphereFrame(
                            scene.instanceId(), atmosphere.key(),
                            atmosphere.skyTint().sample(local), atmosphere.fogColor().sample(local),
                            atmosphere.fogDensity().sample(local), atmosphere.fogNear().sample(local),
                            atmosphere.fogFar().sample(local), atmosphere.cloudOpacity().sample(local),
                            atmosphere.starBrightness().sample(local), atmosphere.windStrength().sample(local));
                    CineFxRuntime.INSTANCE.cinematicBackends().atmosphere(frame);
                } else if (raw instanceof EventElement.Emitter emitter) {
                    Transform transform = emitter.transform().sample(local)
                            .combine(emitter.motion().sample(local, scene.options().seed()));
                    Vec3d position = scene.options().anchor().add(emitter.baseOffset()).add(transform.translation());
                    particles.add(new ParticleFrame(
                            scene.instanceId(), emitter.key(), emitter.particleId(), emitter.shape(), position,
                            transform.rotationDegrees(), Math.max(0.0, emitter.ratePerSecond().sample(local)),
                            Math.max(0.0, emitter.spread().sample(local)), Math.max(0.0, emitter.speed().sample(local)),
                            Math.max(0.001, emitter.size().sample(local)), emitter.color().sample(local),
                            emitter.maxParticlesPerFrame(), scene.options().seed(), local));
                }
            }
        }

        if (!particles.isEmpty() && !CineFxRuntime.INSTANCE.cinematicBackends().particles(List.copyOf(particles))) {
            spawnVanillaFallback(client, particles, deltaTicks);
        }
    }

    private static void play(MinecraftClient client, ActiveScene scene, EventElement.AudioCue cue) {
        SoundEvent sound = SoundEvent.of(cue.soundId());
        if (!cue.spatial()) {
            client.getSoundManager().play(PositionedSoundInstance.ui(sound, cue.pitch(), cue.volume()));
            return;
        }
        Vec3d pos = scene.options().anchor().add(cue.baseOffset());
        SoundCategory category;
        try {
            category = SoundCategory.valueOf(cue.category().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            category = SoundCategory.MASTER;
        }
        client.world.playSound(null, BlockPos.ofFloored(pos), sound, category, cue.volume(), cue.pitch());
    }

    private static void spawnVanillaFallback(MinecraftClient client, List<ParticleFrame> frames, double deltaTicks) {
        if (deltaTicks <= 0.0 || client.world == null) return;
        for (ParticleFrame frame : frames) {
            var rawType = Registries.PARTICLE_TYPE.get(frame.particleId());
            if (!(rawType instanceof SimpleParticleType particle)) continue;

            String key = frame.sceneInstanceId() + ":" + frame.elementKey();
            double exact = PARTICLE_REMAINDER.getOrDefault(key, 0.0)
                    + frame.ratePerSecond() * (deltaTicks / 20.0);
            int count = Math.min(frame.maxParticlesPerFrame(), (int) Math.floor(exact));
            PARTICLE_REMAINDER.put(key, exact - count);
            if (count <= 0) continue;

            long timeSalt = Double.doubleToLongBits(frame.localTick());
            Random random = new Random(frame.seed() ^ timeSalt ^ frame.elementKey().hashCode());
            for (int i = 0; i < count; i++) {
                Spawn spawn = spawn(frame, random);
                client.world.addParticleClient(particle,
                        spawn.position.x, spawn.position.y, spawn.position.z,
                        spawn.velocity.x, spawn.velocity.y, spawn.velocity.z);
            }
        }
    }

    private static Spawn spawn(ParticleFrame frame, Random random) {
        double spread = frame.spread();
        Vec3d offset;
        Vec3d direction;
        switch (frame.shape()) {
            case SPHERE -> {
                direction = randomUnit(random);
                offset = direction.multiply(spread * Math.cbrt(random.nextDouble()));
            }
            case DISC -> {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = Math.sqrt(random.nextDouble()) * spread;
                offset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                direction = new Vec3d(0.0, 1.0, 0.0);
            }
            case CONE -> {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radial = random.nextDouble() * spread;
                direction = new Vec3d(Math.cos(angle) * radial, 1.0, Math.sin(angle) * radial).normalize();
                offset = Vec3d.ZERO;
            }
            default -> {
                offset = Vec3d.ZERO;
                direction = randomUnit(random);
            }
        }
        return new Spawn(frame.position().add(offset), direction.multiply(frame.speed()));
    }

    private static Vec3d randomUnit(Random random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double xz = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3d(Math.cos(angle) * xz, y, Math.sin(angle) * xz);
    }

    private static long cycle(ActiveScene scene, double absoluteTick) {
        if (!scene.definition().looping()) return 0L;
        return (long) Math.floor(Math.max(0.0, absoluteTick - scene.startGameTime()) / scene.definition().durationTicks());
    }

    private static String fireKey(ActiveScene scene, String key, long cycle) {
        return scene.instanceId() + ":" + cycle + ":" + key;
    }

    private record Spawn(Vec3d position, Vec3d velocity) { }
}
