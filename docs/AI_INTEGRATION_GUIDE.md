# CineFX AI Integration Guide

This guide is written for coding agents generating a separate Minecraft mod that depends on CineFX.

The goal is not only to make code compile. The goal is to generate mods that preserve CineFX's synchronization, performance, fallback and compatibility model.

## 1. Start by deciding what kind of mod you are generating

Before writing code, classify the request:

### A. A simple cinematic effect
Use `SceneElement` and a registered `SceneDefinition`.

Examples:
- countdown
- meteor
- animated block extraction
- ring/shockwave
- beam
- HUD/world text
- screen grade

### B. A complex rendered scene
Use `ComplexElement`.

Examples:
- fake player / mob actor
- custom glTF model
- repeated mesh fleet/debris
- trail/decal/volume
- hierarchy of parented transform nodes

### C. A live event with advanced orchestration
Use `AdvancedEventElement` plus `EventProgram` / `EventDirector`.

Examples:
- multi-phase boss intro
- crowd evacuation
- player-control cutscene
- sky/audio layer changes
- attachments to actors/sockets
- preload and late-join resync

### D. A premium interactive event
Use `UltraEventElement` and optionally an `UltraBackend`.

Examples:
- bloom/DOF/glitch intent
- cinematic light rigs
- fracture/destruction
- rope/cloth/tentacle
- IK
- force-driven particles
- rail/orbit camera
- spatial audio
- dissolve/freeze/hologram effects
- visual terrain deformation
- portals/mirrors/camera feeds

Do not jump directly to a custom renderer if a built-in primitive already models the effect.

---

## 2. Project dependency

The dependent Fabric mod should declare CineFX as a dependency in `fabric.mod.json`:

```json
{
  "depends": {
    "cinefx": ">=0.1.0"
  }
}
```

For local development after publishing CineFX to Maven Local:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "dev.garfield:cinefx:0.1.0"
}
```

Never copy CineFX internal source files into the dependent mod as a shortcut.

---

## 3. Public package boundary

Allowed from dependent mods:

```text
dev.garfield.cinefx.api.*
dev.garfield.cinefx.client.api.*
```

The second package is client-only.

Do not import implementation classes from:

```text
dev.garfield.cinefx.client.*
```

If you need behavior that is not exposed publicly, either use a public backend SPI or extend CineFX itself in a separate change.

---

## 4. Registration pattern

A normal dependent mod should register deterministic scene definitions during common initialization.

```java
public final class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MyScenes.register();
    }
}
```

Keep the scene definition deterministic and side-neutral whenever possible.

Client renderer registrations belong in a `ClientModInitializer`:

```java
public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCineFx.registerUltraBackend(
                Identifier.of("mymod", "ultra"),
                100,
                new MyUltraBackend()
        );
    }
}
```

---

## 5. The synchronization model

The server should not stream transform state every tick.

The intended model is:

```text
registered scene definition on both sides
+
scene id
anchor
server start tick
seed
small variables map
=
deterministic local evaluation on each client
```

Use `CineFxServer` for simple playback and `EventDirector` for orchestrated events.

Bad generated pattern:

```text
server tick -> compute 500 transforms -> send 500 packets -> clients apply transforms
```

Good generated pattern:

```text
server starts one registered scene -> clients evaluate tracks/motion locally
```

---

## 6. Simple synchronized event pattern

Create and register the scene once, then start it from authoritative server code.

Conceptual flow:

```java
long startTick = world.getTime() + 5;
SceneOptions options = new SceneOptions(
        eventCenter,
        startTick,
        world.random.nextLong(),
        Map.of("phase", "intro")
);

CineFxServer.playAround(world, MY_SCENE, options, 384.0);
```

Do not make the render callback authoritative for gameplay decisions.

---

## 7. Live-event pattern

Use `EventProgram` for phases and transitions.

Recommended structure:

```text
preload
  -> intro
  -> combat_phase_1
  -> combat_phase_2
  -> success / failure
  -> cleanup
```

Use Director variables for state that naturally has a value and `EventSignals` for named external events.

Example signal names:

```text
boss.dead
engine.left.destroyed
engine.right.destroyed
players.ready
overload
```

A dependent server mod can emit the signal when authoritative gameplay reaches that state.

Do not poll client visual state to decide a server transition.

---

## 8. Late join and audience zones

`EventDirector` already manages dynamic audiences and resynchronization.

A generated mod should not:

- restart the event when a new player joins
- maintain a duplicate client timeline table
- manually replay every old event packet

The Director preserves the original scene start time so a late joiner enters at the correct local timeline point.

Keep event radius meaningful so clients outside the event do not receive unnecessary scene state.

---

## 9. Asset preload

Use `AssetBundle` for important resources or logical backend assets that must exist before the reveal.

Typical flow:

```text
phase preload
  -> request bundle
  -> wait for assetsReady(bundle) or controlled timeout
  -> start cinematic phase
