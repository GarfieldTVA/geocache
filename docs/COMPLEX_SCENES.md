# Complex scenes: 10-minute live events, actors and large 3D worlds

CineFX's complex-scene layer is designed for events that are closer to a small directed game sequence than to a one-shot particle effect: thousands of scheduled changes, long timelines, moving assemblies, fake characters, large repeated geometry and renderer-specific VFX.

## 1. Long timeline architecture

A scene may last minutes and contain thousands of elements. `ActiveScene` builds a temporal interval index when playback starts. Render/tick bridges first select the current time bucket and only then run the exact `activeAt()` check.

That means a 12,000-tick / 10-minute scene with 8,000 short cues does not blindly iterate all 8,000 cues every rendered frame.

The network model remains unchanged: multiplayer sends the scene id, authoritative start tick, anchor, deterministic seed and variables. Clients evaluate the detailed timeline locally.

## 2. Parent / child scene graph

`ComplexElement.Node` is a transform-only parent. `Actor`, `Mesh`, `InstanceBatch`, `Shadow`, `Trail`, `Decal` and `Volume` can all reference `parentKey`.

Example hierarchy:

```text
mothership
├── left_engine
│   ├── engine_mesh
│   ├── engine_light
│   ├── exhaust_volume
│   └── exhaust_trail
├── right_engine
├── hangar_door
├── pilot
└── projected_shadow
```

Move or rotate `mothership` and every descendant follows automatically. A child can add its own independent transform and analytic motion. Cycles are detected instead of recursively crashing the client.

## 3. Independent transform channels

`AdvancedTransformTrack` separates:

- translation XYZ
- rotation XYZ
- scale XYZ
- pivot XYZ

Each channel has its own keyframes/easing. `Vec3Track.angles(...)` takes the shortest rotation path.

This enables things that a single uniform scale cannot express:

```java
AdvancedTransformTrack doorAnimation = new AdvancedTransformTrack(
        Vec3Track.constant(Vec3d.ZERO),
        Vec3Track.angles(
                Keyframe.at(0, new Vec3d(0, 0, 0), Easing.EASE_IN_OUT_CUBIC),
                Keyframe.at(45, new Vec3d(0, 0, -110))
        ),
        Vec3Track.of(
                Keyframe.at(0, new Vec3d(1, 0.05, 1)),
                Keyframe.at(20, new Vec3d(1, 1, 1), Easing.EASE_OUT_BACK)
        ),
        Vec3Track.constant(new Vec3d(-2.5, 0, 0)) // hinge pivot
);
```

A portal can grow only on X/Y, a giant can squash only on Y, a door can rotate around its hinge, and negative scale can mirror one axis.

## 4. Actors: fake players, mobs and custom characters

`ComplexElement.Actor` has three kinds:

- `PLAYER`: render-only fake Minecraft player
- `ENTITY`: render-only vanilla/modded entity model by entity type id
- `CUSTOM_MODEL`: renderer-owned rigged/skinned model

PLAYER and ENTITY have a built-in vanilla fallback. CineFX creates a cached client-side object only to let Minecraft extract its normal render state. It is **never added to ClientWorld**, never receives an entity tracker and never creates network traffic.

The actor frame also carries:

- multiple weighted animation layers
- OVERRIDE / ADDITIVE / MULTIPLY blend modes
- per-bone transform tracks
- named morph/blend-shape weights
- look-at target
- tint, opacity and emissive values
- shadow participation
- full parented world matrix

The built-in vanilla fallback understands basic `minecraft:walk`, `run`, `crouch`, `swim`, `hurt`, `shake` and `death` cues. A cinematic backend can consume the same resolved actor frame to drive GeckoLib, glTF skeletal animation, a custom GPU skinning renderer, facial morphs or IK without reimplementing CineFX timing/parenting.

## 5. Arbitrary 3D meshes

`ComplexElement.Mesh` identifies a `modelId` and optional `materialId`, with full graph transform, tint, opacity, emissive, shadow request and arbitrary parameter map.

CineFX deliberately does not force one proprietary model format. A backend can map ids to glTF/GLB, OBJ, Blockbench exports, baked Minecraft geometry or its own GPU mesh cache while CineFX owns timeline, transforms, culling and synchronization.

## 6. Tens of thousands of repeated objects

Do not create 20,000 independent Java scene elements for identical debris/ships/crystals. Use `ComplexElement.InstanceBatch`.

One batch stores one model/material plus compact `InstanceSpec`s. Each instance can still have:

- independent base offset
- independent advanced transform track
- analytic motion
- tint/opacity
- time offset and time scale
- variant integer

The cinematic backend receives one `InstanceBatchFrame`, so a GPU renderer can issue an instanced draw instead of thousands of draw calls.

The API caps a single logical batch at 100,000 instances to catch accidental unbounded generation.

## 7. Shadows

`ComplexElement.Shadow` has three quality levels:

- `BLOB`: cheap built-in planar fallback
- `PROJECTED`: backend-projected/decal shadow
- `GEOMETRY`: backend real geometry/depth shadow

This lets low-end clients keep a readable contact shadow while a shader integration can render real moving silhouettes from actors/meshes.

Actors/meshes/batches also expose `castShadow` so a shadow backend can build a shadow-caster pass from the same resolved scene graph.

## 8. Trails and ribbons

`ComplexElement.Trail` keeps bounded temporal history for a moving source. It supports RIBBON/TUBE/STREAK semantics, width/color/opacity tracks, point lifetime, maximum points and minimum sample distance.

CineFX has a simple camera-facing ribbon fallback. A GPU backend may replace it with smooth tubes, Bézier ribbons, lightning arcs or motion-blurred streaks.

## 9. Decals and volumetrics

`ComplexElement.Decal` represents projected ground/wall marks such as runes, scorch marks, cracks, portal light or boss telegraphs. A flat-plane fallback exists; conforming projection is a backend capability.

`ComplexElement.Volume` carries a transformable SPHERE/BOX/CYLINDER volume with color, density, noise scale, distortion and emissive tracks. It is intended for fog banks, clouds, portal energy, smoke, dust storms and ray-marched effects.

## 10. Renderer contract

CineFX resolves the expensive scene logic before the backend sees it:

```text
long timeline
 -> temporal index
 -> active elements
 -> parent/child graph
 -> independent transform channels + pivot
 -> motion
 -> distance culling
 -> actor animation samples / trails / batches
 -> compact frame lists
 -> backend or built-in fallback
```

A `CinematicBackend` can independently consume actors, meshes, instance batches, shadows, trails, decals, volumes, particles, camera and atmosphere.

This separation is important: an advanced renderer should not have to rebuild event synchronization, graph parenting, animation clocks or conflict policy.

## 11. Practical event scale

A large event can therefore be structured as phases rather than one giant per-frame script:

```text
0:00-1:20   sky transition + distant fleet instance batches
1:20-3:00   mothership graph enters, engines/trails/volumes active
3:00-4:30   fake players evacuate, doors and lifts animate
4:30-6:00   boss actor + skeletal animation + projected shadows
6:00-7:40   environment corruption via block skins/decals/particles
7:40-9:15   battle: beams, actors, debris batches, camera cuts
9:15-10:00  destruction hierarchy + thousands of timed debris instances
```

Only elements that overlap the current temporal bucket are considered for the frame. Repeated geometry belongs in instance batches, and expensive custom assets should be GPU-cached by the renderer backend.
