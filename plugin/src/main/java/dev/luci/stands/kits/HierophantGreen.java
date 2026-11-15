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

/** Hierophant Green — long-range emeralds and control. */
public final class HierophantGreen extends BaseKit {
    public HierophantGreen(StandManager m, TimeStopManager ts, Abilities a) { super("heirophantgreen", m, ts, a, "Emerald Splash", "Webs", "Tendril Grab", "20m Emerald Splash"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "emerald-splash");
        bar(p, "Emerald Splash!");
        a.volley(p, Material.EMERALD, cfg.getInt("emeralds", 4), 14, 2.0, false, cfg.getDouble("damage-per-emerald", 2), null);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "web");
        Location eye = p.getEyeLocation();
        int ticks = (int) (cfg.getDouble("duration", 5) * 20), placed = 0;
        for (double d = 3; d <= 6; d += 1) {
            Block b = eye.clone().add(eye.getDirection().multiply(d)).getBlock();
            if (b.getType().isAir() && a.tempBlock(b, Material.COBWEB, ticks)) placed++;
        }
        // Fallback when world edits are off or there's no room: a slowing zone.
        if (placed == 0) a.zone(p, a.aim(p, 5), 2, ticks, 10, Particle.ITEM_SLIME, le -> a.buff(le, 1, PotionEffectType.SLOWNESS, 3));
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        double range = cfg.getDouble("range", 12);
        m.anim(p, "cast");
        line(p, range, Particle.HAPPY_VILLAGER);
        LivingEntity t = a.target(p, range);
        if (t == null) return true;
        m.hit(p, t, cfg.getDouble("damage", 2));
        a.root(t, cfg.getDouble("root-seconds", 1.5));
        bar(p, "Caught!");
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        title(p, "20 METER EMERALD SPLASH!", "");
        int waves = cfg.getInt("waves", 2), per = cfg.getInt("emeralds-per-wave", 8);
        double dmg = cfg.getDouble("damage-per-emerald", 2);
        for (int w = 0; w < waves; w++) later(w * 6L, () -> {
            if (!p.isOnline() || ts.isFrozen(p)) return;
            m.sound(p.getLocation(), "emerald-splash");
            a.volley(p, Material.EMERALD, per, 50, 2.3, false, dmg, null);
        });
        return true;
    }
}
