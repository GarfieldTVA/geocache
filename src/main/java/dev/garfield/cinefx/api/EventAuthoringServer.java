package dev.garfield.cinefx.api;

import dev.garfield.cinefx.network.PublishEventPublicationPayload;
import dev.garfield.cinefx.network.PublishEventPublicationResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit authoring endpoint used by CineFX editor tooling on dedicated servers. Only gamemaster
 * (legacy command level 2) or stronger command sources may publish. Payloads are size-limited by
 * their codec, and the complete publication is decoded/validated before either registry changes.
 */
public final class EventAuthoringServer {
    private static final long MIN_INTERVAL_MS = 250L;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static boolean initialized;

    private EventAuthoringServer() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ServerPlayNetworking.registerGlobalReceiver(PublishEventPublicationPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.server(), context.player(), payload)));
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, PublishEventPublicationPayload payload) {
        if (player == null) return;
        if (!CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK).test(player.getCommandSource())) {
            reply(player, payload.requestId(), false, "Remote CineFX authoring requires gamemaster/operator permission", 0);
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = LAST_REQUEST.put(player.getUuid(), now);
        if (previous != null && now - previous < MIN_INTERVAL_MS) {
            reply(player, payload.requestId(), false, "Remote CineFX authoring is rate limited", 0);
            return;
        }

        try {
            EventPublication publication = EventPublicationJson.decode(payload.publicationJson());
            publication.publish();
            int refreshed = payload.refreshRunning()
                    ? EventPrograms.refreshRunning(server, publication.program().id())
                    : 0;
            String message = refreshed > 0
                    ? "Published and refreshed " + refreshed + " running session(s)"
                    : "Published to server CineFX registries";
            reply(player, payload.requestId(), true, message, refreshed);
        } catch (RuntimeException exception) {
            reply(player, payload.requestId(), false, compact(exception), 0);
        }
    }

    private static void reply(ServerPlayerEntity player, long requestId, boolean success, String message, int refreshed) {
        if (!ServerPlayNetworking.canSend(player, PublishEventPublicationResultPayload.ID)) return;
        ServerPlayNetworking.send(player, new PublishEventPublicationResultPayload(requestId, success, message, refreshed));
    }

    private static String compact(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ');
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
