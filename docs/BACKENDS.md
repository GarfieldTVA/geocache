# CineFX Rendering Backends

CineFX deliberately separates event orchestration from renderer-specific implementation. The core owns timing, synchronization, scene graph resolution, culling, quality policy and backend dispatch. A renderer backend only consumes resolved frame data for the channels it understands.

## Priority and replacement

Backends are registered with a priority. The first backend that consumes a channel wins for that channel. This lets a mod provide a high-end implementation without disabling unrelated CineFX fallbacks.

For example, a renderer can replace only `renderMeshes` and `renderVolumes` while keeping the built-in vanilla actor, audio and cutscene-control channels.

## Native baseline

The built-in client currently provides real fallbacks for:

- vanilla `PLAYER` and `ENTITY` actors without adding them to the world/tracker
- custom fake-player `skinTexture`
- wide/slim player model selection through `appearance.skin_type`
- vanilla walk/run/crouch/swim/hurt/shake/death animation hints
- actor `lookAt`
- simple `head`/`neck`/`body` bone rotation overrides
- blob shadows
- ribbon-style trail fallback
- flat decals
- parented beam geometry
- parented vanilla particle emitters
- parented dynamic-light requests when a lighting backend exists
- quality-limited fake crowds by expanding them into render-only vanilla actors
- looping audio layers with live volume/pitch automation
- cinematic FOV/input/HUD/hand control

These fallbacks are designed to make the library testable and useful with no other renderer installed.

## Renderer-neutral channels

A high-end backend can consume:

```java
renderActors(...)
renderMeshes(...)
renderInstanceBatches(...)
renderShadows(...)
renderTrails(...)
renderDecals(...)
renderVolumes(...)
renderAttachments(...)
renderCrowds(...)
renderMegaEnvironments(...)
applySky(...)
mixAudioLayers(...)
applyPlayerControl(...)
```

It also receives `SceneRenderContext`, including the world render context, matrices, ordered render command queue, camera and absolute game tick.

## Actors and skeletons

`ActorFrame` contains:

- actor kind/resource id/profile
- optional skin texture + appearance map
- fully resolved world matrix
- world position and look-at target
- tint, opacity and emissive values
- animation layers
- per-bone overrides
- morph weights
- shadow intent

The native vanilla renderer supports only the subset that maps cleanly onto vanilla render state. A skeletal custom-model backend should resolve its own model bones and apply `BoneSample`/`MorphSample` before rendering.

### Bone/socket attachments

`AttachmentFrame` carries both the actor/root world matrix and optional `boneName`.

CineFX resolves the parent scene node. If a backend owns the actual skeleton, it can multiply the provided attachment transform by its resolved bone/socket matrix. This avoids coupling CineFX core to one skeleton/model format.

## Meshes and glTF/GLB

CineFX does not hard-code a proprietary 3D asset format. `MeshFrame.modelId` is a logical resource id.

A production backend may map it to:

- glTF / GLB
- OBJ
- Blockbench exports
- baked Minecraft models
- a custom GPU mesh cache

This is intentional: loading and skinning glTF is renderer ownership, while CineFX owns when/where/how the model participates in the event. A backend should preload logical model ids through the asset-preload API to avoid first-frame stalls.

## Instance batches

`InstanceBatchFrame` keeps the root matrix resolved but intentionally leaves the immutable instance specs compact. Do not expand 20,000 instances into 20,000 Minecraft entities.

A GPU backend should upload instance transforms/variants to a buffer and draw shared geometry in batches. Respect `lodGroup` and the current CineFX quality tier.

## Shadows

CineFX supplies a cheap blob-shadow fallback. Higher-quality modes are exposed as `PROJECTED` and `GEOMETRY` so a backend can implement projected/depth/geometry-aware shadows using its own renderer lifecycle.

Avoid raw GL state changes outside Minecraft's 1.21.11 ordered render pipeline. Geometry shadow capture should be integrated with the same renderer/model source used by the backend so actor/mesh deformation and shadow silhouettes remain consistent.

## Volumes

`VolumeFrame` describes shape, material, transform, color, density, noise, distortion and emissive strength.

A shader backend can interpret this as ray-marched fog/energy/cloud volume. The core intentionally does not force a shader framework because Iris, custom renderers and vanilla-only installations have different capabilities.

When volumetrics are disabled by adaptive quality, the event should retain enough rings, particles, meshes or lighting to preserve the narrative beat.

## Sky

`SkyFrame` contains horizon/zenith colors, sun/moon brightness, eclipse amount, aurora amount, rotation and an optional logical skybox id.

A backend may render a cubemap/dome, procedural atmosphere or shader sky. Keep it as a visual layer only; CineFX does not mutate world time/biomes/chunks unless the server mod explicitly chooses to do so.

## Audio

The native mixer supports looping stems and live volume/pitch changes. `lowPass` and any other DSP parameter remain hints for a specialized audio backend.

If sample-perfect crossfades are required, consume `AudioLayerFrame` in a higher-priority backend and maintain one continuous audio clock rather than restarting Minecraft `SoundInstance`s.

## Dynamic lighting

Lighting is a separate renderer-neutral bridge. CineFX batches current point/spot-light requests and hands them to the registered lighting backend. With no lighting backend, the request is skipped instead of rebuilding chunks or spawning fake light blocks.

This design is compatible with Iris/Sodium/custom shader/dynamic-light integrations.

## Backend contract

A good backend should:

1. consume only the channels it actually implements;
2. cache models/materials and prewarm requested assets;
3. avoid creating tracker/world entities for visual-only actors;
4. use Minecraft 1.21.11 ordered render submission rather than uncontrolled immediate-mode GL;
5. honor opacity/emissive/culling/quality information;
6. batch repeated geometry;
7. clear all per-scene caches when scenes disappear or the client disconnects;
8. remain deterministic from scene time/seed rather than inventing unsynchronized state.

The core/backends split is what allows the same 10-minute event program to run on a vanilla-quality fallback, a lightweight Sodium setup and a custom cinematic renderer without changing the server timeline.