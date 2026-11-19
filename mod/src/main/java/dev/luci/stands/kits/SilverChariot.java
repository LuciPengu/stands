package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Silver Chariot — fastest stand: short cooldowns, lunges, and a glass-cannon ultimate. */
public final class SilverChariot extends BaseKit {
    public SilverChariot(StandManager m, TimeStopManager ts, Abilities a) { super("silverchariot", m, ts, a, "Lunge", "Flurry", "Sword Tip", "Armor Off"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.anim(p, "dash");
        m.standForward(p, 12);
        m.sound(p, "chariot-slash");
        a.dash(p, cfg.getDouble("distance", 4));
        double dmg = cfg.getDouble("damage", 6), range = cfg.getDouble("range", 3.5);
        later(4, () -> {
            if (!online(p) || !p.isAlive() || ts.isFrozen(p)) return;
            m.anim(p, "punch");
            line(p, range, ParticleTypes.CRIT);
            for (LivingEntity le : a.cone(p, range, 0.7)) m.hit(p, le, dmg);
        });
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "chariot-slash", "HORA HORA HORA!", null);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        m.sound(p, "chariot-slash");
        bar(p, "Sword Ejection!");
        double kb = cfg.getDouble("knockback", 1.0);
        a.shoot(p, StandsMod.look(Items.STICK), p.getLookAngle().scale(2.6), false, cfg.getDouble("damage", 8), (loc, le) -> {
            if (le != null && !ts.isFrozen(le)) {
                Vec3 d = Fx.flatDir(p).scale(kb);
                Fx.push(le, new Vec3(d.x, 0.3, d.z));
            }
        });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 8);
        m.anim(p, "pose");
        m.sound(p, "armor-off");
        title(p, "ARMOR OFF!", "Faster, but you take more damage");
        a.buff(p, secs, MobEffects.SPEED, 2);
        a.buff(p, secs, MobEffects.JUMP_BOOST, 1);
        m.setVulnerable(p, cfg.getDouble("damage-taken-multiplier", 1.2), (int) (secs * 20));
        return true;
    }
}
