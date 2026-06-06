package com.mck.godsbeamtrigger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BeamAccessPayload(boolean allowed) implements CustomPayload {
    public static final CustomPayload.Id<BeamAccessPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "beam_access"));

    public static final PacketCodec<PacketByteBuf, BeamAccessPayload> CODEC =
            PacketCodec.of(
                    (BeamAccessPayload payload, PacketByteBuf buf) -> buf.writeBoolean(payload.allowed()),
                    (PacketByteBuf buf) -> new BeamAccessPayload(buf.readBoolean())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}