package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class GodScreen extends Screen {
    private static String lastEnteredTargetName = "";

    private String selectedTargetName;
    private boolean targetSelected;
    private boolean targetBeamAccess;

    private boolean beamEnabled;
    private boolean lightningEnabled;

    private int beamRed;
    private int beamGreen;
    private int beamBlue;

    private TextFieldWidget targetTextField;
    private ButtonWidget beamButton;
    private ButtonWidget lightningButton;
    private ButtonWidget grantGodsGiftButton;
    private ButtonWidget revokeGodsGiftButton;

    private RgbSlider redSlider;
    private RgbSlider greenSlider;
    private RgbSlider blueSlider;

    private ButtonWidget redPresetButton;
    private ButtonWidget purplePresetButton;
    private ButtonWidget goldPresetButton;
    private ButtonWidget resetButton;

    private String statusText = "Enter an online username first.";

    public GodScreen(
            String targetName,
            boolean targetSelected,
            boolean targetBeamAccess,
            boolean beamEnabled,
            boolean lightningEnabled,
            int beamRed,
            int beamGreen,
            int beamBlue,
            String beamAccessUsersText
    ) {
        super(Text.literal("KSMP S5"));

        String sanitizedTargetName = sanitizeName(targetName);

        if (targetSelected) {
            this.selectedTargetName = sanitizedTargetName;
            lastEnteredTargetName = sanitizedTargetName;
        } else if (!sanitizedTargetName.isEmpty()) {
            this.selectedTargetName = sanitizedTargetName;
            lastEnteredTargetName = sanitizedTargetName;
        } else {
            this.selectedTargetName = lastEnteredTargetName;
        }

        this.targetSelected = targetSelected;
        this.targetBeamAccess = targetBeamAccess;

        this.beamEnabled = beamEnabled;
        this.lightningEnabled = lightningEnabled;

        this.beamRed = clampColor(beamRed);
        this.beamGreen = clampColor(beamGreen);
        this.beamBlue = clampColor(beamBlue);

        updateStatusText();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 142;

        this.targetTextField = new TextFieldWidget(
                this.textRenderer,
                centerX - 100,
                startY,
                138,
                20,
                Text.literal("Username")
        );

        this.targetTextField.setMaxLength(16);
        this.targetTextField.setText(this.selectedTargetName);
        this.addDrawableChild(this.targetTextField);

        ButtonWidget loadTargetButton = ButtonWidget.builder(Text.literal("Load Player"), button -> requestTargetLoad())
                .dimensions(centerX + 44, startY, 96, 20)
                .build();

        this.beamButton = ButtonWidget.builder(getBeamButtonText(), button -> {
                    if (!this.targetSelected) return;

                    this.beamEnabled = !this.beamEnabled;

                    refreshButtons();
                    sendStateToServer();
                })
                .dimensions(centerX - 100, startY + 46, 200, 20)
                .build();

        this.lightningButton = ButtonWidget.builder(getLightningButtonText(), button -> {
                    if (!this.targetSelected) return;

                    this.lightningEnabled = !this.lightningEnabled;

                    refreshButtons();
                    sendStateToServer();
                })
                .dimensions(centerX - 100, startY + 72, 200, 20)
                .build();

        this.grantGodsGiftButton = ButtonWidget.builder(Text.literal("Grant God's Gift"), button -> {
                    if (!this.targetSelected) return;

                    this.targetBeamAccess = true;

                    updateStatusText();
                    refreshButtons();
                    sendStateToServer();
                })
                .dimensions(centerX - 100, startY + 98, 100, 20)
                .build();

        this.revokeGodsGiftButton = ButtonWidget.builder(Text.literal("Revoke God's Gift"), button -> {
                    if (!this.targetSelected) return;

                    this.targetBeamAccess = false;

                    updateStatusText();
                    refreshButtons();
                    sendStateToServer();
                })
                .dimensions(centerX, startY + 98, 100, 20)
                .build();

        this.redSlider = new RgbSlider(centerX - 100, startY + 138, 200, 20, Channel.RED, this.beamRed);
        this.greenSlider = new RgbSlider(centerX - 100, startY + 164, 200, 20, Channel.GREEN, this.beamGreen);
        this.blueSlider = new RgbSlider(centerX - 100, startY + 190, 200, 20, Channel.BLUE, this.beamBlue);

        this.redPresetButton = ButtonWidget.builder(Text.literal("Red"), button -> setBeamColor(255, 0, 0))
                .dimensions(centerX - 100, startY + 228, 62, 20)
                .build();

        this.purplePresetButton = ButtonWidget.builder(Text.literal("Purple"), button -> setBeamColor(160, 0, 255))
                .dimensions(centerX - 31, startY + 228, 62, 20)
                .build();

        this.goldPresetButton = ButtonWidget.builder(Text.literal("Gold"), button -> setBeamColor(255, 190, 40))
                .dimensions(centerX + 38, startY + 228, 62, 20)
                .build();

        this.resetButton = ButtonWidget.builder(Text.literal("Reset blue"), button -> setBeamColor(80, 230, 255))
                .dimensions(centerX - 100, startY + 254, 200, 20)
                .build();

        ButtonWidget closeButton = ButtonWidget.builder(Text.literal("Done"), button -> {
                    rememberCurrentTargetText();

                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                })
                .dimensions(centerX - 100, startY + 280, 200, 20)
                .build();

        this.addDrawableChild(loadTargetButton);
        this.addDrawableChild(this.beamButton);
        this.addDrawableChild(this.lightningButton);
        this.addDrawableChild(this.grantGodsGiftButton);
        this.addDrawableChild(this.revokeGodsGiftButton);
        this.addDrawableChild(this.redSlider);
        this.addDrawableChild(this.greenSlider);
        this.addDrawableChild(this.blueSlider);
        this.addDrawableChild(this.redPresetButton);
        this.addDrawableChild(this.purplePresetButton);
        this.addDrawableChild(this.goldPresetButton);
        this.addDrawableChild(this.resetButton);
        this.addDrawableChild(closeButton);

        this.setInitialFocus(this.targetTextField);

        refreshButtons();
        syncSlidersFromState();
        updateControlEnabledState();
    }

    public void setState(
            String targetName,
            boolean targetSelected,
            boolean targetBeamAccess,
            boolean beamEnabled,
            boolean lightningEnabled,
            int beamRed,
            int beamGreen,
            int beamBlue,
            String beamAccessUsersText
    ) {
        String sanitizedTargetName = sanitizeName(targetName);

        this.targetSelected = targetSelected;
        this.selectedTargetName = targetSelected ? sanitizedTargetName : this.selectedTargetName;
        this.targetBeamAccess = targetBeamAccess;

        if (targetSelected && !sanitizedTargetName.isEmpty()) {
            lastEnteredTargetName = sanitizedTargetName;
        }

        this.beamEnabled = beamEnabled;
        this.lightningEnabled = lightningEnabled;

        this.beamRed = clampColor(beamRed);
        this.beamGreen = clampColor(beamGreen);
        this.beamBlue = clampColor(beamBlue);

        if (this.targetTextField != null && targetSelected) {
            this.targetTextField.setText(this.selectedTargetName);
        }

        if (!targetSelected && !sanitizedTargetName.isEmpty()) {
            this.statusText = "Player not found online: " + sanitizedTargetName;
        } else {
            updateStatusText();
        }

        refreshButtons();
        syncSlidersFromState();
        updateControlEnabledState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 142;

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("KSMP S5"),
                centerX,
                startY - 30,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(this.statusText),
                centerX,
                startY + 25,
                this.targetSelected ? (this.targetBeamAccess ? 0xAAFFAA : 0xFFCC66) : 0xFFAAAA
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Beam colour RGB: " + this.beamRed + ", " + this.beamGreen + ", " + this.beamBlue),
                centerX,
                startY + 126,
                this.targetSelected ? 0xFFFFFF : 0x777777
        );

        int previewColor = 0xFF000000
                | (this.beamRed << 16)
                | (this.beamGreen << 8)
                | this.beamBlue;

        context.fill(centerX - 41, startY + 216, centerX + 41, startY + 224, 0xFF000000);
        context.fill(centerX - 40, startY + 217, centerX + 40, startY + 223, previewColor);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            requestTargetLoad();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void requestTargetLoad() {
        if (this.targetTextField == null) {
            return;
        }

        String requestedName = sanitizeName(this.targetTextField.getText());
        lastEnteredTargetName = requestedName;

        if (requestedName.isEmpty()) {
            this.targetSelected = false;
            this.selectedTargetName = "";
            this.statusText = "Enter an online username first.";
            updateControlEnabledState();
            return;
        }

        this.statusText = "Loading: " + requestedName;
        this.targetSelected = false;
        this.selectedTargetName = requestedName;
        updateControlEnabledState();

        ClientPlayNetworking.send(new GodTargetSelectPayload(requestedName));
    }

    private void setBeamColor(int red, int green, int blue) {
        if (!this.targetSelected) {
            return;
        }

        this.beamRed = clampColor(red);
        this.beamGreen = clampColor(green);
        this.beamBlue = clampColor(blue);

        syncSlidersFromState();
        refreshButtons();
        sendStateToServer();
    }

    private void rememberCurrentTargetText() {
        if (this.targetTextField == null) {
            return;
        }

        lastEnteredTargetName = sanitizeName(this.targetTextField.getText());
    }

    private void updateStatusText() {
        if (!this.targetSelected || this.selectedTargetName.isEmpty()) {
            this.statusText = "Enter an online username first.";
            return;
        }

        this.statusText = "Selected: " + this.selectedTargetName
                + " | God's Gift: "
                + (this.targetBeamAccess ? "YES" : "NO");
    }

    private void refreshButtons() {
        if (this.beamButton != null) {
            this.beamButton.setMessage(getBeamButtonText());
        }

        if (this.lightningButton != null) {
            this.lightningButton.setMessage(getLightningButtonText());
        }

        if (this.grantGodsGiftButton != null) {
            this.grantGodsGiftButton.active = this.targetSelected && !this.targetBeamAccess;
        }

        if (this.revokeGodsGiftButton != null) {
            this.revokeGodsGiftButton.active = this.targetSelected && this.targetBeamAccess;
        }

        if (this.redSlider != null) {
            this.redSlider.updateMessageFromState();
        }

        if (this.greenSlider != null) {
            this.greenSlider.updateMessageFromState();
        }

        if (this.blueSlider != null) {
            this.blueSlider.updateMessageFromState();
        }
    }

    private void syncSlidersFromState() {
        if (this.redSlider != null) {
            this.redSlider.syncValueFromState();
        }

        if (this.greenSlider != null) {
            this.greenSlider.syncValueFromState();
        }

        if (this.blueSlider != null) {
            this.blueSlider.syncValueFromState();
        }
    }

    private void updateControlEnabledState() {
        if (this.beamButton != null) {
            this.beamButton.active = this.targetSelected;
        }

        if (this.lightningButton != null) {
            this.lightningButton.active = this.targetSelected;
        }

        if (this.grantGodsGiftButton != null) {
            this.grantGodsGiftButton.active = this.targetSelected && !this.targetBeamAccess;
        }

        if (this.revokeGodsGiftButton != null) {
            this.revokeGodsGiftButton.active = this.targetSelected && this.targetBeamAccess;
        }

        if (this.redSlider != null) {
            this.redSlider.active = this.targetSelected;
        }

        if (this.greenSlider != null) {
            this.greenSlider.active = this.targetSelected;
        }

        if (this.blueSlider != null) {
            this.blueSlider.active = this.targetSelected;
        }

        if (this.redPresetButton != null) {
            this.redPresetButton.active = this.targetSelected;
        }

        if (this.purplePresetButton != null) {
            this.purplePresetButton.active = this.targetSelected;
        }

        if (this.goldPresetButton != null) {
            this.goldPresetButton.active = this.targetSelected;
        }

        if (this.resetButton != null) {
            this.resetButton.active = this.targetSelected;
        }
    }

    private Text getBeamButtonText() {
        return Text.literal("Beam: " + (this.beamEnabled ? "ON" : "OFF"));
    }

    private Text getLightningButtonText() {
        return Text.literal("Lightning: " + (this.lightningEnabled ? "ON" : "OFF"));
    }

    private void sendStateToServer() {
        if (!this.targetSelected || this.selectedTargetName.isEmpty()) {
            return;
        }

        ClientPlayNetworking.send(
                new GodTogglePayload(
                        this.selectedTargetName,
                        this.targetBeamAccess,
                        this.beamEnabled,
                        this.lightningEnabled,
                        this.beamRed,
                        this.beamGreen,
                        this.beamBlue
                )
        );
    }

    private int getChannelValue(Channel channel) {
        return switch (channel) {
            case RED -> this.beamRed;
            case GREEN -> this.beamGreen;
            case BLUE -> this.beamBlue;
        };
    }

    private void setChannelValue(Channel channel, int value) {
        if (!this.targetSelected) {
            return;
        }

        int clamped = clampColor(value);

        switch (channel) {
            case RED -> this.beamRed = clamped;
            case GREEN -> this.beamGreen = clamped;
            case BLUE -> this.beamBlue = clamped;
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String sanitizeName(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        if (trimmed.length() > 16) {
            return trimmed.substring(0, 16);
        }

        return trimmed;
    }

    private enum Channel {
        RED("Red"),
        GREEN("Green"),
        BLUE("Blue");

        private final String displayName;

        Channel(String displayName) {
            this.displayName = displayName;
        }
    }

    private final class RgbSlider extends SliderWidget {
        private final Channel channel;

        private RgbSlider(int x, int y, int width, int height, Channel channel, int initialValue) {
            super(
                    x,
                    y,
                    width,
                    height,
                    Text.literal(channel.displayName + ": " + clampColor(initialValue)),
                    clampColor(initialValue) / 255.0D
            );

            this.channel = channel;
        }

        @Override
        protected void updateMessage() {
            updateMessageFromState();
        }

        @Override
        protected void applyValue() {
            if (!GodScreen.this.targetSelected) {
                return;
            }

            int newValue = clampColor((int) Math.round(this.value * 255.0D));
            GodScreen.this.setChannelValue(this.channel, newValue);
            updateMessageFromState();
            GodScreen.this.sendStateToServer();
        }

        private void syncValueFromState() {
            this.value = GodScreen.this.getChannelValue(this.channel) / 255.0D;
            updateMessageFromState();
        }

        private void updateMessageFromState() {
            this.setMessage(Text.literal(this.channel.displayName + ": " + GodScreen.this.getChannelValue(this.channel)));
        }
    }
}