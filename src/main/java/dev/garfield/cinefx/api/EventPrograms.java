package dev.garfield.cinefx.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Runtime bridge for declarative programs published through {@link EventProgramSpec.Registry}.
 * This keeps EventDirector's object-based API intact while letting data-driven tooling start and
 * restore the latest hot-reloaded program by id.
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
}
