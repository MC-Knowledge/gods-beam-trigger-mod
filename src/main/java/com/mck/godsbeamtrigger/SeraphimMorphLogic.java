package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SeraphimMorphLogic {
    private static final String TARGET_PLAYER_NAME = "MCKnowledge";

    private static final String MORPH_PHRASE =
            "You all shall kneel before your lord, and accept his true form.";

    private static final String UNMORPH_PHRASE =
            "Thank you.";

    private static final int TRANSFORM_DELAY_TICKS = 60;

    private static final Map<UUID, PendingMorph> PENDING_MORPHS = new HashMap<>();
    private static final Set<UUID> MORPHED_PLAYERS = new HashSet<>();
    private static final Map<UUID, SeraphimMorphEntity> MORPH_ENTITIES = new HashMap<>();
    private static final Map<UUID, RegistryKey<World>> MORPH_ENTITY_DIMENSIONS = new HashMap<>();

    private SeraphimMorphLogic() {
    }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            handleChatMessage(message, sender);
        });

        ServerTickEvents.END_SERVER_TICK.register(SeraphimMorphLogic::serverTick);
    }

    public static void syncTo(ServerPlayerEntity viewer) {
        if (viewer == null) {
            return;
        }

        for (UUID morphedUuid : MORPHED_PLAYERS) {
            ServerPlayNetworking.send(
                    viewer,
                    new SeraphimMorphPayload(morphedUuid, SeraphimMorphPayload.MODE_MORPHED)
            );
        }

        for (PendingMorph pendingMorph : PENDING_MORPHS.values()) {
            ServerPlayNetworking.send(
                    viewer,
                    new SeraphimMorphPayload(pendingMorph.playerUuid(), SeraphimMorphPayload.MODE_START)
            );
        }
    }

    private static void handleChatMessage(SignedMessage message, ServerPlayerEntity sender) {
        if (message == null || sender == null) {
            return;
        }

        if (!sender.getName().getString().equalsIgnoreCase(TARGET_PLAYER_NAME)) {
            return;
        }

        String content = message.getContent().getString();

        if (MORPH_PHRASE.equals(content)) {
            startMorphSequence(sender);
            return;
        }

        if (UNMORPH_PHRASE.equals(content)) {
            clearMorph(sender);
        }
    }

    private static void startMorphSequence(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        MinecraftServer server = player.getCommandSource().getServer();

        removeMorphEntity(playerUuid);

        PENDING_MORPHS.put(playerUuid, new PendingMorph(playerUuid, 0));
        MORPHED_PLAYERS.remove(playerUuid);

        broadcast(
                server,
                new SeraphimMorphPayload(playerUuid, SeraphimMorphPayload.MODE_START)
        );
    }

    private static void clearMorph(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        MinecraftServer server = player.getCommandSource().getServer();

        PENDING_MORPHS.remove(playerUuid);
        MORPHED_PLAYERS.remove(playerUuid);
        removeMorphEntity(playerUuid);

        broadcast(
                server,
                new SeraphimMorphPayload(playerUuid, SeraphimMorphPayload.MODE_CLEAR)
        );
    }

    private static void serverTick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        tickPendingMorphs(server);
        tickMorphedEntities(server);
    }

    private static void tickPendingMorphs(MinecraftServer server) {
        if (PENDING_MORPHS.isEmpty()) {
            return;
        }

        Set<UUID> completedMorphs = new HashSet<>();
        Map<UUID, PendingMorph> updatedPendingMorphs = new HashMap<>();

        for (PendingMorph pendingMorph : PENDING_MORPHS.values()) {
            int newAge = pendingMorph.ageTicks() + 1;

            if (newAge >= TRANSFORM_DELAY_TICKS) {
                completedMorphs.add(pendingMorph.playerUuid());
            } else {
                updatedPendingMorphs.put(
                        pendingMorph.playerUuid(),
                        new PendingMorph(pendingMorph.playerUuid(), newAge)
                );
            }
        }

        PENDING_MORPHS.clear();
        PENDING_MORPHS.putAll(updatedPendingMorphs);

        for (UUID completedUuid : completedMorphs) {
            MORPHED_PLAYERS.add(completedUuid);
            spawnOrMoveMorphEntity(server, completedUuid);

            broadcast(
                    server,
                    new SeraphimMorphPayload(completedUuid, SeraphimMorphPayload.MODE_MORPHED)
            );
        }
    }

    private static void tickMorphedEntities(MinecraftServer server) {
        for (UUID playerUuid : new HashSet<>(MORPHED_PLAYERS)) {
            spawnOrMoveMorphEntity(server, playerUuid);
        }
    }

    private static void spawnOrMoveMorphEntity(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) {
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);

        if (player == null || player.isRemoved()) {
            removeMorphEntity(playerUuid);
            return;
        }

        ServerWorld world = player.getCommandSource().getWorld();
        RegistryKey<World> playerDimension = world.getRegistryKey();

        SeraphimMorphEntity entity = MORPH_ENTITIES.get(playerUuid);
        RegistryKey<World> entityDimension = MORPH_ENTITY_DIMENSIONS.get(playerUuid);

        boolean needsNewEntity =
                entity == null
                        || entity.isRemoved()
                        || entityDimension == null
                        || !entityDimension.equals(playerDimension);

        if (needsNewEntity) {
            removeMorphEntity(playerUuid);

            entity = new SeraphimMorphEntity(S5Entities.SERAPHIM_MORPH, world);
            entity.refreshPositionAndAngles(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYaw(),
                    player.getPitch()
            );
            entity.setNoGravity(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);

            world.spawnEntity(entity);

            MORPH_ENTITIES.put(playerUuid, entity);
            MORPH_ENTITY_DIMENSIONS.put(playerUuid, playerDimension);
        }

        entity.setPosition(player.getX(), player.getY(), player.getZ());
        entity.setYaw(player.getYaw());
        entity.setPitch(player.getPitch());
        entity.setVelocity(player.getVelocity());
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
    }

    private static void removeMorphEntity(UUID playerUuid) {
        SeraphimMorphEntity oldEntity = MORPH_ENTITIES.remove(playerUuid);
        MORPH_ENTITY_DIMENSIONS.remove(playerUuid);

        if (oldEntity != null && !oldEntity.isRemoved()) {
            oldEntity.discard();
        }
    }

    private static void broadcast(MinecraftServer server, SeraphimMorphPayload payload) {
        if (server == null || payload == null) {
            return;
        }

        for (ServerPlayerEntity viewer : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private record PendingMorph(UUID playerUuid, int ageTicks) {
    }
}