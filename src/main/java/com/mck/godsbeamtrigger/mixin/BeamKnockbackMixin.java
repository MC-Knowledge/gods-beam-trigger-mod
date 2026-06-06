package com.mck.godsbeamtrigger.mixin;

import com.mck.godsbeamtrigger.BeamKnockbackContext;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BeamKnockbackMixin {
    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void s5utils$cancelBeamKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (BeamKnockbackContext.isBeamDamageActive()) {
            ci.cancel();
        }
    }
}