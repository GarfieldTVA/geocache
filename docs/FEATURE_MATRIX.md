# CineFX Feature Matrix

Use this table before choosing an API. Prefer the lowest-level primitive that already expresses the requested effect.

| Goal | Preferred API | Built-in baseline | Premium/backend path |
| --- | --- | --- | --- |
| Animated block | `SceneElement.Block` | Native virtual block rendering | Custom renderer only if special material needed |
| Rip real block visually | sampled `SceneElement.Block` | Snapshots real `BlockState` then animates copy | Same timeline usable by custom renderer |
| Visual block replacement | `SceneElement.BlockSkin` | Visual-only replacement model | Renderer can replace material behavior |
| World text | `SceneElement.WorldText` | Native world text | Custom font/resources supported |
| HUD text/countdown | `SceneElement.HudText` | Native HUD | Can coexist with post-FX backend |
| Beam / laser | `SceneElement.Beam` | Native geometry | Attachment/backend versions available |
| Ring / shockwave | `SceneElement.Ring` | Native geometry | Custom renderer optional |
| Atmosphere / grade | `EventElement.Atmosphere`, `SceneElement.ScreenGrade` | Conservative fallback | `PostFxBackend` / Ultra post FX |
| Custom glTF mesh | `ComplexElement.Mesh` | Premium glTF/GLB path, then simpler/reference/proxy fallback | `CinematicBackend.renderMeshes` |
| Fake player or mob | `ComplexElement.Actor` | Render-only vanilla actor path | `CinematicBackend.renderActors` |
| Skinned/morphed custom hero | `ComplexElement.Actor` with `CUSTOM_MODEL` | Premium glTF animations, morphs, CPU skinning | Higher-end cinematic renderer can replace |
| Parent/child hierarchy | `ComplexElement.Node` + transformable elements | Shared scene graph | Backend receives resolved matrices |
| Thousands of repeated objects | `ComplexElement.InstanceBatch` | Quality-limited proxy fallback | GPU instancing backend |
| Trail | `ComplexElement.Trail` | Ribbon fallback | Custom ribbon/tube renderer |
| Decal | `ComplexElement.Decal` | Flat fallback | Conforming/deferred backend |
| Shadow intent | `ComplexElement.Shadow` | Blob/projected/geometry approximations | Shadow-map/depth renderer |
| Volume | `ComplexElement.Volume` | Proxy volume | Raymarch backend |
| Attach light/beam/audio/text to actor | `AdvancedEventElement.Attachment` | Root/socket-aware fallback | Backend resolves advanced skeleton/material behavior |
| Fake crowd | `AdvancedEventElement.Crowd` | Quality-limited vanilla actor expansion | Batched crowd backend |
| Large modular environment | `AdvancedEventElement.MegaEnvironment` | Quality-limited proxy cells | Renderer-specific streaming/instancing |
| Sky change | `AdvancedEventElement.Sky` | Horizon/zenith/eclipse/aurora fallback | Custom sky shader/backend |
| Music/audio stem layer | `AdvancedEventElement.AudioLayer` | Looping volume/pitch mixer | High-end audio backend |
| Lock player / hide HUD / FOV | `AdvancedEventElement.PlayerControl` | Native input/HUD/hand/FOV control | Backend can consume richer control |
| Bloom / DOF / glitch / film effects | `UltraEventElement.PostProcess` | Safe visual approximation | `UltraBackend.postProcess` for true framebuffer effects |
| Cinematic light rig | `UltraEventElement.LightRig` | Dynamic-light requests + visual volumetric approximation | True shader lights/shadows/god-rays |
| Break model into pieces | `UltraEventElement.Fracture` | Client-side fracture/debris approximation | Geometry-aware/GPU destruction backend |
| Rope / chain / cloth / tentacle | `UltraEventElement.SoftBody` | Client-only constraint simulation | GPU/advanced solver backend |
| IK / aim / look-at / foot plant | `UltraEventElement.ProceduralRig` | Converted to model bone overrides for premium glTF path | Full skeletal solver backend |
| Massive force particles | `UltraEventElement.ParticleField` | Client simulation with force fields, culling and quality budgets | GPU particle backend |
| Rail / orbit / handheld camera | `UltraEventElement.CameraRig` | Native camera position/look-at/FOV/shake/collision | Backend can add roll/advanced optics |
| 3D moving sound | `UltraEventElement.SpatialAudio` | Attenuation, occlusion approximation, Doppler and reverb approximation | DSP/spatial-audio backend |
| Dissolve / freeze / hologram / cloak | `UltraEventElement.MaterialEffect` | Modulates built-in model rendering/fallback | Shader material backend |
| Visual terrain crack/lift/rebuild | `UltraEventElement.WorldDeform` | Visual deformation representation | Shader/geometry terrain backend |
| Portal / mirror / camera feed | `UltraEventElement.PortalSurface` | Animated fallback representation | Render-target/recursive portal backend |
| Timeline labels | `UltraEventElement.EditorMarker` | Ultra editor overlay | External editor/backend tooling |
| Multi-phase live event | `EventProgram` + `EventDirector` | Server-authoritative phases, variables, preload, audience and resync | External director UI can call same API |
| Gameplay-driven branch | `EventSignals` or Director variables | Named deterministic transition condition | Same server orchestration |
| Heavy asset warmup | `AssetBundle` | Client resource/logical preload + ACK | Custom `AssetPreloadBackend` |

## Selection rules for AI agents

1. If one row matches the user's intent, use that API first.
2. If multiple rows apply, combine elements in one scene rather than creating independent render systems.
3. For an effect attached to something moving, prefer parenting/socket attachment rather than recomputing world position server-side.
4. For large counts, use batching/crowd/field primitives before creating many independent elements.
5. Only write a backend when the built-in primitive describes the intent but the requested visual quality requires renderer-specific implementation.
6. Never replace deterministic timeline data with per-tick networking just because a custom effect is complex.

## Quality model

The event must preserve timing/readability even when visual detail drops.

| Tier | Intended behavior |
| --- | --- |
| `ULTRA` | Full premium effect where supported |
| `HIGH` | High detail with modest budgets |
| `MEDIUM` | Reduced particles/shadows/instances |
| `LOW` | Simplified effects/proxies |
| `SAFE` | Minimum-cost representation that preserves event meaning |

Custom backends should query `ClientCineFx.quality()` and degrade gracefully.

## Source authority

This matrix is a navigation aid. Constructor signatures and exact enums must always be read from the current public API source before generating final code.