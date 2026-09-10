# CineFX Rendering Backends

CineFX deliberately separates event orchestration from renderer-specific implementation. The core owns timing, synchronization, scene graph resolution, culling, quality policy and backend dispatch. A renderer backend only consumes resolved frame data for the channels it understands.

## Priority and replacement

Backends are registered with a priority. The first backend that consumes a channel wins for that channel. This lets a mod provide a high-end implementation without disabling unrelated CineFX fallbacks.

For example, a renderer can replace only `renderMeshes` and `renderVolumes` while keeping the built-in vanilla actor, audio and cutscene-control channels.

A backend should return `true` only when it has actually consumed that channel/frame. Returning `false` lets a lower-priority backend or the built-in fallback handle it.

## Native baseline

The built-in client currently provides real fallbacks for:

- vanilla `PLAYER` and `ENTITY` actors without adding them to the world/tracker
- custom fake-player `skinTexture`
- wide/slim player model selection through `appearance.skin_type`
- vanilla walk/run/crouch/swim/hurt/shake/death animation hints
- actor `lookAt`
- simple vanilla head/neck/body bone rotation overrides
- premium glTF 2.0 / GLB rendering for `Mesh` and `CUSTOM_MODEL`
- glTF scene/node transforms, triangle/strip/fan primitives and indexed/non-indexed geometry
- GLB BIN chunks, external buffers and base64/data-URI buffers
- UVs and textures, including embedded image data where supported by the reference loader
- material base color plus metallic/roughness/emissive factors and alpha modes
- vertex colors and normals with safe fallback/generation paths
- glTF node animations for translation/rotation/scale/weights
- LINEAR / STEP / CUBICSPLINE interpolation paths
- morph targets
- CPU skinning from `JOINTS_0` / `WEIGHTS_0` and inverse-bind matrices
- sparse-accessor handling used by the premium reader
- named glTF node lookup for sockets/bone-target attachments
- procedural Ultra rig data translated into bone overrides for the premium glTF actor path
- Ultra material/light state translated into the built-in model rendering path
- automatic simpler glTF/procedural proxy fallback when a logical model is missing or unsupported
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
- Ultra soft-body, particle-field, fracture, spatial-audio, camera, deformation, portal and post-FX approximations

These fallbacks are designed to make the library testable and useful with no other renderer installed.

## Renderer-neutral CinematicBackend channels

A high-end `CinematicBackend` can consume:

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

## UltraBackend channels

Ultra effects have a separate renderer-neutral SPI. Register it through `ClientCineFx.registerUltraBackend(...)`.

Independent channels are:

```text
postProcess
lightRigs
fractures
softBodies
proceduralRigs
particleFields
cameraRigs
spatialAudio
materialEffects
worldDeforms
portals
editorMarkers
```

A custom shader renderer can therefore replace only portals/post-processing while CineFX keeps handling physics/audio/camera fallbacks.

See `docs/ULTRA.md` for the element contracts and `docs/AI_INTEGRATION_GUIDE.md` for dependent-mod rules.

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

The native vanilla renderer handles the subset that maps cleanly onto vanilla render state. `CUSTOM_MODEL` actors are offered to the premium glTF/GLB renderer first. If no compatible resource is present, they fall back through the simpler reference loader and then the procedural mesh proxy instead of disappearing.

The premium reference glTF path supports CPU skinning, morph deformation and glTF animation channels. A higher-priority skeletal renderer can still replace the actor channel when it needs GPU skinning, custom shading, larger animation systems or renderer-owned rig caches.

### Bone/socket attachments

`AttachmentFrame` carries the parent/root world matrix and optional `boneName`.

For built-in glTF `CUSTOM_MODEL` actors, CineFX records named node transforms while rendering. If an attachment targets that actor and its `boneName` matches a glTF node name, the attachment is refined onto that node before the native attachment fallback runs. If the node is absent, the attachment keeps its already-resolved actor/root transform.

A higher-priority skeletal backend can replace both actor and attachment channels and perform the same operation against its own animated skeleton.

## Meshes and glTF/GLB

`MeshFrame.modelId` remains a logical resource id rather than a hard-coded file path. The built-in renderer tries common resource forms for a logical model such as `mymod:model/ship`:

- `mymod:cinefx/models/ship.glb`
- `mymod:cinefx/models/ship.gltf`
- `mymod:models/ship.glb`
- `mymod:models/ship.gltf`

A model id that already ends in `.gltf` or `.glb` is also tried directly.

The premium/reference loaders cover the important dependency-free path, including:

