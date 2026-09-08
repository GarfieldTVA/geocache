# Building huge live events with CineFX

This document covers the high-level event layer added on top of CineFX's entity-free scene graph.

## The event channels

CineFX now separates a large event into independent channels so another mod can own only the pieces it needs:

- `EventElement.Camera` — native additive shake or anchor-based cutscene camera, no camera entity.
- `EventElement.Atmosphere` — sky/fog/cloud/star/wind requests for shader integrations.
- `EventElement.Emitter` — batched particle emitters with deterministic timing.
- `EventElement.AudioCue` — one-shot UI or spatial event audio.
- `EventElement.Overlay` — flashes, fades, cinematic bars and post-FX intensity hints.
- `EventElement.Marker` — one-shot timeline callbacks so gameplay code can react to visual beats.
- `SceneLight` — point/spot lights through the lighting backend bridge.
- Existing `Block`, `BlockSkin`, `Ring`, `Beam`, `WorldText`, `HudText`, `ScreenGrade` and `Custom` elements remain fully composable with the event layer.

All of these are still immutable scene data. No display entities, armor stands or camera entities are created.

## One-line impact preset

For a meteor landing, boss slam, giant door impact or explosion:

```java
builder.addAll(EventFx.impact(
        "meteor_impact",
        Vec3d.ZERO,
        0xFFFF7A2A,
        Identifier.of("mymod", "event.meteor_impact"),
        Identifier.of("minecraft", "poof"),
        1.4
));
```

That expands into:

- animated ground shockwave ring;
- short point light burst;
- deterministic particle burst;
- native camera shake;
- full-screen flash/chromatic edge hit;
- synchronized spatial sound;
- `impact` marker for dependent gameplay code.

The caller is free to copy the returned elements and replace individual tracks if the preset is only a starting point.

## Portal opening preset

```java
builder.addAll(EventFx.portalOpen(
        "rift",
        new Vec3d(0, 3, 0),
        0xFF8A5CFF,
        Identifier.of("mymod", "event.rift_open"),
        5.5,
        160
));
```

This composes an expanding ring, portal particles, dynamic light, atmosphere request, low camera rumble, cinematic bars, sound and a `portal_open` timeline marker.

## Native camera rigs

### Impact shake

```java
builder.add(EventFx.shake(
        "quake",
        80,
        30,
        0.10, // translation amplitude in blocks
        1.8,  // rotation amplitude in degrees
        1.2   // frequency
));
```

The camera is modified after vanilla `Camera.update()`. The effect is purely client-side and does not spawn or teleport an entity.

### True anchor-based cutscene camera

```java
builder.add(new EventElement.Camera(
        "flyby",
        0, 120,
        200,
        ConflictPolicy.REPLACE_LOWER,
        EventElement.CameraMode.ANCHOR_ABSOLUTE,
        new Vec3d(-14, 8, -14),
        TransformTrack.of(
                Keyframe.at(0, Transform.translation(0, 0, 0), Easing.EASE_IN_OUT_CUBIC),
                Keyframe.at(120, Transform.translation(28, 3, 28))
        ),
        MotionCurve.none(),
        ScalarTrack.constant(0.0),
        ScalarTrack.constant(0.0),
        ScalarTrack.constant(1.0),
        new Vec3d(0, 3, 0) // camera continuously looks at this anchor-local point
));
```

`ADDITIVE` preserves normal player camera movement and layers an offset/shake on top. `ANCHOR_ABSOLUTE` turns the scene anchor into a true cutscene rig.

A specialized camera mod can take ownership instead:

```java
ClientCineFx.registerCinematicBackend(
        Identifier.of("mymod", "camera_backend"),
        500,
        new CinematicBackend() {
            @Override
            public boolean applyCamera(CameraFrame frame) {
                myCameraSystem.consume(frame);
                return true; // suppress CineFX's native camera application for this frame
            }
        }
);
```

## Massive particle emitters

```java
builder.add(new EventElement.Emitter(
        "portal_sparks",
        0, 200,
        20,
        ConflictPolicy.ALLOW,
        Identifier.of("minecraft", "portal"),
        EventElement.EmitterShape.DISC,
        new Vec3d(0, 3, 0),
        TransformTrack.identity(),
        MotionCurve.none(),
        ScalarTrack.constant(500), // particles / second requested
        ScalarTrack.constant(5.0),
        ScalarTrack.constant(0.08),
        ScalarTrack.constant(1.0),
        ColorTrack.constant(0xFFFFFFFF),
        800 // hard per-frame safety cap
));
```

CineFX first offers all emitters for the frame to the highest-priority `CinematicBackend` as one batch. This is where a GPU particle system can consume thousands of particles efficiently.

