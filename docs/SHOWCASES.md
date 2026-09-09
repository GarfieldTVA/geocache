# CineFX Showcase & Stress Test Catalog

The showcase catalog ships with CineFX so the renderer, timeline, premium model path and server orchestration can be exercised without a separate demo mod.

All showcase commands place the scene/program at the command source position and use a local audience radius suitable for testing.

For AI-generated dependent mods, the showcase source is also a reference library: search for the closest effect and adapt that pattern rather than inventing a second rendering architecture.

## Visual showcases

| Command | Theme | Main systems exercised |
| --- | --- | --- |
| `/cinefxshowcase skyfall` | Sci-fi armada | Catmull-Rom flight path, model hook, parented engine lights, attached particles, attached beam, crowd, impact rings, camera shake |
| `/cinefxshowcase titan` | Ancient fantasy titan | Huge fake mob scale, rise animation, dust, shadow fallback, quake rings, camera shake |
| `/cinefxshowcase void` | Cosmic/void breach | portal particles, multiple rings, sky/atmosphere channel, fake Enderman actor, world text, shake |
| `/cinefxshowcase cyber` | Cyber city | animated virtual blocks, timed construction, screen effects, particles, world text |
| `/cinefxshowcase frozen` | Ice collapse | environmental animation, particles, grading/atmosphere, large-scale movement |
| `/cinefxshowcase desert` | Desert relic | dust, animated geometry, path motion, cinematic reveal |
| `/cinefxshowcase ocean` | Underwater leviathan | long spline motion, giant actor, fog/atmosphere, repeated agents/particles |
| `/cinefxshowcase nature` | Magical reclamation | progressive world visuals, growth animation, particles and lighting |
| `/cinefxshowcase moonbase` | Space evacuation | crowd/fake actors, alarms/audio layers, countdown/event staging, destruction |
| `/cinefxshowcase reality` | Reality fracture tech demo | broad feature mix: actors, meshes, attachments, particles, text, sky, camera, overlays and large scene graph |

## Premium renderer showcase

```text
/cinefxshowcase premium
```

This is the reference test for the built-in premium glTF/GLB path.

It uses the embedded `premium_sentinel.gltf` asset to exercise the important model pipeline, including the current texture/UV, animation, morph, skinning and named-socket path. The scene also attaches effects to the model so socket refinement is tested in context rather than only by parsing a file.

Use `PremiumShowcase` as the closest reference when generating a dependent mod with one important animated custom model.

## Ultra all-systems showcase

```text
/cinefxshowcase ultra
```

The Ultra showcase combines many premium systems in one event:

- premium custom-model actor
- procedural rig / IK intent
- light rig with volumetric intent
- post-processing intent
- material effects
- soft-body/chain physics
- force-driven particle field
- rail camera with look-at/FOV/shake
- spatial audio
- world deformation
- portal surface
- fracture/destruction
- editor markers
- preload through an `EventProgram`

Use `UltraShowcase` as the primary reference for complex AI-generated events.

## Ultra overload branch

```text
/cinefxshowcase ultra_overload
```

This starts the same event with the `overload` signal already active so the destructive branch can be tested immediately.

It demonstrates the intended relationship between:

```text
authoritative gameplay state
 -> EventSignals
 -> EventProgram transition
 -> stop one scene
 -> start another scene
```

A dependent mod can use the same pattern for `boss.dead`, `reactor.failed`, `players.ready` or any other named authoritative gameplay event.

## Marathon

```text
/cinefxshowcase marathon
```

The marathon is a real `EventProgram`, not a timer hard-coded into the command. It starts with asset preload and then walks through the hero universes using Director phases and scene swaps.

This is useful for testing:

- phase transitions
- long-lived event sessions
- entering/leaving the audience during an event
- late join synchronization
- asset readiness
- cleanup between different visual styles

## Stress tests

### Long timeline

```text
/cinefxshowcase stress_timeline
```

A 10-minute scene with roughly 8,000 short cues. It validates the temporal bucket index in `ActiveScene`; the renderer should evaluate the current bucket rather than scanning every cue every rendered frame.

### Instance storm

```text
/cinefxshowcase stress_instances
```

Creates an `InstanceBatch` containing roughly 20,000 repeated debris instances. A high-end backend can turn this into one or a few GPU-instanced submissions.

### Actor crowd

```text
/cinefxshowcase stress_crowd
```

Requests hundreds of fake actors. The native fallback deliberately respects the current adaptive-quality crowd budget rather than blindly rendering every actor.

### Attachment hell

```text
/cinefxshowcase stress_attachments
```

Exercises many parented beams, lights and particle emitters on moving scene roots. It is intended to expose graph-resolution, cleanup and backend-batching mistakes.

## Structural verifier

```text
/cinefxshowcase verify
```

The built-in validator checks the built-in catalog for structural scene errors such as:

- duplicate element keys
- missing scene registrations
- transformable parents that do not exist
- scene-graph cycles
- negative start ticks
- elements whose end time extends past the scene duration (warning)

The same validation is executed during CineFX initialization for the built-in catalog, so a broken registered showcase cannot silently ship.

## Visual QA procedure

Compilation alone cannot prove that an event looks correct. For a release candidate, run these checks on a real client:

1. Start with `premium`, then `ultra` and `ultra_overload`.
2. Run `skyfall`, `titan`, `void`, `cyber`, then the remaining hero scenes.
3. Verify camera motion/FOV never leaves the player stuck after a scene ends.
4. Check fake actors at normal and extreme scales.
5. Check a fake player with a custom `skinTexture` and both wide/slim model selection.
6. Verify `lookAt` follows its target without snapping the whole body unexpectedly.
7. Confirm parented and socket-attached light/beam/particle origins remain glued to moving parents.
8. Verify premium glTF animations, morphs and skinning visibly animate the embedded test model.
9. Run the marathon and join halfway through; the current scene must appear at the correct elapsed time.
10. Walk out of the event radius and back in; old visuals must stop outside and resynchronize on re-entry.
11. Test `pause`, `resume`, `phase`, `seek` and `resync` using `/cinefxevent`.
12. Run every stress test while the debug overlay is enabled and watch frame time/quality-tier changes.
13. Repeat once with only CineFX fallbacks and once with the production renderer/audio backend if one is used.

## AI agent reference rule

For generated code:

- use `PremiumShowcase` when the main problem is a hero glTF actor/model;
- use `UltraShowcase` when combining premium systems or branching;
- use stress scenes to understand batching/crowd/attachment scale;
- inspect actual public constructor signatures before copying/adapting code;
- do not import showcase/internal client implementation classes into a dependent mod just because they are convenient examples.

The showcase code is a pattern library, not a replacement for the public API boundary.