package com.mck.godsbeamtrigger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class HealingCircleRenderer {
    private static final Identifier HEALING_CIRCLE_TEXTURE =
            Identifier.of("s5utils", "textures/misc/healing_circle.png");

    private static final int ACTIVE_TICKS = 4;

    /*
     * Feet circle spin speed.
     * 0.25 = one full spin every 4 seconds.
     * This is time-based, not FPS-based.
     */
    private static final double SPINS_PER_SECOND = 0.25;

    private static final double MAIN_RADIUS = 1.65;
    private static final double OUTER_RADIUS = 2.05;
    private static final double INNER_RADIUS = 1.25;

    /*
     * Small lift above the player's feet so it does not z-fight with blocks.
     */
    private static final double FEET_Y_OFFSET = 0.035;

    private static final Map<UUID, Integer> activePlayers = new HashMap<>();

    private HealingCircleRenderer() {
    }

    public static void init() {
        /*
         * Render is called by WorldRendererMixin.
         */
    }

    public static void markActive(UUID playerUuid) {
        activePlayers.put(playerUuid, ACTIVE_TICKS);
    }

    public static void clientTick() {
        Iterator<Map.Entry<UUID, Integer>> iterator = activePlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null || client.gameRenderer == null || activePlayers.isEmpty()) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

        double timeSeconds = System.nanoTime() / 1_000_000_000.0;
        double baseRotation = timeSeconds * Math.PI * 2.0 * SPINS_PER_SECOND;

        VertexConsumer consumer = immediate.getBuffer(RenderLayers.weather(HEALING_CIRCLE_TEXTURE, false));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        for (UUID uuid : activePlayers.keySet()) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);

            if (player != null && player.isAlive()) {
                renderForPlayer(consumer, entry, matrix, player, camera, tickDelta, baseRotation);
            }
        }
    }

    private static void renderForPlayer(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            PlayerEntity player,
            Vec3d camera,
            float tickDelta,
            double baseRotation
    ) {
        /*
         * getLerpedPos(tickDelta) is the player's feet/base position.
         */
        Vec3d feet = player.getLerpedPos(tickDelta);

        Vec3d center = new Vec3d(
                feet.x,
                feet.y + FEET_Y_OFFSET,
                feet.z
        ).subtract(camera);

        drawFlatCircleQuad(
                consumer,
                entry,
                matrix,
                center,
                MAIN_RADIUS,
                baseRotation,
                255,
                255,
                255,
                210
        );

        drawFlatCircleQuad(
                consumer,
                entry,
                matrix,
                center,
                OUTER_RADIUS,
                -baseRotation * 0.72,
                255,
                255,
                255,
                90
        );

        drawFlatCircleQuad(
                consumer,
                entry,
                matrix,
                center,
                INNER_RADIUS,
                -baseRotation * 1.15,
                255,
                255,
                255,
                120
        );
    }

    private static void drawFlatCircleQuad(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d center,
            double radius,
            double rotation,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d cornerA = rotateFlat(-radius, -radius, rotation);
        Vec3d cornerB = rotateFlat(-radius, radius, rotation);
        Vec3d cornerC = rotateFlat(radius, radius, rotation);
        Vec3d cornerD = rotateFlat(radius, -radius, rotation);

        Vec3d p1 = center.add(cornerA.x, 0.0, cornerA.z);
        Vec3d p2 = center.add(cornerB.x, 0.0, cornerB.z);
        Vec3d p3 = center.add(cornerC.x, 0.0, cornerC.z);
        Vec3d p4 = center.add(cornerD.x, 0.0, cornerD.z);

        vertex(consumer, entry, matrix, p1, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p4, 1.0f, 0.0f, r, g, b, a);
    }

    private static Vec3d rotateFlat(double x, double z, double rotation) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);

        double rotatedX = (x * cos) - (z * sin);
        double rotatedZ = (x * sin) + (z * cos);

        return new Vec3d(rotatedX, 0.0, rotatedZ);
    }

    private static void vertex(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d pos,
            float u,
            float v,
            int r,
            int g,
            int b,
            int a
    ) {
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }
}