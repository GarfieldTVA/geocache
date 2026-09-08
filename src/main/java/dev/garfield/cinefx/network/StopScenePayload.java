package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Stops all active instances of a named scene on one client. */
public record StopScenePayload(String sceneId) implements CustomPayload {
    public static final Id<StopScenePayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "stop_scene"));
    public static final PacketCodec<RegistryByteBuf, StopScenePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, StopScenePayload::sceneId, StopScenePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
