package com.mck.godsbeamtrigger.mixin;

import com.mck.godsbeamtrigger.SeraphimMorphClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("TAIL"))
    private void s5utils$moveCameraToSeraphimEye(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickProgress,
            CallbackInfo ci
    ) {
        if (thirdPerson) {
            return;
        }

        if (!(focusedEntity instanceof ClientPlayerEntity player)) {
            return;
        }

        if (!SeraphimMorphClient.isPlayerMorphed(player.getUuid())) {
            return;
        }

        this.setPos(SeraphimMorphClient.getSeraphimCameraPosition(player, tickProgress));
    }
}