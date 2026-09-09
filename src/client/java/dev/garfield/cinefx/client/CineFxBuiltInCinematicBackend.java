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
            List<ActorFrame> premiumMissing = CineFxPremiumGltfRenderer.renderActors(context, List.copyOf(custom));
            List<ActorFrame> referenceMissing = premiumMissing.isEmpty()
                    ? List.of()
                    : CineFxGltfRenderer.renderCustomActors(context, premiumMissing);
            if (!referenceMissing.isEmpty()) {
                ArrayList<MeshFrame> proxies = new ArrayList<>(referenceMissing.size());
                for (ActorFrame actor : referenceMissing) {
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
        List<MeshFrame> premiumMissing = CineFxPremiumGltfRenderer.renderMeshes(context, meshes);
        if (premiumMissing.isEmpty()) return true;
        List<MeshFrame> referenceMissing = CineFxGltfRenderer.renderMeshes(context, premiumMissing);
        if (referenceMissing.isEmpty()) return true;
        return CineFxNativeVisualFallback.renderMeshes(context, referenceMissing);
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
        List<AttachmentFrame> premium = CineFxPremiumGltfRenderer.refineAttachments(attachments);
        return CineFxNativeVisualFallback.renderAttachments(context, CineFxGltfRenderer.refineAttachments(premium));
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
