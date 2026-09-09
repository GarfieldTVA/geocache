# CineFX

CineFX is a **Minecraft 1.21.11 / Fabric** framework for cinematic events, live-event orchestration and high-end visual effects.

The core design rule is simple: **visual CineFX objects are not Minecraft entities**. Scene elements are evaluated from deterministic local timelines and rendered through Minecraft's client rendering path instead of turning every visual object into a Display entity, ArmorStand or tracker-heavy fake entity.

That makes CineFX suitable for things such as Fortnite-style live events, boss introductions, portals, meteors, synchronized cutscenes, moving glTF actors, cinematic lighting, destruction, crowds, particle storms, world transitions and long server events.

## AI / coding agents: read this first

CineFX is intentionally documented so a coding agent can generate a dependent mod without guessing the architecture.

If you are Codex, Claude, GPT, Gemini or another coding agent, read in this order:

1. [`AGENTS.md`](AGENTS.md) — non-negotiable compatibility and architecture rules.
2. [`llms.txt`](llms.txt) — compact API/navigation entrypoint.
3. [`docs/AI_INTEGRATION_GUIDE.md`](docs/AI_INTEGRATION_GUIDE.md) — end-to-end instructions for generating a compatible mod.
4. [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) — choose the correct primitive instead of reinventing it.
5. [`docs/ULTRA.md`](docs/ULTRA.md) — post FX, lighting, destruction, physics, IK, particle fields, cameras, audio, materials, portals and deformation.
6. [`docs/EVENT_ENGINE.md`](docs/EVENT_ENGINE.md) — multi-phase events, preload, variables, late join and audience resync.
7. [`docs/BACKENDS.md`](docs/BACKENDS.md) — custom renderer/audio/backend contract.
8. [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) — scaling and quality rules.
9. [`docs/SHOWCASES.md`](docs/SHOWCASES.md) and `src/main/java/dev/garfield/cinefx/showcase/` — working reference implementations.

**Do not invent CineFX methods or constructors.** When prose and source disagree, the current public API source on this branch is authoritative.

## Public API contract

Dependent mods should use only:

```text
dev.garfield.cinefx.api.*
dev.garfield.cinefx.client.api.*   // client source set only
```

Everything under implementation packages such as `dev.garfield.cinefx.client.*` is internal to CineFX and may change.

## Version

