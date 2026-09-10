# CineFX architecture

## Modules

```text
src/main/java/dev/garfield/cinefx/api
    Stable common API: scene data, tracks, motion, server playback.

src/main/java/dev/garfield/cinefx/network
    Typed S2C start/stop payloads.

src/client/java/dev/garfield/cinefx/client/api
    Stable client API: local playback and renderer/post-FX extension points.

src/client/java/dev/garfield/cinefx/client
    Internal runtime and 1.21.11 render implementation.
```

## Rendering path

CineFX registers one `WorldRenderEvents.END_MAIN` callback. Active scenes are already ordered by priority. An active element is sampled from absolute game time plus partial render tick. The resulting block/text/primitive is submitted to Minecraft's `OrderedRenderCommandQueue`.

There is no render entity per scene element.

## Synchronization

The server sends `PlayScenePayload(sceneId, anchor, startGameTime, seed, variables)`.

Definitions are code/assets that are already installed on clients. This is deliberate: network traffic should describe *which deterministic event starts*, not stream its animation every frame.

## Sampled blocks

When an active scene is constructed, any `SceneElement.Block` with `fixedState == null` snapshots its source world `BlockState`. This lets a visual copy survive after the authoritative server removes the source block and removes a repeated world lookup from the render loop.

## Conflict order

Ordering is lexicographic:

1. scene priority descending,
2. element priority descending.

Because exclusive claims are resolved before lower-priority elements render, the winner does not need an expensive deferred renderer or rollback.

## Post FX

CineFX has a compatibility-first HUD fallback for tint/exposure/vignette. Saturation and contrast are preserved in `GradeFrame` for a GPU backend. This avoids coupling the base framework to one framebuffer/shader implementation and makes integrations with Iris/Sodium/custom pipelines possible.

## World authority

CineFX does not perform gameplay block changes. The calling mod controls server state. This keeps a rendering dependency from becoming an accidental gameplay authority and prevents client-only scenes from causing world desynchronization.
