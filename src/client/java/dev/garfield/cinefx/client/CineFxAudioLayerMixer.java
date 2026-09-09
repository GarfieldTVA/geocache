package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend.AudioLayerFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in audio-layer mixer. It provides real looping stems, live volume/pitch automation and
 * graceful stale cleanup without requiring an external audio engine. DSP hints such as low-pass
 * remain available to higher-priority backends that can implement them sample-accurately.
 */
final class CineFxAudioLayerMixer {
    private static final long STALE_NANOS = 350_000_000L;
    private static final Map<String, LayerSound> LAYERS = new HashMap<>();

    private CineFxAudioLayerMixer() { }

    static boolean apply(List<AudioLayerFrame> frames) {
        if (frames == null || frames.isEmpty()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) return false;
        long now = System.nanoTime();
        for (AudioLayerFrame frame : frames) {
            String key = frame.sceneInstanceId() + ":" + frame.elementKey();
            LayerSound sound = LAYERS.get(key);
            if (sound == null || !sound.matches(frame)) {
                if (sound != null) client.getSoundManager().stop(sound);
                sound = new LayerSound(frame);
                LAYERS.put(key, sound);
                client.getSoundManager().play(sound);
            }
            sound.update(frame, now);
        }
        return true;
    }

    static void tick(MinecraftClient client) {
        if (client.getSoundManager() == null) return;
        long now = System.nanoTime();
        LAYERS.entrySet().removeIf(entry -> {
            LayerSound sound = entry.getValue();
            if (now - sound.lastUpdateNanos <= STALE_NANOS) return false;
            client.getSoundManager().stop(sound);
            return true;
        });
    }

    static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() != null) {
            for (LayerSound sound : LAYERS.values()) client.getSoundManager().stop(sound);
        }
        LAYERS.clear();
    }

    private static final class LayerSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final String soundId;
        private final boolean layerLooping;
        private long lastUpdateNanos;
        private boolean done;

        LayerSound(AudioLayerFrame frame) {
            super(SoundEvent.of(frame.soundId()), frame.music() ? SoundCategory.MUSIC : SoundCategory.AMBIENT, Random.create());
            this.soundId = frame.soundId().toString();
            this.layerLooping = frame.looping();
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.repeat = frame.looping();
            this.repeatDelay = 0;
            this.relative = true;
            update(frame, System.nanoTime());
        }

        boolean matches(AudioLayerFrame frame) {
            return soundId.equals(frame.soundId().toString()) && layerLooping == frame.looping();
        }

        void update(AudioLayerFrame frame, long now) {
            this.lastUpdateNanos = now;
            this.volume = (float)Math.max(0.0, Math.min(4.0, frame.volume()));
            this.pitch = (float)Math.max(0.01, Math.min(4.0, frame.pitch()));
            this.done = false;
        }

        @Override
        public void tick() {
            if (System.nanoTime() - lastUpdateNanos > STALE_NANOS) {
                done = true;
                volume = 0.0F;
            }
        }

        @Override
        public boolean isDone() { return done; }

        @Override
        public boolean canPlay() { return !done; }
    }
}
