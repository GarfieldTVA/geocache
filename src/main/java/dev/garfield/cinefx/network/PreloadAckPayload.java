package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client readiness response for a PreloadAssetsPayload. */
public record PreloadAckPayload(long requestId, boolean ready, int missingResources) implements CustomPayload {
    public static final Id<PreloadAckPayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "preload_ack"));

    public PreloadAckPayload {
        if (missingResources < 0) missingResources = 0;
    }

    public static final PacketCodec<RegistryByteBuf, PreloadAckPayload> CODEC = PacketCodec.of((value, buf) -> {
        buf.writeVarLong(value.requestId);
        buf.writeBoolean(value.ready);
        buf.writeVarInt(value.missingResources);
    }, buf -> new PreloadAckPayload(buf.readVarLong(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
