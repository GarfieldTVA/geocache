package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Permission-checked C2S authoring payload. The server validates the complete JSON before publish. */
public record PublishEventPublicationPayload(long requestId, String publicationJson, boolean refreshRunning)
        implements CustomPayload {
    public static final int MAX_JSON_CHARS = 1_048_576;
    public static final Id<PublishEventPublicationPayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "publish_event_program"));

    public PublishEventPublicationPayload {
        publicationJson = publicationJson == null ? "" : publicationJson;
        if (publicationJson.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("publication is too large");
    }

    public static final PacketCodec<RegistryByteBuf, PublishEventPublicationPayload> CODEC = PacketCodec.of((value, buf) -> {
        buf.writeVarLong(value.requestId);
        buf.writeString(value.publicationJson, MAX_JSON_CHARS);
        buf.writeBoolean(value.refreshRunning);
    }, buf -> new PublishEventPublicationPayload(buf.readVarLong(), buf.readString(MAX_JSON_CHARS), buf.readBoolean()));

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
