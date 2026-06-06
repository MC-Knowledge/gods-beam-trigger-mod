package com.mck.godsbeamtrigger.mixin;

import com.mck.godsbeamtrigger.BeamRenderer;
import com.mck.godsbeamtrigger.HealingCircleRenderer;
import com.mck.godsbeamtrigger.OrbitalStrikeRenderer;
import com.mck.godsbeamtrigger.SeraphimMorphClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderBlockDamage", at = @At("TAIL"))
    private void s5utils$renderEffects(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            WorldRenderState renderStates,
            CallbackInfo ci
    ) {
        HealingCircleRenderer.render(matrices, immediate);
        BeamRenderer.render(matrices, immediate);
        OrbitalStrikeRenderer.render(matrices, immediate);
        SeraphimMorphClient.render(matrices, immediate);

        immediate.draw();
    }
}