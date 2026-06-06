package com.mck.godsbeamtrigger;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class S5Entities {
    public static final String MOD_ID = "s5utils";

    public static final Identifier SERAPHIM_MORPH_ID = Identifier.of(MOD_ID, "seraphim_morph");

    public static final EntityType<SeraphimMorphEntity> SERAPHIM_MORPH = register(
            "seraphim_morph",
            EntityType.Builder.create(SeraphimMorphEntity::new, SpawnGroup.MISC)
                    .dimensions(4.0f, 16.0f)
                    .maxTrackingRange(128)
                    .trackingTickInterval(1)
    );

    private S5Entities() {
    }

    public static void init() {
        /*
         * Static init holder.
         */
    }

    private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
        Identifier identifier = Identifier.of(MOD_ID, id);
        RegistryKey<EntityType<?>> registryKey = RegistryKey.of(RegistryKeys.ENTITY_TYPE, identifier);

        return Registry.register(
                Registries.ENTITY_TYPE,
                registryKey,
                builder.build(registryKey)
        );
    }
}