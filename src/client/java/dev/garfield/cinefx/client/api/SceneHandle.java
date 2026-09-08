package dev.garfield.cinefx.client.api;

import net.minecraft.util.Identifier;

/** Stable handle returned for one active client-side scene instance. */
public record SceneHandle(long instanceId, Identifier sceneId) { }
