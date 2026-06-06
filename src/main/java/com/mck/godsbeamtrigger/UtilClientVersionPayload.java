package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UtilClientVersionPayload(String version) implements CustomPayload {
    public static final CustomPayload.Id<UtilClientVersionPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "client_version"));

    public static final PacketCodec<RegistryByteBuf, UtilClientVersionPayload> CODEC =
            CustomPayload.codecOf(UtilClientVersionPayload::write, UtilClientVersionPayload::new);

    public UtilClientVersionPayload {
        if (version == null || version.trim().isEmpty()) {
            version = "unknown";
        } else {
            version = version.trim();
        }

        if (version.length() > 64) {
            version = version.substring(0, 64);
        }
    }

    private UtilClientVersionPayload(RegistryByteBuf buf) {
        this(buf.readString(64));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.version);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}