This branch targets exactly:

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.19.2`
- Fabric API `0.141.4+1.21.11`
- Yarn `1.21.11+build.5`

Do not assume examples are mapping-compatible with a different Minecraft version.

---

# What CineFX provides

## Deterministic scene engine

- named reusable `SceneDefinition`s
- scene duration, looping and priority
- `TransformTrack`, `AdvancedTransformTrack`, `ScalarTrack`, `ColorTrack`, `Vec3Track`
- easing curves and analytic `MotionCurve`s
- `PathTrack` for linear, Catmull-Rom and Bezier cinematic paths
- per-scene deterministic seed and small variables map
- frame-local conflict/claim system
- distance culling
- long-timeline temporal bucketing

## Basic visual primitives

- virtual blocks
- sampled real blocks that can be visually ripped from the world
- visual-only block skins
- world text and HUD text
- custom resource-pack fonts
- beams and rings/shockwaves
- atmosphere and screen grading
- custom renderer escape hatch

## Complex scene graph

- transform nodes and parent/child graphs
- fake player / fake entity / custom-model actors
- meshes
- instance batches
- shadows
- trails
- decals
- volumes
- animation layers
- bone overrides
- morph tracks

## Advanced event elements

- actor/node attachments
- attached lights, particles, beams, audio and text
- crowds
- sky control
- audio layers/stems
- player/cutscene control
- mega-environments
- adaptive quality tiers
- preload bundles
- debug/editor overlays

## CineFX Ultra

Ultra adds renderer-neutral intent for:

- bloom, DOF, motion blur, chromatic aberration, vignette, film grain, lens dirt, heat haze, underwater refraction, glitch and transitions
- cinematic point/spot/area/tube light rigs and volumetric intent
- fracture/destruction
- rope, chain, cloth, spring, ragdoll and tentacle-style soft bodies
- procedural IK (`LOOK_AT`, `TWO_BONE`, `CCD`, `FABRIK`, `FOOT_PLANT`, `AIM`, `TENTACLE`)
- force-driven particle fields
- dolly/crane/orbit/rail/handheld/follow camera rigs
- spatial audio with moving-source attenuation, Doppler and occlusion intent
- dissolve/corruption/freeze/burn/hologram/scan/phase/cloak material effects
- visual world deformation
- portals, mirrors, camera feeds, dimension views and kaleidoscope surfaces
- editor/timeline markers

The built-in client supplies portable fallbacks. A high-end renderer can replace individual channels with `UltraBackend` without changing the authoritative server timeline.

---

# Premium glTF / GLB path

CineFX includes a reference premium model path for `Mesh` and `CUSTOM_MODEL` actors.

It supports the important built-in path for:

- glTF 2.0 and GLB 2.0
- scene/node transforms
- indexed and non-indexed geometry
- triangle/strip/fan handling in the premium path
- buffers from GLB, external resources and data URIs
- UVs and textures
- base color and material factors
- normals and vertex colors
- node animation channels
- morph targets
- CPU skinning with joints/weights and inverse-bind matrices
- named nodes/sockets for attachments
- integration with CineFX bone/morph/Ultra rig state

A missing or unsupported model does **not** have to make the event disappear: CineFX keeps fallback paths.

For exact current behavior and renderer limitations, see [`docs/BACKENDS.md`](docs/BACKENDS.md).

---

# Multiplayer scaling model

The scene definition is registered by code. The server does **not** stream every visual transform.

The intended synchronization payload is approximately:

```text
scene id
+ anchor
+ authoritative server start tick
+ deterministic seed
+ small variables map
```

Every compatible client evaluates the timeline locally.

That is the main scaling strategy: hundreds or thousands of visual objects do not require hundreds or thousands of movement packets every tick.

**Do not build a dependent mod that sends CineFX transform state every server tick.**

---

# Quick start

## 1. Add CineFX as a dependency

In the dependent mod's `fabric.mod.json`:

```json
{
  "depends": {
    "cinefx": ">=0.1.0"
  }
}
```

For local development:

```bash
./gradlew publishToMavenLocal
```

Then in another Fabric project:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "dev.garfield:cinefx:0.1.0"
}
```

## 2. Register a scene

Register common scene definitions during normal mod initialization.

```java
import dev.garfield.cinefx.api.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class MyScenes {
    public static final Identifier METEOR = Identifier.of("mymod", "meteor");

    public static void register() {
        CineFxApi.scene(METEOR)
                .duration(240)
                .priority(100)
                .add(new SceneElement.Block(
                        "meteor",
                        0, 180,
                        20,
                        ConflictPolicy.REPLACE_LOWER,
                        Blocks.OBSIDIAN.getDefaultState(),
                        null,
                        new Vec3d(0, 45, 0),
                        TransformTrack.of(
                                Keyframe.at(0, Transform.scale(4.0), Easing.EASE_OUT_CUBIC),
                                Keyframe.at(180, Transform.scale(1.5))
                        ),
                        Motions.ballistic(
                                new Vec3d(0.22, -0.10, 0.14),
                                new Vec3d(0, -0.006, 0),
                                new Vec3d(1.2, 2.3, 0.5),
                                Vec3d.ZERO
                        ),
                        true,
                        0,
                        true
                ))
                .register();
    }
}
```

Keyframe time is element-local: keyframe tick `0` is the element's own `startTick`.

## 3. Play locally

Client-only code:

```java
var handle = ClientCineFx.play(
        MyScenes.METEOR,
        SceneOptions.at(new Vec3d(100.5, 70, -42.5))
                .seeded(123456789L)
                .with("boss", "The Watcher")
);

ClientCineFx.stop(handle);
```

## 4. Synchronize from the server

```java
long startTick = world.getTime() + 5;

SceneOptions options = new SceneOptions(
        eventCenter,
        startTick,
        world.random.nextLong(),
        Map.of("phase", "intro")
);

CineFxServer.playAround(world, MyScenes.METEOR, options, 384.0);
```

