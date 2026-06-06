package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GodStatePayload(
        String targetName,
        boolean targetSelected,
        boolean targetBeamAccess,
        boolean beamEnabled,
        boolean lightningEnabled,
        int beamRed,
        int beamGreen,
        int beamBlue,
        String beamAccessUsers
) implements CustomPayload {
    public static final CustomPayload.Id<GodStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "god_state"));

    public static final PacketCodec<RegistryByteBuf, GodStatePayload> CODEC =
            CustomPayload.codecOf(GodStatePayload::write, GodStatePayload::new);

    public GodStatePayload {
        targetName = sanitizeName(targetName);
        beamAccessUsers = sanitizeLongText(beamAccessUsers);
        beamRed = clampColor(beamRed);
        beamGreen = clampColor(beamGreen);
        beamBlue = clampColor(beamBlue);
    }

    private GodStatePayload(RegistryByteBuf buf) {
        this(
                buf.readString(16),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readString(4096)
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.targetName);
        buf.writeBoolean(this.targetSelected);
        buf.writeBoolean(this.targetBeamAccess);
        buf.writeBoolean(this.beamEnabled);
        buf.writeBoolean(this.lightningEnabled);
        buf.writeInt(this.beamRed);
        buf.writeInt(this.beamGreen);
        buf.writeInt(this.beamBlue);
        buf.writeString(this.beamAccessUsers);
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

    private static String sanitizeLongText(String value) {
        if (value == null) {
            return "";
        }

        if (value.length() > 4096) {
            return value.substring(0, 4096);
        }

        return value;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}