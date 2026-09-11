package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C acknowledgement for Event Studio remote publication. */
public record PublishEventPublicationResultPayload(long requestId, boolean success, String message, int refreshedSessions)
        implements CustomPayload {
    public static final int MAX_MESSAGE_CHARS = 512;
    public static final Id<PublishEventPublicationResultPayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "publish_event_program_result"));

    public PublishEventPublicationResultPayload {
        message = message == null ? "" : message;
        if (message.length() > MAX_MESSAGE_CHARS) message = message.substring(0, MAX_MESSAGE_CHARS);
        if (refreshedSessions < 0) refreshedSessions = 0;
    }

    public static final PacketCodec<RegistryByteBuf, PublishEventPublicationResultPayload> CODEC = PacketCodec.of((value, buf) -> {
        buf.writeVarLong(value.requestId);
        buf.writeBoolean(value.success);
        buf.writeString(value.message, MAX_MESSAGE_CHARS);
        buf.writeVarInt(value.refreshedSessions);
    }, buf -> new PublishEventPublicationResultPayload(
            buf.readVarLong(), buf.readBoolean(), buf.readString(MAX_MESSAGE_CHARS), buf.readVarInt()));

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
