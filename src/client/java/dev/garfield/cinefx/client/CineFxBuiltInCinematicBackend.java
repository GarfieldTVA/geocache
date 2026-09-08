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
    public boolean applyPlayerControl(PlayerControlFrame control) {
        return CineFxPlayerControlState.apply(control);
    }
}
