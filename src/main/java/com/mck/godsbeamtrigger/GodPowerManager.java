package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GodPowerManager {
    private static final Map<UUID, GodPowerState> STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> GOD_GUI_SESSION_ALLOWED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STRIKE_GUI_SESSION_ALLOWED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, UUID> GOD_GUI_TARGETS = new ConcurrentHashMap<>();

    private GodPowerManager() {
    }

    public static void grantGodGuiSession(ServerPlayerEntity player) {
        if (player != null) {
            GOD_GUI_SESSION_ALLOWED.add(player.getUuid());
            GOD_GUI_TARGETS.remove(player.getUuid());
        }
    }

    public static void grantStrikeGuiSession(ServerPlayerEntity player) {
        if (player != null) {
            STRIKE_GUI_SESSION_ALLOWED.add(player.getUuid());
        }
    }

    public static void revokeGodGuiSession(ServerPlayerEntity player) {
        if (player != null) {
            GOD_GUI_SESSION_ALLOWED.remove(player.getUuid());
            STRIKE_GUI_SESSION_ALLOWED.remove(player.getUuid());
            GOD_GUI_TARGETS.remove(player.getUuid());
        }
    }

    public static boolean canUseGodGui(ServerPlayerEntity player) {
        return player != null && GOD_GUI_SESSION_ALLOWED.contains(player.getUuid());
    }

    public static boolean canUseStrikeGui(ServerPlayerEntity player) {
        return player != null && STRIKE_GUI_SESSION_ALLOWED.contains(player.getUuid());
    }

    public static void selectTarget(ServerPlayerEntity operator, ServerPlayerEntity target) {
        if (operator == null || target == null || !canUseGodGui(operator)) {
            return;
        }

        GOD_GUI_TARGETS.put(operator.getUuid(), target.getUuid());
        sendTargetStateToOperator(operator, target);
    }

    public static void sendTargetMissing(ServerPlayerEntity operator, String attemptedName) {
        if (operator == null || !canUseGodGui(operator)) {
            return;
        }

        ServerPlayNetworking.send(
                operator,
                new GodStatePayload(
                        attemptedName,
                        false,
                        false,
                        false,
                        false,
                        80,
                        230,
                        255,
                        BeamAccessLogic.getAccessUsersText()
                )
        );
    }

    public static void setSelectedTargetState(
            ServerPlayerEntity operator,
            String requestedTargetName,
            boolean targetBeamAccess,
            boolean beamEnabled,
            boolean lightningEnabled,
            int beamRed,
            int beamGreen,
            int beamBlue
    ) {
        if (operator == null || !canUseGodGui(operator)) {
            return;
        }

        UUID targetUuid = GOD_GUI_TARGETS.get(operator.getUuid());

        if (targetUuid == null) {
            sendTargetMissing(operator, requestedTargetName);
            return;
        }

        MinecraftServer server = operator.getCommandSource().getServer();
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);

        if (target == null) {
            GOD_GUI_TARGETS.remove(operator.getUuid());
            sendTargetMissing(operator, requestedTargetName);
            return;
        }

        String realTargetName = target.getName().getString();

        if (!realTargetName.equalsIgnoreCase(requestedTargetName)) {
            sendTargetStateToOperator(operator, target);
            return;
        }

        BeamAccessLogic.setAccess(realTargetName, targetBeamAccess);
        BeamAccessLogic.sync(target);

        GodPowerState oldState = getState(target.getUuid());

        STATES.put(
                target.getUuid(),
                new GodPowerState(
                        beamEnabled,
                        lightningEnabled,
                        clampColor(beamRed),
                        clampColor(beamGreen),
                        clampColor(beamBlue),
                        oldState.strikeEnabled(),
                        oldState.strikeRed(),
                        oldState.strikeGreen(),
                        oldState.strikeBlue()
                )
        );

        sync(target);
        sendTargetStateToOperator(operator, target);
    }

    public static void setStrikeSettings(
            ServerPlayerEntity operator,
            String targetName,
            boolean strikeEnabled,
            int strikeRed,
            int strikeGreen,
            int strikeBlue
    ) {
        if (operator == null || !canUseStrikeGui(operator)) {
            return;
        }

        ServerPlayerEntity target = findOnlinePlayer(operator, targetName);

        if (target == null) {
            return;
        }

        GodPowerState oldState = getState(target.getUuid());

        STATES.put(
                target.getUuid(),
                new GodPowerState(
                        oldState.beamEnabled(),
                        oldState.lightningEnabled(),
                        oldState.beamRed(),
                        oldState.beamGreen(),
                        oldState.beamBlue(),
                        strikeEnabled,
                        clampColor(strikeRed),
                        clampColor(strikeGreen),
                        clampColor(strikeBlue)
                )
        );

        sync(target);
    }

    private static ServerPlayerEntity findOnlinePlayer(ServerPlayerEntity operator, String targetName) {
        if (operator == null) {
            return null;
        }

        if (targetName == null || targetName.trim().isEmpty()) {
            return operator;
        }

        String cleanedName = targetName.trim();
        MinecraftServer server = operator.getCommandSource().getServer();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(cleanedName)) {
                return player;
            }
        }

        return null;
    }

    public static boolean isBeamEnabled(ServerPlayerEntity player) {
        return getState(player.getUuid()).beamEnabled();
    }

    public static boolean isLightningEnabled(ServerPlayerEntity player) {
        return getState(player.getUuid()).lightningEnabled();
    }

    public static boolean isStrikeEnabled(ServerPlayerEntity player) {
        return getState(player.getUuid()).strikeEnabled();
    }

    public static int getBeamRed(ServerPlayerEntity player) {
        return getState(player.getUuid()).beamRed();
    }

    public static int getBeamGreen(ServerPlayerEntity player) {
        return getState(player.getUuid()).beamGreen();
    }

    public static int getBeamBlue(ServerPlayerEntity player) {
        return getState(player.getUuid()).beamBlue();
    }

    public static int getStrikeRed(ServerPlayerEntity player) {
        return getState(player.getUuid()).strikeRed();
    }

    public static int getStrikeGreen(ServerPlayerEntity player) {
        return getState(player.getUuid()).strikeGreen();
    }

    public static int getStrikeBlue(ServerPlayerEntity player) {
        return getState(player.getUuid()).strikeBlue();
    }

    public static void sync(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        GodPowerState state = getState(player.getUuid());
        String playerName = player.getName().getString();

        ServerPlayNetworking.send(
                player,
                new GodStatePayload(
                        playerName,
                        true,
                        BeamAccessLogic.hasAccess(player),
                        state.beamEnabled(),
                        state.lightningEnabled(),
                        state.beamRed(),
                        state.beamGreen(),
                        state.beamBlue(),
                        BeamAccessLogic.getAccessUsersText()
                )
        );
    }

    private static void sendTargetStateToOperator(ServerPlayerEntity operator, ServerPlayerEntity target) {
        GodPowerState state = getState(target.getUuid());
        String targetName = target.getName().getString();

        ServerPlayNetworking.send(
                operator,
                new GodStatePayload(
                        targetName,
                        true,
                        BeamAccessLogic.hasAccess(target),
                        state.beamEnabled(),
                        state.lightningEnabled(),
                        state.beamRed(),
                        state.beamGreen(),
                        state.beamBlue(),
                        BeamAccessLogic.getAccessUsersText()
                )
        );
    }

    public static GodPowerState getState(UUID uuid) {
        return STATES.computeIfAbsent(
                uuid,
                ignored -> new GodPowerState(
                        true,
                        true,
                        80,
                        230,
                        255,
                        false,
                        80,
                        230,
                        255
                )
        );
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public record GodPowerState(
            boolean beamEnabled,
            boolean lightningEnabled,
            int beamRed,
            int beamGreen,
            int beamBlue,
            boolean strikeEnabled,
            int strikeRed,
            int strikeGreen,
            int strikeBlue
    ) {
    }
}