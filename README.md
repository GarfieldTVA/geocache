# CineFX

CineFX is a **Minecraft 1.21.11 / Fabric** framework for cinematic events and high-end visual effects that other mods can drive through a small public API.

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

---

# 1. Register a scene

Register scenes during normal mod initialization, on both physical sides when common code references them.

```java
import dev.garfield.cinefx.api.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class MyScenes {
    public static final Identifier METEOR = Identifier.of("mymod", "meteor");

    public static void register() {
        CineFxApi.scene(METEOR)
                .duration(240) // 12 seconds
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
                .add(new SceneElement.Ring(
                        "impact_wave",
                        180, 220,
                        30,
                        ConflictPolicy.ALLOW,
                        Vec3d.ZERO,
                        ScalarTrack.of(
                                Keyframe.at(0, 0.1, Easing.EASE_OUT_CUBIC),
                                Keyframe.at(40, 24.0)
                        ),
                        ScalarTrack.of(
                                Keyframe.at(0, 1.2),
                                Keyframe.at(40, 0.08)
                        ),
                        64,
                        TransformTrack.identity(),
                        MotionCurve.none(),
                        ColorTrack.of(
                                Keyframe.at(0, 0xFFFFB050),
                                Keyframe.at(40, 0x00FF7030)
                        )
                ))
                .register();
    }
}
```

A scene element's keyframe track uses **element-local ticks**: keyframe tick `0` is the element's own `startTick`.

---

# 2. Play locally

Client-only code can start a scene directly:

```java
import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.client.api.ClientCineFx;

var handle = ClientCineFx.play(
        MyScenes.METEOR,
        SceneOptions.at(new Vec3d(100.5, 70, -42.5))
                .seeded(123456789L)
                .with("boss", "The Watcher")
);

// Later:
ClientCineFx.stop(handle);
```

Client calls must execute on the Minecraft client thread.

---

# 3. Synchronize an event for multiplayer

From server code, choose one authoritative world tick. A small S2C payload makes all compatible clients begin from that same timeline.

```java
long startTick = world.getTime() + 5; // tiny scheduling margin

SceneOptions options = new SceneOptions(
        eventCenter,
        startTick,
        world.random.nextLong(),
        Map.of("boss", "The Watcher", "phase", "2")
);

// Recommended on a large server: do not notify players 10,000 blocks away.
CineFxServer.playAround(world, MyScenes.METEOR, options, 384.0);
```

If a player does not have CineFX/the receiving channel, `ServerPlayNetworking.canSend` prevents the packet from being sent to that player.

Do not send block transforms every tick. Put the motion in the registered scene and send one start command.

---

# 4. Rip a real block out of the ground

This is the use case CineFX deliberately handles without an entity.

The `sampledBlock` primitive reads the source `BlockState` **once when the client scene starts**, caches it, then renders that cached model while its transform changes.

```java
SceneElement.Block ripped = new SceneElement.Block(
        "ripped_block",
        0, 120,
        50,
        ConflictPolicy.REPLACE_LOWER,
        null,                    // no fixed BlockState
        BlockPos.ORIGIN,         // sample anchor block
        Vec3d.ZERO,
        TransformTrack.of(
                Keyframe.at(0, Transform.IDENTITY, Easing.EASE_OUT_CUBIC),
                Keyframe.at(20, Transform.translation(0, 1.8, 0))
        ),
        Motions.levitate(0.18, 28, 0)
                .and(Motions.spin(0.6, 2.4, 0.3)),
        false,
        0,
        true
);
```

Then anchor the scene on the chosen real block:

```java
BlockPos source = player.getBlockPos().offset(player.getHorizontalFacing(), 2);
Vec3d anchor = Vec3d.of(source);
```

### Important: visual state vs real world state

CineFX is a rendering framework. It intentionally does **not** silently remove or place server blocks.

If the event is supposed to physically remove the source block, the calling server mod remains authoritative and should perform that world mutation itself. Send/start the CineFX scene before (or with a very small scheduled lead before) replacing the real block so the client can snapshot its model.

