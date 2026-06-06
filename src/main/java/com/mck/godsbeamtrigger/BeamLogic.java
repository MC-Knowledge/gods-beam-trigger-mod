package com.mck.godsbeamtrigger;

import com.mck.godsbeamtrigger.mixin.DamageSourcesAccessor;
import com.mck.godsbeamtrigger.mixin.EntityAccessor;
import com.mck.godsbeamtrigger.mixin.LivingEntityAccessor;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BeamLogic {
    private static final String PLAYER_BEAM_FUNCTION = "function beam:fire";

    private static final RegistryKey<DamageType> BEAM_INCINERATE_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("s5utils", "beam_incinerate"));

    private static final double BEAM_RANGE = 8.0;
    private static final double BEAM_RADIUS = 1.15;

    private static final int DAMAGE_COOLDOWN_TICKS = 4;
    private static final float BEAM_DAMAGE = 1.0f;

    private BeamLogic() {
    }

    public static void handleUse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) return;

        ItemStack stack = player.getMainHandStack();
        if (!isValidNormal(stack)) return;

        igniteTntAlongBeam(player);
        damageTargets(player);
        runFunction(player);
        broadcastRender(player);
    }

    public static void handleSpecialUse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) return;

        igniteTntAlongBeam(player);
        damageTargets(player);
        runFunction(player);
        broadcastRender(player);
    }

    private static boolean isValidNormal(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        String id = item.toString();

        boolean validWeapon =
                item instanceof AxeItem ||
                item instanceof MaceItem ||
                id.contains("_sword");

        if (!validWeapon) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        var nbt = customData.copyNbt();
        return nbt.contains("BeamWand") && nbt.getBoolean("BeamWand").orElse(false);
    }

    private static void runFunction(ServerPlayerEntity player) {
        ServerCommandSource source = player.getCommandSource()
                .getServer()
                .getCommandSource()
                .withSilent();

        String name = player.getName().getString();
        String command = "execute as " + name + " at " + name + " run " + PLAYER_BEAM_FUNCTION;

        source.getServer().getCommandManager().parseAndExecute(source, command);
    }

    private static void broadcastRender(ServerPlayerEntity player) {
        GodPowerManager.GodPowerState state = GodPowerManager.getState(player.getUuid());

        BeamRenderPayload payload = new BeamRenderPayload(
                player.getUuid(),
                state.beamRed(),
                state.beamGreen(),
                state.beamBlue()
        );

        for (ServerPlayerEntity viewer : PlayerLookup.world(player.getCommandSource().getWorld())) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static void igniteTntAlongBeam(ServerPlayerEntity player) {
        ServerWorld world = player.getCommandSource().getWorld();

        Vec3d start = new Vec3d(player.getX(), player.getY() + 1.55, player.getZ());
        Vec3d direction = getForwardVector(player.getYaw(), player.getPitch()).normalize();
        Vec3d end = start.add(direction.multiply(BEAM_RANGE));

        double step = 0.20;
        Set<BlockPos> checkedPositions = new HashSet<>();

        for (double distance = 0.0; distance <= BEAM_RANGE; distance += step) {
            Vec3d point = start.add(direction.multiply(distance));
            BlockPos center = BlockPos.ofFloored(point.x, point.y, point.z);

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        BlockPos pos = center.add(ox, oy, oz);

                        if (!checkedPositions.add(pos)) {
                            continue;
                        }

                        BlockState state = world.getBlockState(pos);

                        if (!state.isOf(Blocks.TNT)) {
                            continue;
                        }

                        Vec3d blockCenter = Vec3d.ofCenter(pos);
                        double distanceSq = distanceSquaredToSegment(blockCenter, start, end);

                        if (distanceSq <= 0.75 * 0.75) {
                            boolean primed = TntBlock.primeTnt(world, pos);

                            if (primed) {
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void damageTargets(ServerPlayerEntity player) {
        if (player.age % DAMAGE_COOLDOWN_TICKS != 0) {
            return;
        }

        ServerWorld world = player.getCommandSource().getWorld();

        Vec3d start = new Vec3d(player.getX(), player.getY() + 1.55, player.getZ());
        Vec3d direction = getForwardVector(player.getYaw(), player.getPitch()).normalize();
        Vec3d end = start.add(direction.multiply(BEAM_RANGE));

        double minX = Math.min(start.x, end.x) - BEAM_RADIUS;
        double minY = Math.min(start.y, end.y) - BEAM_RADIUS;
        double minZ = Math.min(start.z, end.z) - BEAM_RADIUS;
        double maxX = Math.max(start.x, end.x) + BEAM_RADIUS;
        double maxY = Math.max(start.y, end.y) + BEAM_RADIUS;
        double maxZ = Math.max(start.z, end.z) + BEAM_RADIUS;

        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive() && entity != player
        );

        for (LivingEntity target : targets) {
            Vec3d targetCenter = new Vec3d(
                    target.getX(),
                    target.getY() + (target.getHeight() * 0.5),
                    target.getZ()
            );

            double distanceSq = distanceSquaredToSegment(targetCenter, start, end);

            if (distanceSq <= BEAM_RADIUS * BEAM_RADIUS) {
                damageSingleTarget(world, player, target);
            }
        }
    }

    private static void damageSingleTarget(ServerWorld world, ServerPlayerEntity attacker, LivingEntity target) {
        Vec3d velocityBeforeDamage = target.getVelocity();

        LivingEntityAccessor livingAccessor = (LivingEntityAccessor) target;
        EntityAccessor entityAccessor = (EntityAccessor) target;

        entityAccessor.s5utils$setTimeUntilRegen(0);
        livingAccessor.s5utils$setHurtTime(0);
        livingAccessor.s5utils$setMaxHurtTime(0);
        livingAccessor.s5utils$setLastDamageTaken(0.0f);

        DamageSource source = ((DamageSourcesAccessor) world.getDamageSources())
                .s5utils$create(BEAM_INCINERATE_DAMAGE_TYPE, attacker);

        BeamKnockbackContext.push();

        try {
            target.damage(world, source, BEAM_DAMAGE);
        } finally {
            BeamKnockbackContext.pop();
        }

        /*
         * Backup anti-knockback.
         * The mixin cancels takeKnockback during beam damage, and this resets
         * motion afterwards in case anything else still touched velocity.
         */
        target.setVelocity(velocityBeforeDamage);
    }

    private static double distanceSquaredToSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();

        if (lengthSq <= 0.000001) {
            return point.squaredDistanceTo(start);
        }

        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));

        Vec3d closest = start.add(segment.multiply(t));
        return point.squaredDistanceTo(closest);
    }

    private static Vec3d getForwardVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);

        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);

        return new Vec3d(x, y, z);
    }
}