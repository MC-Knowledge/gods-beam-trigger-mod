package com.mck.godsbeamtrigger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class GodCommand {
    private GodCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("god")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .executes(context -> openGodScreen(context.getSource()))
                        .then(CommandManager.literal("list")
                                .executes(context -> listPowerUsers(context.getSource()))
                        )
                        .then(CommandManager.literal("strike")
                                .executes(context -> openStrikePasswordScreen(context.getSource()))
                        )
        );
    }

    private static int openGodScreen(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();

        GodPowerManager.grantGodGuiSession(player);

        ServerPlayNetworking.send(
                player,
                new GodScreenOpenPayload(
                        "",
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

        return 1;
    }

    private static int openStrikePasswordScreen(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        GodPowerManager.GodPowerState state = GodPowerManager.getState(player.getUuid());

        GodPowerManager.grantStrikeGuiSession(player);

        ServerPlayNetworking.send(
                player,
                new StrikeScreenOpenPayload(
                        player.getName().getString(),
                        state.strikeEnabled(),
                        state.strikeRed(),
                        state.strikeGreen(),
                        state.strikeBlue()
                )
        );

        return 1;
    }

    private static int listPowerUsers(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        List<String> users = BeamAccessLogic.getAccessUsers();

        if (users.isEmpty()) {
            player.sendMessage(Text.literal("No players currently have access to the powers."), false);
            return 1;
        }

        StringBuilder message = new StringBuilder();
        message.append("Players with power access:");

        for (String user : users) {
            message.append("\n- ").append(user);
        }

        player.sendMessage(Text.literal(message.toString()), false);
        return users.size();
    }
}