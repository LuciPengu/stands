package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.entity.StandShot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

/** The Emperor — a gun. No stand model. */
public final class TheEmperor extends BaseKit {
    public TheEmperor(StandManager m, TimeStopManager ts, Abilities a) { super("theemperor", m, ts, a, "Shoot", "Curving Shot", "Slide", "Bullet Storm"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.sound(p, "gun");
        a.hitscan(p, cfg.getDouble("range", 40), cfg.getDouble("damage", 4), ParticleTypes.CRIT);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.sound(p, "gun");
        bar(p, "The bullet is a stand too!");
        StandShot s = a.shoot(p, StandsMod.look(Items.IRON_NUGGET), p.getLookAngle().scale(1.6), false, cfg.getDouble("damage", 6), null);
        a.home(s, p, cfg.getDouble("homing-range", 8), 1.6);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        a.dash(p, cfg.getDouble("distance", 6));
        Fx.sound(p.level(), p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 1.3f);
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        title(p, "EMPEROR!", "Bullet storm");
        int shots = cfg.getInt("shots", 6);
        double dmg = cfg.getDouble("damage-per-shot", 3), range = cfg.getDouble("range", 40);
        for (int i = 0; i < shots; i++) later(i * 3L, () -> {
            if (!online(p) || ts.isFrozen(p)) return;
            m.sound(p, "gun");
            a.hitscan(p, range, dmg, ParticleTypes.CRIT);
        });
        return true;
    }
}
