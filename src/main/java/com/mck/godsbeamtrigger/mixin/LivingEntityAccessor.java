package com.mck.godsbeamtrigger.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("hurtTime")
    void s5utils$setHurtTime(int value);

    @Accessor("maxHurtTime")
    void s5utils$setMaxHurtTime(int value);

    @Accessor("lastDamageTaken")
    void s5utils$setLastDamageTaken(float value);
}