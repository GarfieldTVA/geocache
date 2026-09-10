# CineFX GUI

CineFX GUI is a **separate Fabric client mod** that acts as an editor for the `cinefx` mod. It does not embed, fork, shade, or copy CineFX runtime code. CineFX remains the renderer/runtime/API; this project only creates and edits data and calls CineFX's public API.

## What the editor is designed for

- In-world editor with the Minecraft world visible behind the UI.
- Blender-like free camera while editing: RMB look, WASD, Q/E vertical movement, Shift boost, mouse wheel speed/dolly.
- Flashback-inspired timeline at the bottom with a playhead, zoom, clips, drag/resize, snapping and keyframe diamonds.
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

- `F10` — open/close CineFX GUI.
- `Space` — play/pause timeline.
- `Ctrl+S` — save preset.
- `Ctrl+Z` / `Ctrl+Y` — undo/redo.
- `Delete` — delete selected element.
- `Ctrl+D` — duplicate selected element.
- `Home` / `End` — timeline start/end.
- `F` — focus editor camera around the selected element when a position field can be resolved.
- `RMB + mouse` — free-look camera.
- `WASD`, `Q`, `E` — fly editor camera while RMB is held.
- `Shift` — camera boost.
- Mouse wheel over the viewport — adjust camera speed.
- Drag timeline playhead — scrub.
- Drag a clip — move it; drag near its left/right edge — resize it.
- Drag keyframe diamonds — change their local tick.
- Mouse wheel over timeline — zoom around cursor; Shift+wheel pans.
- Mouse wheel over inspector/library — scroll.

## Preset format

The editor saves an editor-owned JSON document. Each element stores the CineFX API class name plus recursively encoded public record fields. The bridge converts this back into real CineFX `SceneElement` objects only when validating/previewing/registering. Minecraft-native values such as `Identifier`, `Vec3d`, `BlockPos` and `BlockState` have explicit codecs.

This split is intentional: presets remain editable even when incomplete, while CineFX constructors remain the final validation authority.
