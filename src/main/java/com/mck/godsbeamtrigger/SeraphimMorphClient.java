package com.mck.godsbeamtrigger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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

public final class SeraphimMorphClient {
    private static final Identifier WHITE_TEXTURE =
            Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final Identifier RING_TEXTURE =
            Identifier.of("s5utils", "textures/misc/healing_circle.png");

    private static final int TRANSFORM_DELAY_TICKS = 60;

    private static final double SKY_BEAM_HEIGHT = 320.0;
    private static final double SKY_BEAM_BOTTOM_DROP = 2.0;
    private static final double SKY_BEAM_OUTER_RADIUS = 6.0;
    private static final double SKY_BEAM_MIDDLE_RADIUS = 3.25;
    private static final double SKY_BEAM_CORE_RADIUS = 1.15;

    /*
     * These are visual offsets for the Seraphim model.
     * Increase EYE_Y_OFFSET if the camera/beam appears too low.
     * Increase EYE_FORWARD_OFFSET if the camera/beam appears inside the eye.
     */
    private static final double SERAPHIM_EYE_Y_OFFSET = 8.7;
    private static final double SERAPHIM_EYE_FORWARD_OFFSET = 1.15;
    private static final double SERAPHIM_CAMERA_FORWARD_OFFSET = 2.15;

    private static final double EYE_RING_RADIUS_1 = 2.05;
    private static final double EYE_RING_RADIUS_2 = 2.75;
    private static final double EYE_RING_RADIUS_3 = 3.45;

    private static final Map<UUID, ClientMorphState> STATES = new HashMap<>();

    private SeraphimMorphClient() {
    }

    public static void init() {
    }

    public static void applyPayload(SeraphimMorphPayload payload) {
        if (payload == null) {
            return;
        }

        UUID playerUuid = payload.playerUuid();

        if (playerUuid == null || isZeroUuid(playerUuid)) {
            return;
        }

        if (payload.mode() == SeraphimMorphPayload.MODE_CLEAR) {
            STATES.remove(playerUuid);
            return;
        }

        if (payload.mode() == SeraphimMorphPayload.MODE_START) {
            STATES.put(playerUuid, new ClientMorphState(playerUuid, 0, false));
            return;
        }

        if (payload.mode() == SeraphimMorphPayload.MODE_MORPHED) {
            STATES.put(playerUuid, new ClientMorphState(playerUuid, TRANSFORM_DELAY_TICKS, true));
        }
    }