For multi-phase events, use `EventProgram` + `EventDirector` rather than manually chaining packets/timers.

---

# EventDirector

Use the Director when the event needs phases, branching, preload, dynamic audiences or late-join reconstruction.

Typical flow:

```text
preload
 -> intro
 -> phase_1
 -> gameplay signal / variable
 -> phase_2
 -> success or failure
 -> cleanup
```

Use `EventSignals` for named authoritative gameplay transitions such as:

```text
boss.dead
engine.left.destroyed
players.ready
overload
```

The Director handles players joining late or walking into/out of the event audience radius without restarting the entire event for everyone.

See [`docs/EVENT_ENGINE.md`](docs/EVENT_ENGINE.md).

---

# Client integrations

Client-only integrations are registered through `ClientCineFx`:

```text
registerRenderer
registerPostFxBackend
registerLightingBackend
registerCinematicBackend
registerUltraBackend
registerAssetPreloader
```

The first/highest-priority backend that consumes a channel wins for that channel. A backend should return `false` when it does not actually handle the frame so lower-priority fallbacks can still run.

Also available:

```text
preload
quality
forceQuality
automaticQuality
debugOverlay
ultraEditor
onMarker
```

---

# Performance rules

1. Do not turn visual-only objects into Minecraft entities unless gameplay truly requires entities.
2. Do not send visual transforms every tick.
3. Use `InstanceBatch` for large repeated geometry.
4. Use `Crowd` for large fake crowds.
5. Use `ParticleField` for force-driven mass particles.
6. Use cull distances.
7. Preload important heavyweight assets.
8. Let quality tiers reduce expensive visual work.
9. Keep gameplay/world mutation on the server.
10. Preserve event timing/readability even on low quality.

See [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md).

---

# Visual state vs real world state

CineFX is presentation/orchestration. It intentionally does not silently make gameplay changes just because a visual element looks like it changed the world.

If a cutscene removes a block, opens a real door, damages a boss or changes collision, the calling server mod must perform that authoritative change.

Never trust a client visual effect as proof that gameplay happened.

---

# ScalarTrack typing

`ScalarTrack` is Double-based.

Use:

```java
ScalarTrack.of(
        Keyframe.at(0, 0.0),
        Keyframe.at(40, 1.0)
)
```

Do not mix `Keyframe<Integer>` and `Keyframe<Double>` values in the same scalar track.

---

# Built-in validation / showcases

CineFX ships test/showcase commands for development builds, including:

```text
/cinefxshowcase premium
/cinefxshowcase ultra
/cinefxshowcase ultra_overload
/cinefxshowcase marathon
/cinefxshowcase verify
```

There are also stress-test scenes for timeline size, instances, crowds and attachments.

A green build proves compile/mapping compatibility. It does not replace visual QA in a running Minecraft client.

---

# Documentation index

- [`AGENTS.md`](AGENTS.md) — coding-agent rules
- [`llms.txt`](llms.txt) — compact LLM entrypoint
- [`docs/AI_INTEGRATION_GUIDE.md`](docs/AI_INTEGRATION_GUIDE.md) — AI/dependent-mod integration guide
- [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) — feature → API selection
- [`docs/ULTRA.md`](docs/ULTRA.md) — Ultra reference
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture overview
- [`docs/COMPLEX_SCENES.md`](docs/COMPLEX_SCENES.md) — complex scene graph
- [`docs/EVENT_ENGINE.md`](docs/EVENT_ENGINE.md) — event Director
- [`docs/LIGHTING.md`](docs/LIGHTING.md) — renderer-neutral lighting
- [`docs/LIVE_EVENTS.md`](docs/LIVE_EVENTS.md) — live-event patterns
- [`docs/BACKENDS.md`](docs/BACKENDS.md) — renderer/audio integrations and glTF path
- [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) — budgets and profiling
- [`docs/SHOWCASES.md`](docs/SHOWCASES.md) — built-in showcases

---

# Build

```bash
./gradlew build
```

Expected jar output:

```text
build/libs/cinefx-0.1.0.jar
```

The GitHub Actions workflow also builds the branch and uploads jars as an artifact.

## License

MIT.
