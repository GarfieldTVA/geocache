package dev.garfield.cinefx.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime bridge for declarative programs published through {@link EventProgramSpec.Registry}.
 * This keeps EventDirector's object-based API intact while letting data-driven tooling start,
 * restore and refresh the latest hot-reloaded program by id.
 */
public final class EventPrograms {
    private EventPrograms() { }

    /** Compiles the latest published specification for an id. */
    public static EventProgram require(Identifier programId) {
        if (programId == null) throw new IllegalArgumentException("programId is required");
        return EventProgramSpec.Registry.compiled(programId)
                .orElseThrow(() -> new IllegalStateException("Unknown CineFX event program: " + programId));
    }

    /** Starts the latest published version of a declarative event program. */
    public static EventDirector.SessionHandle start(MinecraftServer server, ServerWorld world, Identifier programId,
                                                    Vec3d anchor, double audienceRadius, long seed,
                                                    Map<String, String> variables) {
        return EventDirector.INSTANCE.start(server, world, require(programId), anchor, audienceRadius, seed, variables);
    }

    /** Restores a snapshot using the latest published version of its declarative program. */
    public static EventDirector.SessionHandle restore(MinecraftServer server, Identifier programId,
                                                      EventDirector.EventSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        EventProgram program = require(programId);
        if (!program.id().equals(snapshot.programId())) {
            throw new IllegalArgumentException("Snapshot program id mismatch: expected " + program.id()
                    + " but snapshot contains " + snapshot.programId());
        }
        return EventDirector.INSTANCE.restore(server, program, snapshot);
    }

    /** Restores a snapshot using the program id already stored inside it. */
    public static EventDirector.SessionHandle restore(MinecraftServer server, EventDirector.EventSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        return restore(server, snapshot.programId(), snapshot);
    }

    /**
     * Rebinds running sessions to the newest declarative program while preserving phase elapsed time,
     * variables, audience geometry, seed and each active scene's original startGameTime. The existing
     * EventDirector public snapshot/restore contract is deliberately used here instead of reflective
     * access to its private session implementation.
     */
    public static int refreshRunning(MinecraftServer server, Identifier programId) {
        if (server == null || programId == null) return 0;
        // Compile before touching any running session. A bad publication therefore cannot interrupt
        // a live event even if an integration calls refreshRunning directly.
        EventProgram replacement = require(programId);
        ArrayList<EventDirector.SessionHandle> matching = new ArrayList<>();
        for (EventDirector.SessionInfo info : EventDirector.INSTANCE.sessions(server)) {
            if (programId.equals(info.programId())) matching.add(new EventDirector.SessionHandle(info.sessionId(), info.programId()));
        }
        if (matching.isEmpty()) return 0;

        record Saved(EventDirector.SessionHandle handle, EventDirector.EventSnapshot snapshot) { }
        ArrayList<Saved> saved = new ArrayList<>(matching.size());
        for (EventDirector.SessionHandle handle : matching) {
            EventDirector.EventSnapshot snapshot = EventDirector.INSTANCE.snapshot(server, handle);
            // If the edited program removed the current phase, move the snapshot to the new initial
            // phase while preserving all other state. This avoids restoring a permanently dead phase.
            if (!replacement.phases().containsKey(snapshot.phaseId())) {
                snapshot = new EventDirector.EventSnapshot(snapshot.programId(), snapshot.worldKey(), snapshot.anchor(),
                        snapshot.audienceRadius(), snapshot.seed(), replacement.initialPhase(), 0L,
                        snapshot.paused(), snapshot.variables(), snapshot.activeScenes());
            }
            saved.add(new Saved(handle, snapshot));
        }

        for (Saved value : saved) EventDirector.INSTANCE.stop(server, value.handle());
        int restored = 0;
        for (Saved value : saved) {
            EventDirector.INSTANCE.restore(server, replacement, value.snapshot());
            restored++;
        }
        return restored;
    }
}
