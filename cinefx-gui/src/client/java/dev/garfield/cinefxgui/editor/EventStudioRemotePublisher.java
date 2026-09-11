package dev.garfield.cinefxgui.editor;

import dev.garfield.cinefx.api.AssetBundle;
import dev.garfield.cinefx.api.EventPublication;
import dev.garfield.cinefx.api.EventPublicationJson;
import dev.garfield.cinefx.api.EventProgramSpec;
import dev.garfield.cinefx.network.PublishEventPublicationPayload;
import dev.garfield.cinefx.network.PublishEventPublicationResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Client-side request/ack layer for explicit operator-authorized Event Studio publication. */
public final class EventStudioRemotePublisher {
    public record Result(boolean success, String message, int refreshedSessions) { }

    private static final AtomicLong REQUEST_IDS = new AtomicLong(1L);
    private static final Map<Long, Consumer<Result>> CALLBACKS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private EventStudioRemotePublisher() { }

    public static synchronized void initializeClient() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PublishEventPublicationResultPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    Consumer<Result> callback = CALLBACKS.remove(payload.requestId());
                    if (callback != null) callback.accept(new Result(payload.success(), payload.message(), payload.refreshedSessions()));
                }));
    }

    public static boolean canPublishRemote() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getNetworkHandler() != null && ClientPlayNetworking.canSend(PublishEventPublicationPayload.ID);
    }

    public static void publish(EventAuthoringModel.Workspace workspace, boolean refreshRunning, Consumer<Result> callback) {
        if (workspace == null) throw new IllegalArgumentException("workspace is required");
        List<String> errors = EventWorkspaceStore.validate(workspace);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst());
        EventProgramSpec program = workspace.program.compile();
        ArrayList<AssetBundle> bundles = new ArrayList<>();
        if (workspace.bundles != null) for (EventAuthoringModel.Bundle draft : workspace.bundles) {
            if (draft != null && draft.id != null && !draft.id.isBlank()) bundles.add(draft.compile());
        }
        String json = EventPublicationJson.encode(new EventPublication(program, bundles));
        if (json.length() > PublishEventPublicationPayload.MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Event workspace is too large for remote publish");
        }
        if (!canPublishRemote()) throw new IllegalStateException("Remote server does not expose CineFX authoring");
        long requestId = REQUEST_IDS.getAndIncrement();
        if (callback != null) CALLBACKS.put(requestId, callback);
        ClientPlayNetworking.send(new PublishEventPublicationPayload(requestId, json, refreshRunning));
    }
}
