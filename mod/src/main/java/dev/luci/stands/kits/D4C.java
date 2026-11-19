package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/** D4C — slips between dimensions: untouchable hops, a decoy, and Love Train damage reduction. */
public final class D4C extends BaseKit {
    public D4C(StandManager m, TimeStopManager ts, Abilities a) { super("d4c", m, ts, a, "Punch", "Dimension Hop", "Decoy", "Love Train"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "dash");
        m.sound(p, "dojyaan");
        bar(p, "Dojyaaan~!");
        a.blink(p, cfg.getDouble("distance", 8), true);
        a.ghost(p, (int) (cfg.getDouble("duration", 1.5) * 20), null);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        int ticks = (int) (cfg.getDouble("duration", 6) * 20);
        ServerLevel l = p.level();
        Vec3 at = p.position();
        // A Mannequin wearing your skin and a copy of your armor. Never saved, so the armor copy can't be farmed.
        Mannequin decoy = new Mannequin(EntityTypes.MANNEQUIN, l) {
            @Override public boolean shouldBeSaved() { return false; }
            @Override protected void dropAllDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) { }
        };
        decoy.snapTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        decoy.setYHeadRot(p.getYHeadRot());
        decoy.setYBodyRot(p.yBodyRot);
        decoy.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
        decoy.setPermanentlyInvulnerable(true);
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            decoy.setItemSlot(s, p.getItemBySlot(s).copy());
        decoy.setCustomName(p.getName());
        decoy.setCustomNameVisible(true);
        l.addFreshEntity(decoy);
        // You slip away while everything nearby loses track of you.
        for (Mob mob : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20), mob -> mob.getTarget() == p)) mob.setTarget(null);
        a.buff(p, 2, MobEffects.INVISIBILITY, 0);
        a.buff(p, 2, MobEffects.SPEED, 1);
        m.hideStand(p, 40);
        m.sound(l, at, "dojyaan");
        later(ticks, () -> {
            if (!decoy.isRemoved()) { Fx.particle(l, ParticleTypes.CLOUD, decoy.position().add(0, 1, 0), 15, .3, .6, .3, .02); decoy.discard(); }
        });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 5);
        m.anim(p, "pose");
        m.sound(p, "dojyaan");
        title(p, "LOVE TRAIN", "Damage against you is halved");
        m.setVulnerable(p, cfg.getDouble("damage-taken-multiplier", 0.5), (int) (secs * 20));
        for (int t = 0; t < secs * 20; t += 4) later(t, () -> { if (online(p)) Fx.particle(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 12, .8, 1, .8, 0); });
        return true;
    }
}
