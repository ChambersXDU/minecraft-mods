package dev.aviation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlightControls(int entityId, int buttons) implements CustomPacketPayload {
    public static final int FORWARD = 1, BRAKE = 2, LEFT = 4, RIGHT = 8, CLIMB = 16, DESCEND = 32, FIRE = 64, EXIT = 128;
    public static final Type<FlightControls> TYPE = new Type<>(AviationMod.id("flight_controls"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightControls> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.entityId()); buffer.writeByte(value.buttons()); },
            buffer -> new FlightControls(buffer.readVarInt(), buffer.readUnsignedByte()));
    @Override public Type<FlightControls> type() { return TYPE; }
}
