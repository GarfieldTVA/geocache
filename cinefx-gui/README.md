# CineFX GUI

CineFX GUI is a **separate Fabric client mod** that acts as an editor for the `cinefx` mod. It does not embed, fork, shade, or copy CineFX runtime code. CineFX remains the renderer/runtime/API; this project only creates and edits data and calls CineFX's public API.

## What the editor is designed for

- In-world studio with the Minecraft world visible as the live 3D viewport.
- Flashback-inspired layout: scene/outliner on the left, viewport in the middle, inspector on the right and a dense timeline at the bottom.
- Blender-like free camera while editing: RMB look, WASD, Q/E vertical movement, Shift boost and mouse-wheel speed control.
- Real viewport gizmos for move / rotate / scale on X/Y/Z plus free-plane move and uniform scale handles.
- WORLD / LOCAL transform spaces with hierarchy-aware parent conversion, including parent rotation and non-uniform scale where CineFX can represent the result without shear.
- World-space rotation rings, direct viewport selection and visible animation paths.
- Full hierarchical outliner with collapse/expand, hierarchy-aware search, keyboard navigation, drag/drop re-parenting, root drops and cycle protection/recovery.
- Runtime-equivalent parent/child viewport transforms.
- Gizmo editing creates/updates animation keys at the current playhead, including `TransformTrack`, `AdvancedTransformTrack` and compatible `Vec3Track` channels.
- Searchable Minecraft block browser: choose a block for the selected CineFX block or create a new virtual block directly from the browser.
- Typed asset/resource browser for sound, particle, texture, model/glTF, font and general resource fields using live registries and the ResourceManager.
- Camera-from-view authoring, optional look-at of the currently selected element and editor/scene-camera switching.
- Array duplication with configurable count, axis and spacing.
- Flashback-inspired timeline with playhead, zoom/pan, clips, drag/resize, multi-key selection, box selection, snapping and interpolation shortcuts.
- Dedicated channel **Dope Sheet** with collapsible Position/Rotation/Scale/etc. subtracks, structural key multi-selection, box selection and time dragging.
- Dedicated Curve Editor with live numeric channels, key dragging, time/value zoom, fit-to-curve and editable cubic Bezier tangent handles.
- Cubic Bezier timing is stored on CineFX keyframes themselves, so the curve shown by the editor is the curve used by the runtime rather than editor-only metadata.
- Runtime discovery of CineFX element types from `SceneElement`, `EventElement`, `ComplexElement`, `AdvancedEventElement`, `UltraEventElement`, plus `SceneLight`.
- Generic inspector driven from CineFX record components, so new fields/types can appear without duplicating CineFX classes.
- Keyframed `ScalarTrack`, `ColorTrack`, `Vec3Track`, `TransformTrack`, `AdvancedTransformTrack` and `PathTrack` editing.
- Motion presets and baked imported motion support.
- Visual Event Studio for declarative `EventProgramSpec` phases, transitions, conditions, actions and `AssetBundle` authoring.
- Event workspaces support draft save, autosave, validation, local publish and explicit permission-checked remote-server publish.
- Import registered scenes from `CineFxApi.scenes()`.
- Live preview through `CineFxApi.replace(...)` + `ClientCineFx.play(...)`.
- `CineFxApi.replace(...)` hot-refreshes already-running client scene definitions in place, preserving instance id, options and start time.
- Local preset save/load under `config/cinefx-gui/presets/*.json`.
- Undo/redo snapshots, duplicate/delete, enable/hide/lock editor state, scene metadata and variables.
- Validation is per element: one invalid draft is reported without preventing valid elements from previewing.

The API feature matrix currently includes virtual/animated blocks, block skins, world/HUD text, beams, rings, grades, glTF meshes, actors, hierarchy/instances/trails/decals/shadows/volumes, attachments, crowds, mega-environments, sky, audio layers, player control, post processing, cinematic lights, fractures, soft bodies, procedural rigs, particle fields, camera rigs, spatial audio, material effects, world deformation, portal surfaces and editor markers. CineFX GUI discovers those element records rather than reimplementing them.

## Build against the separate CineFX mod

From the root CineFX API project:

```bash
./gradlew test
./gradlew publishToMavenLocal
```

