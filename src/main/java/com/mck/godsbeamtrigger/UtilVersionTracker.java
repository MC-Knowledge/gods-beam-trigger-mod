package com.mck.godsbeamtrigger;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UtilVersionTracker {
    private static final String MOD_ID = "s5utils";
    private static final Map<UUID, String> CLIENT_VERSIONS = new ConcurrentHashMap<>();

    private UtilVersionTracker() {
    }

    public static String getOwnModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static void setClientVersion(ServerPlayerEntity player, String version) {
        if (player == null) {
            return;
        }

        String cleanedVersion = cleanVersion(version);
        CLIENT_VERSIONS.put(player.getUuid(), cleanedVersion);
    }

    public static Optional<String> getClientVersion(ServerPlayerEntity player) {
        if (player == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(CLIENT_VERSIONS.get(player.getUuid()));
    }

    public static void removeClientVersion(ServerPlayerEntity player) {
        if (player != null) {
            CLIENT_VERSIONS.remove(player.getUuid());
        }
    }

    private static String cleanVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "unknown";
        }

        String cleaned = version.trim();

        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }

        return cleaned;
    }
}