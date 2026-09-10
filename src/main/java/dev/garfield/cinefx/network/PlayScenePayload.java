package dev.garfield.cinefx.network;

import dev.garfield.cinefx.CineFx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small S2C command. The scene itself is never streamed every tick: clients already own its definition
 * and receive only id + anchor + synchronized start + deterministic seed + tiny string variables.
 */
public record PlayScenePayload(
        String sceneId,
        double x,
        double y,
        double z,
        long startGameTime,
        long seed,
        Map<String, String> variables
) implements CustomPayload {
    public static final Id<PlayScenePayload> ID = new Id<>(Identifier.of(CineFx.MOD_ID, "play_scene"));
    private static final int MAX_VARIABLES = 64;
    private static final int MAX_STRING = 512;

    public PlayScenePayload {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        if (variables.size() > MAX_VARIABLES) throw new IllegalArgumentException("Too many CineFX scene variables");
    }

    public static final PacketCodec<RegistryByteBuf, PlayScenePayload> CODEC = PacketCodec.of((value, buf) -> {
        buf.writeString(value.sceneId, MAX_STRING);
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
        buf.writeVarLong(value.startGameTime);
        buf.writeVarLong(value.seed);
        buf.writeVarInt(value.variables.size());
        for (Map.Entry<String, String> entry : value.variables.entrySet()) {
            buf.writeString(entry.getKey(), MAX_STRING);
            buf.writeString(entry.getValue(), MAX_STRING);
        }
    }, buf -> {
        String sceneId = buf.readString(MAX_STRING);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        long start = buf.readVarLong();
        long seed = buf.readVarLong();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_VARIABLES) throw new IllegalArgumentException("Invalid CineFX variable count: " + size);
        LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            variables.put(buf.readString(MAX_STRING), buf.readString(MAX_STRING));
        }
        return new PlayScenePayload(sceneId, x, y, z, start, seed, variables);
    });

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
