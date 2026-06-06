package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GodTargetSelectPayload(String targetName) implements CustomPayload {
    public static final CustomPayload.Id<GodTargetSelectPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "god_target_select"));

    public static final PacketCodec<RegistryByteBuf, GodTargetSelectPayload> CODEC =
            CustomPayload.codecOf(GodTargetSelectPayload::write, GodTargetSelectPayload::new);

    public GodTargetSelectPayload {
        targetName = sanitize(targetName);
    }

    private GodTargetSelectPayload(RegistryByteBuf buf) {
        this(buf.readString(16));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.targetName);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        if (trimmed.length() > 16) {
            return trimmed.substring(0, 16);
        }

        return trimmed;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}