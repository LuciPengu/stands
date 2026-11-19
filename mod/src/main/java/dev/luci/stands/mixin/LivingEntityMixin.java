package dev.luci.stands.mixin;

import dev.luci.stands.StandsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets stands scale incoming damage (Armor Off, time-stop cap). Cancelling is done with Fabric's ALLOW_DAMAGE. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float stands$modifyDamage(float damage, ServerLevel level, DamageSource source) {
        return StandsMod.modifyDamage((LivingEntity) (Object) this, source, damage);
    }
}
