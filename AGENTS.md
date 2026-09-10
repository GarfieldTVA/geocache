# CineFX coding-agent contract

This file is the first thing an AI coding agent should read before generating or modifying a mod that depends on CineFX.

## Target

CineFX on this branch targets Minecraft 1.21.11, Java 21, Fabric Loader 0.19.2, Fabric API 0.141.4+1.21.11 and Yarn 1.21.11+build.5.

Do not silently port examples to another Minecraft version. Mappings and rendering APIs are version-specific.

## Public API boundary

Dependent mods should use only:

- `dev.garfield.cinefx.api.*`
- `dev.garfield.cinefx.client.api.*` from client code only

Do not import `dev.garfield.cinefx.client.*` implementation classes from a dependent mod. Those are internal and may change.

If you are implementing CineFX itself, internal packages are allowed. If you are building a separate mod that uses CineFX, stay on the public API.

## Architecture rules

1. Register deterministic scene definitions in common initialization when the server and client both need to know the scene id.
2. Keep gameplay and world mutation server-authoritative. CineFX is primarily a rendering/event orchestration framework.
3. Synchronize an event by sending scene id + anchor + authoritative start tick + seed + small variables, not transforms every tick.
4. Prefer CineFX virtual rendering over Minecraft Display entities, ArmorStands, marker entities or fake tracked entities.
5. Never create per-frame/per-object S2C movement packets for a CineFX timeline. Put motion in `TransformTrack`, `AdvancedTransformTrack`, `MotionCurve` or `PathTrack`.
6. Prefer a built-in CineFX primitive over a custom renderer when the primitive already expresses the effect.
7. Do not use raw OpenGL in a dependent mod just to render a CineFX effect. Use the provided backend SPIs and Minecraft 1.21.11 render command queue.
8. If a premium backend does not consume a channel, allow CineFX's built-in fallback to run instead of duplicating the same effect.
9. Do not mutate the world client-side to fake an effect. If blocks must really change, do it in authoritative server gameplay code and let CineFX render the cinematic layer.
10. Treat scene element keys as stable ids inside a scene. Parent keys, actor targets, material targets and IK targets depend on those ids.

## Choose the lowest sufficient level

Use the smallest API layer that expresses the effect:

- `SceneElement`: blocks, sampled blocks, block skins, text, HUD, beams, rings, atmosphere, grades and other basic cinematic primitives.
- `ComplexElement`: actors, meshes, instance batches, graph nodes, trails, decals, shadows and volumes.
- `AdvancedEventElement`: attachments, crowds, sky, audio layers, player/cutscene control and mega-environments.
- `UltraEventElement`: post FX, cinematic light rigs, fracture, soft-body physics, procedural IK, particle fields, camera rigs, spatial audio, material FX, visual world deformation, portals and editor markers.
- `CustomWorldRenderer`, `CinematicBackend` or `UltraBackend`: only when the effect genuinely needs renderer-specific implementation.

Do not reimplement an Ultra primitive with hundreds of ordinary particles/entities unless a user explicitly asks for that look.

## Server event orchestration

For a simple synchronized scene use `CineFxServer`.

For a multi-phase live event use `EventProgram` + `EventDirector`.

Use `AssetBundle` preload before large glTF/GLB or renderer-owned logical assets.

Use `EventSignals` for named gameplay-driven transitions such as:

- `boss.dead`
- `engine.left.destroyed`
- `players.ready`
- `overload`

Prefer a named signal or a Director variable over polling unrelated game state from client render code.

Late joiners and players entering/leaving the audience radius are handled by the Director. Do not restart the whole event for a late joiner.

## Client integrations

Register client-only integrations through `ClientCineFx`:

- `registerRenderer`
- `registerPostFxBackend`
- `registerLightingBackend`
- `registerCinematicBackend`
- `registerUltraBackend`
- `registerAssetPreloader`

A backend should return `true` only when it has fully consumed that channel/frame. Returning `false` lets a lower-priority backend or CineFX fallback handle it.

Higher priority backends run before lower priority ones.

## glTF / GLB

Prefer logical model ids such as `mymod:model/ship` rather than hard-coding internal CineFX file lookup paths.

The built-in model path looks for common forms including:

- `assets/<namespace>/cinefx/models/<name>.glb`
- `assets/<namespace>/cinefx/models/<name>.gltf`
- `assets/<namespace>/models/<name>.glb`
- `assets/<namespace>/models/<name>.gltf`

The premium reference renderer supports glTF/GLB loading, textures/UVs, material factors, node animations, morph targets, CPU skinning and named nodes/sockets. Use named glTF nodes for attachment targets where practical.

If an asset is unsupported or missing, keep the CineFX fallback path rather than crashing the event.

## Performance rules

- Use `InstanceBatch` for large repeated geometry instead of thousands of independent meshes.
- Use `Crowd` for large fake crowds instead of hundreds of independently networked actors.
- Use `ParticleField` for force-driven mass particles instead of spawning an uncontrolled particle storm manually.
- Use cull distances.
- Keep scene timelines deterministic.
- Let `QualityTier` scale expensive fallback behavior.
- Avoid server tick loops that send visual state every tick.
- Avoid real chunk-light rebuilds for temporary cinematic lighting.
- Use preload for expensive assets before the moment they first appear.

## Java typing rule for tracks

`ScalarTrack` is `Double`-based. When creating scalar keyframes, use floating-point values:

```java
ScalarTrack.of(
    Keyframe.at(0, 0.0),
    Keyframe.at(40, 1.0)
)
```

Do not mix `Keyframe<Integer>` and `Keyframe<Double>` in the same scalar track.

## Threading

Client playback/renderer registration belongs on the Minecraft client thread or normal client initialization path.

Gameplay/world changes belong on the server thread.

Do not perform world mutations from a render callback.

## Validation before declaring a generated mod complete

At minimum:

1. `./gradlew build`
2. Verify no dependent mod imports CineFX internal client implementation packages.
3. Check that server gameplay code does not rely on client-only classes.
4. Check that visual synchronization does not send per-frame transforms.
5. Check glTF/logical assets are preloaded if they are important to the first visible frame.
6. Exercise the closest built-in showcase pattern.
7. If developing CineFX itself, run `/cinefxshowcase verify` in a test client and visually inspect the relevant showcase.

## Read next

For an AI agent, read in this order:

1. `llms.txt`
2. `docs/AI_INTEGRATION_GUIDE.md`
3. `docs/FEATURE_MATRIX.md`
4. `docs/ULTRA.md` when using Ultra effects
5. `docs/EVENT_ENGINE.md` for phased live events
6. `docs/BACKENDS.md` when implementing renderer/audio integrations
7. `docs/PERFORMANCE.md` before creating a large event
8. `docs/SHOWCASES.md` and the showcase source for working reference patterns

When documentation and source disagree, the current public API source on this branch is authoritative.