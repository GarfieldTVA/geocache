package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AttachmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AtmosphereFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AudioLayerFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.CameraFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.CrowdFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.DecalFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.InstanceBatchFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MegaEnvironmentFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.ParticleFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.PlayerControlFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import dev.garfield.cinefx.client.api.CinematicBackend.ShadowFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.SkyFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.TrailFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.VolumeFrame;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Priority-ordered integration registry for high-end cinematic renderer channels. */
public final class CinematicBackendRegistry {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void register(Identifier id, int priority, CinematicBackend backend) {
        if (id == null || backend == null) throw new IllegalArgumentException("id and backend are required");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalStateException("CineFX cinematic backend already registered: " + id);
        }
        entries.add(new Entry(id, priority, backend));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    public synchronized boolean camera(CameraFrame frame) { return first("Camera", entry -> entry.backend.applyCamera(frame)); }
    public synchronized boolean atmosphere(AtmosphereFrame frame) { return first("Atmosphere", entry -> entry.backend.applyAtmosphere(frame)); }
    public synchronized boolean particles(List<ParticleFrame> frames) { return first("Particle", entry -> entry.backend.emitParticles(frames)); }
    public synchronized boolean actors(SceneRenderContext context, List<ActorFrame> frames) { return first("Actor", entry -> entry.backend.renderActors(context, frames)); }
    public synchronized boolean meshes(SceneRenderContext context, List<MeshFrame> frames) { return first("Mesh", entry -> entry.backend.renderMeshes(context, frames)); }
    public synchronized boolean instances(SceneRenderContext context, List<InstanceBatchFrame> frames) { return first("Instance", entry -> entry.backend.renderInstanceBatches(context, frames)); }
    public synchronized boolean shadows(SceneRenderContext context, List<ShadowFrame> frames) { return first("Shadow", entry -> entry.backend.renderShadows(context, frames)); }
    public synchronized boolean trails(SceneRenderContext context, List<TrailFrame> frames) { return first("Trail", entry -> entry.backend.renderTrails(context, frames)); }
    public synchronized boolean decals(SceneRenderContext context, List<DecalFrame> frames) { return first("Decal", entry -> entry.backend.renderDecals(context, frames)); }
    public synchronized boolean volumes(SceneRenderContext context, List<VolumeFrame> frames) { return first("Volume", entry -> entry.backend.renderVolumes(context, frames)); }

    public synchronized boolean attachments(SceneRenderContext context, List<AttachmentFrame> frames) { return first("Attachment", entry -> entry.backend.renderAttachments(context, frames)); }
    public synchronized boolean crowds(SceneRenderContext context, List<CrowdFrame> frames) { return first("Crowd", entry -> entry.backend.renderCrowds(context, frames)); }
    public synchronized boolean megaEnvironments(SceneRenderContext context, List<MegaEnvironmentFrame> frames) { return first("MegaEnvironment", entry -> entry.backend.renderMegaEnvironments(context, frames)); }
    public synchronized boolean sky(SkyFrame frame) { return first("Sky", entry -> entry.backend.applySky(frame)); }
    public synchronized boolean audioLayers(List<AudioLayerFrame> frames) { return first("AudioLayer", entry -> entry.backend.mixAudioLayers(frames)); }
    public synchronized boolean playerControl(PlayerControlFrame frame) { return first("PlayerControl", entry -> entry.backend.applyPlayerControl(frame)); }

    private boolean first(String channel, BackendCall call) {
        for (Entry entry : entries) {
            try {
                if (call.call(entry)) return true;
            } catch (RuntimeException exception) {
                failed(channel, entry, exception);
            }
        }
        return false;
    }

    private static void failed(String channel, Entry entry, RuntimeException exception) {
        System.err.println("[CineFX] " + channel + " backend failed: " + entry.id + " - " + exception.getMessage());
    }

    @FunctionalInterface
    private interface BackendCall { boolean call(Entry entry); }
    private record Entry(Identifier id, int priority, CinematicBackend backend) { }
}
