# CineFX performance model

CineFX is optimized around *scene evaluation* rather than simulated visual objects.

## Why no entities?

A Display entity is useful when an object must participate in Minecraft's entity lifecycle. It is expensive as the default primitive for a cinematic scene because it brings identity, ticking/interpolation state, world bookkeeping and potentially networking/tracking.

CineFX virtual blocks are simple immutable scene data. On a frame, the renderer computes:

```text
scene time -> keyframe sample + analytic motion -> transform -> render command
```

No object needs to integrate velocity from the previous tick; ballistic motion evaluates `v*t + 0.5*a*t^2` directly.

## Expected budgets

These are engineering targets, not hard limits because GPU, shader packs, resource packs and block models vary dramatically.

- `< 200` active virtual blocks: normally trivial on a modern client.
- `200-1000`: reasonable for a short cinematic if block models are simple and the effect is nearby.
- `1000+`: use LOD/culling/short active windows; consider a custom batched renderer for repeated geometry.
- Rings: 24-64 segments is usually enough. 128-256 should be reserved for very large/close rings.
- Custom GPU particles/meshes: batch them in one custom renderer rather than adding thousands of Java scene elements.

## Network budget

A normal synchronized event sends one `PlayScenePayload` per participating client. Its size is effectively constant relative to the number of visual objects in the scene.

Use `CineFxServer.playAround(...)` so only relevant players receive it.

## Scene design

Prefer:

- one scene containing 500 blocks,
- deterministic keyframes/motion,
- short active ranges,
- reusable custom renderers for repeated geometry.

Avoid:

- starting 500 separate scenes when one scene can contain 500 elements,
- rebuilding scene definitions every frame,
- enormous per-frame template strings,
- doing server world scans from a client custom renderer,
- mutating block light or chunk block states every render frame.

## Claims

Claims are frame-local and use compact string/block keys. They exist to avoid overlapping exclusive effects. `ALLOW` and `FORCE` do not reserve resources.

## Custom renderer responsibility

A `CustomWorldRenderer` is intentionally unrestricted. It can also ruin performance. A custom implementation should:

- cache GPU buffers,
- avoid allocating large arrays per frame,
- cull by distance/frustum where appropriate,
- batch equivalent materials,
- release GPU resources on reload/disconnect if it owns them,
- never modify the shared matrix stack without a matching push/pop.

## Profiling

Profile the actual event with the target shader/resource pack. FPS alone can hide allocation spikes; watch frame-time percentiles and GC as well as average FPS.
