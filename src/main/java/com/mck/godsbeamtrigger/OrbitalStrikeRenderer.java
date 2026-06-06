package com.mck.godsbeamtrigger;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OrbitalStrikeRenderer {
    private static final Identifier BEAM_TEXTURE =
            Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final double SKY_HEIGHT_ABOVE_TARGET = 256.0;
    private static final double SKY_HEIGHT_ABOVE_SURFACE = 192.0;
    private static final double WARNING_RADIUS = 14.0;

    /*
     * Terrain ripple settings.
     *
     * The ripple still follows blocks, but now sits slightly higher and each
     * ring gets a tiny separate Y layer. This stops the still rings flickering
     * from z-fighting against the grass/block surface.
     */
    private static final int TERRAIN_SUBDIVISIONS_PER_BLOCK = 5;
    private static final double TERRAIN_SEARCH_UP = 10.0;
    private static final double TERRAIN_SEARCH_DOWN = 18.0;
    private static final double TERRAIN_SURFACE_OFFSET = 0.075;
    private static final double TERRAIN_EDGE_FEATHER = 0.22;
    private static final double TERRAIN_RING_LAYER_OFFSET = 0.016;

    /*
     * Side quads are drawn very slightly away from the block face, otherwise
     * they z-fight with the side of the block and flicker when stationary.
     */
    private static final double SIDE_SURFACE_OFFSET = 0.006;
    private static final double SIDE_HEIGHT_THRESHOLD = 0.18;
    private static final double MAX_SIDE_DROP_RENDERED = 4.0;

    private static final List<ClientStrike> ACTIVE_STRIKES = new ArrayList<>();

    private OrbitalStrikeRenderer() {
    }

    public static void init() {
    }

    public static void markStrike(BlockPos targetPos, int windupTicks, int strikeTicks, int red, int green, int blue) {
        ACTIVE_STRIKES.add(new ClientStrike(
                targetPos.toImmutable(),
                windupTicks,
                strikeTicks,
                windupTicks + strikeTicks + 20,
                0,
                clampColor(red),
                clampColor(green),
                clampColor(blue)
        ));
    }

    public static void clientTick() {
        Iterator<ClientStrike> iterator = ACTIVE_STRIKES.iterator();

        while (iterator.hasNext()) {
            ClientStrike strike = iterator.next();
            strike.age++;

            if (strike.age > strike.totalTicks) {
                iterator.remove();
            }
        }
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null || client.gameRenderer == null || ACTIVE_STRIKES.isEmpty()) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        double timeSeconds = System.nanoTime() / 1_000_000_000.0;

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        VertexConsumer consumer = immediate.getBuffer(RenderLayers.weather(BEAM_TEXTURE, false));

        for (ClientStrike strike : new ArrayList<>(ACTIVE_STRIKES)) {
            renderStrike(client, consumer, entry, matrix, camera, strike, tickDelta, timeSeconds);
        }
    }

    private static void renderStrike(
            MinecraftClient client,
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            ClientStrike strike,
            float tickDelta,
            double timeSeconds
    ) {
        double age = strike.age + tickDelta;
        double chargeProgress = clamp(age / strike.windupTicks);
        double fireProgress = clamp((age - strike.windupTicks) / strike.strikeTicks);
        double fadeProgress = clamp((age - strike.windupTicks - strike.strikeTicks) / 20.0);

        Vec3d beamTarget = Vec3d.ofCenter(strike.targetPos).add(0.0, 0.08, 0.0);
        Vec3d ringTarget = new Vec3d(
                strike.targetPos.getX() + 0.5,
                strike.targetPos.getY() + 1.02,
                strike.targetPos.getZ() + 0.5
        );

        int topBlockY = client.world.getTopY(
                Heightmap.Type.WORLD_SURFACE,
                strike.targetPos.getX(),
                strike.targetPos.getZ()
        );

        double topY = Math.max(
                beamTarget.y + SKY_HEIGHT_ABOVE_TARGET,
                topBlockY + SKY_HEIGHT_ABOVE_SURFACE
        );

        Vec3d sky = new Vec3d(beamTarget.x, topY, beamTarget.z);

        int baseAlpha = (int) Math.round(190.0 * (1.0 - fadeProgress));
        int hotAlpha = (int) Math.round(255.0 * (1.0 - fadeProgress));

        double pulse = 0.5 + 0.5 * Math.sin(timeSeconds * 17.0);
        double spin = timeSeconds * 4.5;

        int red = strike.red;
        int green = strike.green;
        int blue = strike.blue;

        if (age < strike.windupTicks) {
            double warningRadius = 3.0 + chargeProgress * (WARNING_RADIUS - 3.0);
            double innerRadius = 0.35 + chargeProgress * 2.8;
            int warningAlpha = (int) Math.round(50.0 + (140.0 * chargeProgress) + (30.0 * pulse));

            drawSmoothGroundFollowingRing(client, consumer, entry, matrix, camera, ringTarget, warningRadius, warningRadius + 0.18, spin, 0.0, red, green, blue, warningAlpha);
            drawSmoothGroundFollowingRing(client, consumer, entry, matrix, camera, ringTarget, innerRadius, innerRadius + 0.14, -spin * 1.25, TERRAIN_RING_LAYER_OFFSET, 255, 255, 255, warningAlpha);
            drawSmoothGroundFollowingRing(client, consumer, entry, matrix, camera, ringTarget, WARNING_RADIUS - 0.25, WARNING_RADIUS + 0.25, spin * 0.45, TERRAIN_RING_LAYER_OFFSET * 2.0, red, green, blue, Math.max(35, warningAlpha / 2));

            double previewRadius = 0.12 + chargeProgress * 0.18 + (pulse * 0.05);
            drawTube(consumer, entry, matrix, camera, sky, beamTarget, previewRadius, red, green, blue, (int) (55 + chargeProgress * 85));
            drawTube(consumer, entry, matrix, camera, sky, beamTarget, 0.045 + chargeProgress * 0.07, 255, 255, 255, (int) (75 + chargeProgress * 95));
            return;
        }

        double blastScale = 1.0 + (1.0 - fireProgress) * 0.65;
        double coreRadius = 1.10 * blastScale;
        double midRadius = 2.60 * blastScale;
        double outerRadius = 4.80 * blastScale;

        drawTube(consumer, entry, matrix, camera, sky, beamTarget.add(0.0, -24.0, 0.0), outerRadius, red, green, blue, Math.max(0, baseAlpha / 2));
        drawTube(consumer, entry, matrix, camera, sky, beamTarget.add(0.0, -24.0, 0.0), midRadius, red, green, blue, Math.max(0, baseAlpha));
        drawTube(consumer, entry, matrix, camera, sky, beamTarget.add(0.0, -24.0, 0.0), coreRadius, 245, 255, 255, Math.max(0, hotAlpha));
        drawTube(consumer, entry, matrix, camera, sky, beamTarget.add(0.0, -24.0, 0.0), 0.46, 255, 255, 255, Math.max(0, hotAlpha));

        double shockwave = 3.0 + fireProgress * 15.0;
        drawSmoothGroundFollowingRing(client, consumer, entry, matrix, camera, ringTarget, shockwave, shockwave + 0.42, spin, 0.0, 255, 255, 255, Math.max(0, hotAlpha));
        drawSmoothGroundFollowingRing(client, consumer, entry, matrix, camera, ringTarget, shockwave * 0.72, shockwave * 0.72 + 0.30, -spin, TERRAIN_RING_LAYER_OFFSET, red, green, blue, Math.max(0, baseAlpha));
    }

    private static void drawSmoothGroundFollowingRing(
            MinecraftClient client,
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldCenter,
            double innerRadius,
            double outerRadius,
            double rotation,
            double layerOffset,
            int r,
            int g,
            int b,
            int baseAlpha
    ) {
        if (baseAlpha <= 0 || client.world == null) {
            return;
        }

        double cellSize = 1.0 / TERRAIN_SUBDIVISIONS_PER_BLOCK;

        int minX = (int) Math.floor(worldCenter.x - outerRadius - TERRAIN_EDGE_FEATHER - 1.0);
        int maxX = (int) Math.floor(worldCenter.x + outerRadius + TERRAIN_EDGE_FEATHER + 1.0);
        int minZ = (int) Math.floor(worldCenter.z - outerRadius - TERRAIN_EDGE_FEATHER - 1.0);
        int maxZ = (int) Math.floor(worldCenter.z + outerRadius + TERRAIN_EDGE_FEATHER + 1.0);

        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                for (int subX = 0; subX < TERRAIN_SUBDIVISIONS_PER_BLOCK; subX++) {
                    for (int subZ = 0; subZ < TERRAIN_SUBDIVISIONS_PER_BLOCK; subZ++) {
                        double x1 = blockX + (subX * cellSize);
                        double z1 = blockZ + (subZ * cellSize);
                        double x2 = x1 + cellSize;
                        double z2 = z1 + cellSize;

                        double centerX = (x1 + x2) * 0.5;
                        double centerZ = (z1 + z2) * 0.5;

                        int alpha = getRingAlpha(worldCenter.x, worldCenter.z, centerX, centerZ, innerRadius, outerRadius, baseAlpha);

                        if (alpha <= 0) {
                            continue;
                        }

                        double y = findGroundTopY(client, centerX, centerZ, worldCenter.y);

                        if (Double.isNaN(y)) {
                            continue;
                        }

                        drawTopDecalCell(
                                consumer,
                                entry,
                                matrix,
                                camera,
                                x1,
                                z1,
                                x2,
                                z2,
                                y + layerOffset,
                                worldCenter,
                                rotation,
                                r,
                                g,
                                b,
                                alpha
                        );

                        drawSmoothSideIfNeeded(
                                client,
                                consumer,
                                entry,
                                matrix,
                                camera,
                                worldCenter,
                                innerRadius,
                                outerRadius,
                                baseAlpha,
                                x2,
                                z1,
                                z2,
                                centerX + cellSize,
                                centerZ,
                                y,
                                layerOffset,
                                true,
                                r,
                                g,
                                b
                        );

                        drawSmoothSideIfNeeded(
                                client,
                                consumer,
                                entry,
                                matrix,
                                camera,
                                worldCenter,
                                innerRadius,
                                outerRadius,
                                baseAlpha,
                                z2,
                                x1,
                                x2,
                                centerX,
                                centerZ + cellSize,
                                y,
                                layerOffset,
                                false,
                                r,
                                g,
                                b
                        );
                    }
                }
            }
        }
    }

    private static void drawSmoothSideIfNeeded(
            MinecraftClient client,
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            Vec3d worldCenter,
            double innerRadius,
            double outerRadius,
            int baseAlpha,
            double fixedAxis,
            double startAxis,
            double endAxis,
            double neighborCenterX,
            double neighborCenterZ,
            double currentY,
            double layerOffset,
            boolean fixedX,
            int r,
            int g,
            int b
    ) {
        int neighborAlpha = getRingAlpha(
                worldCenter.x,
                worldCenter.z,
                neighborCenterX,
                neighborCenterZ,
                innerRadius,
                outerRadius,
                baseAlpha
        );

        if (neighborAlpha <= 0) {
            return;
        }

        double neighborY = findGroundTopY(client, neighborCenterX, neighborCenterZ, worldCenter.y);

        if (Double.isNaN(neighborY)) {
            return;
        }

        double difference = Math.abs(currentY - neighborY);

        if (difference < SIDE_HEIGHT_THRESHOLD) {
            return;
        }

        double topY = Math.max(currentY, neighborY) + layerOffset;
        double bottomY = Math.min(currentY, neighborY) + layerOffset;

        if (topY - bottomY > MAX_SIDE_DROP_RENDERED) {
            bottomY = topY - MAX_SIDE_DROP_RENDERED;
        }

        /*
         * Push the side decal slightly toward the lower side of the height change.
         * This prevents it from fighting with the real block side face.
         */
        double sideAxisOffset = currentY >= neighborY ? SIDE_SURFACE_OFFSET : -SIDE_SURFACE_OFFSET;
        double shiftedFixedAxis = fixedAxis + sideAxisOffset;

        int sideAlpha = Math.max(18, Math.min(180, (int) Math.round(neighborAlpha * 0.62)));

        if (fixedX) {
            drawVerticalXQuad(consumer, entry, matrix, camera, shiftedFixedAxis, startAxis, endAxis, bottomY, topY, r, g, b, sideAlpha);
        } else {
            drawVerticalZQuad(consumer, entry, matrix, camera, shiftedFixedAxis, startAxis, endAxis, bottomY, topY, r, g, b, sideAlpha);
        }
    }

    private static int getRingAlpha(
            double centerX,
            double centerZ,
            double x,
            double z,
            double innerRadius,
            double outerRadius,
            int baseAlpha
    ) {
        double distance = distance2d(centerX, centerZ, x, z);

        if (distance < innerRadius - TERRAIN_EDGE_FEATHER || distance > outerRadius + TERRAIN_EDGE_FEATHER) {
            return 0;
        }

        double innerFade = smoothStep(innerRadius - TERRAIN_EDGE_FEATHER, innerRadius + TERRAIN_EDGE_FEATHER, distance);
        double outerFade = 1.0 - smoothStep(outerRadius - TERRAIN_EDGE_FEATHER, outerRadius + TERRAIN_EDGE_FEATHER, distance);

        double alphaMultiplier = clamp(innerFade * outerFade);

        return (int) Math.round(baseAlpha * alphaMultiplier);
    }

    private static double findGroundTopY(MinecraftClient client, double x, double z, double centerY) {
        if (client.world == null) {
            return Double.NaN;
        }

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        int startY = (int) Math.floor(centerY + TERRAIN_SEARCH_UP);
        int endY = (int) Math.floor(centerY - TERRAIN_SEARCH_DOWN);

        for (int y = startY; y >= endY; y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            BlockState state = client.world.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            if (state.getCollisionShape(client.world, pos).isEmpty()) {
                continue;
            }

            BlockPos abovePos = pos.up();
            BlockState aboveState = client.world.getBlockState(abovePos);

            if (!aboveState.isAir() && !aboveState.getCollisionShape(client.world, abovePos).isEmpty()) {
                continue;
            }

            return y + 1.0 + TERRAIN_SURFACE_OFFSET;
        }

        return Double.NaN;
    }

    private static void drawTopDecalCell(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            double x1,
            double z1,
            double x2,
            double z2,
            double y,
            Vec3d worldCenter,
            double rotation,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d p1 = new Vec3d(x1, y, z1).subtract(camera);
        Vec3d p2 = new Vec3d(x1, y, z2).subtract(camera);
        Vec3d p3 = new Vec3d(x2, y, z2).subtract(camera);
        Vec3d p4 = new Vec3d(x2, y, z1).subtract(camera);

        float u1 = rippleU(x1, worldCenter.x, rotation);
        float v1 = rippleV(z1, worldCenter.z, rotation);
        float u2 = rippleU(x2, worldCenter.x, rotation);
        float v2 = rippleV(z2, worldCenter.z, rotation);

        vertex(consumer, entry, matrix, p1, u1, v1, r, g, b, a);
        vertex(consumer, entry, matrix, p2, u1, v2, r, g, b, a);
        vertex(consumer, entry, matrix, p3, u2, v2, r, g, b, a);
        vertex(consumer, entry, matrix, p4, u2, v1, r, g, b, a);
    }

    private static void drawVerticalXQuad(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            double x,
            double z1,
            double z2,
            double y1,
            double y2,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d p1 = new Vec3d(x, y1, z1).subtract(camera);
        Vec3d p2 = new Vec3d(x, y2, z1).subtract(camera);
        Vec3d p3 = new Vec3d(x, y2, z2).subtract(camera);
        Vec3d p4 = new Vec3d(x, y1, z2).subtract(camera);

        vertex(consumer, entry, matrix, p1, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p4, 1.0f, 1.0f, r, g, b, a);

        vertex(consumer, entry, matrix, p4, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p1, 0.0f, 1.0f, r, g, b, a);
    }

    private static void drawVerticalZQuad(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Matrix4f matrix,
            Vec3d camera,
            double z,
            double x1,
            double x2,
            double y1,
            double y2,
            int r,
            int g,
            int b,
            int a
    ) {
        Vec3d p1 = new Vec3d(x1, y1, z).subtract(camera);
        Vec3d p2 = new Vec3d(x2, y1, z).subtract(camera);
        Vec3d p3 = new Vec3d(x2, y2, z).subtract(camera);
        Vec3d p4 = new Vec3d(x1, y2, z).subtract(camera);

        vertex(consumer, entry, matrix, p1, 0.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p4, 0.0f, 0.0f, r, g, b, a);

        vertex(consumer, entry, matrix, p4, 0.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p3, 1.0f, 0.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p2, 1.0f, 1.0f, r, g, b, a);
        vertex(consumer, entry, matrix, p1, 0.0f, 1.0f, r, g, b, a);
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

    private static float rippleU(double worldX, double centerX, double rotation) {
        return (float) ((worldX - centerX) * 0.25 + rotation * 0.025);
    }

    private static float rippleV(double worldZ, double centerZ, double rotation) {
        return (float) ((worldZ - centerZ) * 0.25 - rotation * 0.025);
    }

    private static double distance2d(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt((dx * dx) + (dz * dz));
    }

    private static double smoothStep(double edge0, double edge1, double value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0 : 1.0;
        }

        double t = clamp((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - (2.0 * t));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class ClientStrike {
        private final BlockPos targetPos;
        private final int windupTicks;
        private final int strikeTicks;
        private final int totalTicks;
        private final int red;
        private final int green;
        private final int blue;
        private int age;

        private ClientStrike(
                BlockPos targetPos,
                int windupTicks,
                int strikeTicks,
                int totalTicks,
                int age,
                int red,
                int green,
                int blue
        ) {
            this.targetPos = targetPos;
            this.windupTicks = windupTicks;
            this.strikeTicks = strikeTicks;
            this.totalTicks = totalTicks;
            this.age = age;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}