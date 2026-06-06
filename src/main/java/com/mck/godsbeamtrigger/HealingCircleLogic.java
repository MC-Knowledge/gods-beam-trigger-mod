package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class HealingCircleLogic {
    private static final String FUNCTION = "function alchemy:healing_circle";

    private HealingCircleLogic() {}

    public static void handleUse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) return;

        ItemStack stack = player.getMainHandStack();
        if (!hasHealingCircleTag(stack)) return;

        runFunction(player);
        broadcastRender(player);
    }

    public static void handleSpecialUse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) return;

        runFunction(player);
        broadcastRender(player);
    }

    private static boolean hasHealingCircleTag(ItemStack stack) {
        if (stack.isEmpty()) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        var nbt = customData.copyNbt();
        return nbt.contains("HealingCircle") && nbt.getBoolean("HealingCircle").orElse(false);
    }

    private static void runFunction(ServerPlayerEntity player) {
        ServerCommandSource source = player.getCommandSource()
                .getServer()
                .getCommandSource()
                .withSilent();

        String name = player.getName().getString();
        String command = "execute as " + name + " at " + name + " run " + FUNCTION;

        source.getServer().getCommandManager().parseAndExecute(source, command);
    }

    private static void broadcastRender(ServerPlayerEntity player) {
        HealingRenderPayload payload = new HealingRenderPayload(player.getUuid());

        for (ServerPlayerEntity viewer : PlayerLookup.world(player.getCommandSource().getWorld())) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }
}