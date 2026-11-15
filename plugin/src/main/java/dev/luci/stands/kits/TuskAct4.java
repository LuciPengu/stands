package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.TimeStopManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/** Tusk Act 4 — nail sniper; Infinite Rotation is a short, nasty debuff. */
public final class TuskAct4 extends BaseKit {
    public TuskAct4(StandManager m, TimeStopManager ts, Abilities a) { super("tuskact4", m, ts, a, "Nail Shot", "Nail Burst", "Golden Spin", "Infinite Rotation"); }

    private void spin(Player p, LivingEntity t, int ticks, double dps) {
        for (int i = 0; i < ticks; i += 2) {
            final boolean dmgTick = i % 20 == 0 && i > 0;
            later(i, () -> {
                if (!a.valid(p, t)) return;
                t.setRotation(t.getLocation().getYaw() + 30, t.getLocation().getPitch());
                t.getWorld().spawnParticle(Particle.ENCHANTED_HIT, t.getLocation().add(0, 1, 0), 3, .3, .5, .3, 0);
                if (dmgTick && dps > 0) m.hit(p, t, dps);
            });
        }
        a.buff(t, ticks / 20.0, PotionEffectType.SLOWNESS, 1);
    }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.8f);
        a.shoot(p, Material.IRON_NUGGET, p.getEyeLocation().getDirection().multiply(2.6), false, cfg.getDouble("damage", 4), null);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        int n = cfg.getInt("nails", 5);
        double dmg = cfg.getDouble("damage-per-nail", 3);
        m.animFor(p, "rush", n * 3);
        m.sound(p.getLocation(), "tusk");
        bar(p, "ORA ORA!");
        for (int i = 0; i < n; i++) later(i * 3L, () -> {
            if (!p.isOnline() || ts.isFrozen(p)) return;
            a.shoot(p, Material.IRON_NUGGET, p.getEyeLocation().getDirection().multiply(2.6), false, dmg, null);
        });
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        m.sound(p.getLocation(), "tusk");
        bar(p, "Golden Spin!");
        int ticks = (int) (cfg.getDouble("spin-seconds", 2) * 20);
        a.shoot(p, Material.GOLD_NUGGET, p.getEyeLocation().getDirection().multiply(2.4), false, cfg.getDouble("damage", 7), (loc, le) -> { if (le != null) spin(p, le, ticks, 0); });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        m.sound(p.getLocation(), "tusk");
        title(p, "INFINITE ROTATION!", "");
        int ticks = (int) (cfg.getDouble("duration", 6) * 20);
        double dps = cfg.getDouble("damage-per-second", 1.5);
        Snowball s = a.shoot(p, Material.GOLD_INGOT, p.getEyeLocation().getDirection().multiply(2.0), false, cfg.getDouble("damage", 4), (loc, le) -> { if (le != null) spin(p, le, ticks, dps); });
        a.home(s, p, 5, 2.0);
        return true;
    }
}
