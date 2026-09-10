package dev.garfield.cinefx.client.api;

import net.minecraft.util.Identifier;

import java.util.Map;

/** Called once when a Scene marker crosses its timeline window on this client. */
@FunctionalInterface
public interface EventMarkerListener {
    void onMarker(Identifier sceneId, long sceneInstanceId, String markerName, Map<String, String> parameters);
}
