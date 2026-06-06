package com.mck.godsbeamtrigger;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Locale;

public final class LightningLogic {
    private static final double LIGHTNING_RANGE = 64.0;

    private LightningLogic() {}

    public static void handleUse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) return;
        if (!BeamAccessLogic.hasAccess(player)) return;

        ServerWorld world = player.getCommandSource().getWorld();

        Vec3d start = new Vec3d(player.getX(), player.getY() + 1.55, player.getZ());
        Vec3d direction = getForwardVector(player.getYaw(), player.getPitch()).normalize();
        Vec3d end = start.add(direction.multiply(LIGHTNING_RANGE));

        HitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();

        summonLightning(world, target);
    }

    private static void summonLightning(ServerWorld world, Vec3d target) {
        String dimension = world.getRegistryKey().getValue().toString();

        String command = String.format(
                Locale.ROOT,
                "execute in %s run summon minecraft:lightning_bolt %.3f %.3f %.3f",
                dimension,
                target.x,
                target.y,
                target.z
        );

        ServerCommandSource source = world.getServer()
                .getCommandSource()
                .withSilent();

        source.getServer().getCommandManager().parseAndExecute(source, command);
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