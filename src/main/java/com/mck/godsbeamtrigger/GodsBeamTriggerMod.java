package com.mck.godsbeamtrigger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class GodsBeamTriggerMod implements ModInitializer {
    @Override
    public void onInitialize() {
        S5Entities.init();

        BeamAccessLogic.init();
        BladeOfGodLogic.init();

        PayloadTypeRegistry.playC2S().register(BeamUsePayload.ID, BeamUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HealingUsePayload.ID, HealingUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LightningUsePayload.ID, LightningUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OrbitalStrikeUsePayload.ID, OrbitalStrikeUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GodTogglePayload.ID, GodTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GodTargetSelectPayload.ID, GodTargetSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StrikeColorPayload.ID, StrikeColorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UtilClientVersionPayload.ID, UtilClientVersionPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(HealingRenderPayload.ID, HealingRenderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BeamRenderPayload.ID, BeamRenderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BeamAccessPayload.ID, BeamAccessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GodStatePayload.ID, GodStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GodScreenOpenPayload.ID, GodScreenOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrikeScreenOpenPayload.ID, StrikeScreenOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OrbitalStrikeRenderPayload.ID, OrbitalStrikeRenderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SeraphimMorphPayload.ID, SeraphimMorphPayload.CODEC);

        GodCommand.register();
        UtilCheckCommand.register();
        OrbitalStrikeLogic.init();
        SeraphimMorphLogic.init();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            BeamAccessLogic.sync(handler.player);
            GodPowerManager.revokeGodGuiSession(handler.player);
            GodPowerManager.sync(handler.player);
            SeraphimMorphLogic.syncTo(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GodPowerManager.revokeGodGuiSession(handler.player);
            UtilVersionTracker.removeClientVersion(handler.player);
        });

        ServerPlayNetworking.registerGlobalReceiver(UtilClientVersionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                UtilVersionTracker.setClientVersion(context.player(), payload.version());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BeamUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();

                if (!GodPowerManager.isBeamEnabled(player)) {
                    return;
                }

                if (payload.special()) {
                    if (BeamAccessLogic.hasAccess(player)) {
                        BeamLogic.handleSpecialUse(player);
                        HealingCircleLogic.handleSpecialUse(player);
                    }
                } else {
                    BeamLogic.handleUse(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HealingUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> HealingCircleLogic.handleUse(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(LightningUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();

                if (!GodPowerManager.isLightningEnabled(player)) {
                    return;
                }

                if (BeamAccessLogic.hasAccess(player)) {
                    LightningLogic.handleUse(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OrbitalStrikeUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();

                if (!GodPowerManager.isStrikeEnabled(player)) {
                    return;
                }

                if (BeamAccessLogic.hasAccess(player)) {
                    OrbitalStrikeLogic.startStrike(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(StrikeColorPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();

                if (!GodPowerManager.canUseStrikeGui(player)) {
                    return;
                }

                GodPowerManager.setStrikeSettings(
                        player,
                        payload.targetName(),
                        payload.enabled(),
                        payload.red(),
                        payload.green(),
                        payload.blue()
                );
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GodTargetSelectPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity operator = context.player();

                if (!GodPowerManager.canUseGodGui(operator)) {
                    return;
                }

                ServerPlayerEntity target = findOnlinePlayer(context.server(), payload.targetName());

                if (target == null) {
                    GodPowerManager.sendTargetMissing(operator, payload.targetName());
                    return;
                }

                GodPowerManager.selectTarget(operator, target);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GodTogglePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity operator = context.player();

                if (!GodPowerManager.canUseGodGui(operator)) {
                    return;
                }

                GodPowerManager.setSelectedTargetState(
                        operator,
                        payload.targetName(),
                        payload.targetBeamAccess(),
                        payload.beamEnabled(),
                        payload.lightningEnabled(),
                        payload.beamRed(),
                        payload.beamGreen(),
                        payload.beamBlue()
                );
            });
        });
    }

    private static ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String name) {
        if (server == null || name == null) {
            return null;
        }

        String cleanedName = name.trim();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(cleanedName)) {
                return player;
            }
        }

        return null;
    }
}