package com.mck.godsbeamtrigger.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DamageSources.class)
public interface DamageSourcesAccessor {
    @Invoker("create")
    DamageSource s5utils$create(RegistryKey<DamageType> key, Entity attacker);
}