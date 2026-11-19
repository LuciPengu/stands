package dev.luci.stands.entity;

import dev.luci.stands.StandsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * Stand projectile (knives, emeralds, nails, fireballs...). Looks like any item. Damage goes through
 * StandManager.hit(); it flies through the shooter, stands, and anyone stand PvP rules protect.
 */
public class StandShot extends ThrowableItemProjectile {
    private double damage;
    private BiConsumer<Vec3, LivingEntity> onHit;
    private int life = 100;

    public StandShot(EntityType<? extends StandShot> type, Level level) { super(type, level); }

    public StandShot(Level level, ServerPlayer owner, ItemStack look, double damage, BiConsumer<Vec3, LivingEntity> onHit) {
        super(StandsMod.SHOT, owner, level, look);
        this.damage = damage;
        this.onHit = onHit;
    }

    public void setLife(int ticks) { this.life = ticks; }

    @Override protected Item getDefaultItem() { return Items.SNOWBALL; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > life && !StandsMod.timeStop().isFrozen(this)) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!(getOwner() instanceof ServerPlayer shooter)) return false;
        return super.canHitEntity(e) && StandsMod.abilities().valid(shooter, e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide() || !(getOwner() instanceof ServerPlayer shooter) || !(hit.getEntity() instanceof LivingEntity le)) return;
        if (damage > 0) StandsMod.stands().hit(shooter, le, damage);
        level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1f, 1.4f);
        if (onHit != null) onHit.accept(le.position().add(0, le.getBbHeight() / 2, 0), le);
        onHit = null;
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level().isClientSide()) return;
        if (onHit != null) onHit.accept(hit.getLocation(), null);
        onHit = null;
        discard();
    }

    @Override public boolean shouldBeSaved() { return false; }
}
