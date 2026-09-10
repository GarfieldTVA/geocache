# CineFX dynamic lighting

CineFX separates **light requests** from the actual renderer implementation.

Why: changing vanilla block-light values every rendered frame would force light-engine/chunk work and would be the opposite of a high-performance cinematic framework. Shader packs and dynamic-light mods also own different GPU pipelines.

A common scene can still describe lights in a renderer-neutral way:

```java
builder.add(SceneLights.point(
        "meteor_glow",
        new Vec3d(0, 3, 0),
        0xFFFF8A32,
        4.0,
        18.0
));

builder.add(SceneLights.spot(
        "searchlight",
        new Vec3d(0, 12, 0),
        new Vec3d(0, -1, 0),
        0xFFFFFFFF,
        8.0,
        40.0,
        12.0,
        28.0
));
```

For animation, instantiate `SceneLight` directly and provide normal `TransformTrack`, `MotionCurve`, `ColorTrack`, `ScalarTrack intensity` and `ScalarTrack radius` values.

On the client, a lighting mod/shader integration registers one backend:

```java
ClientCineFx.registerLightingBackend(
        Identifier.of("mymod", "shader_lights"),
        100,
        lights -> {
            // One list for the whole frame: upload/batch all point/spot lights here.
            // Each LightFrame has world position, rotated direction, ARGB color,
            // intensity, radius and cone angles.
            myGpuLightBuffer.upload(lights);
            return true;
        }
);
```

Backends are ordered by priority. The first backend returning `true` consumes the frame.

When no backend is installed, CineFX simply skips local point/spot lighting rather than modifying world/chunk lighting. Global cinematic illumination is still available through `ScreenGrade` (tint/exposure) and virtual blocks can render full-bright.

This design lets CineFX communicate cleanly with Iris/Sodium/custom pipelines or a dedicated dynamic-light mod without hard-depending on any one renderer.
