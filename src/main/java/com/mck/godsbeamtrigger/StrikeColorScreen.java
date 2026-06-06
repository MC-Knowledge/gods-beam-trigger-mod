package com.mck.godsbeamtrigger;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class StrikeColorScreen extends Screen {
    private final String defaultTargetName;

    private boolean strikeEnabled;

    private int strikeRed;
    private int strikeGreen;
    private int strikeBlue;

    private TextFieldWidget targetField;
    private RgbSlider redSlider;
    private RgbSlider greenSlider;
    private RgbSlider blueSlider;
    private ButtonWidget enabledButton;

    public StrikeColorScreen(String targetName, boolean strikeEnabled, int strikeRed, int strikeGreen, int strikeBlue) {
        super(Text.literal("Orbital Strike Settings"));

        this.defaultTargetName = targetName == null ? "" : targetName.trim();

        this.strikeEnabled = strikeEnabled;
        this.strikeRed = clampColor(strikeRed);
        this.strikeGreen = clampColor(strikeGreen);
        this.strikeBlue = clampColor(strikeBlue);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 148;
        int mainX = centerX - 100;

        this.targetField = new TextFieldWidget(
                this.textRenderer,
                mainX,
                startY + 30,
                200,
                20,
                Text.literal("Target Player")
        );

        this.targetField.setMaxLength(64);
        this.targetField.setText(this.defaultTargetName);
        this.addDrawableChild(this.targetField);

        this.enabledButton = ButtonWidget.builder(getEnabledButtonText(), button -> toggleStrikeEnabled())
                .dimensions(mainX, startY + 58, 200, 20)
                .build();

        this.redSlider = new RgbSlider(mainX, startY + 88, 200, 20, Channel.RED, this.strikeRed);
        this.greenSlider = new RgbSlider(mainX, startY + 114, 200, 20, Channel.GREEN, this.strikeGreen);
        this.blueSlider = new RgbSlider(mainX, startY + 140, 200, 20, Channel.BLUE, this.strikeBlue);

        ButtonWidget redPresetButton = ButtonWidget.builder(Text.literal("Red"), button -> setStrikeColor(255, 0, 0))
                .dimensions(mainX, startY + 198, 62, 20)
                .build();

        ButtonWidget whitePresetButton = ButtonWidget.builder(Text.literal("White"), button -> setStrikeColor(255, 255, 255))
                .dimensions(centerX - 31, startY + 198, 62, 20)
                .build();

        ButtonWidget goldPresetButton = ButtonWidget.builder(Text.literal("Gold"), button -> setStrikeColor(255, 190, 40))
                .dimensions(centerX + 38, startY + 198, 62, 20)
                .build();

        ButtonWidget bluePresetButton = ButtonWidget.builder(Text.literal("Reset blue"), button -> setStrikeColor(80, 230, 255))
                .dimensions(mainX, startY + 224, 200, 20)
                .build();

        ButtonWidget applyButton = ButtonWidget.builder(Text.literal("Apply to Player"), button -> sendSettingsToServer())
                .dimensions(mainX, startY + 250, 200, 20)
                .build();

        ButtonWidget closeButton = ButtonWidget.builder(Text.literal("Done"), button -> {
                    sendSettingsToServer();

                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                })
                .dimensions(mainX, startY + 276, 200, 20)
                .build();

        this.addDrawableChild(this.enabledButton);
        this.addDrawableChild(this.redSlider);
        this.addDrawableChild(this.greenSlider);
        this.addDrawableChild(this.blueSlider);
        this.addDrawableChild(redPresetButton);
        this.addDrawableChild(whitePresetButton);
        this.addDrawableChild(goldPresetButton);
        this.addDrawableChild(bluePresetButton);
        this.addDrawableChild(applyButton);
        this.addDrawableChild(closeButton);

        syncSlidersFromState();
        syncEnabledButtonFromState();
        this.setInitialFocus(this.targetField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 148;
        int mainX = centerX - 100;

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Orbital Strike Settings"),
                centerX,
                startY,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Target player:"),
                mainX,
                startY + 18,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("RGB: " + this.strikeRed + ", " + this.strikeGreen + ", " + this.strikeBlue),
                centerX,
                startY + 166,
                0xFFFFFF
        );

        int previewColor = 0xFF000000
                | (this.strikeRed << 16)
                | (this.strikeGreen << 8)
                | this.strikeBlue;

        /*
         * Colour preview is now above the preset buttons instead of inside them.
         */
        context.fill(mainX - 1, startY + 178, mainX + 201, startY + 188, 0xFF000000);
        context.fill(mainX, startY + 179, mainX + 200, startY + 187, previewColor);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void toggleStrikeEnabled() {
        this.strikeEnabled = !this.strikeEnabled;
        syncEnabledButtonFromState();
        sendSettingsToServer();
    }

    private void setStrikeColor(int red, int green, int blue) {
        this.strikeRed = clampColor(red);
        this.strikeGreen = clampColor(green);
        this.strikeBlue = clampColor(blue);

        syncSlidersFromState();
        sendSettingsToServer();
    }

    private void sendSettingsToServer() {
        ClientPlayNetworking.send(new StrikeColorPayload(
                getTargetName(),
                this.strikeEnabled,
                this.strikeRed,
                this.strikeGreen,
                this.strikeBlue
        ));
    }

    private String getTargetName() {
        if (this.targetField == null) {
            return this.defaultTargetName;
        }

        String name = this.targetField.getText();

        if (name == null || name.trim().isEmpty()) {
            return this.defaultTargetName;
        }

        return name.trim();
    }

    private void syncEnabledButtonFromState() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(getEnabledButtonText());
        }
    }

    private Text getEnabledButtonText() {
        return Text.literal(this.strikeEnabled ? "Strike: ON" : "Strike: OFF");
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

    private int getChannelValue(Channel channel) {
        return switch (channel) {
            case RED -> this.strikeRed;
            case GREEN -> this.strikeGreen;
            case BLUE -> this.strikeBlue;
        };
    }

    private void setChannelValue(Channel channel, int value) {
        int clamped = clampColor(value);

        switch (channel) {
            case RED -> this.strikeRed = clamped;
            case GREEN -> this.strikeGreen = clamped;
            case BLUE -> this.strikeBlue = clamped;
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
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
            int newValue = clampColor((int) Math.round(this.value * 255.0D));
            StrikeColorScreen.this.setChannelValue(this.channel, newValue);
            updateMessageFromState();
            StrikeColorScreen.this.sendSettingsToServer();
        }

        private void syncValueFromState() {
            this.value = StrikeColorScreen.this.getChannelValue(this.channel) / 255.0D;
            updateMessageFromState();
        }

        private void updateMessageFromState() {
            this.setMessage(Text.literal(this.channel.displayName + ": " + StrikeColorScreen.this.getChannelValue(this.channel)));
        }
    }
}