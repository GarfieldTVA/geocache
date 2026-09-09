package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.CinematicBackend;

import java.util.List;

/** Lowest-priority built-in channels. User/mod backends registered above this can replace them. */
final class CineFxBuiltInCinematicBackend implements CinematicBackend {
    @Override
    public boolean renderActors(SceneRenderContext context, List<ActorFrame> actors) {
        CineFxVanillaActorRenderer.render(context, actors);
        return true;
    }

    @Override
    public boolean renderMeshes(SceneRenderContext context, List<MeshFrame> meshes) {
        return CineFxNativeVisualFallback.renderMeshes(context, meshes);
    }

    @Override
    public boolean renderInstanceBatches(SceneRenderContext context, List<InstanceBatchFrame> batches) {
        return CineFxNativeVisualFallback.renderInstances(context, batches);
    }

    @Override
    public boolean renderShadows(SceneRenderContext context, List<ShadowFrame> shadows) {
        return CineFxNativeVisualFallback.renderShadows(context, shadows);
    }

    @Override
    public boolean renderVolumes(SceneRenderContext context, List<VolumeFrame> volumes) {
        return CineFxNativeVisualFallback.renderVolumes(context, volumes);
    }

    @Override
    public boolean renderAttachments(SceneRenderContext context, List<AttachmentFrame> attachments) {
        return CineFxNativeVisualFallback.renderAttachments(context, attachments);
    }

    @Override
    public boolean renderMegaEnvironments(SceneRenderContext context, List<MegaEnvironmentFrame> environments) {
        return CineFxNativeVisualFallback.renderMegaEnvironments(context, environments);
    }

    @Override
    public boolean applySky(SkyFrame sky) {
        return CineFxNativeVisualFallback.acceptSky(sky);
    }

    @Override
    public boolean mixAudioLayers(List<AudioLayerFrame> layers) {
        return CineFxAudioLayerMixer.apply(layers);
    }

    @Override
    public boolean applyPlayerControl(PlayerControlFrame control) {
        return CineFxPlayerControlState.apply(control);
    }
}