    public static void clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null) {
            STATES.clear();
            return;
        }

        Iterator<ClientMorphState> iterator = STATES.values().iterator();

        while (iterator.hasNext()) {
            ClientMorphState state = iterator.next();

            if (!state.morphed) {
                state.ageTicks++;

                if (state.ageTicks >= TRANSFORM_DELAY_TICKS) {
                    state.morphed = true;
                }
            }
        }
    }

    public static boolean isPlayerMorphed(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        ClientMorphState state = STATES.get(playerUuid);

        return state != null && state.morphed;
    }

    public static boolean isLocalPlayerMorphed() {
        MinecraftClient client = MinecraftClient.getInstance();

        return client.player != null && isPlayerMorphed(client.player.getUuid());
    }

    public static boolean shouldHideEntityId(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || STATES.isEmpty()) {
            return false;
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getId() == entityId) {
                ClientMorphState state = STATES.get(player.getUuid());
                return state != null && state.morphed;
            }
        }

        return false;
    }

    public static Vec3d getSeraphimEyePosition(PlayerEntity player, float tickDelta) {
        Vec3d playerPos = player.getLerpedPos(tickDelta);
        float yaw = lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch = player.getPitch(tickDelta);

        Vec3d forward = getMinecraftForwardVector(yaw, pitch).normalize();

        return playerPos
                .add(0.0, SERAPHIM_EYE_Y_OFFSET, 0.0)
                .add(forward.multiply(SERAPHIM_EYE_FORWARD_OFFSET));
    }

    public static Vec3d getSeraphimCameraPosition(PlayerEntity player, float tickDelta) {
        Vec3d playerPos = player.getLerpedPos(tickDelta);
        float yaw = lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch = player.getPitch(tickDelta);

        Vec3d forward = getMinecraftForwardVector(yaw, pitch).normalize();

        return playerPos
                .add(0.0, SERAPHIM_EYE_Y_OFFSET, 0.0)
                .add(forward.multiply(SERAPHIM_CAMERA_FORWARD_OFFSET));
    }

    public static Vec3d getSeraphimLookVector(PlayerEntity player, float tickDelta) {
        float yaw = lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch = player.getPitch(tickDelta);

        return getMinecraftForwardVector(yaw, pitch).normalize();
    }

    public static Vec3d getSeraphimRightVector(PlayerEntity player, float tickDelta) {
        float yaw = lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);

        return getMinecraftRightVector(yaw).normalize();
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null || client.gameRenderer == null || STATES.isEmpty()) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        VertexConsumer beamConsumer = immediate.getBuffer(RenderLayers.weather(WHITE_TEXTURE, false));
        VertexConsumer ringConsumer = immediate.getBuffer(RenderLayers.weather(RING_TEXTURE, false));

        boolean firstPerson = client.options.getPerspective().isFirstPerson();

        for (ClientMorphState state : STATES.values()) {
            AbstractClientPlayerEntity player = findPlayer(client, state.playerUuid);

            if (player == null) {
                continue;
            }

            if (!state.morphed) {
                Vec3d playerPos = player.getLerpedPos(tickDelta);
                renderSkyBeam(beamConsumer, entry, matrix, camera, playerPos, state);
                continue;
            }

            /*
             * Do not draw the extra animated rings around your own camera in first person.
             * Other players still see them.
             */
            if (firstPerson && client.player != null && player.getUuid().equals(client.player.getUuid())) {
                continue;
            }

            renderAnimatedEyeRings(ringConsumer, entry, matrix, camera, player, tickDelta);
        }
    }

    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID playerUuid) {
        if (client.world == null || playerUuid == null) {
            return null;
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(playerUuid)) {
                return player;
            }
        }

        return null;
    }

    private static void renderSkyBeam(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d playerPos,
            ClientMorphState state
    ) {
        double progress = Math.max(0.0, Math.min(1.0, state.ageTicks / (double) TRANSFORM_DELAY_TICKS));
        double pulse = 0.5 + 0.5 * Math.sin((System.nanoTime() / 1_000_000_000.0) * 18.0);

        int outerAlpha = (int) Math.round(110.0 + 45.0 * pulse);
        int middleAlpha = (int) Math.round(170.0 + 55.0 * pulse);
        int coreAlpha = 255;

        double growth = 0.45 + progress * 0.55;

        Vec3d bottom = playerPos.add(0.0, -SKY_BEAM_BOTTOM_DROP, 0.0);
        Vec3d top = playerPos.add(0.0, SKY_BEAM_HEIGHT, 0.0);

        drawTube(consumer, entry, matrix, camera, top, bottom, SKY_BEAM_OUTER_RADIUS * growth, 255, 255, 255, outerAlpha);
        drawTube(consumer, entry, matrix, camera, top, bottom, SKY_BEAM_MIDDLE_RADIUS * growth, 255, 255, 255, middleAlpha);
        drawTube(consumer, entry, matrix, camera, top, bottom, SKY_BEAM_CORE_RADIUS * growth, 255, 255, 255, coreAlpha);
    }

    private static void renderAnimatedEyeRings(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            PlayerEntity player,
            float tickDelta
    ) {
        Vec3d eye = getSeraphimEyePosition(player, tickDelta);
        Vec3d forward = getSeraphimLookVector(player, tickDelta);
        Vec3d right = getSeraphimRightVector(player, tickDelta);
        Vec3d up = forward.crossProduct(right).normalize();

        double time = System.nanoTime() / 1_000_000_000.0;

        drawDoubleSidedOrientedCircleQuad(
                consumer,
                entry,
                matrix,
                camera,
                eye.add(forward.multiply(0.08)),
                forward,
                right,
                EYE_RING_RADIUS_1,
                time * 1.25,
                255,
                255,
                255,
                235
        );

        drawDoubleSidedOrientedCircleQuad(
                consumer,
                entry,
                matrix,
                camera,
                eye.add(forward.multiply(0.12)),
                forward.add(up.multiply(0.20)).normalize(),
                right,
                EYE_RING_RADIUS_2,
                -time * 0.82,
                255,
                255,
                255,
                200
        );

        drawDoubleSidedOrientedCircleQuad(
                consumer,
                entry,
                matrix,
                camera,
                eye.add(forward.multiply(0.16)),
                forward.add(right.multiply(0.22)).normalize(),
                right,
                EYE_RING_RADIUS_3,
                time * 0.56,
                255,
                255,
                255,
                165
        );
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

    private static void drawTube(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldStart,
            Vec3d worldEnd,
            double radius,
            int r,
            int g,
            int b,
            int a
    ) {
        if (a <= 0 || radius <= 0.0) {
            return;
        }

        Vec3d start = worldStart.subtract(camera);
        Vec3d end = worldEnd.subtract(camera);
        Vec3d direction = end.subtract(start);

        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        direction = direction.normalize();

        Vec3d axisA = direction.crossProduct(new Vec3d(0.0, 1.0, 0.0));

        if (axisA.lengthSquared() < 0.0001) {
            axisA = direction.crossProduct(new Vec3d(1.0, 0.0, 0.0));
        }

        axisA = axisA.normalize();

        Vec3d axisB = direction.crossProduct(axisA).normalize();

        int sides = 32;

        for (int i = 0; i < sides; i++) {
            double angle1 = (Math.PI * 2.0 * i) / sides;
            double angle2 = (Math.PI * 2.0 * (i + 1)) / sides;

            Vec3d offset1 = axisA.multiply(Math.cos(angle1) * radius).add(axisB.multiply(Math.sin(angle1) * radius));
            Vec3d offset2 = axisA.multiply(Math.cos(angle2) * radius).add(axisB.multiply(Math.sin(angle2) * radius));

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

    private static boolean isZeroUuid(UUID uuid) {
        return uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L;
    }

    private static final class ClientMorphState {
        private final UUID playerUuid;
        private int ageTicks;
        private boolean morphed;

        private ClientMorphState(UUID playerUuid, int ageTicks, boolean morphed) {
            this.playerUuid = playerUuid;
            this.ageTicks = ageTicks;
            this.morphed = morphed;
        }
    }
}