If nobody consumes the batch, CineFX falls back to vanilla spawning for simple vanilla particle types. Parameterized particles remain integration territory because their codecs/data are mod-specific.

The emitter rate is converted using rendered frame delta and keeps a fractional remainder, so particle density is not tied to 60 FPS.

## Atmosphere and shader communication

```java
builder.add(new EventElement.Atmosphere(
        "rift_sky",
        0, 200,
        100,
        ConflictPolicy.REPLACE_LOWER,
        ColorTrack.constant(0x558A5CFF),
        ColorTrack.constant(0x668A5CFF),
        ScalarTrack.constant(0.22),
        ScalarTrack.constant(0.0),
        ScalarTrack.constant(80.0),
        ScalarTrack.constant(0.7),
        ScalarTrack.constant(0.25),
        ScalarTrack.constant(0.8)
));
```

CineFX intentionally does not rewrite biome data, vanilla weather or chunk fog every frame. Instead, a shader/renderer integration receives an `AtmosphereFrame` containing:

- sky tint;
- fog color;
- density;
- near/far distance;
- cloud opacity;
- star brightness;
- wind strength.

A backend can map that data to Iris, a custom shader uniform buffer or another rendering engine.

## Full-screen event language

`EventElement.Overlay` gives the scene an inexpensive built-in visual language even without a shader backend:

```java
builder.add(EventFx.flash("white_hit", 100, 8, 0xFFFFFF, 0.9));
builder.add(EventFx.letterbox("cinema", 0, 160, 0.8));
```

The safe fallback supports:

- full-screen color flash/fade;
- animated black cinematic bars;
- subtle RGB edge separation for chromatic impact.

`blurHint` and `chromaticAberrationHint` are also exposed so a post-processing integration can replace the fallback with real framebuffer blur/distortion.

## Audio cues

```java
builder.add(EventFx.sound(
        "boom",
        120,
        Identifier.of("mymod", "event.boom"),
        new Vec3d(0, 2, 0),
        1.8F,
        0.9F,
        true
));
```

Audio cues are fired once per scene instance and once per loop cycle. The element's `endTick` acts as a late-packet grace window: a client joining the event a few ticks late may still hear the cue, but a client arriving far into the scene will not replay every old sound.

Custom sound ids can come from the dependent mod's `sounds.json`; CineFX builds a `SoundEvent` id holder at playback time and does not mutate frozen registries.

## Timeline markers = gameplay integration

Markers are the cleanest way for unrelated mods to communicate with a CineFX event without polling the visual state.

Scene:

```java
builder.add(EventFx.marker(
        "phase_two_marker",
        260,
        "boss_phase",
        Map.of("phase", "2")
));
```

Client integration:

```java
ClientCineFx.onMarker((sceneId, instanceId, name, params) -> {
    if (name.equals("boss_phase") && params.get("phase").equals("2")) {
        myClientUi.beginPhaseTwo();
    }
});
```

For authoritative gameplay changes, the server mod should still change server state itself at the matching authoritative world tick. Client markers are for presentation/client integrations, not anti-cheat-sensitive game logic.

## Recommended architecture for a very large event

A 2-minute event can be split into phases without sending continuous network traffic:

```text
SERVER
  schedules worldTick T
  sends one CineFX scene start packet
  performs authoritative gameplay changes at T + known offsets

CLIENT CineFX
  scene graph evaluates from T
  ├─ virtual blocks / skins / rings / beams
  ├─ camera rig + shakes
  ├─ HUD / fonts / overlays
  ├─ point / spot light requests
  ├─ atmosphere requests
  ├─ particle batches
  ├─ spatial audio cues
  └─ timeline markers

OPTIONAL RENDER MOD
  consumes lighting / atmosphere / particles / post-FX in GPU batches
```

The important scaling property remains the same: the network synchronizes **the timeline**, not every object every tick.

## Performance rules for event authors

1. Prefer one deterministic scene start packet over per-tick transforms.
2. Use analytic `MotionCurve`s for debris instead of ticking objects.
3. Use `BlockSkin` for temporary visual corruption instead of rebuilding chunks.
4. Use `EventElement.Emitter` for repeated particles so backends can batch them.
5. Keep explicit per-frame particle caps even when using a GPU backend.
6. Cull entire events with `CineFxServer.playAround` when distant clients cannot see them.
7. Use `Marker`s to coordinate systems instead of searching the active scene every tick from another mod.
8. Use `ConflictPolicy.REPLACE_LOWER` for exclusive camera/atmosphere channels and `ALLOW` for additive rings/lights/particles.
9. Put heavy shader work behind `PostFxBackend`, `LightingBackend` or `CinematicBackend` rather than hard-depending on a single renderer.
10. Keep server world mutation outside CineFX so visual effects cannot desynchronize authoritative game state.
