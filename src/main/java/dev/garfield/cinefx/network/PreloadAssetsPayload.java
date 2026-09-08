package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server asks a client to warm a registered AssetBundle before an event phase starts. */
public record PreloadAssetsPayload(String bundleId, long requestId) implements CustomPayload {
    public static final Id<PreloadAssetsPayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "preload_assets"));
    private static final int MAX_STRING = 512;

    public static final PacketCodec<RegistryByteBuf, PreloadAssetsPayload> CODEC = PacketCodec.of((value, buf) -> {
        buf.writeString(value.bundleId, MAX_STRING);
        buf.writeVarLong(value.requestId);
    }, buf -> new PreloadAssetsPayload(buf.readString(MAX_STRING), buf.readVarLong()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
