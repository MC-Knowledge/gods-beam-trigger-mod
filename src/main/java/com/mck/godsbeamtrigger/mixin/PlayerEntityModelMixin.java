package com.mck.godsbeamtrigger.mixin;

import com.mck.godsbeamtrigger.BeamRenderer;
import com.mck.godsbeamtrigger.SeraphimMorphClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin extends BipedEntityModel<PlayerEntityRenderState> {
    @Unique
    private static final float HALF_PI = 1.5707964f;

    @Unique
    private static final float ARM_SIDE_SPREAD = 0.16f;

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles", at = @At("TAIL"), order = 3000)
    private void s5utils$poseBeamArmsAndHideSeraphimMorph(PlayerEntityRenderState state, CallbackInfo ci) {
        if (SeraphimMorphClient.shouldHideEntityId(state.id)) {
            this.head.visible = false;
            this.hat.visible = false;
            this.body.visible = false;
            this.rightArm.visible = false;
            this.leftArm.visible = false;
            this.rightLeg.visible = false;
            this.leftLeg.visible = false;
            return;
        }

        this.head.visible = true;
        this.hat.visible = true;
        this.body.visible = true;
        this.rightArm.visible = true;
        this.leftArm.visible = true;
        this.rightLeg.visible = true;
        this.leftLeg.visible = true;

        boolean beamActive = BeamRenderer.isBeamActiveForEntityId(state.id);

        if (!beamActive) {
            return;
        }

        float relativeYawDegrees = clamp(state.relativeHeadYaw, -85.0f, 85.0f);
        float relativeYawRadians = (float) Math.toRadians(relativeYawDegrees);

        float pitchRadians = (float) Math.toRadians(state.pitch);
        float armPitch = -HALF_PI + pitchRadians;

        this.rightArm.pitch = armPitch;
        this.leftArm.pitch = armPitch;

        this.rightArm.yaw = relativeYawRadians - ARM_SIDE_SPREAD;
        this.leftArm.yaw = relativeYawRadians + ARM_SIDE_SPREAD;

        this.rightArm.roll = 0.0f;
        this.leftArm.roll = 0.0f;
    }

    @Unique
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}