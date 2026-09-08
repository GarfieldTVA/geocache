package dev.garfield.cinefx.api;

import dev.garfield.cinefx.network.PreloadAckPayload;
import dev.garfield.cinefx.network.PreloadAssetsPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server-authoritative phase/event runtime with late-join reconstruction and asset readiness. */
public final class EventDirector {
    public static final EventDirector INSTANCE = new EventDirector();

    private final AtomicLong sessionIds = new AtomicLong(1L);
    private final AtomicLong preloadIds = new AtomicLong(1L);
    private final LinkedHashMap<Long, Session> sessions = new LinkedHashMap<>();
    private final HashMap<Long, Long> preloadToSession = new HashMap<>();
    private boolean initialized;

    private EventDirector() { }

    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> resync(handler.player)));
        ServerPlayNetworking.registerGlobalReceiver(PreloadAckPayload.ID, (payload, context) ->
                context.server().execute(() -> acknowledgePreload(context.player(), payload)));
    }

    public SessionHandle start(MinecraftServer server, ServerWorld world, EventProgram program,
                               Vec3d anchor, double audienceRadius, long seed, Map<String, String> variables) {
        if (server == null || world == null || program == null) throw new IllegalArgumentException("server/world/program are required");
        if (anchor == null) anchor = Vec3d.ZERO;
        if (!Double.isFinite(audienceRadius) || audienceRadius < 0.0) throw new IllegalArgumentException("audienceRadius must be finite and >= 0");
        long id = sessionIds.getAndIncrement();
        Session session = new Session(id, program, world.getRegistryKey(), anchor, audienceRadius, seed,
                variables == null ? Map.of() : variables, world.getTime());
        sessions.put(id, session);
        session.enterPhase(server, world, program.initialPhase());
        return new SessionHandle(id, program.id());
    }

    public SessionHandle restore(MinecraftServer server, EventProgram program, EventSnapshot snapshot) {
        ServerWorld world = server.getWorld(snapshot.worldKey());
        if (world == null) throw new IllegalStateException("World unavailable: " + snapshot.worldKey().getValue());
        if (!program.id().equals(snapshot.programId())) throw new IllegalArgumentException("Snapshot program id mismatch");
        long id = sessionIds.getAndIncrement();
        Session session = new Session(id, program, snapshot.worldKey(), snapshot.anchor(), snapshot.audienceRadius(),
                snapshot.seed(), snapshot.variables(), world.getTime());
        session.phaseId = snapshot.phaseId();
        session.phaseStartedAt = world.getTime() - Math.max(0L, snapshot.phaseElapsedTicks());
        session.paused = snapshot.paused();
        for (RunningSceneSnapshot running : snapshot.activeScenes()) {
            session.runningScenes.put(running.sceneId(), new RunningScene(running.sceneId(), running.anchor(),
                    running.startGameTime(), running.seed(), running.variables()));
        }
        sessions.put(id, session);
        for (ServerPlayerEntity player : world.getPlayers()) session.resyncPlayer(player);
        return new SessionHandle(id, program.id());
    }

    public void stop(MinecraftServer server, SessionHandle handle) {
        Session session = handle == null ? null : sessions.remove(handle.sessionId());
        if (session == null) return;
        ServerWorld world = server.getWorld(session.worldKey);
        if (world != null) for (Identifier sceneId : List.copyOf(session.runningScenes.keySet())) session.stopScene(world, sceneId);
        for (PreloadState state : session.preloads.values()) preloadToSession.remove(state.requestId);
    }

    public void pause(SessionHandle handle) { require(handle).paused = true; }

    public void resume(MinecraftServer server, SessionHandle handle) {
        Session session = require(handle);
        if (!session.paused) return;
        ServerWorld world = server.getWorld(session.worldKey);
        if (world == null) return;
        session.paused = false;
        session.phaseStartedAt = world.getTime() - session.pausedPhaseTicks;
    }

    public void setPhase(MinecraftServer server, SessionHandle handle, String phaseId) {
        Session session = require(handle);
        ServerWorld world = server.getWorld(session.worldKey);
        if (world != null) session.transition(server, world, phaseId, List.of());
    }

    public void setVariable(SessionHandle handle, String key, String value) {
        Session session = require(handle);
        if (value == null) session.variables.remove(key); else session.variables.put(key, value);
    }

    public void seekScene(MinecraftServer server, SessionHandle handle, Identifier sceneId, double localTick) {
        Session session = require(handle);
        ServerWorld world = server.getWorld(session.worldKey);
        RunningScene previous = session.runningScenes.get(sceneId);
        if (world == null || previous == null) return;
        session.stopScene(world, sceneId);
        session.playScene(world, sceneId, world.getTime() - Math.max(0L, (long)Math.floor(localTick)), previous.seed, previous.variables);
    }

    public EventSnapshot snapshot(MinecraftServer server, SessionHandle handle) {
        Session session = require(handle);
        ServerWorld world = server.getWorld(session.worldKey);
        long elapsed = world == null ? 0L : Math.max(0L, world.getTime() - session.phaseStartedAt);
        ArrayList<RunningSceneSnapshot> running = new ArrayList<>();
        for (RunningScene value : session.runningScenes.values()) {
            running.add(new RunningSceneSnapshot(value.sceneId, value.anchor, value.startGameTime, value.seed, value.variables));
        }
        return new EventSnapshot(session.program.id(), session.worldKey, session.anchor, session.audienceRadius,
                session.seed, session.phaseId, elapsed, session.paused, Map.copyOf(session.variables), List.copyOf(running));
    }

    public List<SessionInfo> sessions(MinecraftServer server) {
        ArrayList<SessionInfo> result = new ArrayList<>();
        for (Session session : sessions.values()) {
            ServerWorld world = server.getWorld(session.worldKey);
            long phaseTicks = world == null ? 0L : Math.max(0L, world.getTime() - session.phaseStartedAt);
            result.add(new SessionInfo(session.id, session.program.id(), session.phaseId, phaseTicks,
                    session.paused, session.runningScenes.size(), session.preloads.size()));
        }
        return List.copyOf(result);
    }

    public void resync(ServerPlayerEntity player) {
        if (player == null) return;
        for (Session session : sessions.values()) {
            if (session.worldKey.equals(player.getEntityWorld().getRegistryKey())) session.resyncPlayer(player);
        }
    }

    private void tick(MinecraftServer server) {
        for (Session session : List.copyOf(sessions.values())) {
            ServerWorld world = server.getWorld(session.worldKey);
            if (world == null) continue;
            session.purgeCompletedScenes(world.getTime());
            if (session.paused) {
                session.pausedPhaseTicks = Math.max(0L, world.getTime() - session.phaseStartedAt);
                continue;
            }
            int guard = 0;
            while (guard++ < 8) {
                EventProgram.Phase phase = session.program.phases().get(session.phaseId);
                if (phase == null) break;
                RuntimeContext context = new RuntimeContext(world, session);
                EventProgram.Transition selected = null;
                for (EventProgram.Transition transition : phase.transitions()) {
                    try {
                        if (transition.condition().test(context)) { selected = transition; break; }
                    } catch (RuntimeException exception) {
                        System.err.println("[CineFX] Event condition failed: " + exception.getMessage());
                    }
                }
                if (selected == null) break;
                session.transition(server, world, selected.targetPhase(), selected.actions());
            }
        }
    }

    private void acknowledgePreload(ServerPlayerEntity player, PreloadAckPayload payload) {
        Long sessionId = preloadToSession.get(payload.requestId());
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) return;
        for (PreloadState state : session.preloads.values()) {
            if (state.requestId != payload.requestId()) continue;
            UUID uuid = player.getUuid();
            if (payload.ready()) state.ready.add(uuid); else state.failed.add(uuid);
            break;
        }
    }

    private Session require(SessionHandle handle) {
        if (handle == null) throw new IllegalArgumentException("handle is required");
        Session session = sessions.get(handle.sessionId());
        if (session == null) throw new IllegalStateException("Unknown CineFX event session: " + handle.sessionId());
        return session;
    }

    public record SessionHandle(long sessionId, Identifier programId) { }
    public record SessionInfo(long sessionId, Identifier programId, String phaseId, long phaseTicks,
                              boolean paused, int activeScenes, int pendingPreloads) { }
    public record RunningSceneSnapshot(Identifier sceneId, Vec3d anchor, long startGameTime, long seed,
                                       Map<String, String> variables) { }
    public record EventSnapshot(Identifier programId, RegistryKey<World> worldKey, Vec3d anchor,
                                double audienceRadius, long seed, String phaseId, long phaseElapsedTicks,
                                boolean paused, Map<String, String> variables, List<RunningSceneSnapshot> activeScenes) { }

    private final class Session {
        final long id;
        final EventProgram program;
        final RegistryKey<World> worldKey;
        final Vec3d anchor;
        final double audienceRadius;
        final double radiusSquared;
        final long seed;
        final LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        final LinkedHashMap<Identifier, RunningScene> runningScenes = new LinkedHashMap<>();
        final LinkedHashMap<Identifier, PreloadState> preloads = new LinkedHashMap<>();
        String phaseId;
        long phaseStartedAt;
        boolean paused;
        long pausedPhaseTicks;

        Session(long id, EventProgram program, RegistryKey<World> worldKey, Vec3d anchor,
                double audienceRadius, long seed, Map<String, String> variables, long now) {
            this.id = id;
            this.program = program;
            this.worldKey = worldKey;
            this.anchor = anchor;
            this.audienceRadius = audienceRadius;
            this.radiusSquared = audienceRadius * audienceRadius;
            this.seed = seed;
            this.variables.putAll(variables);
            this.phaseId = program.initialPhase();
            this.phaseStartedAt = now;
        }

        void enterPhase(MinecraftServer server, ServerWorld world, String target) {
            EventProgram.Phase phase = program.phases().get(target);
            if (phase == null) throw new IllegalArgumentException("Unknown CineFX phase: " + target);
            phaseId = target;
            phaseStartedAt = world.getTime();
            runActions(phase.onEnter(), new RuntimeContext(world, this));
        }

        void transition(MinecraftServer server, ServerWorld world, String target, List<EventProgram.Action> actions) {
            EventProgram.Phase current = program.phases().get(phaseId);
            RuntimeContext context = new RuntimeContext(world, this);
            if (current != null) runActions(current.onExit(), context);
            runActions(actions, context);
            enterPhase(server, world, target);
        }

        void playScene(ServerWorld world, Identifier sceneId, long start, long sceneSeed, Map<String, String> sceneVariables) {
            RunningScene running = new RunningScene(sceneId, anchor, start, sceneSeed, Map.copyOf(sceneVariables));
            runningScenes.put(sceneId, running);
            for (ServerPlayerEntity player : world.getPlayers()) if (contains(player)) {
                CineFxServer.play(player, sceneId, anchor, start, sceneSeed, sceneVariables);
            }
        }

        void stopScene(ServerWorld world, Identifier sceneId) {
            runningScenes.remove(sceneId);
            for (ServerPlayerEntity player : world.getPlayers()) if (contains(player)) CineFxServer.stop(player, sceneId);
        }

        void purgeCompletedScenes(long now) {
            runningScenes.entrySet().removeIf(entry -> CineFxApi.find(entry.getKey()).map(definition ->
                    !definition.looping() && now - entry.getValue().startGameTime > definition.durationTicks()).orElse(false));
        }

        void requestPreload(ServerWorld world, Identifier bundleId) {
            PreloadState state = preloads.get(bundleId);
            if (state != null && state.failed.isEmpty()) return;
            if (state != null) preloadToSession.remove(state.requestId);
            state = new PreloadState(preloadIds.getAndIncrement());
            preloads.put(bundleId, state);
            preloadToSession.put(state.requestId, id);
            for (ServerPlayerEntity player : world.getPlayers()) if (contains(player)) sendPreload(player, bundleId, state);
        }

        boolean assetsReady(Identifier bundleId) {
            PreloadState state = preloads.get(bundleId);
            return state != null && state.failed.isEmpty() && state.ready.containsAll(state.expected);
        }

        void resyncPlayer(ServerPlayerEntity player) {
            if (!contains(player)) return;
            for (RunningScene running : runningScenes.values()) {
                CineFxServer.play(player, running.sceneId, running.anchor, running.startGameTime, running.seed, running.variables);
            }
            for (Map.Entry<Identifier, PreloadState> entry : preloads.entrySet()) sendPreload(player, entry.getKey(), entry.getValue());
        }

        void sendPreload(ServerPlayerEntity player, Identifier bundleId, PreloadState state) {
            if (!ServerPlayNetworking.canSend(player, PreloadAssetsPayload.ID)) return;
            state.expected.add(player.getUuid());
            ServerPlayNetworking.send(player, new PreloadAssetsPayload(bundleId.toString(), state.requestId));
        }

        boolean contains(ServerPlayerEntity player) {
            return worldKey.equals(player.getEntityWorld().getRegistryKey())
                    && player.getEntityPos().squaredDistanceTo(anchor) <= radiusSquared;
        }
    }

    private static final class RunningScene {
        final Identifier sceneId;
        final Vec3d anchor;
        final long startGameTime;
        final long seed;
        final Map<String, String> variables;
        RunningScene(Identifier sceneId, Vec3d anchor, long startGameTime, long seed, Map<String, String> variables) {
            this.sceneId = sceneId; this.anchor = anchor; this.startGameTime = startGameTime; this.seed = seed; this.variables = variables;
        }
    }

    private static final class PreloadState {
        final long requestId;
        final Set<UUID> expected = new HashSet<>();
        final Set<UUID> ready = new HashSet<>();
        final Set<UUID> failed = new HashSet<>();
        PreloadState(long requestId) { this.requestId = requestId; }
    }

    private static void runActions(List<EventProgram.Action> actions, EventProgram.Context context) {
        for (EventProgram.Action action : actions) try { action.run(context); }
        catch (RuntimeException exception) { System.err.println("[CineFX] Event action failed: " + exception.getMessage()); }
    }

    private final class RuntimeContext implements EventProgram.Context {
        private final ServerWorld world;
        private final Session session;
        RuntimeContext(ServerWorld world, Session session) { this.world = world; this.session = session; }
        @Override public Identifier programId() { return session.program.id(); }
        @Override public long sessionId() { return session.id; }
        @Override public String phaseId() { return session.phaseId; }
        @Override public double phaseTicks() { return Math.max(0L, world.getTime() - session.phaseStartedAt); }
        @Override public long worldTime() { return world.getTime(); }
        @Override public String variable(String key) { return session.variables.get(key); }
        @Override public Map<String, String> variables() { return Map.copyOf(session.variables); }
        @Override public void setVariable(String key, String value) { if (value == null) session.variables.remove(key); else session.variables.put(key, value); }
        @Override public void playScene(Identifier sceneId, long offset, long seedSalt, Map<String, String> actionVariables) {
            LinkedHashMap<String, String> merged = new LinkedHashMap<>(session.variables);
            if (actionVariables != null) merged.putAll(actionVariables);
            long sceneSeed = session.seed ^ seedSalt ^ ((long)sceneId.hashCode() << 32) ^ session.id;
            session.playScene(world, sceneId, world.getTime() + offset, sceneSeed, merged);
        }
        @Override public void stopScene(Identifier sceneId) { session.stopScene(world, sceneId); }
        @Override public void requestPreload(Identifier bundleId) { session.requestPreload(world, bundleId); }
        @Override public boolean assetsReady(Identifier bundleId) { return session.assetsReady(bundleId); }
        @Override public void marker(String name, Map<String, String> parameters) {
            System.out.println("[CineFX] Event marker " + session.program.id() + "/" + session.id + ": " + name + " " + parameters);
        }
    }
}
