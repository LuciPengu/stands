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

/** The Emperor — a gun. No stand model; the disc is the gun. */
public final class TheEmperor extends BaseKit {
    public TheEmperor(StandManager m, TimeStopManager ts, Abilities a) { super("theemperor", m, ts, a, "Shoot", "Curving Shot", "Slide", "Bullet Storm"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.sound(p.getLocation(), "gun");
        a.hitscan(p, cfg.getDouble("range", 40), cfg.getDouble("damage", 4), Particle.CRIT);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.sound(p.getLocation(), "gun");
        bar(p, "The bullet is a stand too!");
        Snowball s = a.shoot(p, Material.IRON_NUGGET, p.getEyeLocation().getDirection().multiply(1.6), false, cfg.getDouble("damage", 6), null);
        a.home(s, p, cfg.getDouble("homing-range", 8), 1.6);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        a.dash(p, cfg.getDouble("distance", 6));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.3f);
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        title(p, "EMPEROR!", "Bullet storm");
        int shots = cfg.getInt("shots", 6);
        double dmg = cfg.getDouble("damage-per-shot", 3), range = cfg.getDouble("range", 40);
        for (int i = 0; i < shots; i++) later(i * 3L, () -> {
            if (!p.isOnline() || ts.isFrozen(p)) return;
            m.sound(p.getLocation(), "gun");
            a.hitscan(p, range, dmg, Particle.CRIT);
        });
        return true;
    }
}
