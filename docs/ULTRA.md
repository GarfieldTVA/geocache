# CineFX Ultra

CineFX Ultra is the renderer-neutral premium layer for effects that go beyond ordinary Minecraft rendering. The common API describes intent; the client samples deterministic frames; the built-in client provides safe approximations; higher-priority `UltraBackend` implementations can replace individual channels.

Use Ultra when a normal `SceneElement`, `ComplexElement` or `AdvancedEventElement` is not enough.

## Core rule

Ultra elements are still scene elements. They obey normal scene timing, priorities, conflict handling and deterministic synchronization.

Do not send Ultra transforms or particle state every tick from the server. Register the scene and let clients sample it locally.

---

## 1. PostProcess

`UltraEventElement.PostProcess`

Effects:

- `BLOOM`
- `DEPTH_OF_FIELD`
- `MOTION_BLUR`
- `CHROMATIC_ABERRATION`
- `VIGNETTE`
- `FILM_GRAIN`
- `LENS_DIRT`
- `HEAT_HAZE`
- `UNDERWATER_REFRACTION`
- `GLITCH`
- `TRANSITION`

The element also carries tint, focus distance/range and a free parameters map.

Use it for cinematic intent even if the current client only has the safe fallback. A shader backend can consume the same frame and implement true framebuffer post-processing.

Example pattern:

```java
new UltraEventElement.PostProcess(
        "intro_post",
        0, 120,
        100,
        ConflictPolicy.REPLACE_LOWER,
        Map.of(
                UltraEventElement.PostEffect.BLOOM, ScalarTrack.constant(0.35),
                UltraEventElement.PostEffect.VIGNETTE, ScalarTrack.constant(0.25),
                UltraEventElement.PostEffect.GLITCH,
                ScalarTrack.of(
                        Keyframe.at(0, 0.0),
                        Keyframe.at(90, 0.0),
                        Keyframe.at(110, 0.35),
                        Keyframe.at(120, 0.0)
                )
        ),
        ColorTrack.constant(0x00102040),
        ScalarTrack.constant(10.0),
        ScalarTrack.constant(4.0),
        Map.of()
)
```

---

## 2. LightRig

`UltraEventElement.LightRig`

Light kinds:

- `POINT`
- `SPOT`
- `AREA`
- `TUBE`

Each `RigLight` carries local offset/direction, color, intensity, radius, inner/outer cone, shadow intent and volumetric amount.

A rig can be parented to an actor, mesh or graph node.

Use it instead of manually updating temporary world light blocks.

The built-in path can feed dynamic-light integrations and visual volumetric approximations. A renderer backend can implement real clustered/deferred lights, shadow maps and god rays.

---

## 3. Fracture

`UltraEventElement.Fracture`

Modes:

- `RADIAL`
- `VORONOI`
- `GRID`
- `DIRECTIONAL`
- `PREBAKED`

Important fields include shard count, impulse, force, gravity, drag, angular speed, tint, opacity, ground collision and reverse/rebuild behavior.

The public API caps shard count to protect clients.

Use for:

- exploding ships
- breaking statues
- collapsing architecture
- reversed magical reconstruction

For gameplay destruction, change the real world server-side separately.

---

## 4. SoftBody

`UltraEventElement.SoftBody`

Modes:

- `CLOTH`
- `ROPE`
- `CHAIN`
- `SPRING`
- `RAGDOLL`
- `TENTACLE`

A soft body is made from `SoftPoint` and `SoftLink` records.

`SoftPoint`:

- offset
- inverse mass
- pinned flag

`SoftLink`:

- point A
- point B
- rest length
- stiffness

The built-in path uses client-only constraint simulation with configurable gravity, wind, damping, thickness, collision and solver iterations.

Prefer this over spawning physical Minecraft entities for every rope segment.

---

## 5. ProceduralRig / IK

`UltraEventElement.ProceduralRig`

IK modes:

- `LOOK_AT`
- `TWO_BONE`
- `CCD`
- `FABRIK`
- `FOOT_PLANT`
- `AIM`
- `TENTACLE`

A `ProceduralRig` targets an actor element key and contains one or more `IkGoal` entries.

An IK goal can reference:

- chain name
- solver mode
- end bone
- pole bone
- target offset
- another scene element key as target
- weight
- iterations
- tolerance
- parameters

Bone names must match the model/skeleton naming used by the renderer. The built-in premium glTF path converts Ultra rig intent into bone overrides that the model path can consume.

Use this for:

- boss head tracking
- aiming weapons
- tentacles following a target
- procedural arm placement
- foot placement

Do not implement gameplay hitboxes from visual IK state.

---

## 6. ParticleField

`UltraEventElement.ParticleField`

Force kinds:

- `DIRECTIONAL`
- `ATTRACTOR`
- `REPELLER`
- `VORTEX`
- `TURBULENCE`
- `DRAG`
- `EXPLOSION`

The element carries particle id, transform, spawn rate, lifetime, speed, size, color, force list, max-particle budget, ground collision, trails and culling.

Use `ParticleField` for large force-driven effects rather than manually emitting uncontrolled particles from a server tick.

Examples:

- black-hole vortex
- magic energy orbit
- debris cloud
- explosion shock field
- attraction to a boss core

