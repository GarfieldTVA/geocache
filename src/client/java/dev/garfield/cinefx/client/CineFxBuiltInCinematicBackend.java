package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.client.api.CinematicBackend;

import java.util.ArrayList;
import java.util.List;

/** Lowest-priority built-in channels. User/mod backends registered above this can replace them. */
final class CineFxBuiltInCinematicBackend implements CinematicBackend {
    @Override
    public boolean renderActors(SceneRenderContext context, List<ActorFrame> actors) {
        ArrayList<ActorFrame> vanilla = new ArrayList<>();
        ArrayList<ActorFrame> custom = new ArrayList<>();
        for (ActorFrame actor : actors) {
            if (actor.kind() == ComplexElement.ActorKind.CUSTOM_MODEL) custom.add(actor);
            else vanilla.add(actor);
        }

        if (!vanilla.isEmpty()) CineFxVanillaActorRenderer.render(context, List.copyOf(vanilla));
        if (!custom.isEmpty()) {
            List<ActorFrame> missing = CineFxGltfRenderer.renderCustomActors(context, List.copyOf(custom));
            if (!missing.isEmpty()) {
                ArrayList<MeshFrame> proxies = new ArrayList<>(missing.size());
                for (ActorFrame actor : missing) {
                    proxies.add(new MeshFrame(actor.sceneInstanceId(), actor.elementKey(), actor.resourceId(), null,
                            actor.worldMatrix(), actor.worldPosition(), actor.tintArgb(), actor.opacity(), actor.emissive(),
                            actor.castShadow(), actor.appearance(), actor.localTick()));
                }
                CineFxNativeVisualFallback.renderMeshes(context, List.copyOf(proxies));
            }
        }
        return true;
    }

    @Override
    public boolean renderMeshes(SceneRenderContext context, List<MeshFrame> meshes) {
        List<MeshFrame> missing = CineFxGltfRenderer.renderMeshes(context, meshes);
        if (missing.isEmpty()) return true;
        return CineFxNativeVisualFallback.renderMeshes(context, missing);
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
        return CineFxNativeVisualFallback.renderAttachments(context, CineFxGltfRenderer.refineAttachments(attachments));
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
