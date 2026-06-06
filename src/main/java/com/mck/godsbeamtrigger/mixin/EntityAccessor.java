package com.mck.godsbeamtrigger.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("timeUntilRegen")
    void s5utils$setTimeUntilRegen(int value);
}