Then test and build this standalone project:

```bash
./gradlew -p cinefx-gui test
./gradlew -p cinefx-gui build
```

At runtime put **both** jars in the `mods` folder. `fabric.mod.json` declares a hard dependency on mod id `cinefx`, so CineFX GUI cannot silently run against a missing engine.

The repository CI runs CineFX API regression tests, publishes the API locally, runs editor-authoring regression tests, compiles the standalone GUI mod with Loom/Mixin validation and uploads the resulting jar artifact.

## Controls

### General

- `F10` — open/close CineFX Studio.
- `Space` — play/pause timeline.
- `Ctrl+S` — save preset.
- `Ctrl+Z` / `Ctrl+Y` — undo/redo.
- `Delete` — delete selected element.
- `Ctrl+D` or `Shift+D` — duplicate selected element.
- `Home` / `End` — timeline start/end.
- `Left` / `Right` — move playhead by 1 tick; hold `Shift` for 10 ticks.
- `Ctrl+E` — open the Curve Editor for the selected element.
- `Ctrl+T` — open the channel Dope Sheet for the selected element.
- `Ctrl+Shift+P` — open the typed asset/resource browser for the selected element.
- `Ctrl+Shift+E` — open CineFX Event Studio.

### Outliner / hierarchy

- Click an element row — select it.
- Click the triangle beside a parent — collapse/expand its children.
- Drag an element onto another element — set that element as its `parentKey`.
- Drag an element to empty hierarchy space — move it back to the scene root.
- Right-click a child — remove its parent.
- `Alt+Right` — expand the selected parent or move selection to its first child.
- `Alt+Left` — collapse the selected parent or move selection to its parent.
- Search keeps matching descendants **and their ancestor context** visible.
- Broken parent references and pre-existing hierarchy cycles stay visible as repairable root rows instead of disappearing.

### 3D viewport

- `G` — move gizmo.
- `R` — rotate gizmo.
- `S` — scale gizmo.
- `X` — switch gizmo transform space between WORLD and LOCAL.
- Drag X/Y/Z handles to constrain the transform to an axis.
- Drag the center move handle for camera-plane movement; center scale is uniform scale.
- Hold `Shift` while dragging a gizmo for fine adjustment.
- Hold `Ctrl` while dragging a gizmo for snapping.
- `F` — focus the editor camera around the selected element.
- Numpad `1` / `3` / `7` — front / side / top editor views.
- `C` — switch between editor camera and CineFX scene-camera preview.
- `RMB + mouse` — free-look camera.
- `WASD`, `Q`, `E` — fly editor camera while RMB is held.
- `Shift` — camera boost while flying.
- Mouse wheel over the viewport — adjust camera speed.

### Timeline / keyframes

- Drag timeline playhead — scrub.
- Hold `Shift` while dragging the playhead — snap to the nearest keyframe or clip boundary, Flashback-style.
- Drag a clip — move it; drag near its left/right edge — resize it.
- Drag keyframe diamonds — change their local tick.
- `Ctrl` + click keyframes — build a multi-selection.
- `Ctrl+A` — select the editable keyframes of the active element.
- `Alt` + drag in the timeline — rectangular keyframe selection; combine with `Ctrl` to add to the selection.
- Right-click a keyframe — cycle to the next CineFX easing curve.
- `Ctrl` + right-click a keyframe — cycle easing backwards.
- `Shift` + right-click a keyframe — delete it when the track contains another key.
- Right-click empty timeline space — add keys at the playhead for the selected element.
- `K` or `I` — key all editable tracks of the selected element at the playhead.
- Mouse wheel over timeline — zoom around cursor; `Shift` + wheel pans.

### Dope Sheet

- Channel groups are derived from the exact numeric animation tracks used by CineFX, not duplicated editor metadata.
- Click a group row — collapse/expand it.
- `Ctrl` + click keys — add/remove structural keys from the selection.
- `Ctrl+A` — select all structural keys of the selected element.
- Drag selected keys — move them in time; normal dragging snaps to quarter ticks, hold `Ctrl` for whole ticks.
- `Alt` + drag empty sheet space — rectangular key selection.
- Mouse wheel — zoom time around the cursor; `Shift` + wheel pans horizontally.
- Mouse wheel over the channel list — vertical scroll.
- `Delete` — remove selected keys while retaining at least one key in each structural track.
- `F` — fit all keys horizontally.
- `Ctrl+E` or **Curves** — open the Curve Editor for detailed value/tangent editing.
- `Space` — play/pause the shared preview.

