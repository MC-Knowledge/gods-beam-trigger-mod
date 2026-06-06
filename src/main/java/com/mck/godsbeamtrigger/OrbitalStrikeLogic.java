package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrbitalStrikeLogic {
    private static final RegistryKey<DamageType> ORBITAL_STRIKE_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("s5utils", "orbital_strike"));

    private static final SoundEvent ORBITAL_STRIKE_EXPLODE_SOUND =
            SoundEvent.of(Identifier.of("s5utils", "orbital_strike_explode"));

    private static final double TARGET_RANGE = 300.0;

    /*
     * Bigger kill cylinder to match the larger visual strike.
     * The owner is still excluded by UUID below.
     */
    private static final double DAMAGE_RADIUS = 14.0;
    private static final double DAMAGE_DOWN = 12.0;
    private static final double DAMAGE_HEIGHT = 96.0;

    /*
     * Bigger crater to match the fired beam.
     * Renderer outer beam impact starts at roughly 8 blocks wide, so this matches it.
     */
    private static final double CRATER_RADIUS = 8.0;
    private static final double CRATER_DEPTH = 4.75;

    private static final int WINDUP_TICKS = 45;
    private static final int STRIKE_TICKS = 18;
    private static final int TOTAL_TICKS = WINDUP_TICKS + STRIKE_TICKS + 20;

    private static final List<StrikeState> ACTIVE_STRIKES = new ArrayList<>();

    private OrbitalStrikeLogic() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(OrbitalStrikeLogic::serverTick);
    }

    public static void startStrike(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) {
            return;
        }

        ServerWorld world = player.getCommandSource().getWorld();
        BlockPos targetPos = findTargetPos(player, world);

        ACTIVE_STRIKES.add(new StrikeState(
                player.getUuid(),
                world.getRegistryKey(),
                targetPos,
                0,
                false
        ));

        OrbitalStrikeRenderPayload payload = new OrbitalStrikeRenderPayload(
                targetPos,
                WINDUP_TICKS,
                STRIKE_TICKS,
                GodPowerManager.getStrikeRed(player),
                GodPowerManager.getStrikeGreen(player),
                GodPowerManager.getStrikeBlue(player)
        );

        for (ServerPlayerEntity viewer : PlayerLookup.around(world, Vec3d.ofCenter(targetPos), 320.0)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static void serverTick(MinecraftServer server) {
        for (int index = ACTIVE_STRIKES.size() - 1; index >= 0; index--) {
            StrikeState strike = ACTIVE_STRIKES.get(index);
            int nextAge = strike.age() + 1;

            if (!strike.hasHit() && nextAge >= WINDUP_TICKS) {
                hitStrike(server, strike);

                ACTIVE_STRIKES.set(index, new StrikeState(
                        strike.ownerUuid(),
                        strike.dimension(),
                        strike.targetPos(),
                        nextAge,
                        true
                ));

                continue;
            }

            if (nextAge >= TOTAL_TICKS) {
                ACTIVE_STRIKES.remove(index);
                continue;
            }

            ACTIVE_STRIKES.set(index, new StrikeState(
                    strike.ownerUuid(),
                    strike.dimension(),
                    strike.targetPos(),
                    nextAge,
                    strike.hasHit()
            ));
        }
    }

    private static BlockPos findTargetPos(ServerPlayerEntity player, ServerWorld world) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d direction = getForwardVector(player.getYaw(), player.getPitch()).normalize();
        Vec3d end = start.add(direction.multiply(TARGET_RANGE));

        HitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }

        return BlockPos.ofFloored(end);
    }

    private static void hitStrike(MinecraftServer server, StrikeState strike) {
        ServerWorld world = server.getWorld(strike.dimension());

        if (world == null) {
            return;
        }

        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(strike.ownerUuid());
        Vec3d center = Vec3d.ofCenter(strike.targetPos());

        playStrikeSound(world, center);
        killTargets(world, center, strike.ownerUuid(), owner);
        breakCraterBlocks(world, center);
    }

    private static void playStrikeSound(ServerWorld world, Vec3d center) {
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                ORBITAL_STRIKE_EXPLODE_SOUND,
                SoundCategory.PLAYERS,
                7.0f,
                1.0f
        );
    }

    private static void killTargets(ServerWorld world, Vec3d center, UUID ownerUuid, ServerPlayerEntity owner) {
        Box box = new Box(
                center.x - DAMAGE_RADIUS,
                center.y - DAMAGE_DOWN,
                center.z - DAMAGE_RADIUS,
                center.x + DAMAGE_RADIUS,
                center.y + DAMAGE_HEIGHT,
                center.z + DAMAGE_RADIUS
        );

        DamageSource damageSource = createOrbitalStrikeDamageSource(world, owner);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive() && !entity.isSpectator()
        );

        for (LivingEntity target : targets) {
            if (target.getUuid().equals(ownerUuid)) {
                continue;
            }

            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;

            if ((dx * dx) + (dz * dz) > DAMAGE_RADIUS * DAMAGE_RADIUS) {
                continue;
            }

            forceKillWithCustomDeathMessage(target, damageSource);
        }
    }

    private static DamageSource createOrbitalStrikeDamageSource(ServerWorld world, ServerPlayerEntity owner) {
        RegistryEntry<DamageType> damageType = world.getRegistryManager()
                .getOrThrow(RegistryKeys.DAMAGE_TYPE)
                .getOrThrow(ORBITAL_STRIKE_DAMAGE_TYPE);

        if (owner == null) {
            return new DamageSource(damageType);
        }

        return new DamageSource(damageType, owner, owner);
    }

    private static void forceKillWithCustomDeathMessage(LivingEntity target, DamageSource damageSource) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }

        float damageForTracker = Math.max(target.getHealth(), 1.0f);

        target.getDamageTracker().onDamage(damageSource, damageForTracker);
        target.setHealth(0.0f);
        target.onDeath(damageSource);
    }

    private static void breakCraterBlocks(ServerWorld world, Vec3d center) {
        BlockPos min = BlockPos.ofFloored(
                center.x - CRATER_RADIUS - 1.0,
                center.y - CRATER_DEPTH - 1.0,
                center.z - CRATER_RADIUS - 1.0
        );

        BlockPos max = BlockPos.ofFloored(
                center.x + CRATER_RADIUS + 1.0,
                center.y + 0.25,
                center.z + CRATER_RADIUS + 1.0
        );

        for (BlockPos mutablePos : BlockPos.iterate(min, max)) {
            BlockPos pos = mutablePos.toImmutable();

            double dx = (pos.getX() + 0.5) - center.x;
            double dz = (pos.getZ() + 0.5) - center.z;
            double horizontalDistance = Math.sqrt((dx * dx) + (dz * dz));

            if (horizontalDistance > CRATER_RADIUS + 0.75) {
                continue;
            }

            /*
             * 0.0 = impact layer
             * positive = below impact
             * negative = above impact
             */
            double blockRelativeY = center.y - (pos.getY() + 0.5);

            if (blockRelativeY < -0.05 || blockRelativeY > CRATER_DEPTH + 1.0) {
                continue;
            }

            double distanceRatio = horizontalDistance / CRATER_RADIUS;
            double baseDepth = CRATER_DEPTH * (1.0 - Math.pow(distanceRatio, 1.85));

            double roughness = blockNoise(pos) * 1.05;
            double allowedDepth = baseDepth + roughness;

            /*
             * Softer edge so it is not just a perfect cylinder.
             */
            if (horizontalDistance > CRATER_RADIUS * 0.82) {
                allowedDepth -= 0.75;
            }

            if (allowedDepth < 0.0) {
                continue;
            }

            if (blockRelativeY > allowedDepth) {
                continue;
            }

            /*
             * Leave some uneven blocks in the deeper centre so the crater does not look
             * like a clean drilled hole.
             */
            if (blockRelativeY > 1.8 && horizontalDistance < 2.5) {
                double preserveNoise = blockNoise(pos.add(17, 11, 23));

                if (preserveNoise > 0.45) {
                    continue;
                }
            }

            BlockState state = world.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            if (state.getHardness(world, pos) < 0.0f) {
                continue;
            }

            world.breakBlock(pos, false);
        }
    }

    private static double blockNoise(BlockPos pos) {
        long x = pos.getX();
        long y = pos.getY();
        long z = pos.getZ();

        long hash = x * 73428767L ^ y * 912931L ^ z * 4382891L;
        hash ^= (hash >> 13);
        hash *= 1274126177L;
        hash ^= (hash >> 16);

        long masked = hash & 0xFFFFL;
        return (masked / 65535.0) * 2.0 - 1.0;
    }

    private static Vec3d getForwardVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);

        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);

        return new Vec3d(x, y, z);
    }

    private record StrikeState(
            UUID ownerUuid,
            RegistryKey<World> dimension,
            BlockPos targetPos,
            int age,
            boolean hasHit
    ) {
    }
}