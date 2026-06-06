package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record OrbitalStrikeRenderPayload(
        BlockPos targetPos,
        int windupTicks,
        int strikeTicks,
        int red,
        int green,
        int blue
) implements CustomPayload {
    public static final CustomPayload.Id<OrbitalStrikeRenderPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "orbital_strike_render"));

    public static final PacketCodec<RegistryByteBuf, OrbitalStrikeRenderPayload> CODEC =
            CustomPayload.codecOf(OrbitalStrikeRenderPayload::write, OrbitalStrikeRenderPayload::new);

    public OrbitalStrikeRenderPayload {
        if (targetPos == null) {
            targetPos = BlockPos.ORIGIN;
        }

        windupTicks = clampTicks(windupTicks);
        strikeTicks = clampTicks(strikeTicks);
        red = clampColor(red);
        green = clampColor(green);
        blue = clampColor(blue);
    }

    private OrbitalStrikeRenderPayload(RegistryByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(this.targetPos);
        buf.writeInt(this.windupTicks);
        buf.writeInt(this.strikeTicks);
        buf.writeInt(this.red);
        buf.writeInt(this.green);
        buf.writeInt(this.blue);
    }

    private static int clampTicks(int value) {
        return Math.max(1, Math.min(200, value));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}