package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public final class BladeOfGodLogic {
    private static final RegistryKey<DamageType> BLADE_OF_GOD_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("s5utils", "blade_of_god"));

    private BladeOfGodLogic() {
    }

    public static void init() {
        AttackEntityCallback.EVENT.register(BladeOfGodLogic::onAttackEntity);
    }

    private static ActionResult onAttackEntity(
            PlayerEntity attacker,
            World world,
            Hand hand,
            Entity target,
            EntityHitResult hitResult
    ) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }

        if (!(attacker instanceof ServerPlayerEntity serverAttacker)) {
            return ActionResult.PASS;
        }

        if (!(target instanceof ServerPlayerEntity targetPlayer)) {
            return ActionResult.PASS;
        }

        if (!targetPlayer.isCreative()) {
            return ActionResult.PASS;
        }

        ItemStack stack = attacker.getStackInHand(hand);

        if (!isBladeOfGod(stack)) {
            return ActionResult.PASS;
        }

        DamageSource damageSource = createBladeOfGodDamageSource(serverWorld, serverAttacker);
        forceKillWithCustomDeathMessage(targetPlayer, damageSource);

        return ActionResult.SUCCESS_SERVER;
    }

    private static DamageSource createBladeOfGodDamageSource(ServerWorld world, ServerPlayerEntity attacker) {
        RegistryEntry<DamageType> damageType = world.getRegistryManager()
                .getOrThrow(RegistryKeys.DAMAGE_TYPE)
                .getOrThrow(BLADE_OF_GOD_DAMAGE_TYPE);

        return new DamageSource(damageType, attacker, attacker);
    }

    private static void forceKillWithCustomDeathMessage(ServerPlayerEntity target, DamageSource damageSource) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }

        float damageForTracker = Math.max(target.getHealth(), 1.0f);

        /*
         * This records our custom damage source so the death message uses:
         * death.attack.blade_of_god
         * instead of Minecraft's generic "<player> was killed".
         */
        target.getDamageTracker().onDamage(damageSource, damageForTracker);

        /*
         * Do not use target.kill(world) here.
         * kill(world) uses the generic kill damage source, which creates the boring
         * "<player> was killed" message.
         */
        target.setHealth(0.0f);
        target.onDeath(damageSource);
    }

    private static boolean isBladeOfGod(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (!stack.isOf(Items.DIAMOND_SWORD)) {
            return false;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);

        if (customData == null) {
            return false;
        }

        var nbt = customData.copyNbt();

        return nbt.contains("god")
                && nbt.getBoolean("BeamWand").orElse(false)
                && nbt.getBoolean("HealingCircle").orElse(false);
    }
}