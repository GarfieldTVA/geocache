package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Positioned looping audio with simple occlusion and Doppler approximations. */
final class CineFxUltraSpatialAudio {
    private static final long STALE_NANOS = 400_000_000L;
    private static final Map<String, SpatialSound> SOUNDS = new HashMap<>();

    private CineFxUltraSpatialAudio() { }

    static boolean apply(List<UltraBackend.SpatialAudioFrame> frames) {
        if (frames == null || frames.isEmpty()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) return false;
        long now = System.nanoTime();
        for (UltraBackend.SpatialAudioFrame frame : frames) {
            String key = frame.sceneInstanceId() + ":" + frame.elementKey();
            SpatialSound sound = SOUNDS.get(key);
            if (sound == null || !sound.matches(frame)) {
                if (sound != null) client.getSoundManager().stop(sound);
                sound = new SpatialSound(frame);
                SOUNDS.put(key, sound);
                client.getSoundManager().play(sound);
            }
            sound.update(client, frame, now);
        }
        return true;
    }

    static void tick(MinecraftClient client) {
        if (client.getSoundManager() == null) return;
        long now = System.nanoTime();
        SOUNDS.entrySet().removeIf(entry -> {
            SpatialSound sound = entry.getValue();
            if (now - sound.lastUpdateNanos <= STALE_NANOS) return false;
            client.getSoundManager().stop(sound);
            return true;
        });
    }

    static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() != null) for (SpatialSound sound : SOUNDS.values()) client.getSoundManager().stop(sound);
        SOUNDS.clear();
    }

    private static final class SpatialSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final String soundId;
        private final boolean looping;
        private Vec3d previousPosition;
        private double previousTick = Double.NaN;
        private long lastUpdateNanos;
        private boolean done;

        SpatialSound(UltraBackend.SpatialAudioFrame frame) {
            super(SoundEvent.of(frame.soundId()), category(frame), Random.create());
            this.soundId = frame.soundId().toString();
            this.looping = frame.looping();
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
            this.repeat = frame.looping();
            this.repeatDelay = 0;
            this.relative = false;
            this.previousPosition = frame.position();
            this.x = frame.position().x;
            this.y = frame.position().y;
            this.z = frame.position().z;
        }

        boolean matches(UltraBackend.SpatialAudioFrame frame) {
            return soundId.equals(frame.soundId().toString()) && looping == frame.looping();
        }

        void update(MinecraftClient client, UltraBackend.SpatialAudioFrame frame, long now) {
            lastUpdateNanos = now;
            done = false;
            Vec3d position = frame.position();
            x = position.x; y = position.y; z = position.z;

            double occlusion = frame.occlusion() ? occlusion(client, position) : 0.0;
            double lowPassLoss = 1.0 - clamp01(frame.lowPass()) * 0.32;
            double reverbPresence = reverbPresence(frame.reverb(), frame.reverbMix());
            double gain = frame.volume() * (1.0 - occlusion * 0.72) * lowPassLoss * reverbPresence;
            volume = (float)clamp(gain, 0.0, 4.0);

            double doppler = 1.0;
            if (frame.doppler() && previousPosition != null && !Double.isNaN(previousTick)) {
                double dt = Math.max(0.001, frame.localTick() - previousTick);
                Vec3d listener = client.gameRenderer.getCamera().getCameraPos();
                Vec3d sourceVelocity = position.subtract(previousPosition).multiply(20.0 / dt);
                Vec3d radial = listener.subtract(position);
                if (radial.lengthSquared() > 1.0e-8) {
                    double radialVelocity = sourceVelocity.dotProduct(radial.normalize());
                    doppler = clamp(1.0 + radialVelocity / 343.0, 0.65, 1.55);
                }
            }
            pitch = (float)clamp(frame.pitch() * doppler, 0.01, 4.0);
            previousPosition = position;
            previousTick = frame.localTick();
        }

        @Override public void tick() {
            if (System.nanoTime() - lastUpdateNanos > STALE_NANOS) {
                done = true;
                volume = 0.0F;
            }
        }

        @Override public boolean isDone() { return done; }
        @Override public boolean canPlay() { return !done; }
    }

    private static SoundCategory category(UltraBackend.SpatialAudioFrame frame) {
        String raw = frame.parameters().getOrDefault("category", "AMBIENT");
        try { return SoundCategory.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException ignored) { return SoundCategory.AMBIENT; }
    }

    private static double occlusion(MinecraftClient client, Vec3d source) {
        if (client.world == null) return 0.0;
        Vec3d listener = client.gameRenderer.getCamera().getCameraPos();
        int samples = 7;
        int blocked = 0;
        for (int i = 1; i < samples; i++) {
            double t = i / (double)samples;
            Vec3d p = listener.lerp(source, t);
            BlockPos pos = BlockPos.ofFloored(p.x, p.y, p.z);
            if (!client.world.getBlockState(pos).isAir()) blocked++;
        }
        return clamp01(blocked / (double)(samples - 1));
    }

    private static double reverbPresence(UltraEventElement.ReverbPreset preset, double mix) {
        double wet = clamp01(mix);
        double boost = switch (preset) {
            case NONE -> 0.0;
            case ROOM -> 0.03;
            case HALL -> 0.08;
            case CAVE -> 0.10;
            case ARENA -> 0.06;
            case UNDERWATER -> -0.08;
            case SPACE -> -0.14;
            case CUSTOM -> 0.04;
        };
        return Math.max(0.55, 1.0 + boost * wet);
    }

    private static double clamp01(double value) { return clamp(value, 0.0, 1.0); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
