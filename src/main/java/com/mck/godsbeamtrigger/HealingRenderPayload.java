package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record HealingRenderPayload(UUID playerUuid) implements CustomPayload {
    public static final CustomPayload.Id<HealingRenderPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "healing_render"));

    public static final PacketCodec<PacketByteBuf, HealingRenderPayload> CODEC =
            PacketCodec.of(
                    (HealingRenderPayload payload, PacketByteBuf buf) -> buf.writeUuid(payload.playerUuid()),
                    (PacketByteBuf buf) -> new HealingRenderPayload(buf.readUuid())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}