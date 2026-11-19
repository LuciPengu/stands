package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.entity.StandShot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/** Tusk Act 4 — nail sniper; Infinite Rotation is a short, nasty debuff. */
public final class TuskAct4 extends BaseKit {
    public TuskAct4(StandManager m, TimeStopManager ts, Abilities a) { super("tuskact4", m, ts, a, "Nail Shot", "Nail Burst", "Golden Spin", "Infinite Rotation"); }

    private void spin(ServerPlayer p, LivingEntity t, int ticks, double dps) {
        for (int i = 0; i < ticks; i += 2) {
            final boolean dmgTick = i % 20 == 0 && i > 0;
            later(i, () -> {
                if (!a.valid(p, t)) return;
                float yaw = t.getYRot() + 30;
                if (t instanceof ServerPlayer tp) Fx.teleport(tp, tp.position(), yaw, tp.getXRot());
                else { t.setYRot(yaw); t.setYHeadRot(yaw); t.setYBodyRot(yaw); }
                if (t.level() instanceof ServerLevel l) Fx.particle(l, ParticleTypes.ENCHANTED_HIT, t.position().add(0, 1, 0), 3, .3, .5, .3, 0);
                if (dmgTick && dps > 0) m.hit(p, t, dps);
            });
        }
        a.buff(t, ticks / 20.0, MobEffects.SLOWNESS, 1);
    }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        Fx.sound(p.level(), p.position(), SoundEvents.ARROW_SHOOT, 1f, 1.8f);
        a.shoot(p, StandsMod.look(Items.IRON_NUGGET), p.getLookAngle().scale(2.6), false, cfg.getDouble("damage", 4), null);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        int n = cfg.getInt("nails", 5);
        double dmg = cfg.getDouble("damage-per-nail", 3);
        m.animFor(p, "rush", n * 3);
        m.sound(p, "tusk");
        bar(p, "ORA ORA!");
        for (int i = 0; i < n; i++) later(i * 3L, () -> {
            if (!online(p) || ts.isFrozen(p)) return;
            a.shoot(p, StandsMod.look(Items.IRON_NUGGET), p.getLookAngle().scale(2.6), false, dmg, null);
        });
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        m.sound(p, "tusk");
        bar(p, "Golden Spin!");
        int ticks = (int) (cfg.getDouble("spin-seconds", 2) * 20);
        a.shoot(p, StandsMod.look(Items.GOLD_NUGGET), p.getLookAngle().scale(2.4), false, cfg.getDouble("damage", 7), (loc, le) -> { if (le != null) spin(p, le, ticks, 0); });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        m.sound(p, "tusk");
        title(p, "INFINITE ROTATION!", "");
        int ticks = (int) (cfg.getDouble("duration", 6) * 20);
        double dps = cfg.getDouble("damage-per-second", 1.5);
        StandShot s = a.shoot(p, StandsMod.look(Items.GOLD_INGOT), p.getLookAngle().scale(2.0), false, cfg.getDouble("damage", 4), (loc, le) -> { if (le != null) spin(p, le, ticks, dps); });
        a.home(s, p, 5, 2.0);
        return true;
    }
}
