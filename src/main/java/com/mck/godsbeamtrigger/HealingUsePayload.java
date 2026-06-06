package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingUsePayload() implements CustomPayload {
    public static final CustomPayload.Id<HealingUsePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "healing_use"));

    public static final PacketCodec<PacketByteBuf, HealingUsePayload> CODEC =
            PacketCodec.unit(new HealingUsePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}