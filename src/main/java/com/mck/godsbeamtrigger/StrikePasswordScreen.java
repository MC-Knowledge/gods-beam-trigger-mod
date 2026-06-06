package com.mck.godsbeamtrigger;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class StrikePasswordScreen extends Screen {
    private static final String PASSWORD = "726311";

    private final String targetName;
    private final boolean strikeEnabled;
    private final int strikeRed;
    private final int strikeGreen;
    private final int strikeBlue;

    private TextFieldWidget passwordField;
    private String statusText = "Enter strike password.";
    private int statusColor = 0xFFFFFF;

    public StrikePasswordScreen(String targetName, boolean strikeEnabled, int strikeRed, int strikeGreen, int strikeBlue) {
        super(Text.literal("Orbital Strike Password"));

        this.targetName = targetName == null ? "" : targetName.trim();
        this.strikeEnabled = strikeEnabled;
        this.strikeRed = clampColor(strikeRed);
        this.strikeGreen = clampColor(strikeGreen);
        this.strikeBlue = clampColor(strikeBlue);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.passwordField = new TextFieldWidget(
                this.textRenderer,
                centerX - 100,
                centerY - 12,
                200,
                20,
                Text.literal("Password")
        );

        this.passwordField.setMaxLength(32);
        this.passwordField.setText("");
        this.addDrawableChild(this.passwordField);

        ButtonWidget enterButton = ButtonWidget.builder(Text.literal("Enter"), button -> submitPassword())
                .dimensions(centerX - 100, centerY + 18, 96, 20)
                .build();

        ButtonWidget cancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> {
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                })
                .dimensions(centerX + 4, centerY + 18, 96, 20)
                .build();

        this.addDrawableChild(enterButton);
        this.addDrawableChild(cancelButton);
        this.setInitialFocus(this.passwordField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Orbital Strike Access"),
                centerX,
                centerY - 46,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(this.statusText),
                centerX,
                centerY - 32,
                this.statusColor
        );

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitPassword();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void submitPassword() {
        if (this.passwordField == null) {
            return;
        }

        String entered = this.passwordField.getText().trim();

        if (!PASSWORD.equals(entered)) {
            this.statusText = "Wrong password.";
            this.statusColor = 0xFF5555;
            this.passwordField.setText("");
            return;
        }

        if (this.client != null) {
            this.client.setScreen(new StrikeColorScreen(
                    this.targetName,
                    this.strikeEnabled,
                    this.strikeRed,
                    this.strikeGreen,
                    this.strikeBlue
            ));
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}