package com.mck.godsbeamtrigger;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SeraphimMorphPayload(UUID playerUuid, int mode) implements CustomPayload {
    public static final int MODE_START = 0;
    public static final int MODE_MORPHED = 1;
    public static final int MODE_CLEAR = 2;

    public static final CustomPayload.Id<SeraphimMorphPayload> ID =
            new CustomPayload.Id<>(Identifier.of("s5utils", "seraphim_morph"));

    public static final PacketCodec<RegistryByteBuf, SeraphimMorphPayload> CODEC =
            CustomPayload.codecOf(SeraphimMorphPayload::write, SeraphimMorphPayload::new);

    public SeraphimMorphPayload {
        if (playerUuid == null) {
            playerUuid = new UUID(0L, 0L);
        }

        if (mode < MODE_START || mode > MODE_CLEAR) {
            mode = MODE_CLEAR;
        }
    }

    private SeraphimMorphPayload(RegistryByteBuf buf) {
        this(buf.readUuid(), buf.readInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(this.playerUuid);
        buf.writeInt(this.mode);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}