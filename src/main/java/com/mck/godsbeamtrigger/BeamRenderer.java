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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BeamRenderer {
    private static final Identifier BEAM_TEXTURE =
            Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final Identifier CASTING_CIRCLE_TEXTURE =
            Identifier.of("s5utils", "textures/misc/healing_circle.png");

    private static final int ACTIVE_TICKS = 4;
    private static final double BEAM_LENGTH = 8.0;
    private static final double SERAPHIM_BEAM_LENGTH = 18.0;

    private static final double SHOULDER_Y = 1.34;
    private static final double SHOULDER_FORWARD = 0.03;
    private static final double SHOULDER_SIDE = 0.36;
    private static final double HAND_FORWARD = 0.88;
    private static final double HAND_Y_OFFSET = -0.05;

    private static final double CASTING_CIRCLE_FORWARD = -0.06;

    private static final double CASTING_CIRCLE_RADIUS = 0.32;
    private static final double CASTING_CIRCLE_OUTER_RADIUS = 0.43;
    private static final double CASTING_CIRCLE_INNER_RADIUS = 0.21;
    private static final double CASTING_CIRCLE_CORE_RADIUS = 0.13;

    private static final double CASTING_CIRCLE_SPINS_PER_SECOND = 0.45;

    private static final Map<UUID, Integer> activePlayers = new HashMap<>();
    private static final Map<UUID, BeamColor> activeColors = new HashMap<>();
    private static final Map<Integer, Integer> activeEntityIds = new HashMap<>();

    private BeamRenderer() {
    }

    public static void init() {
    }

    public static void markActive(UUID playerUuid) {
        markActive(playerUuid, 80, 230, 255);
    }

    public static void markActive(UUID playerUuid, int red, int green, int blue) {
        activePlayers.put(playerUuid, ACTIVE_TICKS);
        activeColors.put(playerUuid, new BeamColor(clampColor(red), clampColor(green), clampColor(blue)));

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(playerUuid);

            if (player != null) {
                activeEntityIds.put(player.getId(), ACTIVE_TICKS);
            }
        }
    }

    public static boolean isBeamActiveForEntityId(int entityId) {
        return activeEntityIds.containsKey(entityId);
    }

    public static void clientTick() {
        Iterator<Map.Entry<UUID, Integer>> uuidIterator = activePlayers.entrySet().iterator();

        while (uuidIterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = uuidIterator.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                activeColors.remove(entry.getKey());
                uuidIterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        Iterator<Map.Entry<Integer, Integer>> idIterator = activeEntityIds.entrySet().iterator();

        while (idIterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = idIterator.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                idIterator.remove();
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
        double castingCircleRotation = timeSeconds * Math.PI * 2.0 * CASTING_CIRCLE_SPINS_PER_SECOND;

        for (UUID uuid : new ArrayList<>(activePlayers.keySet())) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);

            if (player != null && player.isAlive()) {
                BeamColor color = activeColors.getOrDefault(uuid, new BeamColor(80, 230, 255));
                renderForPlayer(matrices, immediate, player, camera, tickDelta, color, castingCircleRotation);
            }
        }
    }

    private static void renderForPlayer(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            PlayerEntity player,
            Vec3d camera,
            float tickDelta,
            BeamColor color,
            double castingCircleRotation
    ) {
        if (SeraphimMorphClient.isPlayerMorphed(player.getUuid())) {
            renderSeraphimEyeBeam(matrices, immediate, player, camera, tickDelta);
            return;
        }

        Vec3d playerPos = player.getLerpedPos(tickDelta);

        float headYaw = lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float bodyYaw = lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        float pitch = player.getPitch(tickDelta);

        Vec3d lookForward = getMinecraftForwardVector(headYaw, pitch).normalize();
        Vec3d bodyForward = getMinecraftForwardVector(bodyYaw, 0.0f).normalize();
        Vec3d bodyRight = getMinecraftRightVector(bodyYaw).normalize();

        Vec3d eye = playerPos.add(0.0, 1.55, 0.0);
        Vec3d target = eye.add(lookForward.multiply(BEAM_LENGTH));

        Vec3d shoulderCenter = playerPos
                .add(0.0, SHOULDER_Y, 0.0)
                .add(bodyForward.multiply(SHOULDER_FORWARD));

        Vec3d leftShoulder = shoulderCenter.add(bodyRight.multiply(-SHOULDER_SIDE));
        Vec3d rightShoulder = shoulderCenter.add(bodyRight.multiply(SHOULDER_SIDE));

        Vec3d leftHand = leftShoulder
                .add(lookForward.multiply(HAND_FORWARD))
                .add(0.0, HAND_Y_OFFSET, 0.0);

        Vec3d rightHand = rightShoulder
                .add(lookForward.multiply(HAND_FORWARD))
                .add(0.0, HAND_Y_OFFSET, 0.0);

        Vec3d leftBeamStart = leftHand.add(lookForward.multiply(CASTING_CIRCLE_FORWARD));
        Vec3d rightBeamStart = rightHand.add(lookForward.multiply(CASTING_CIRCLE_FORWARD));

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        VertexConsumer beamConsumer = immediate.getBuffer(RenderLayers.weather(BEAM_TEXTURE, false));

        drawFocusedSolidLaser(beamConsumer, entry, matrix, camera, leftBeamStart, target, color);
        drawFocusedSolidLaser(beamConsumer, entry, matrix, camera, rightBeamStart, target, color);

        drawFocusFlare(beamConsumer, entry, matrix, camera, target, 0.34f, color.red(), color.green(), color.blue(), 120);
        drawFocusFlare(beamConsumer, entry, matrix, camera, target, 0.16f, 245, 255, 255, 240);

        VertexConsumer circleConsumer = immediate.getBuffer(RenderLayers.weather(CASTING_CIRCLE_TEXTURE, false));

        drawCastingCircle(circleConsumer, entry, matrix, camera, leftBeamStart, lookForward, bodyRight, color, castingCircleRotation);
        drawCastingCircle(circleConsumer, entry, matrix, camera, rightBeamStart, lookForward, bodyRight, color, -castingCircleRotation);
    }

    private static void renderSeraphimEyeBeam(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            PlayerEntity player,
            Vec3d camera,
            float tickDelta
    ) {
        Vec3d eye = SeraphimMorphClient.getSeraphimEyePosition(player, tickDelta);
        Vec3d forward = SeraphimMorphClient.getSeraphimLookVector(player, tickDelta);

        Vec3d start = eye.add(forward.multiply(0.65));
        Vec3d target = start.add(forward.multiply(SERAPHIM_BEAM_LENGTH));

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        VertexConsumer beamConsumer = immediate.getBuffer(RenderLayers.weather(BEAM_TEXTURE, false));

        drawTube(beamConsumer, entry, matrix, camera, start, target, 0.74f, 255, 255, 255, 90);
        drawTube(beamConsumer, entry, matrix, camera, start, target, 0.43f, 255, 255, 255, 175);
        drawTube(beamConsumer, entry, matrix, camera, start, target, 0.19f, 255, 255, 255, 255);
        drawTube(beamConsumer, entry, matrix, camera, start, target, 0.075f, 255, 255, 255, 255);

        drawFocusFlare(beamConsumer, entry, matrix, camera, start, 0.72f, 255, 255, 255, 220);
        drawFocusFlare(beamConsumer, entry, matrix, camera, target, 0.52f, 255, 255, 255, 170);
    }

    private static void drawCastingCircle(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldCenter,
            Vec3d normal,
            Vec3d right,
            BeamColor color,
            double rotation
    ) {
        BeamColor glow = brighten(color, 0.90);
        Vec3d normalOffset = normal.normalize();

        drawDoubleSidedOrientedCircleQuad(consumer, entry, matrix, camera, worldCenter.add(normalOffset.multiply(0.003)), normal, right, CASTING_CIRCLE_OUTER_RADIUS, -rotation * 0.65, glow.red(), glow.green(), glow.blue(), 255);
        drawDoubleSidedOrientedCircleQuad(consumer, entry, matrix, camera, worldCenter.add(normalOffset.multiply(0.006)), normal, right, CASTING_CIRCLE_RADIUS, rotation, 255, 255, 255, 255);
        drawDoubleSidedOrientedCircleQuad(consumer, entry, matrix, camera, worldCenter.add(normalOffset.multiply(0.009)), normal, right, CASTING_CIRCLE_RADIUS, rotation, 255, 255, 255, 255);
        drawDoubleSidedOrientedCircleQuad(consumer, entry, matrix, camera, worldCenter.add(normalOffset.multiply(0.012)), normal, right, CASTING_CIRCLE_INNER_RADIUS, -rotation * 1.3, 255, 255, 255, 255);
        drawDoubleSidedOrientedCircleQuad(consumer, entry, matrix, camera, worldCenter.add(normalOffset.multiply(0.015)), normal, right, CASTING_CIRCLE_CORE_RADIUS, rotation * 1.8, 255, 255, 255, 255);
    }

    private static void drawDoubleSidedOrientedCircleQuad(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldCenter,
            Vec3d normal,
            Vec3d right,
            double radius,
            double rotation,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d center = worldCenter.subtract(camera);

        Vec3d circleNormal = normal.normalize();
        Vec3d circleRight = right.normalize();

        circleRight = circleRight.subtract(circleNormal.multiply(circleRight.dotProduct(circleNormal)));

        if (circleRight.lengthSquared() < 0.0001) {
            circleRight = new Vec3d(1.0, 0.0, 0.0);
        }

        circleRight = circleRight.normalize();

        Vec3d circleUp = circleNormal.crossProduct(circleRight);

        if (circleUp.lengthSquared() < 0.0001) {
            circleUp = new Vec3d(0.0, 1.0, 0.0);
        }

        circleUp = circleUp.normalize();

        Vec3d p1 = center.add(rotateOnPlane(-radius, -radius, circleRight, circleUp, rotation));
        Vec3d p2 = center.add(rotateOnPlane(-radius, radius, circleRight, circleUp, rotation));
        Vec3d p3 = center.add(rotateOnPlane(radius, radius, circleRight, circleUp, rotation));
        Vec3d p4 = center.add(rotateOnPlane(radius, -radius, circleRight, circleUp, rotation));

        vertex(consumer, entry, matrix, p1, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p4, 1.0f, 0.0f, r, g, b, a);

        vertex(consumer, entry, matrix, p4, 1.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p1, 0.0f, 0.0f, r, g, b, a);
    }

    private static Vec3d rotateOnPlane(double x, double y, Vec3d right, Vec3d up, double rotation) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);

        double rotatedX = (x * cos) - (y * sin);
        double rotatedY = (x * sin) + (y * cos);

        return right.multiply(rotatedX).add(up.multiply(rotatedY));
    }

    private static void drawFocusedSolidLaser(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldStart,
            Vec3d worldEnd,
            BeamColor color
    ) {
        drawTube(consumer, entry, matrix, camera, worldStart, worldEnd, 0.130f, color.red(), color.green(), color.blue(), 70);
        drawTube(consumer, entry, matrix, camera, worldStart, worldEnd, 0.075f, color.red(), color.green(), color.blue(), 150);
        drawTube(consumer, entry, matrix, camera, worldStart, worldEnd, 0.035f, 220, 255, 255, 245);
        drawTube(consumer, entry, matrix, camera, worldStart, worldEnd, 0.018f, 255, 255, 255, 255);
    }

    private static void drawTube(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldStart,
            Vec3d worldEnd,
            float radius,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d start = worldStart.subtract(camera);
        Vec3d end = worldEnd.subtract(camera);

        Vec3d direction = end.subtract(start).normalize();

        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        Vec3d axisA = direction.crossProduct(up);

        if (axisA.lengthSquared() < 0.0001) {
            axisA = direction.crossProduct(new Vec3d(1.0, 0.0, 0.0));
        }

        axisA = axisA.normalize();
        Vec3d axisB = direction.crossProduct(axisA).normalize();

        int sides = 18;

        for (int i = 0; i < sides; i++) {
            double a1 = (Math.PI * 2.0 * i) / sides;
            double a2 = (Math.PI * 2.0 * (i + 1)) / sides;

            Vec3d offset1 = axisA.multiply(Math.cos(a1) * radius).add(axisB.multiply(Math.sin(a1) * radius));
            Vec3d offset2 = axisA.multiply(Math.cos(a2) * radius).add(axisB.multiply(Math.sin(a2) * radius));

            Vec3d p1 = start.add(offset1);
            Vec3d p2 = start.add(offset2);
            Vec3d p3 = end.add(offset2);
            Vec3d p4 = end.add(offset1);

            vertex(consumer, entry, matrix, p1, 0.0f, 0.0f, r, g, b, a);
            vertex(consumer, entry, matrix, p2, 0.0f, 1.0f, r, g, b, a);
            vertex(consumer, entry, matrix, p3, 1.0f, 1.0f, r, g, b, a);
            vertex(consumer, entry, matrix, p4, 1.0f, 0.0f, r, g, b, a);
        }
    }

    private static void drawFocusFlare(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldCenter,
            float size,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d center = worldCenter.subtract(camera);

        Vec3d toCamera = center.multiply(-1.0);

        if (toCamera.lengthSquared() < 0.0001) {
            toCamera = new Vec3d(0.0, 0.0, 1.0);
        }

        toCamera = toCamera.normalize();

        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = up.crossProduct(toCamera);

        if (right.lengthSquared() < 0.0001) {
            right = new Vec3d(1.0, 0.0, 0.0);
        }

        right = right.normalize().multiply(size);
        Vec3d vertical = toCamera.crossProduct(right).normalize().multiply(size);

        Vec3d p1 = center.subtract(right).subtract(vertical);
        Vec3d p2 = center.subtract(right).add(vertical);
        Vec3d p3 = center.add(right).add(vertical);
        Vec3d p4 = center.add(right).subtract(vertical);

        vertex(consumer, entry, matrix, p1, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p4, 1.0f, 0.0f, r, g, b, a);
    }

    private static Vec3d getMinecraftForwardVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);

        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);

        return new Vec3d(x, y, z);
    }

    private static Vec3d getMinecraftRightVector(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);

        double x = Math.cos(yaw);
        double z = Math.sin(yaw);

        return new Vec3d(x, 0.0, z);
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

    private static float lerpAngleDegrees(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    private static float wrapDegrees(float value) {
        value = value % 360.0f;

        if (value >= 180.0f) {
            value -= 360.0f;
        }

        if (value < -180.0f) {
            value += 360.0f;
        }

        return value;
    }

    private static BeamColor brighten(BeamColor color, double amount) {
        int red = brightenChannel(color.red(), amount);
        int green = brightenChannel(color.green(), amount);
        int blue = brightenChannel(color.blue(), amount);

        return new BeamColor(red, green, blue);
    }

    private static int brightenChannel(int value, double amount) {
        return clampColor((int) Math.round(value + ((255 - value) * amount)));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record BeamColor(int red, int green, int blue) {
    }
}