This separation avoids desync, ghost blocks and accidental griefing logic hidden inside a visual library.

---

# 5. True custom fonts, not Minecraft's default font

`WorldText` and `HudText` accept a font `Identifier`. Put a normal Minecraft font definition in the **calling mod's resources**.

Example:

```text
src/main/resources/assets/mymod/font/event.json
src/main/resources/assets/mymod/font/event.ttf
```

`event.json`:

```json
{
  "providers": [
    {
      "type": "ttf",
      "file": "mymod:font/event.ttf",
      "shift": [0, 0],
      "size": 18,
      "oversample": 2.0
    }
  ]
}
```

Then use:

```java
Identifier eventFont = Identifier.of("mymod", "event");
```

CineFX never redistributes somebody else's font. The dependent mod owns its licensed font asset.

3D text is submitted directly into the 1.21.11 world render queue and can be transparent, see-through or depth-tested.

---

# 6. Countdown / HUD timer

Templates are deliberately small and deterministic:

```text
{ticks}             remaining ticks
{elapsed_ticks}     elapsed ticks
{time}              remaining seconds, one decimal
{elapsed}           elapsed seconds, one decimal
{time:mm:ss}        remaining mm:ss
{seed}              scene seed
{var:name}           runtime variable
```

Example:

```java
new SceneElement.HudText(
        "countdown",
        0, 200,
        100,
        ConflictPolicy.REPLACE_LOWER,
        "EVENT IN {time:mm:ss}",
        Identifier.of("mymod", "event"),
        0.5, 0.16,
        0, 0,
        SceneElement.HorizontalAlign.CENTER,
        ScalarTrack.of(
                Keyframe.at(0, 1.0),
                Keyframe.at(190, 1.0),
                Keyframe.at(200, 1.35, Easing.EASE_OUT_BACK)
        ),
        ColorTrack.constant(0xFFFFFFFF),
        true
)
```

---

# 7. Visually change blocks around an event

`BlockSkin` draws a replacement model slightly inflated over the real block. It is visual-only, so it does not rebuild chunks or alter server state.

```java
for (int x = -8; x <= 8; x++) {
    for (int z = -8; z <= 8; z++) {
        builder.add(new SceneElement.BlockSkin(
                "corruption_" + x + "_" + z,
                40, 180,
                10,
                ConflictPolicy.REPLACE_LOWER,
                new BlockPos(x, -1, z),
                Blocks.SCULK.getDefaultState(),
                1.003,
                false
        ));
    }
}
```

For a permanent gameplay change, mutate the world in the authoritative mod instead.

---

# 8. Beams and shockwaves

No entities and no particles are required for simple large geometry.

```java
builder.add(new SceneElement.Beam(
        "sky_beam",
        0, 100,
        10,
        ConflictPolicy.ALLOW,
        new Vec3d(0, 0, 0),
        new Vec3d(0, 180, 0),
        0.35,
        TransformTrack.identity(),
        Motions.levitate(0.15, 30, 0),
        ColorTrack.constant(0xCC80C8FF)
));
```

A `Ring` is an annulus in local XZ. Animate its radius for a ground shockwave, or rotate the transform by 90 degrees for a portal ring.

---

# 9. Screen grading and scene lighting

`ScreenGrade` exposes:

- ARGB tint
- saturation
- exposure
- contrast
- vignette

```java
new SceneElement.ScreenGrade(
        "storm_grade",
        0, 160,
        50,
        ConflictPolicy.REPLACE_LOWER,
        ColorTrack.of(
                Keyframe.at(0, 0x00102050),
                Keyframe.at(30, 0x40204090)
        ),
        ScalarTrack.constant(0.65), // saturation
        ScalarTrack.constant(-0.18), // exposure
        ScalarTrack.constant(1.12), // contrast
        ScalarTrack.constant(0.55)  // vignette
)
```

The built-in renderer always has a conservative compatibility fallback for tint/exposure/vignette. **True framebuffer saturation and contrast are intentionally exposed through a backend SPI** rather than blindly injecting into every shader pipeline. This makes CineFX coexist much more safely with Iris/Sodium/custom renderer stacks.

