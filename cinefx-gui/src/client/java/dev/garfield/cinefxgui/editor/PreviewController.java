package dev.garfield.cinefxgui.editor;

import dev.garfield.cinefx.api.CineFxApi;
import dev.garfield.cinefx.api.ConflictPolicy;
import dev.garfield.cinefx.api.EventElement;
import dev.garfield.cinefx.api.MotionCurve;
import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneOptions;
import dev.garfield.cinefx.api.Transform;
import dev.garfield.cinefx.client.api.ClientCineFx;
import dev.garfield.cinefx.client.api.SceneHandle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** Live editor playback built entirely on CineFX's public registry/playback API. */
public final class PreviewController {
    private static final Identifier PREVIEW_ID = Identifier.of("cinefx_gui", "preview_runtime");
    private static final Identifier EDITOR_CAMERA_ID = Identifier.of("cinefx_gui", "editor_camera_runtime");
    private static final double CAMERA_DURATION = 1_728_000.0;

    /**
     * Heavy scene decoding/hot replacement is deliberately capped while a mouse drag is producing
     * dozens of model changes. 10 Hz is fluid enough for authoring and avoids the repeated frame
     * stalls that were visible with large scenes. A quiet edit is flushed much sooner.
     */
    private static final long LIVE_REFRESH_NS = 100_000_000L;
    private static final long QUIET_REFRESH_NS = 45_000_000L;

    private EditorModel.Project project;
    private SceneHandle previewHandle;
    private SceneHandle editorCameraHandle;
    private double currentTick;
    private boolean playing;
    private long playStartGameTime;
    private boolean previewDirty = true;
    private boolean sceneCameraPreview;
    private CineFxBridge.BuildResult lastBuild;

    private long dirtyMarkedNanos = System.nanoTime();
    private long lastRefreshNanos;
    private long previewBuildCount;
    private long totalBuildNanos;
    private long lastBuildNanos;
    private double lastSentSeekTick = Double.NaN;

    private volatile Vec3d editorCameraPosition = Vec3d.ZERO;
    private volatile float editorCameraYaw;
    private volatile float editorCameraPitch;

    public PreviewController(EditorModel.Project project) { this.project = project; }

    public void setProject(EditorModel.Project project) {
        stopPreview();
        this.project = project;
        currentTick = 0.0;
        playing = false;
        previewDirty = true;
        dirtyMarkedNanos = System.nanoTime();
        lastBuild = null;
        lastSentSeekTick = Double.NaN;
    }

    public double currentTick() { return currentTick; }
    public boolean playing() { return playing; }
    public boolean sceneCameraPreview() { return sceneCameraPreview; }
    public CineFxBridge.BuildResult lastBuild() { return lastBuild; }
    public Vec3d editorCameraPosition() { return editorCameraPosition; }
    public float editorCameraYaw() { return editorCameraYaw; }
    public float editorCameraPitch() { return editorCameraPitch; }
    public long previewBuildCount() { return previewBuildCount; }
    public double lastBuildMillis() { return lastBuildNanos / 1_000_000.0; }
    public double averageBuildMillis() {
        return previewBuildCount == 0 ? 0.0 : totalBuildNanos / 1_000_000.0 / previewBuildCount;
    }

    public void markDirty() {
        previewDirty = true;
        dirtyMarkedNanos = System.nanoTime();
    }

    public void setTick(MinecraftClient client, double tick) {
        if (project == null) return;
        currentTick = clamp(tick, 0.0, Math.max(1.0, project.durationTicks));
        ensurePreview(client, playing);
        if (previewHandle == null) return;
        seekIfChanged(currentTick, true);
        if (playing) {
            ClientCineFx.resume(previewHandle);
            resetPlayClock(client);
        }
    }

    public void togglePlay(MinecraftClient client) {
        if (project == null || client == null || client.world == null) return;
        if (playing) {
            currentTick = clamp(client.world.getTime() - playStartGameTime, 0.0, Math.max(1.0, project.durationTicks));
            playing = false;
            ensurePreview(client, false);
            if (previewHandle != null) {
                ClientCineFx.pause(previewHandle);
                seekIfChanged(currentTick, true);
            }
        } else {
            // Playing must see the latest authored state immediately, including audio channels.
            if (previewHandle == null) ensurePreview(client, true);
            else if (previewDirty || lastBuild == null) refreshDefinition(client, true);
            if (previewHandle != null) {
                seekIfChanged(currentTick, true);
                ClientCineFx.resume(previewHandle);
            }
            playing = true;
            resetPlayClock(client);
        }
    }

