package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GodTogglePayload(
        String targetName,
        boolean targetBeamAccess,
        boolean beamEnabled,
        boolean lightningEnabled,
        int beamRed,
        int beamGreen,
        int beamBlue
) implements CustomPayload {
    public static final CustomPayload.Id<GodTogglePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "god_toggle"));

    public static final PacketCodec<RegistryByteBuf, GodTogglePayload> CODEC =
            CustomPayload.codecOf(GodTogglePayload::write, GodTogglePayload::new);

    public GodTogglePayload {
        targetName = sanitizeName(targetName);
        beamRed = clampColor(beamRed);
        beamGreen = clampColor(beamGreen);
        beamBlue = clampColor(beamBlue);
    }

    private GodTogglePayload(RegistryByteBuf buf) {
        this(
                buf.readString(16),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.targetName);
        buf.writeBoolean(this.targetBeamAccess);
        buf.writeBoolean(this.beamEnabled);
        buf.writeBoolean(this.lightningEnabled);
        buf.writeInt(this.beamRed);
        buf.writeInt(this.beamGreen);
        buf.writeInt(this.beamBlue);
    }

    private static String sanitizeName(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        if (trimmed.length() > 16) {
            return trimmed.substring(0, 16);
        }

        return trimmed;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}