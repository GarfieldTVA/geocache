# CineFX Showcase & Stress Test Catalog

The showcase catalog ships with CineFX so the renderer, timeline and server orchestration can be exercised without a separate demo mod.

All showcase commands place the scene at the command source position and broadcast it to players within 256 blocks.

## Visual showcases

| Command | Theme | Main systems exercised |
| --- | --- | --- |
| `/cinefxshowcase skyfall` | Sci-fi armada | Catmull-Rom flight path, mesh hook, parented engine lights, attached particles, attached beam, crowd, impact rings, camera shake |
| `/cinefxshowcase titan` | Ancient fantasy titan | Huge fake mob scale, rise animation, dust, shadow fallback, quake rings, camera shake |
| `/cinefxshowcase void` | Cosmic/void breach | portal particles, multiple rings, sky/atmosphere channel, fake Enderman actor, world text, shake |
| `/cinefxshowcase cyber` | Cyber city | dozens of animated virtual blocks, timed construction, screen effects, particles, world text |
| `/cinefxshowcase frozen` | Ice collapse | environmental animation, particles, grading/atmosphere, large-scale movement |
| `/cinefxshowcase desert` | Desert relic | dust, animated geometry, path motion, cinematic reveal |
| `/cinefxshowcase ocean` | Underwater leviathan | long spline motion, giant actor, fog/atmosphere, repeated agents/particles |
| `/cinefxshowcase nature` | Magical reclamation | progressive world visuals, growth animation, particles and lighting |
| `/cinefxshowcase moonbase` | Space evacuation | crowd/fake actors, alarms/audio layers, countdown/event staging, destruction |
| `/cinefxshowcase reality` | Reality fracture tech demo | broad feature mix: actors, meshes, attachments, particles, text, sky, camera, overlays and large scene graph |

## Marathon

```text
/cinefxshowcase marathon
```

The marathon is a real `EventProgram`, not a timer hard-coded into the command. It starts with asset preload and then walks through all ten hero universes using Director phases and scene swaps.

This is useful for testing:

- phase transitions
- long-lived event sessions
- entering/leaving the 256-block audience during an event
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

The built-in validator checks every registered showcase for:

- duplicate element keys
- missing scene registrations
- transformable parents that do not exist
- scene-graph cycles
- negative start ticks
- elements whose end time extends past the scene duration (warning)

The same validation is executed during CineFX initialization for the built-in catalog, so a broken showcase cannot silently ship.

## Visual QA procedure

Compilation alone cannot prove that an event looks correct. For a release candidate, run these checks on a real client:

1. Start with `skyfall`, `titan`, `void`, `cyber`, then the remaining hero scenes.
2. Verify camera motion never leaves the player stuck after the scene ends.
3. Check fake actors at normal and extreme scales.
4. Check a fake player with a custom `skinTexture` and both `skin_type=wide` and `skin_type=slim`.
5. Verify `lookAt` follows its target without snapping the whole body unexpectedly.
6. Confirm attached light/beam/particle origins remain glued to moving parents.
7. Run the marathon and join the server halfway through; the current scene must appear at the correct elapsed time.
8. Walk out of the 256-block radius and back in; old visuals must stop outside and resynchronize on re-entry.
9. Test `pause`, `resume`, `phase`, `seek` and `resync` using `/cinefxevent`.
10. Run every stress test while the debug overlay is enabled and watch frame time/quality-tier changes.
11. Repeat once with no optional rendering backend and once with the production backend.

## Optional hero assets

The showcase bundle declares logical assets such as the mothership/reality-core model and custom sky. They are optional for the vanilla baseline: native blocks, rings, actors, beams, particles and fallback effects still provide a visible test. A registered preload/render backend can replace those placeholders with production-quality model/shader assets.