package com.mck.godsbeamtrigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BeamAccessLogic {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("s5utils")
            .resolve("beam_access.json");

    private static final Map<String, String> ACCESS_USERS = new LinkedHashMap<>();

    private BeamAccessLogic() {
    }

    public static void init() {
        load();
    }

    public static boolean hasAccess(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }

        return hasAccess(player.getName().getString());
    }

    public static boolean hasAccess(String username) {
        String cleaned = sanitizeName(username);

        if (cleaned.isEmpty()) {
            return false;
        }

        return ACCESS_USERS.containsKey(toKey(cleaned));
    }

    public static void setAccess(String username, boolean allowed) {
        String cleaned = sanitizeName(username);

        if (cleaned.isEmpty()) {
            return;
        }

        String key = toKey(cleaned);

        if (allowed) {
            ACCESS_USERS.put(key, cleaned);
        } else {
            ACCESS_USERS.remove(key);
        }

        save();
    }

    public static List<String> getAccessUsers() {
        List<String> users = new ArrayList<>(ACCESS_USERS.values());
        users.sort(Comparator.comparing(String::toLowerCase));
        return users;
    }

    public static String getAccessUsersText() {
        return String.join("\n", getAccessUsers());
    }

    public static void sync(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        ServerPlayNetworking.send(player, new BeamAccessPayload(hasAccess(player)));
    }

    private static void load() {
        ACCESS_USERS.clear();

        try {
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                AccessConfig config = GSON.fromJson(reader, AccessConfig.class);

                if (config != null && config.users != null) {
                    for (String user : config.users) {
                        String cleaned = sanitizeName(user);

                        if (!cleaned.isEmpty()) {
                            ACCESS_USERS.put(toKey(cleaned), cleaned);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            ACCESS_USERS.clear();
            save();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            AccessConfig config = new AccessConfig();
            config.users = getAccessUsers();

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception ignored) {
        }
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

    private static String toKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static final class AccessConfig {
        private List<String> users = new ArrayList<>();
    }
}