package com.mck.godsbeamtrigger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;

public final class UtilCheckCommand {
    private UtilCheckCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("utilcheck")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> checkPlayer(
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")
                                ))
                        )
        );
    }

    private static int checkPlayer(ServerCommandSource source, ServerPlayerEntity target) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();

        String serverVersion = UtilVersionTracker.getOwnModVersion();
        Optional<String> clientVersion = UtilVersionTracker.getClientVersion(target);
        String targetName = target.getName().getString();

        if (clientVersion.isEmpty()) {
            sender.sendMessage(
                    Text.literal(
                            targetName + " has not reported an s5utils client version. "
                                    + "They may be using an older mod jar, missing the mod, or not fully joined yet. "
                                    + "Server version: " + serverVersion
                    ),
                    false
            );

            return 0;
        }

        String reportedVersion = clientVersion.get();

        if (serverVersion.equals(reportedVersion)) {
            sender.sendMessage(
                    Text.literal(
                            targetName + " is using s5utils " + reportedVersion + " ✅"
                    ),
                    false
            );

            return 1;
        }

        sender.sendMessage(
                Text.literal(
                        targetName + " is using s5utils " + reportedVersion
                                + " but the server is using " + serverVersion + " ⚠"
                ),
                false
        );

        return 1;
    }
}