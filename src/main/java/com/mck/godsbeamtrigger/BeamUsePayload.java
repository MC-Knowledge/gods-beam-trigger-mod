package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BeamUsePayload(boolean special) implements CustomPayload {
    public static final CustomPayload.Id<BeamUsePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "beam_use"));

    public static final PacketCodec<PacketByteBuf, BeamUsePayload> CODEC =
            PacketCodec.of(
                    (BeamUsePayload payload, PacketByteBuf buf) -> buf.writeBoolean(payload.special()),
                    (PacketByteBuf buf) -> new BeamUsePayload(buf.readBoolean())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}