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
- built-in glTF 2.0 / GLB triangle rendering for `Mesh` and `CUSTOM_MODEL`
- glTF scene/node transforms, indexed and non-indexed triangle primitives
- GLB BIN chunks, external buffers and base64/data-URI buffers
- glTF `pbrMetallicRoughness.baseColorFactor` as the dependency-free material colour
- named glTF node lookup for `boneName`/socket attachments on custom-model actors
- automatic procedural proxy fallback when a logical model is missing or unsupported
- blob/projected/geometry-style shadow fallbacks
- ribbon-style trail fallback
- flat decals
- quality-limited volume proxy geometry
- parented beam geometry
- parented vanilla particle emitters
- parented dynamic-light requests when a lighting backend exists
- quality-limited fake crowds by expanding them into render-only vanilla actors
- quality-limited instance and mega-environment proxy geometry
- looping audio layers with live volume/pitch automation
- cinematic FOV/input/HUD/hand control
- horizon/zenith/eclipse/aurora sky fallback

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

The native vanilla renderer handles the subset that maps cleanly onto vanilla render state. `CUSTOM_MODEL` actors are offered to the built-in glTF/GLB renderer first. If no compatible resource is present, they fall back to the procedural mesh proxy instead of disappearing.

The reference glTF path intentionally does not implement GPU skinning or morph deformation. A skeletal production backend can consume the same `ActorFrame`, resolve its own rig and apply `AnimationSample`, `BoneSample` and `MorphSample` before rendering.

### Bone/socket attachments

`AttachmentFrame` carries the parent/root world matrix and optional `boneName`.

For built-in glTF `CUSTOM_MODEL` actors, CineFX records named node transforms while rendering. If an attachment targets that actor and its `boneName` matches a glTF node name, the attachment is refined onto that node before the native attachment fallback runs. If the node is absent, the attachment keeps its already-resolved actor/root transform.

A higher-priority skeletal backend can replace both actor and attachment channels and perform the same operation against its true animated skeleton.

## Meshes and glTF/GLB

`MeshFrame.modelId` remains a logical resource id rather than a hard-coded file path. The built-in renderer tries these resource forms for a logical model such as `mymod:model/ship`:

- `mymod:cinefx/models/ship.glb`
- `mymod:cinefx/models/ship.gltf`
- `mymod:models/ship.glb`
- `mymod:models/ship.gltf`

A model id that already ends in `.gltf` or `.glb` is also tried directly.

The reference loader supports:

- glTF 2.0 JSON and GLB 2.0 containers
- active scene roots and node hierarchies
- node `matrix` or translation/rotation/scale transforms
- triangle primitive mode (`mode = 4`)
- `POSITION`
- indexed primitives using unsigned-byte, unsigned-short and unsigned-int indices
- non-indexed primitives
- buffer views, accessor byte offsets and interleaved `byteStride`
- GLB BIN chunks
- external sibling buffers
- base64/data-URI buffers
- normalized numeric accessor decoding used by the baseline reader
- material `baseColorFactor`
- named-node/socket caching

The reference path renders through Minecraft 1.21.11's ordered command queue and `RenderLayers.debugQuads()`. It does not use uncontrolled raw OpenGL.

### Reference-renderer limitations

The built-in path is deliberately a safe baseline, not a full PBR engine. It does not currently implement:

- texture sampling / UV materials
- normal maps, metallic/roughness texture evaluation or IBL
- GPU skinning from glTF `skins`
- glTF animation-channel playback
- morph-target deformation
- Draco/Meshopt compressed geometry extensions
- full alpha-material sorting

Those are renderer-quality upgrades rather than scene-timeline requirements. A higher-priority backend may map the same logical ids to a complete glTF renderer, Blockbench runtime, baked Minecraft geometry or its own GPU mesh cache without changing the server event.

If the built-in loader cannot resolve a logical model, CineFX immediately uses the native proxy shape. Missing optional visual assets therefore do not deadlock an event preload.

## Asset preloading

`AssetBundle.logicalAssets()` entries whose paths look like model assets (`model/...`, `models/...`, `.gltf`, `.glb`) are warmed by the built-in glTF cache before external preload backends are consulted.

Other logical assets remain available to registered `AssetPreloadBackend` implementations. This lets a renderer warm textures, rigs, skyboxes or shader-owned resources while CineFX warms its own reference meshes.

## Instance batches

`InstanceBatchFrame` keeps the root matrix resolved but intentionally leaves the immutable instance specs compact. Do not expand 20,000 instances into 20,000 Minecraft entities.

The vanilla-quality fallback samples a quality-limited subset as proxy geometry. A GPU backend should upload instance transforms/variants to a buffer and draw shared geometry in batches. Respect `lodGroup` and the current CineFX quality tier.

## Shadows

CineFX supplies dependency-free blob, projected and geometry-style approximations. Higher-quality modes can still be replaced by a backend implementing projected/depth/geometry-aware shadows using its own renderer lifecycle.

Avoid raw GL state changes outside Minecraft's 1.21.11 ordered render pipeline. Geometry shadow capture should be integrated with the same renderer/model source used by the backend so actor/mesh deformation and shadow silhouettes remain consistent.

## Volumes

`VolumeFrame` describes shape, material, transform, color, density, noise, distortion and emissive strength.

The native path supplies quality-limited translucent proxy shells so the beat remains visible without a shader framework. A shader backend can replace this with ray-marched fog/energy/cloud volume.

When volumetrics are disabled by adaptive quality, the event should retain enough rings, particles, meshes or lighting to preserve the narrative beat.

## Sky

`SkyFrame` contains horizon/zenith colors, sun/moon brightness, eclipse amount, aurora amount, rotation and an optional logical skybox id.

The native fallback supplies a lightweight HUD-space atmosphere approximation. A backend may replace it with a cubemap/dome, procedural atmosphere or shader sky. Keep it as a visual layer only; CineFX does not mutate world time/biomes/chunks unless the server mod explicitly chooses to do so.

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

The core/backends split is what allows the same 10-minute event program to run on the built-in reference renderer, a lightweight Sodium setup and a custom cinematic renderer without changing the server timeline.
