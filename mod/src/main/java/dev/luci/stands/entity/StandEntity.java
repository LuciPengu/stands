package dev.luci.stands.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The visible stand. Pure visual: the server moves it next to its owner every tick and triggers animations;
 * it has no hitbox, never saves, and takes no damage.
 */
public class StandEntity extends Entity implements GeoEntity {
    public static final String[] ONE_SHOTS = {"punch", "pose", "throw", "cast", "dash", "summon"};

    private static final EntityDataAccessor<String> STAND = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> RUSHING = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUSH = RawAnimation.begin().thenLoop("rush");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StandEntity(EntityType<? extends StandEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STAND, "");
        b.define(RUSHING, false);
        b.define(OWNER, -1);
    }

    public String standId() { return entityData.get(STAND); }
    public void setStandId(String id) { entityData.set(STAND, id); }
    public boolean rushing() { return entityData.get(RUSHING); }
    public void setRushing(boolean r) { entityData.set(RUSHING, r); }
    public int ownerId() { return entityData.get(OWNER); }
    public void setOwnerId(int id) { entityData.set(OWNER, id); }

    /** Server side: play a one-shot animation (punch, pose, throw, cast, dash, summon) on every client. */
    public void play(String anim) { triggerAnim("action", anim); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<StandEntity>("main", 3, s -> s.setAndContinue(rushing() ? RUSH : IDLE)));
        AnimationController<StandEntity> action = new AnimationController<>("action", 1, s -> PlayState.STOP);
        for (String a : ONE_SHOTS) action.triggerableAnim(a, RawAnimation.begin().thenPlay(a));
        controllers.add(action);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }

    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }

    @Override
    public void tick() {
        super.tick();
        // The manager re-positions live stands every tick; an orphan (owner gone, server reload) cleans itself up.
        if (!level().isClientSide() && tickCount > 40 && !dev.luci.stands.StandsMod.stands().owns(this)) discard();
    }
}
