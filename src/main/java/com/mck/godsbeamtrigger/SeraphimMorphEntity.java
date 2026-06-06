package com.mck.godsbeamtrigger;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SeraphimMorphEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public SeraphimMorphEntity(EntityType<? extends SeraphimMorphEntity> entityType, World world) {
        super(entityType, world);

        this.noClip = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    @Override
    public void tick() {
        super.tick();

        this.noClip = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomData(ReadView view) {
    }

    @Override
    protected void writeCustomData(WriteView view) {
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        /*
         * Deliberately empty.
         *
         * The Seraphim model itself is rendered by GeckoLib.
         * The visible spinning rings are rendered by s5utils in SeraphimMorphClient,
         * avoiding GeckoLib 5.4.3's unstable AnimationController package/import issue.
         */
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}