package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Priority-ordered Ultra channel dispatch. A backend may consume only the channels it implements. */
public final class UltraBackendRegistry {
    public static final UltraBackendRegistry INSTANCE = new UltraBackendRegistry();

    private final List<Entry> entries = new ArrayList<>();

    private UltraBackendRegistry() { }

    public synchronized void register(Identifier id, int priority, UltraBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id and backend are required");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX Ultra backend already registered: " + id);
        }
        entries.add(new Entry(id, priority, backend));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public synchronized boolean post(UltraBackend.PostProcessFrame frame) {
        for (Entry entry : entries) {
            try { if (entry.backend.postProcess(frame)) return true; }
            catch (RuntimeException exception) { failed(entry, "post", exception); }
        }
        return false;
    }

    public synchronized boolean lights(CinematicBackend.SceneRenderContext context, List<UltraBackend.LightRigFrame> frames) {
        return dispatch("lights", backend -> backend.lightRigs(context, frames));
    }

    public synchronized boolean fractures(CinematicBackend.SceneRenderContext context, List<UltraBackend.FractureFrame> frames) {
        return dispatch("fractures", backend -> backend.fractures(context, frames));
    }

    public synchronized boolean softBodies(CinematicBackend.SceneRenderContext context, List<UltraBackend.SoftBodyFrame> frames) {
        return dispatch("softBodies", backend -> backend.softBodies(context, frames));
    }

    public synchronized boolean procedural(List<UltraBackend.ProceduralRigFrame> frames) {
        return dispatch("proceduralRigs", backend -> backend.proceduralRigs(frames));
    }

    public synchronized boolean particles(CinematicBackend.SceneRenderContext context, List<UltraBackend.ParticleFieldFrame> frames) {
        return dispatch("particleFields", backend -> backend.particleFields(context, frames));
    }

    public synchronized boolean cameras(List<UltraBackend.CameraRigFrame> frames) {
        return dispatch("cameraRigs", backend -> backend.cameraRigs(frames));
    }

    public synchronized boolean audio(List<UltraBackend.SpatialAudioFrame> frames) {
        return dispatch("spatialAudio", backend -> backend.spatialAudio(frames));
    }

    public synchronized boolean materials(List<UltraBackend.MaterialEffectFrame> frames) {
        return dispatch("materialEffects", backend -> backend.materialEffects(frames));
    }

    public synchronized boolean deforms(CinematicBackend.SceneRenderContext context, List<UltraBackend.WorldDeformFrame> frames) {
        return dispatch("worldDeforms", backend -> backend.worldDeforms(context, frames));
    }

    public synchronized boolean portals(CinematicBackend.SceneRenderContext context, List<UltraBackend.PortalFrame> frames) {
        return dispatch("portals", backend -> backend.portals(context, frames));
    }

    public synchronized void markers(List<UltraBackend.EditorMarkerFrame> frames) {
        for (Entry entry : entries) {
            try { entry.backend.editorMarkers(frames); }
            catch (RuntimeException exception) { failed(entry, "editorMarkers", exception); }
        }
    }

    private boolean dispatch(String channel, ChannelCall call) {
        for (Entry entry : entries) {
            try { if (call.call(entry.backend)) return true; }
            catch (RuntimeException exception) { failed(entry, channel, exception); }
        }
        return false;
    }

    private static void failed(Entry entry, String channel, RuntimeException exception) {
        System.err.println("[CineFX] Ultra backend failed in " + channel + ": " + entry.id + " - " + exception.getMessage());
    }

    @FunctionalInterface
    private interface ChannelCall { boolean call(UltraBackend backend); }
    private record Entry(Identifier id, int priority, UltraBackend backend) { }
}