```

This is especially important for:

- glTF/GLB hero models
- large resource-pack assets
- renderer-owned logical assets
- cinematic audio stems

Do not intentionally first-load a large hero model on the exact frame it appears if it can be preloaded.

---

## 10. glTF / GLB model usage

Use logical ids in scene definitions, for example:

```java
Identifier SHIP = Identifier.of("mymod", "model/ship");
```

Do not make dependent code rely on a CineFX internal cache class.

The reference renderer can resolve common resource locations for the logical model. It supports a premium path including textures/UVs, material factors, node animation, morph targets, CPU skinning and named nodes/sockets, with fallback behavior when a model cannot be handled.

Use stable node names in the asset when another CineFX element must attach to a part of the model.

Example naming style:

```text
root
body
head
weapon_socket
left_engine_socket
right_engine_socket
```

For actor IK, use bone names that match the glTF node/skeleton names.

---

## 11. Parenting and attachments

Prefer scene-graph parenting over recomputing world coordinates manually.

If a beam, particle emitter, audio source, light, rope or portal belongs to an actor/mesh, parent it to that element key.

If it belongs to a named glTF node/socket, use the attachment `boneName`/socket mechanism rather than sampling the actor position every tick from server code.

This keeps all movement deterministic on the client.

---

## 12. Ultra integration strategy

`UltraEventElement` is renderer-neutral. The scene describes intent; CineFX samples/synchronizes it.

The built-in client provides safe approximations for many channels. A premium renderer can replace a channel by registering an `UltraBackend`.

A backend should only consume the channels it implements well.

Example architecture:

```text
custom shader backend consumes postProcess + portals
custom audio backend consumes spatialAudio
CineFX fallback still handles softBodies + fractures + editor markers
```

Do not return `true` from a backend method and then render nothing. Returning `true` means the channel was consumed.

---

## 13. Choosing the right scaling primitive

### Many identical or repeated models
Use `ComplexElement.InstanceBatch`.

### Many fake people/mobs
Use `AdvancedEventElement.Crowd`.

### Massive force-driven particles
Use `UltraEventElement.ParticleField`.

### Large environment cells
Use `AdvancedEventElement.MegaEnvironment`.

### One important animated/skinned hero
Use `ComplexElement.Actor` with `CUSTOM_MODEL`.

Do not create 2,000 independently networked entities when one scene element can represent the set.

---

## 14. Quality-aware behavior

Do not hard-code the assumption that every client can render the maximum effect.

CineFX exposes `QualityTier` and adaptive quality behavior. The built-in client scales expensive fallbacks.

If implementing a custom backend, read the current quality through `ClientCineFx.quality()` and degrade gracefully.

Good degradation order:

```text
ULTRA: full effect
HIGH: high detail
MEDIUM: fewer particles/shadows
LOW: simple proxies / reduced counts
SAFE: preserve timing and readability, minimize expensive visuals
```

The event should remain understandable at every tier.

---

## 15. Renderer backends

Use the public client SPIs rather than injecting directly into CineFX internals:

- `PostFxBackend`
- `LightingBackend`
- `CinematicBackend`
- `UltraBackend`
- `AssetPreloadBackend`
- `CustomWorldRenderer`

Backend priority is intentional. A high-priority specialized backend can consume one channel while lower-priority fallbacks handle everything else.

Do not globally disable CineFX fallbacks just because one shader feature is installed.

---

## 16. World mutation and visual deformation

CineFX `WorldDeform`, `BlockSkin`, sampled blocks and similar effects are visual systems.

If the gameplay requires actual block changes:

1. perform the real change on the server
2. coordinate timing with the CineFX scene
3. keep CineFX responsible for the cinematic presentation

Never assume a visual deformation changed collision, drops, redstone or authoritative world state.

---

## 17. ScalarTrack typing trap

`ScalarTrack` expects `Double` keyframes.

Correct:

```java
ScalarTrack.of(
        Keyframe.at(0, 0.0),
        Keyframe.at(20, 1.0)
)
```

Avoid:

```java
ScalarTrack.of(
        Keyframe.at(0, 0),
        Keyframe.at(20, 1)
)
```

The latter can infer `Keyframe<Integer>` and fail compilation when mixed with `Double` values.

---

## 18. A recommended generation workflow for AI agents

When asked to create a CineFX-powered mod:

1. Read `AGENTS.md` and `llms.txt`.
2. Inspect the current signatures of every CineFX class you plan to instantiate.
3. Choose Scene / Complex / Advanced / Ultra primitives using `FEATURE_MATRIX.md`.
4. Search the showcase sources for the closest working pattern.
5. Build the scene in common code.
6. Add server orchestration with `CineFxServer` or `EventDirector`.
7. Add client backend code only if a built-in/fallback channel is insufficient.
8. Add preload if the event depends on heavyweight resources.
9. Build with Java 21.
10. Fix all compile errors rather than replacing typed APIs with reflection or raw hacks.
11. Check imports for internal CineFX classes.
12. Verify performance choices.

---

## 19. Do not invent the API

Coding agents must inspect actual signatures when uncertain.

Never guess:

- constructor argument order
- enum constants
- mixin method descriptors
- client/server package placement
- resource path lookup
- backend return semantics

The source on the active branch is authoritative.

---

## 20. Reference implementations

The built-in showcase source is intentionally valuable documentation.

Use these patterns as examples:

- `PremiumShowcase` for premium glTF rendering
- `UltraShowcase` for all Ultra channels together
- `ShowcaseScenes` for multi-universe scenes and stress tests
- `ShowcaseCommands` for starting test scenes/programs

For large generated events, adapt these patterns rather than starting from a blank custom rendering architecture.

---

## 21. Completion checklist for AI-generated dependent mods

Before telling the user the mod is finished:

- Build succeeds with `./gradlew build`.
- No server common class imports client-only CineFX APIs.
- No dependent class imports CineFX internal client implementation classes.
- Scene ids are registered before playback.
- Heavy assets preload when needed.
- No visual per-tick transform networking.
- World mutation is server-authoritative.
- Large repeated objects use a batching/crowd/field primitive.
- Custom backends consume only channels they truly implement.
- Effects have sensible culling/quality behavior.
- Any glTF attachment/IK bone names match the asset.
- Scalar keyframes use `Double` values.

If all of those are true, the generated mod is following CineFX's intended compatibility model.