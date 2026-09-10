# CineFX showcase asset quality bar

The built-in showcases are presentation material, not geometry-parser smoke tests. Assets used as a focal object in a showcase must therefore look intentional from normal gameplay and cinematic camera distances.

## Current narrative asset pack

The narrative showcases use dedicated glTF assets under `assets/cinefx/cinefx/models/`:

- `alien_fighter.gltf` — swept Starfall fighter with cockpit, layered hull, wing accents and twin emissive engines.
- `chrono_drone.gltf` — multi-ring drone with a central energy core, articulated pods and emissive accents.
- `mothership.gltf` — layered capital ship with outer wings, bridge, keel, six engines, weapon hardpoints and a belly beam aperture.
- `stabilizer_pylon.gltf` — stepped technological pylon with energy bands, stabilizer arms and a caged core.
- `titan_heart.gltf` — ancient stone/metal heart with a hot inner core, armored ribs and runic rings.
- `zeropoint_core.gltf` — nested energy core with multiple orbital rings and satellite shards.

`premium_sentinel.gltf` remains primarily a renderer-feature test asset because it deliberately exercises skinning, morph targets and glTF animation. Narrative events should not use parser-test geometry as their main visual language when a dedicated hero asset is available.

## Rules for humans and coding agents

Do not create a showcase hero asset as a single cube, octahedron, wedge, six-vertex crystal, or other parser-test primitive and present it as final art. A fallback proxy is acceptable only when a resource is missing or a backend cannot render the requested format.

For a focal showcase asset, prefer a readable silhouette plus secondary forms and detail: separate hull/body layers, engines or energy sources, structural supports, emissive accents, and named sockets when the event attaches beams, particles, lights or audio. Use multiple materials when they improve readability.

The intended target is stylized low-poly game art rather than photorealism. Triangle count is not a quality metric by itself, but focal assets should normally contain hundreds of triangles rather than a handful. Small background props may be simpler.

Keep assets deterministic and self-contained where practical. Embedded buffers are supported by the built-in premium glTF renderer. Before committing a showcase asset, validate that the glTF parses, that its bounds are sensible for the scale used by the scene, and that the normal CineFX build still succeeds.

When a showcase needs a unique creature, vehicle, machine or story object, create a dedicated model instead of recoloring an unrelated existing model. Reuse is appropriate for recurring story objects, not as a substitute for art direction.