A renderer integration can provide the full GPU implementation:

```java
ClientCineFx.registerPostFxBackend(
        Identifier.of("mymod", "my_gpu_grade"),
        100,
        (drawContext, frame) -> {
            // Apply frame.saturation(), frame.contrast(), frame.exposure(), etc.
            // Return true when fully consumed.
            return true;
        }
);
```

For **local dynamic world lights** (point/spot lights), use a custom renderer or a bridge to the lighting system your modpack already uses. CineFX does not modify vanilla block light values every frame because that would force light/chunk updates and destroy the performance advantage of a visual event framework.

Virtual blocks can still be submitted full-bright, and global scene illumination can be driven with exposure/tint.

---

# 10. Custom renderer SPI

When blocks/text/beams/rings are not enough, register a renderer type once on the client:

```java
ClientCineFx.registerRenderer(
        Identifier.of("mymod", "portal"),
        context -> {
            context.matrices().push();
            try {
                // context.commandQueue()
                // context.camera()
                // context.sceneAnchor()
                // context.sceneTick()
                // context.sampledTransform()
                // context.element().parameters()
            } finally {
                context.matrices().pop();
            }
        }
);
```

Then a common scene can contain:

```java
Fx.custom(
        "main_portal",
        Identifier.of("mymod", "portal"),
        Map.of("texture", "mymod:textures/fx/portal.png"),
        new Vec3d(0, 2, 0),
        TransformTrack.identity(),
        Motions.spin(0, 0.25, 0)
)
```

This is the escape hatch for custom shaders, textured meshes, ribbons, volumetric-looking geometry, GPU particle systems and mod-specific renderers while CineFX still owns timeline/synchronization/conflicts.

---

# 11. Conflict system

Each scene has a priority. Each element also has a priority. Active scenes are processed from highest scene priority to lowest, then each scene's elements from highest element priority to lowest.

Built-in block cells and screen channels are claimed for the current frame.

`ConflictPolicy`:

| Policy | Meaning |
|---|---|
| `ALLOW` | Does not reserve the resource. Multiple effects may coexist. |
| `DENY` | Draw only if a higher-priority element did not already reserve it. |
| `REPLACE_LOWER` | Normal exclusive behavior: the first/highest-priority claimant wins. |
| `FORCE` | Always draw even if something already owns the resource. Use deliberately. |

For most block replacement effects, use `REPLACE_LOWER`. For beams/rings that are expected to cross each other, `ALLOW` is usually right.

This is how CineFX avoids the common "two mods both render a replacement block in the exact same cell" mess without taking control away from mods that explicitly want layering.

---

# 12. Performance rules

CineFX's built-in path is designed around these rules:

1. **No virtual object is a Minecraft entity.**
2. **No per-object physics tick.** Motion is a function of scene time.
3. **No transform network spam.** Synchronize scene start, not every frame.
4. **Sampled real blocks are cached once**, not read from the world every frame.
5. **Render command queue batching** is used for Minecraft block models/text/custom quads.
6. Built-in world primitives are distance-culled beyond 512 blocks.
7. `playAround` prevents irrelevant clients from receiving large event starts.
8. Custom renderers are responsible for their own GPU resource lifetime and extra culling.

For a huge event, prefer hundreds of virtual blocks inside one scene over hundreds of Display entities.

See [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) for practical budgets and profiling advice.

---

# 13. Threading and authority

- Register common scene definitions during mod initialization.
- Call `ClientCineFx` on the Minecraft client thread.
- Call `CineFxServer` on the server thread.
- Do gameplay/world mutations in the authoritative gameplay mod, not a client FX callback.
- Never trust a client effect as proof that a gameplay action happened.

CineFX visuals are presentation. The server remains authoritative.

---

# 14. Build

```bash
./gradlew build
```

Output:

```text
build/libs/cinefx-0.1.0.jar
```

The GitHub Actions workflow also builds the branch and uploads the generated jars as an artifact.

## License

MIT.