A `TransformTrack` stores one structural key containing translation, rotation and scale. Its nine component rows therefore share that key's time. `AdvancedTransformTrack` keeps its separate structural tracks. The Dope Sheet mirrors those runtime structures rather than pretending every displayed component has an independent timestamp.

### Curve Editor

- Drag a key — change its tick and value.
- `B` — convert the selected outgoing segment to editable cubic Bezier timing.
- `L` — restore the selected outgoing segment to linear timing.
- Drag either tangent handle — edit that Bezier control point independently.
- `F` — fit the graph to the actual interpolated curve, including overshoot.
- Mouse wheel — zoom time around the cursor.
- `Ctrl` + mouse wheel — zoom the value axis around the cursor.
- `Delete` — remove the selected key when another key remains in the track.
- `Space` — play/pause the shared CineFX preview.

`TransformTrack` keeps its historical direct Euler interpolation, so a 0→360 rotation can intentionally make a full turn. `AdvancedTransformTrack.rotationDegrees` is a `Vec3Track.angularDegrees` channel and follows the shortest angular arc. The editor mirrors this distinction exactly.

### Asset Browser

- `Ctrl+Shift+P` opens the browser when the selected element contains asset-like string fields.
- The left side lists discovered fields and their type.
- The right side searches actual candidates from Minecraft registries or the active ResourceManager.
- Sound and particle fields use registry IDs; textures, models/glTF, fonts and generic assets scan resource packs.
- Choosing an entry writes the identifier directly into the selected element and participates in undo/history + live preview.
- The existing Blocks tab remains the richer dedicated browser for block authoring.

### Event Studio

- `Ctrl+S` — save the current workspace as a draft without publishing it.
- `Ctrl+Enter` — validate and publish the current `EventProgramSpec` and asset bundles.
- `Delete` — remove the selected phase/action/transition/condition/bundle/list or map entry when valid.
- Click an Action or Condition type to cycle its declarative type.

Publishing is explicit and validation-first. In singleplayer or an integrated LAN server, the server runtime shares the same JVM and sees the hot-reloaded declarative registries immediately. On a dedicated remote server, Event Studio uses CineFX's C2S authoring protocol only when that server exposes it. The server requires command-level operator permission, rate-limits requests, size-limits the publication, decodes and validates the complete portable `EventPublication` before mutating registries, then returns a real success/failure acknowledgement to the editor.

Remote publication can refresh running sessions onto the latest `EventProgramSpec` while preserving the event snapshot state (world/anchor, variables, seed, elapsed phase time when the phase still exists and active scene start times). If the edited program removed the current phase, the refreshed session safely falls back to the new initial phase. Scene-definition replacement itself is even tighter: active client `SceneDefinition` objects hot-swap in place without changing the scene instance id or start time.

Runtime code can resolve the latest declarative program through `EventPrograms.require(id)` or start/restore/refresh it through the `EventPrograms` helpers, while asset preload resolves bundles through `AssetBundle.Registry`.

## Preset format

The scene editor saves an editor-owned JSON document. Each element stores the CineFX API class name plus recursively encoded public record fields. The bridge converts this back into real CineFX `SceneElement` objects only when validating/previewing/registering. Minecraft-native values such as `Identifier`, `Vec3d`, `BlockPos` and `BlockState` have explicit codecs.

Scene presets are versioned and migrated by CineFX GUI. Saves are atomic and keep rotating backups so editor data can be recovered after an interrupted or bad save.

Event Studio workspaces are stored separately under the CineFX GUI config tree. Saving a workspace and publishing it are intentionally separate operations: an incomplete draft can be saved even when CineFX correctly refuses to publish it.

The portable event publication wire format is owned by the CineFX API (`EventPublicationJson`), not by GUI DTOs, so a dedicated server can validate authoring data without installing the GUI mod.

This split is intentional: presets remain editable even when incomplete, while CineFX constructors and declarative program compilation remain the final validation authority.