    public void stopAndRewind(MinecraftClient client) {
        playing = false;
        currentTick = 0.0;
        ensurePreview(client, false);
        if (previewHandle != null) {
            seekIfChanged(0.0, true);
            ClientCineFx.pause(previewHandle);
        }
    }

    public void tick(MinecraftClient client) {
        if (project == null || client == null || client.world == null) return;
        if (!sceneCameraPreview) ensureEditorCamera(client);

        if (playing) {
            currentTick = Math.max(0.0, client.world.getTime() - playStartGameTime);
            if (project.looping && project.durationTicks > 0.0) currentTick %= project.durationTicks;
            else if (currentTick >= project.durationTicks) {
                currentTick = project.durationTicks;
                playing = false;
                ensurePreview(client, false);
                if (previewHandle != null) {
                    seekIfChanged(currentTick, true);
                    ClientCineFx.pause(previewHandle);
                }
                return;
            }
            refreshIfDue(client, true);
        } else {
            ensurePreview(client, false);
            refreshIfDue(client, false);
            seekIfChanged(currentTick, false);
        }
    }

    public CineFxBridge.BuildResult validateOnly() {
        if (project == null) return null;
        lastBuild = buildTimed(false);
        return lastBuild;
    }

    public void setSceneCameraPreview(MinecraftClient client, boolean enabled) {
        sceneCameraPreview = enabled;
        if (enabled) stopEditorCamera(); else ensureEditorCamera(client);
    }

    public void setEditorCamera(Vec3d position, float yaw, float pitch) {
        if (position != null) editorCameraPosition = position;
        editorCameraYaw = yaw;
        editorCameraPitch = Math.max(-89.9f, Math.min(89.9f, pitch));
    }

