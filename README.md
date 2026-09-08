# CineFX

CineFX is a **Minecraft 1.21.11 / Fabric** framework for cinematic events and high-end visual effects that other mods can drive through a small public API.

> For the new high-level live-event layer (native camera rigs/shake, atmosphere, batched particles, audio cues, overlays, markers and ready-made impact/portal presets), see [`docs/LIVE_EVENTS.md`](docs/LIVE_EVENTS.md).

The important design choice is simple: **virtual CineFX objects are not Minecraft entities**. Animated blocks, beams, rings and world text live in a client scene graph and are submitted directly to Minecraft 1.21.11's render command queue. That avoids hundreds of Display/ArmorStand entities, entity ticking, interpolation packets and tracker overhead during large events.

CineFX is intended for things such as Fortnite-style live events, portals, meteors, world transitions, boss introductions, countdowns, floating debris, cinematic builds, visual block reskins and synchronized server events.

## What is already built

- Named reusable scenes (`namespace:scene`) with duration, looping and priority.
- Smooth keyframed transforms and scalar/color tracks.
- Easing curves: linear, smoothstep, smootherstep, quad, cubic, sine and back.
- Analytic motion: ballistic acceleration, gravity, levitation, orbit, spin and deterministic jitter.
- Virtual animated block models rendered without entities.
- **Sampled blocks**: snapshot a real world block when a scene starts, then animate that visual copy independently.
- Visual-only block skins: render another `BlockState` over an existing position without mutating the server world.
- 3D world text, billboard or world-oriented, with custom resource-pack fonts and transparency.
- HUD text, custom fonts, normalized positioning, alignment, scaling and timer templates.
- Direct geometric beams/lasers and animated rings/shockwaves.
- Global screen grading requests: tint, exposure, saturation, contrast and vignette.
- A safe built-in grade fallback plus a `PostFxBackend` SPI for full GPU/shader grading.
- A `CustomWorldRenderer` SPI for portals, meshes, ribbons, custom GPU geometry or integrations.
- Native event camera shake and anchor-based cutscene rigs without camera entities.
- Batched particle emitters with vanilla fallback and GPU/backend handoff.
- Scene atmosphere requests for sky/fog/cloud/star/wind integrations.
- Spatial/UI audio cues and timeline markers.
- Full-screen flashes, fades, cinematic bars and chromatic-impact fallback.
- High-level `EventFx.impact(...)` and `EventFx.portalOpen(...)` presets.
- Frame-local visual claims and priorities to stop unrelated effects from accidentally drawing over the same block/screen channel.
- Server -> client scene synchronization using one small typed packet instead of transform packets every tick.
- Per-scene deterministic seed and string variables.
- Distance culling for built-in world primitives.
- No server world mutation hidden inside the rendering library.

## Version

This branch targets exactly:

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.19.2`
- Fabric API `0.141.4+1.21.11`
- Yarn `1.21.11+build.5`

## Public API contract

Other mods should only depend on these packages:

```text
dev.garfield.cinefx.api.*
dev.garfield.cinefx.client.api.*   // client source set only
```

Everything else is implementation detail and may change without compatibility guarantees.

A dependent mod should declare CineFX as a dependency:

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

## Mental model

A scene has four layers:

```text
SceneDefinition
  -> SceneElement(s)
      -> keyframed TransformTrack / ScalarTrack / ColorTrack
      -> optional analytic MotionCurve
  -> SceneOptions at playback
      -> world anchor
      -> server world tick to start on
      -> deterministic seed
      -> small variables map
```

The **scene definition is registered locally by code**. In multiplayer, the server does not stream every transform. It sends approximately this:

```text
scene id + anchor + start world tick + seed + variables
```

Every client evaluates the same deterministic timeline itself.

That is the main scaling strategy: a scene with 500 virtual blocks still does not need 500 entity trackers or 500 movement packets per tick.

For advanced event composition, see `docs/LIVE_EVENTS.md`.
