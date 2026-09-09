package dev.garfield.cinefx.api;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Operator-facing live director controls for production events and rehearsals. */
public final class EventDirectorCommands {
    private static boolean initialized;

    private EventDirectorCommands() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("cinefxevent").requires(source -> source.hasPermissionLevel(2));

            root.then(CommandManager.literal("list").executes(context -> list(context.getSource())));
            root.then(CommandManager.literal("stopall").executes(context -> stopAll(context.getSource())));
            root.then(CommandManager.literal("resync").executes(context -> resync(context.getSource())));

            root.then(CommandManager.literal("stop")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                EventDirector.INSTANCE.stop(context.getSource().getServer(), handle);
                                feedback(context.getSource(), "Stopped CineFX session #" + handle.sessionId());
                            }))));

            root.then(CommandManager.literal("pause")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                EventDirector.INSTANCE.pause(handle);
                                feedback(context.getSource(), "Paused CineFX session #" + handle.sessionId());
                            }))));

            root.then(CommandManager.literal("resume")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                EventDirector.INSTANCE.resume(context.getSource().getServer(), handle);
                                feedback(context.getSource(), "Resumed CineFX session #" + handle.sessionId());
                            }))));

            root.then(CommandManager.literal("phase")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .then(CommandManager.argument("phase", StringArgumentType.word())
                                    .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                        String phase = StringArgumentType.getString(context, "phase");
                                        EventDirector.INSTANCE.setPhase(context.getSource().getServer(), handle, phase);
                                        feedback(context.getSource(), "Session #" + handle.sessionId() + " -> phase " + phase);
                                    })))));

            root.then(CommandManager.literal("var")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .then(CommandManager.argument("key", StringArgumentType.word())
                                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                            .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                                String key = StringArgumentType.getString(context, "key");
                                                String value = StringArgumentType.getString(context, "value");
                                                EventDirector.INSTANCE.setVariable(handle, key, value);
                                                feedback(context.getSource(), "Session #" + handle.sessionId() + " variable " + key + "=" + value);
                                            }))))));

            root.then(CommandManager.literal("unset")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .then(CommandManager.argument("key", StringArgumentType.word())
                                    .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                        String key = StringArgumentType.getString(context, "key");
                                        EventDirector.INSTANCE.setVariable(handle, key, null);
                                        feedback(context.getSource(), "Removed variable " + key + " from session #" + handle.sessionId());
                                    })))));

            root.then(CommandManager.literal("seek")
                    .then(CommandManager.argument("session", LongArgumentType.longArg(1))
                            .then(CommandManager.argument("scene", StringArgumentType.word())
                                    .then(CommandManager.argument("ticks", DoubleArgumentType.doubleArg(0.0))
                                            .executes(context -> withHandle(context.getSource(), LongArgumentType.getLong(context, "session"), handle -> {
                                                Identifier scene = parse(StringArgumentType.getString(context, "scene"));
                                                double ticks = DoubleArgumentType.getDouble(context, "ticks");
                                                EventDirector.INSTANCE.seekScene(context.getSource().getServer(), handle, scene, ticks);
                                                feedback(context.getSource(), "Seek session #" + handle.sessionId() + " scene " + scene + " to " + ticks + " ticks");
                                            }))))));

            dispatcher.register(root);
        });
    }

    private static int list(ServerCommandSource source) {
        List<EventDirector.SessionInfo> sessions = EventDirector.INSTANCE.sessions(source.getServer());
        if (sessions.isEmpty()) {
            feedback(source, "No active CineFX event sessions.");
            return 1;
        }
        for (EventDirector.SessionInfo info : sessions) {
            feedback(source, "#" + info.sessionId() + " " + info.programId() + " phase=" + info.phaseId()
                    + " t=" + info.phaseTicks() + " paused=" + info.paused()
                    + " scenes=" + info.activeScenes() + " preloads=" + info.pendingPreloads());
        }
        return sessions.size();
    }

    private static int stopAll(ServerCommandSource source) {
        List<EventDirector.SessionInfo> sessions = EventDirector.INSTANCE.sessions(source.getServer());
        for (EventDirector.SessionInfo info : sessions) {
            EventDirector.INSTANCE.stop(source.getServer(), new EventDirector.SessionHandle(info.sessionId(), info.programId()));
        }
        feedback(source, "Stopped " + sessions.size() + " CineFX session(s).");
        return Math.max(1, sessions.size());
    }

    private static int resync(ServerCommandSource source) {
        int count = 0;
        for (var player : source.getServer().getPlayerManager().getPlayerList()) {
            EventDirector.INSTANCE.resync(player);
            count++;
        }
        feedback(source, "Resynchronized CineFX state for " + count + " player(s).");
        return Math.max(1, count);
    }

    private static int withHandle(ServerCommandSource source, long id, HandleAction action) {
        for (EventDirector.SessionInfo info : EventDirector.INSTANCE.sessions(source.getServer())) {
            if (info.sessionId() != id) continue;
            try {
                action.run(new EventDirector.SessionHandle(info.sessionId(), info.programId()));
                return 1;
            } catch (RuntimeException exception) {
                source.sendError(Text.literal("CineFX: " + exception.getMessage()));
                return 0;
            }
        }
        source.sendError(Text.literal("CineFX: unknown session #" + id));
        return 0;
    }

    private static Identifier parse(String raw) {
        int separator = raw.indexOf(':');
        return separator < 0 ? Identifier.ofVanilla(raw)
                : Identifier.of(raw.substring(0, separator), raw.substring(separator + 1));
    }

    private static void feedback(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
    }

    @FunctionalInterface
    private interface HandleAction { void run(EventDirector.SessionHandle handle); }
}
