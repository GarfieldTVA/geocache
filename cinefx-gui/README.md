# CineFX GUI

CineFX GUI is a **separate Fabric client mod** that acts as an editor for the `cinefx` mod. It does not embed, fork, shade, or copy CineFX runtime code. CineFX remains the renderer/runtime/API; this project only creates and edits data and calls CineFX's public API.

## What the editor is designed for

- In-world studio with the Minecraft world visible as the live 3D viewport.
- Flashback-inspired layout: scene/outliner on the left, viewport in the middle, inspector on the right and a dense timeline at the bottom.
- Blender-like free camera while editing: RMB look, WASD, Q/E vertical movement, Shift boost and mouse-wheel speed control.
- Real viewport gizmos for move / rotate / scale on X/Y/Z plus free-plane move and uniform scale handles.
- World-space rotation rings, direct viewport selection and visible animation paths.
- Gizmo editing creates/updates animation keys at the current playhead, including `TransformTrack`, `AdvancedTransformTrack` and compatible `Vec3Track` channels.
- Searchable Minecraft block browser: choose a block for the selected CineFX block or create a new virtual block directly from the browser.
- Camera-from-view authoring, optional look-at of the currently selected element and editor/scene-camera switching.
- Array duplication with configurable count, axis and spacing.
- Flashback-inspired timeline with playhead, zoom/pan, clips, drag/resize, keyframe diamonds, snapping and interpolation shortcuts.
- Runtime discovery of CineFX element types from `SceneElement`, `EventElement`, `ComplexElement`, `AdvancedEventElement`, `UltraEventElement`, plus `SceneLight`.
- Generic inspector driven from CineFX record components, so new fields/types can appear without duplicating CineFX classes.
- Keyframed `ScalarTrack`, `ColorTrack`, `Vec3Track`, `TransformTrack`, `AdvancedTransformTrack` and `PathTrack` editing.
- Motion presets and baked imported motion support.
- Import registered scenes from `CineFxApi.scenes()`.
- Live preview through `CineFxApi.replace(...)` + `ClientCineFx.play(...)`.
- Local preset save/load under `config/cinefx-gui/presets/*.json`.
- Undo/redo snapshots, duplicate/delete, enable/hide/lock editor state, scene metadata and variables.
- Validation is per element: one invalid draft is reported without preventing valid elements from previewing.

The API feature matrix currently includes virtual/animated blocks, block skins, world/HUD text, beams, rings, grades, glTF meshes, actors, hierarchy/instances/trails/decals/shadows/volumes, attachments, crowds, mega-environments, sky, audio layers, player control, post processing, cinematic lights, fractures, soft bodies, procedural rigs, particle fields, camera rigs, spatial audio, material effects, world deformation, portal surfaces and editor markers. CineFX GUI discovers those element records rather than reimplementing them.

## Build against the separate CineFX mod

From the root CineFX API project:

```bash
./gradlew publishToMavenLocal
```

Then build this standalone project:

```bash
./gradlew -p cinefx-gui build
```

At runtime put **both** jars in the `mods` folder. `fabric.mod.json` declares a hard dependency on mod id `cinefx`, so CineFX GUI cannot silently run against a missing engine.

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

### 3D viewport

- `G` — move gizmo.
- `R` — rotate gizmo.
- `S` — scale gizmo.
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
- Right-click a keyframe — cycle to the next CineFX easing curve.
- `Ctrl` + right-click a keyframe — cycle easing backwards.
- `Shift` + right-click a keyframe — delete it when the track contains another key.
- Right-click empty timeline space — add keys at the playhead for the selected element.
- `K` or `I` — key all editable tracks of the selected element at the playhead.
- Mouse wheel over timeline — zoom around cursor; `Shift` + wheel pans.

## Preset format

The editor saves an editor-owned JSON document. Each element stores the CineFX API class name plus recursively encoded public record fields. The bridge converts this back into real CineFX `SceneElement` objects only when validating/previewing/registering. Minecraft-native values such as `Identifier`, `Vec3d`, `BlockPos` and `BlockState` have explicit codecs.

This split is intentional: presets remain editable even when incomplete, while CineFX constructors remain the final validation authority.
