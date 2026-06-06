package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record BeamRenderPayload(UUID playerUuid, int red, int green, int blue) implements CustomPayload {
    public static final CustomPayload.Id<BeamRenderPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "beam_render"));

    public static final PacketCodec<RegistryByteBuf, BeamRenderPayload> CODEC =
            CustomPayload.codecOf(BeamRenderPayload::write, BeamRenderPayload::new);

    public BeamRenderPayload {
        red = clampColor(red);
        green = clampColor(green);
        blue = clampColor(blue);
    }

    private BeamRenderPayload(RegistryByteBuf buf) {
        this(
                new UUID(buf.readLong(), buf.readLong()),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeLong(this.playerUuid.getMostSignificantBits());
        buf.writeLong(this.playerUuid.getLeastSignificantBits());
        buf.writeInt(this.red);
        buf.writeInt(this.green);
        buf.writeInt(this.blue);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}