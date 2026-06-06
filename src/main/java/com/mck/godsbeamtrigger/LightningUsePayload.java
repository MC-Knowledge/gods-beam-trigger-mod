package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningUsePayload() implements CustomPayload {
    public static final CustomPayload.Id<LightningUsePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "lightning_use"));

    public static final PacketCodec<PacketByteBuf, LightningUsePayload> CODEC =
            PacketCodec.unit(new LightningUsePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}