A GPU backend may replace the built-in simulation while keeping the same scene definition.

---

## 7. CameraRig

`UltraEventElement.CameraRig`

Modes:

- `DOLLY`
- `CRANE`
- `ORBIT`
- `RAIL`
- `HANDHELD`
- `FOLLOW`
- `LOCKED`
- `FREE`

A rig can use a `PathTrack`, look at an offset or another element key, animate roll/FOV/focus, apply shake and request world collision.

The built-in client supports camera position, look-at, FOV, shake and collision-aware movement. A premium backend can add richer roll/optics behavior.

Use `PathTrack` rather than sending camera positions from the server every frame.

---

## 8. SpatialAudio

`UltraEventElement.SpatialAudio`

Reverb presets:

- `NONE`
- `ROOM`
- `HALL`
- `CAVE`
- `ARENA`
- `UNDERWATER`
- `SPACE`
- `CUSTOM`

The element carries sound id, parent/transform, volume, pitch, radius, low-pass, reverb mix, looping, Doppler and occlusion intent.

The built-in path provides moving-source attenuation, occlusion approximation, Doppler and approximate reverb/low-pass behavior. A dedicated audio backend can implement higher-quality DSP.

Use a parented spatial source for engine/weapon/boss sounds that must move with a model.

---

## 9. MaterialEffect

`UltraEventElement.MaterialEffect`

Modes:

- `DISSOLVE`
- `CORRUPTION`
- `FREEZE`
- `BURN`
- `HOLOGRAM`
- `SCAN`
- `PHASE`
- `CLOAK`

A material effect targets another scene element key and carries amount, edge width/color, noise scale, speed, direction and parameters.

The built-in model path can approximate these by modulating model rendering. A shader backend can implement true material-space effects.

Use multiple time-separated material effects to create staged transformations rather than replacing the entire actor/mesh definition.

---

## 10. WorldDeform

`UltraEventElement.WorldDeform`

Modes:

- `LIFT`
- `SINK`
- `CRACK`
- `FISSURE`
- `WAVE`
- `PULSE`
- `GROW`
- `REBUILD`
- `BIOME_ILLUSION`

The element carries center, radius, amplitude, frequency, progress, optional material, color and whether it affects virtual blocks.

This is a visual system. It does not automatically change real server blocks/collision.

Use it for:

- ground splitting
- terrain pulse
- structure rising illusion
- magical reconstruction
- biome-color illusion

If the event changes the actual world, synchronize the real server mutation separately.

---

## 11. PortalSurface

`UltraEventElement.PortalSurface`

Modes:

- `PORTAL`
- `MIRROR`
- `CAMERA_FEED`
- `DIMENSION_VIEW`
- `KALEIDOSCOPE`

The element can be parented, has a full transform, size, target scene id/offset, opacity, rim color, distortion, recursion depth and culling.

The built-in path provides a visible fallback. A renderer backend can use render targets for recursive portals, mirrors or camera feeds.

Recursion depth is intentionally capped by the API.

---

## 12. EditorMarker

`UltraEventElement.EditorMarker`

Editor markers are timeline labels for development tooling.

Use them for sections such as:

```text
INTRO
PORTAL OPEN
BOSS REVEAL
PHASE 2
FRACTURE
OUTRO
```

The Ultra editor overlay consumes them. Normal renderers can ignore them.

---

# UltraBackend contract

Register with:

```java
ClientCineFx.registerUltraBackend(
        Identifier.of("mymod", "premium_ultra"),
        100,
        new MyUltraBackend()
);
```

Independent channels:

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

Return `true` only when the channel has actually been consumed.

A backend may implement only one or two channels. CineFX's lower-priority fallbacks can handle the rest.

---

# Parenting rules

Ultra transformable elements use the same graph model as existing CineFX transformables.

Prefer:

```text
ship
  -> engine light rig
  -> engine particle field
  -> spatial audio
  -> rope/chain
```

instead of calculating the ship's world position on the server every tick and broadcasting child positions.

When a target is a named glTF node/socket, use the model/socket attachment path where supported.

---

# Branching with Ultra

Ultra visuals do not need their own state machine. Use `EventProgram` and `EventSignals`.

Example design:

```text
intro
 -> stable
 -> signal.overload
 -> overload destruction scene
 -> cleanup
```

The built-in `UltraShowcase` demonstrates this model with the `overload` signal.

---

# Performance guidance

Ultra effects can be expensive. AI-generated mods should:

- set realistic cull distances
- cap particle counts
- cap fracture shards
- keep soft-body point/link counts bounded
- use adaptive quality
- preload heavy assets
- avoid running unnecessary Ultra elements outside the visible event window
- prefer one parented field/rig to hundreds of independent elements
- keep event state server-authoritative but rendering client-side

---

# Testing

For CineFX development itself:

```text
/cinefxshowcase ultra
/cinefxshowcase ultra_overload
/cinefxshowcase verify
```

`ultra` exercises the normal all-systems sequence.

`ultra_overload` exercises the destructive branch immediately.

A green compile proves API/mapping compatibility, not visual perfection. Premium effects still need visual QA in a running client.

---

# Source-of-truth rule for agents

Before instantiating an Ultra record, inspect the current `UltraEventElement` signature on the active branch.

Do not guess constructor argument order or enum names from this prose document.