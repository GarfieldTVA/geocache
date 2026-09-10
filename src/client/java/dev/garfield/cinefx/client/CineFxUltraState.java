package dev.garfield.cinefx.client;

import dev.garfield.cinefx.client.api.UltraBackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Short-lived per-frame Ultra state shared by camera, glTF, HUD and native fallbacks. */
final class CineFxUltraState {
    private static final long STALE_NANOS = 400_000_000L;
    private static final Map<String, Timed<List<UltraBackend.ProceduralRigFrame>>> RIGS = new HashMap<>();
    private static final Map<String, Timed<List<UltraBackend.MaterialEffectFrame>>> MATERIALS = new HashMap<>();
    private static Timed<List<UltraBackend.LightRigFrame>> lights = new Timed<>(List.of(), 0L);
    private static Timed<List<UltraBackend.CameraRigFrame>> cameras = new Timed<>(List.of(), 0L);
    private static Timed<List<UltraBackend.EditorMarkerFrame>> markers = new Timed<>(List.of(), 0L);

    private CineFxUltraState() { }

    static synchronized void acceptLights(List<UltraBackend.LightRigFrame> frames) {
        lights = new Timed<>(List.copyOf(frames), System.nanoTime());
    }

    static synchronized List<UltraBackend.LightRigFrame> lights() {
        return fresh(lights) ? lights.value : List.of();
    }

    static synchronized void acceptRigs(List<UltraBackend.ProceduralRigFrame> frames) {
        long now = System.nanoTime();
        HashMap<String, ArrayList<UltraBackend.ProceduralRigFrame>> grouped = new HashMap<>();
        for (UltraBackend.ProceduralRigFrame frame : frames) {
            grouped.computeIfAbsent(key(frame.sceneInstanceId(), frame.actorKey()), ignored -> new ArrayList<>()).add(frame);
        }
        for (Map.Entry<String, ArrayList<UltraBackend.ProceduralRigFrame>> entry : grouped.entrySet()) {
            RIGS.put(entry.getKey(), new Timed<>(List.copyOf(entry.getValue()), now));
        }
    }

    static synchronized List<UltraBackend.ProceduralRigFrame> rigsFor(long sceneInstanceId, String actorKey) {
        Timed<List<UltraBackend.ProceduralRigFrame>> timed = RIGS.get(key(sceneInstanceId, actorKey));
        return fresh(timed) ? timed.value : List.of();
    }

    static synchronized void acceptMaterials(List<UltraBackend.MaterialEffectFrame> frames) {
        long now = System.nanoTime();
        HashMap<String, ArrayList<UltraBackend.MaterialEffectFrame>> grouped = new HashMap<>();
        for (UltraBackend.MaterialEffectFrame frame : frames) {
            grouped.computeIfAbsent(key(frame.sceneInstanceId(), frame.targetElementKey()), ignored -> new ArrayList<>()).add(frame);
        }
        for (Map.Entry<String, ArrayList<UltraBackend.MaterialEffectFrame>> entry : grouped.entrySet()) {
            MATERIALS.put(entry.getKey(), new Timed<>(List.copyOf(entry.getValue()), now));
        }
    }

    static synchronized List<UltraBackend.MaterialEffectFrame> materialsFor(long sceneInstanceId, String targetKey) {
        Timed<List<UltraBackend.MaterialEffectFrame>> timed = MATERIALS.get(key(sceneInstanceId, targetKey));
        return fresh(timed) ? timed.value : List.of();
    }

    static synchronized void acceptCameras(List<UltraBackend.CameraRigFrame> frames) {
        cameras = new Timed<>(List.copyOf(frames), System.nanoTime());
    }

    static synchronized List<UltraBackend.CameraRigFrame> cameras() {
        return fresh(cameras) ? cameras.value : List.of();
    }

    static synchronized void acceptMarkers(List<UltraBackend.EditorMarkerFrame> frames) {
        markers = new Timed<>(List.copyOf(frames), System.nanoTime());
    }

    static synchronized List<UltraBackend.EditorMarkerFrame> markers() {
        return fresh(markers) ? markers.value : List.of();
    }

    static synchronized void tick() {
        long now = System.nanoTime();
        RIGS.entrySet().removeIf(entry -> now - entry.getValue().seen > STALE_NANOS);
        MATERIALS.entrySet().removeIf(entry -> now - entry.getValue().seen > STALE_NANOS);
        if (!fresh(lights)) lights = new Timed<>(List.of(), 0L);
        if (!fresh(cameras)) cameras = new Timed<>(List.of(), 0L);
        if (!fresh(markers)) markers = new Timed<>(List.of(), 0L);
    }

    static synchronized void clear() {
        RIGS.clear();
        MATERIALS.clear();
        lights = new Timed<>(List.of(), 0L);
        cameras = new Timed<>(List.of(), 0L);
        markers = new Timed<>(List.of(), 0L);
    }

    private static boolean fresh(Timed<?> timed) {
        return timed != null && System.nanoTime() - timed.seen <= STALE_NANOS;
    }

    private static String key(long scene, String element) { return scene + ":" + element; }
    private record Timed<T>(T value, long seen) { }
}