    public void initializeEditorCamera(MinecraftClient client) {
        if (client == null) return;
        if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            editorCameraPosition = client.gameRenderer.getCamera().getCameraPos();
            editorCameraYaw = client.gameRenderer.getCamera().getYaw();
            editorCameraPitch = client.gameRenderer.getCamera().getPitch();
        } else if (client.player != null) {
            editorCameraPosition = client.player.getEyePos();
            editorCameraYaw = client.player.getYaw();
            editorCameraPitch = client.player.getPitch();
        }
        if (!sceneCameraPreview) ensureEditorCamera(client);
    }

    /**
     * Called when the Studio UI is hidden. Keep the authored CineFX preview alive in the world, but
     * release editor-only camera control so the player can move around normally like creative mode.
     */
    public void close() {
        sceneCameraPreview = false;
        stopEditorCamera();
    }

    /** Fully remove the persistent preview, used when leaving the world or resetting the Studio. */
    public void shutdown() {
        stopPreview();
        stopEditorCamera();
        sceneCameraPreview = false;
    }

    private void refreshIfDue(MinecraftClient client, boolean includeAudio) {
        if (!previewDirty || previewHandle == null) return;
        long now = System.nanoTime();
        boolean liveBudgetElapsed = now - lastRefreshNanos >= LIVE_REFRESH_NS;
        boolean editWentQuiet = dirtyMarkedNanos != 0L && now - dirtyMarkedNanos >= QUIET_REFRESH_NS;
        if (liveBudgetElapsed || editWentQuiet) refreshDefinition(client, includeAudio);
    }

    /**
     * Keeps the current scene instance alive while authoring. The CineFX runtime hot-swaps the
     * replacement definition in place, so this does not reset camera/audio timing or scene handles.
     */
    private void refreshDefinition(MinecraftClient client, boolean includeAudio) {
        if (project == null || client == null || client.world == null) return;
        lastBuild = buildTimed(includeAudio);
        CineFxApi.replace(lastBuild.scene());
        previewDirty = false;
        dirtyMarkedNanos = 0L;
        lastRefreshNanos = System.nanoTime();
        if (previewHandle == null) {
            long start = client.world.getTime() - Math.max(0L, (long)Math.floor(currentTick));
            SceneOptions options = new SceneOptions(project.anchor(), start, project.seed,
                    project.variables == null ? Map.of() : project.variables);
            previewHandle = ClientCineFx.play(PREVIEW_ID, options);
            lastSentSeekTick = Double.NaN;
            if (!playing) seekIfChanged(currentTick, true);
        }
    }

    /** Create the preview immediately only when there is no running instance. Dirty replacements are throttled. */
    private void ensurePreview(MinecraftClient client, boolean includeAudio) {
        if (previewHandle == null) restartPreview(client, currentTick, includeAudio, !playing);
    }

    private void restartPreview(MinecraftClient client, double localTick, boolean includeAudio, boolean freeze) {
        if (project == null || client == null || client.world == null) return;
        stopPreview();
        lastBuild = buildTimed(includeAudio);
        CineFxApi.replace(lastBuild.scene());
        long start = client.world.getTime() - Math.max(0L, (long)Math.floor(localTick));
        playStartGameTime = start;
        SceneOptions options = new SceneOptions(project.anchor(), start, project.seed,
                project.variables == null ? Map.of() : project.variables);
        previewHandle = ClientCineFx.play(PREVIEW_ID, options);
        lastSentSeekTick = Double.NaN;
        if (freeze) seekIfChanged(localTick, true);
        previewDirty = false;
        dirtyMarkedNanos = 0L;
        lastRefreshNanos = System.nanoTime();
    }

    private CineFxBridge.BuildResult buildTimed(boolean includeAudio) {
        long started = System.nanoTime();
        CineFxBridge.BuildResult result = CineFxBridge.build(project, PREVIEW_ID, includeAudio);
        long elapsed = Math.max(0L, System.nanoTime() - started);
        lastBuildNanos = elapsed;
        totalBuildNanos += elapsed;
        previewBuildCount++;
        return result;
    }

    private void seekIfChanged(double tick, boolean force) {
        if (previewHandle == null) return;
        if (!force && Double.isFinite(lastSentSeekTick) && Math.abs(lastSentSeekTick - tick) < 1.0e-6) return;
        ClientCineFx.seek(previewHandle, tick);
        lastSentSeekTick = tick;
    }

    private void resetPlayClock(MinecraftClient client) {
        if (client != null && client.world != null)
            playStartGameTime = client.world.getTime() - Math.max(0L, (long)Math.floor(currentTick));
    }

    private void stopPreview() {
        if (previewHandle != null) {
            ClientCineFx.stop(previewHandle);
            previewHandle = null;
        }
        lastSentSeekTick = Double.NaN;
    }

    private void ensureEditorCamera(MinecraftClient client) {
        if (sceneCameraPreview || editorCameraHandle != null || client == null || client.world == null) return;
        MotionCurve liveCameraMotion = (tick, seed) -> new Transform(editorCameraPosition,
                new Vec3d(editorCameraPitch, editorCameraYaw, 0.0), new Vec3d(1.0, 1.0, 1.0));
        EventElement.Camera camera = new EventElement.Camera(
                "cinefx_gui_editor_camera", 0.0, CAMERA_DURATION, Integer.MAX_VALUE,
                ConflictPolicy.REPLACE_LOWER, EventElement.CameraMode.ANCHOR_ABSOLUTE,
                Vec3d.ZERO, null, liveCameraMotion, null, null, null, null);
        SceneDefinition definition = new SceneDefinition(EDITOR_CAMERA_ID, CAMERA_DURATION,
                Integer.MAX_VALUE, true, List.of(camera), Map.of("cinefx_gui", "editor_camera"));
        CineFxApi.replace(definition);
        editorCameraHandle = ClientCineFx.play(EDITOR_CAMERA_ID,
                new SceneOptions(Vec3d.ZERO, client.world.getTime(), 0xC1EF00DL, Map.of()));
    }

    private void stopEditorCamera() {
        if (editorCameraHandle != null) {
            ClientCineFx.stop(editorCameraHandle);
            editorCameraHandle = null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
