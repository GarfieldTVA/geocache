# CineFX Event Engine

CineFX is not limited to short visual presets. The event layer is designed for long, server-authoritative live events where the client evaluates deterministic visuals locally.

## Runtime model

`EventProgram` describes phases, transitions and actions. `EventDirector` owns the live session on the server.

A session keeps:

- program id and current phase
- authoritative phase start time
- scene start times
- deterministic seed
- event variables
- audience anchor/radius
- current audience membership
- preload requests and per-player readiness

The server does **not** stream object transforms every frame. Clients receive compact scene start/options payloads and reconstruct the timeline locally.

## Typical program

```java
EventProgram program = EventProgram.builder(id("event/invasion"), "preload")
        .phase(new EventProgram.Phase(
                "preload",
                List.of(EventProgram.Action.preload(id("assets/invasion"))),
                List.of(),
                List.of(new EventProgram.Transition(
                        "intro",
                        EventProgram.Condition.any(
                                EventProgram.Condition.assetsReady(id("assets/invasion")),
                                EventProgram.Condition.after(100)),
                        List.of()))))
        .phase(new EventProgram.Phase(
                "intro",
                List.of(EventProgram.Action.play(id("scene/invasion_intro"))),
                List.of(),
                List.of(new EventProgram.Transition(
                        "battle",
                        EventProgram.Condition.after(600),
                        List.of()))))
        .build();

EventDirector.SessionHandle handle = EventDirector.INSTANCE.start(
        server, world, program, anchor, 256.0, seed, Map.of("difficulty", "hard"));
```

## Conditions and actions

Conditions can depend on time, variables and asset readiness and can be combined with `all`, `any` and `not`.

Actions can start/stop scenes, set variables, request preload bundles and fire event markers. This makes the timeline branchable instead of forcing a single rigid 10-minute animation.

## Dynamic audience

The Director continuously tracks which players are inside the event radius.

When a player enters after the event already started, the player receives each running scene with its **original start tick**. The scene therefore appears at the correct current point instead of restarting from zero.

When a player exits the event radius, CineFX stops the session scenes for that client and removes the player from pending preload readiness. Re-entering triggers a clean resynchronization.

## Preload handshake

Register an `AssetBundle`, request it from a phase and transition only after `assetsReady(bundle)` when appropriate.

The client:

1. receives the bundle id and request id;
2. checks normal resources;
3. offers logical model/material assets to registered preload backends;
4. sends an ACK to the server;
5. the Director tracks readiness for the current audience.

A timeout/fallback transition is recommended so one broken client cannot block a public event indefinitely.

## Pause, seek, snapshot and restore

`EventDirector` supports:

- `pause(handle)` / `resume(server, handle)`
- `setPhase(server, handle, phase)`
- `setVariable(handle, key, value)`
- `seekScene(server, handle, sceneId, localTick)`
- `snapshot(server, handle)`
- `restore(server, program, snapshot)`

A snapshot contains the current phase, elapsed phase time, variables and active scene start times. Persist that record in your own server storage if an event must survive a process restart.

## Operator commands

Operators with permission level 2 can control sessions live:

```text
/cinefxevent list
/cinefxevent pause <session>
/cinefxevent resume <session>
/cinefxevent stop <session>
/cinefxevent stopall
/cinefxevent phase <session> <phase>
/cinefxevent var <session> <key> <value>
/cinefxevent unset <session> <key>
/cinefxevent seek <session> <scene> <ticks>
/cinefxevent resync
```

These commands are intentionally generic so a production server can rehearse, recover and direct an event without recompiling the mod.

## Cutscene player control

`AdvancedEventElement.PlayerControl` has a built-in client fallback for:

- FOV override
- movement lock
- jump lock
- inventory lock
- look lock
- first-person hand hiding
- vanilla HUD hiding while keeping CineFX overlays/debug output

Higher-priority backends can replace the channel when they need partial movement scaling, special locomotion or a custom camera/input system.

## Adaptive quality

CineFX monitors frame time and can lower or raise its quality tier. The tier controls budgets such as crowd size, particle scale and expensive visual channels.

For public events, always design the scene so the important narrative remains understandable when secondary particles, volumetrics or large crowds are reduced.

## Attachments

`AdvancedEventElement.Attachment` can be parented to any transformable scene node and can carry light, particle emitter, beam, audio, text or custom payloads.

`boneName` is carried to skeletal backends. Root/node attachment is resolved by CineFX itself; a custom skeletal renderer can refine the matrix to an exact named bone/socket.

## Production recommendation

Use the Director for orchestration and synchronization, scene definitions for deterministic visual content, AssetBundles for warmup, and backends only for renderer-specific capabilities. Do not replace the Director with per-tick transform packets.