- glTF 2.0 JSON and GLB 2.0 containers
- active scene roots and node hierarchies
- node `matrix` or translation/rotation/scale transforms
- triangle, strip and fan primitive handling in the premium path
- `POSITION`, normals, UVs, vertex colors, joints and weights where available
- indexed primitives using unsigned-byte, unsigned-short and unsigned-int indices
- non-indexed primitives
- buffer views, accessor byte offsets, interleaved `byteStride` and sparse accessors used by the premium reader
- GLB BIN chunks
- external sibling buffers
- base64/data-URI buffers
- material base color and premium material factors
- textures/UV sampling through Minecraft render layers
- node animation channels
- morph targets
- CPU skinning
- named-node/socket caching

The renderer stays inside Minecraft 1.21.11's ordered rendering path rather than relying on uncontrolled raw OpenGL state.

### Reference-renderer limitations

The built-in renderer is intentionally a portable reference path, not a full standalone AAA renderer. Higher-priority backends remain the right place for features such as:

- full physically based IBL/environment lighting
- advanced normal/occlusion/material texture workflows beyond the portable baseline
- GPU skinning for extremely large animated crowds
- renderer-native mesh/animation compression pipelines such as Draco/Meshopt when a backend chooses to support them
- robust order-independent transparency
- recursive render-target portals/mirrors
- true depth shadow maps
- ray-marched volumetrics
- sample-accurate DSP audio

Those are renderer-quality upgrades rather than scene-timeline requirements. The same CineFX scene definition can drive a specialized implementation without changing the server event.

If the built-in loader cannot resolve a logical model, CineFX falls back rather than crashing the event.

## Asset preloading

`AssetBundle.logicalAssets()` entries whose paths look like model assets (`model/...`, `models/...`, `.gltf`, `.glb`) are warmed by the built-in model cache before external preload backends are consulted.

Other logical assets remain available to registered `AssetPreloadBackend` implementations. This lets a renderer warm textures, rigs, skyboxes, shaders or audio-owned resources while CineFX warms its own model path.

## Instance batches

`InstanceBatchFrame` keeps the root matrix resolved but intentionally leaves immutable instance specs compact. Do not expand 20,000 instances into 20,000 Minecraft entities.

The vanilla-quality fallback samples a quality-limited subset as proxy geometry. A GPU backend should upload instance transforms/variants to a buffer and draw shared geometry in batches. Respect `lodGroup` and the current CineFX quality tier.

## Shadows

CineFX supplies dependency-free blob, projected and geometry-style approximations. Higher-quality modes can be replaced by a backend implementing projected/depth/geometry-aware shadows using its renderer lifecycle.

Avoid raw GL state changes outside Minecraft's 1.21.11 ordered render pipeline. Geometry shadow capture should be integrated with the same renderer/model source used by the backend so actor/mesh deformation and shadow silhouettes remain consistent.

## Volumes

`VolumeFrame` describes shape, material, transform, color, density, noise, distortion and emissive strength.

The native path supplies quality-limited translucent proxy shells so the beat remains visible without a shader framework. A shader backend can replace this with ray-marched fog/energy/cloud volumes.

When volumetrics are disabled by adaptive quality, the event should retain enough rings, particles, meshes or lighting to preserve the narrative beat.

## Sky

`SkyFrame` contains horizon/zenith colors, sun/moon brightness, eclipse amount, aurora amount, rotation and an optional logical skybox id.

The native fallback supplies a lightweight atmosphere approximation. A backend may replace it with a cubemap/dome, procedural atmosphere or shader sky. Keep it as a visual layer only; CineFX does not mutate world time/biomes/chunks unless the server mod explicitly chooses to do so.

## Audio

The native mixer supports looping stems and live volume/pitch changes. Ultra spatial audio adds moving-source attenuation, approximate occlusion, Doppler and reverb/low-pass intent.

If sample-perfect crossfades or high-quality DSP are required, consume the audio channel in a higher-priority backend and maintain one continuous audio clock rather than restarting Minecraft sounds unnecessarily.

## Dynamic lighting

Lighting is renderer-neutral. CineFX batches current point/spot-light requests and hands them to registered lighting backends. With no specialized lighting backend, CineFX avoids rebuilding vanilla chunk lighting every frame.

This design is intended to coexist with Iris/Sodium/custom shader/dynamic-light integrations.

## Backend contract

A good backend should:

1. consume only the channels it actually implements;
2. cache models/materials and prewarm requested assets;
3. avoid creating tracker/world entities for visual-only actors;
4. use Minecraft 1.21.11 ordered render submission rather than uncontrolled immediate-mode GL;
5. honor opacity/emissive/culling/quality information;
6. batch repeated geometry;
7. clear per-scene/client caches when scenes disappear or the client disconnects;
8. remain deterministic from scene time/seed rather than inventing unsynchronized state;
9. return `false` for unsupported channels so fallbacks remain available;
10. preserve event readability when lowering quality.

The core/backends split is what allows the same long-running event program to run on the built-in reference renderer, a lightweight renderer stack and a custom cinematic renderer without changing the authoritative server timeline.