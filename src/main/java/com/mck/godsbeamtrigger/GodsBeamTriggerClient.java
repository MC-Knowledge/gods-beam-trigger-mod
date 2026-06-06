package com.mck.godsbeamtrigger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GodsBeamTriggerClient implements ClientModInitializer {
    private static final KeyBinding.Category S5UTILS_CATEGORY =
            KeyBinding.Category.create(Identifier.of("s5utils", "general"));

    private KeyBinding specialKey;
    private KeyBinding lightningKey;
    private KeyBinding orbitalStrikeKey;

    private static boolean beamKeyAccessAllowed = false;
    private static boolean beamPowerEnabled = true;
    private static boolean lightningPowerEnabled = true;

    private static int beamRed = 80;
    private static int beamGreen = 230;
    private static int beamBlue = 255;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(
                S5Entities.SERAPHIM_MORPH,
                context -> new GeoEntityRenderer<>(context, S5Entities.SERAPHIM_MORPH)
        );

        specialKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.s5utils.beam",
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                S5UTILS_CATEGORY
        ));

        lightningKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.s5utils.lightning",
                GLFW.GLFW_KEY_R,
                S5UTILS_CATEGORY
        ));

        orbitalStrikeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.s5utils.orbital_strike",
                GLFW.GLFW_KEY_G,
                S5UTILS_CATEGORY
        ));

        HealingCircleRenderer.init();
        BeamRenderer.init();
        OrbitalStrikeRenderer.init();
        SeraphimMorphClient.init();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.send(new UtilClientVersionPayload(UtilVersionTracker.getOwnModVersion()));
        });

        ClientPlayNetworking.registerGlobalReceiver(HealingRenderPayload.ID, (payload, context) -> {
            context.client().execute(() -> HealingCircleRenderer.markActive(payload.playerUuid()));
        });

        ClientPlayNetworking.registerGlobalReceiver(BeamRenderPayload.ID, (payload, context) -> {
            context.client().execute(() -> BeamRenderer.markActive(
                    payload.playerUuid(),
                    payload.red(),
                    payload.green(),
                    payload.blue()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(BeamAccessPayload.ID, (payload, context) -> {
            context.client().execute(() -> beamKeyAccessAllowed = payload.allowed());
        });

        ClientPlayNetworking.registerGlobalReceiver(GodStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = context.client();

                boolean isOwnState =
                        payload.targetSelected()
                                && client.player != null
                                && payload.targetName().equalsIgnoreCase(client.player.getName().getString());

                if (isOwnState) {
                    beamKeyAccessAllowed = payload.targetBeamAccess();

                    setGodState(
                            payload.beamEnabled(),
                            payload.lightningEnabled(),
                            payload.beamRed(),
                            payload.beamGreen(),
                            payload.beamBlue()
                    );
                }

                if (client.currentScreen instanceof GodScreen godScreen) {
                    godScreen.setState(
                            payload.targetName(),
                            payload.targetSelected(),
                            payload.targetBeamAccess(),
                            payload.beamEnabled(),
                            payload.lightningEnabled(),
                            payload.beamRed(),
                            payload.beamGreen(),
                            payload.beamBlue(),
                            payload.beamAccessUsers()
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OrbitalStrikeRenderPayload.ID, (payload, context) -> {
            context.client().execute(() -> OrbitalStrikeRenderer.markStrike(
                    payload.targetPos(),
                    payload.windupTicks(),
                    payload.strikeTicks(),
                    payload.red(),
                    payload.green(),
                    payload.blue()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(SeraphimMorphPayload.ID, (payload, context) -> {
            context.client().execute(() -> SeraphimMorphClient.applyPayload(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(GodScreenOpenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = context.client();

                boolean isOwnState =
                        payload.targetSelected()
                                && client.player != null
                                && payload.targetName().equalsIgnoreCase(client.player.getName().getString());

                if (isOwnState) {
                    beamKeyAccessAllowed = payload.targetBeamAccess();

                    setGodState(
                            payload.beamEnabled(),
                            payload.lightningEnabled(),
                            payload.beamRed(),
                            payload.beamGreen(),
                            payload.beamBlue()
                    );
                }

                client.setScreen(new GodScreen(
                        payload.targetName(),
                        payload.targetSelected(),
                        payload.targetBeamAccess(),
                        payload.beamEnabled(),
                        payload.lightningEnabled(),
                        payload.beamRed(),
                        payload.beamGreen(),
                        payload.beamBlue(),
                        payload.beamAccessUsers()
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(StrikeScreenOpenPayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new StrikePasswordScreen(
                    payload.targetName(),
                    payload.enabled(),
                    payload.red(),
                    payload.green(),
                    payload.blue()
            )));
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        HealingCircleRenderer.clientTick();
        BeamRenderer.clientTick();
        OrbitalStrikeRenderer.clientTick();
        SeraphimMorphClient.clientTick();

        if (client.player == null || client.world == null) {
            beamKeyAccessAllowed = false;
            return;
        }

        ItemStack stack = client.player.getMainHandStack();

        boolean useHeld = client.options.useKey.isPressed();
        boolean healingItem = hasHealingCircleTag(stack);
        boolean beamItem = hasBeamWandTag(stack);

        boolean specialHeld = beamKeyAccessAllowed && beamPowerEnabled && specialKey.isPressed();

        if (useHeld) {
            if (healingItem) {
                HealingCircleRenderer.markActive(client.player.getUuid());
                ClientPlayNetworking.send(new HealingUsePayload());
            }

            if (beamPowerEnabled && beamItem) {
                BeamRenderer.markActive(client.player.getUuid(), beamRed, beamGreen, beamBlue);
                ClientPlayNetworking.send(new BeamUsePayload(false));
            }
        }

        if (specialHeld) {
            BeamRenderer.markActive(client.player.getUuid(), beamRed, beamGreen, beamBlue);
            HealingCircleRenderer.markActive(client.player.getUuid());

            ClientPlayNetworking.send(new BeamUsePayload(true));
        }

        while (lightningKey.wasPressed()) {
            if (beamKeyAccessAllowed && lightningPowerEnabled) {
                ClientPlayNetworking.send(new LightningUsePayload());
            }
        }

        while (orbitalStrikeKey.wasPressed()) {
            if (beamKeyAccessAllowed) {
                ClientPlayNetworking.send(new OrbitalStrikeUsePayload());
            }
        }
    }

    public static void setGodState(
            boolean beamEnabled,
            boolean lightningEnabled,
            int newBeamRed,
            int newBeamGreen,
            int newBeamBlue
    ) {
        beamPowerEnabled = beamEnabled;
        lightningPowerEnabled = lightningEnabled;
        beamRed = clampColor(newBeamRed);
        beamGreen = clampColor(newBeamGreen);
        beamBlue = clampColor(newBeamBlue);
    }

    private boolean hasHealingCircleTag(ItemStack stack) {
        if (stack.isEmpty()) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        var nbt = customData.copyNbt();
        return nbt.contains("HealingCircle") && nbt.getBoolean("HealingCircle").orElse(false);
    }

    private boolean hasBeamWandTag(ItemStack stack) {
        if (stack.isEmpty()) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        var nbt = customData.copyNbt();
        return nbt.contains("BeamWand") && nbt.getBoolean("BeamWand").orElse(false);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}