package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OrbitalStrikeUsePayload() implements CustomPayload {
    public static final CustomPayload.Id<OrbitalStrikeUsePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "orbital_strike_use"));

    public static final PacketCodec<PacketByteBuf, OrbitalStrikeUsePayload> CODEC =
            PacketCodec.unit(new OrbitalStrikeUsePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}