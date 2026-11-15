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

/** Aerosmith — plane with a minigun, bombs and radar. */
public final class Aerosmith extends BaseKit {
    public Aerosmith(StandManager m, TimeStopManager ts, Abilities a) { super("aerosmith", m, ts, a, "Minigun", "Bomb", "CO2 Radar", "Strafing Run"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        int shots = cfg.getInt("shots", 8);
        double dmg = cfg.getDouble("damage-per-shot", 1);
        m.animFor(p, "rush", shots * 2);
        m.standForward(p, shots * 2 + 6);
        m.sound(p.getLocation(), "minigun");
        bar(p, "VOLA VOLA VOLA!");
        Random r = new Random();
        for (int i = 0; i < shots; i++) later(i * 2L, () -> {
            if (!p.isOnline() || ts.isFrozen(p)) return;
            Vector v = p.getEyeLocation().getDirection().add(new Vector(r.nextGaussian() * .03, r.nextGaussian() * .03, r.nextGaussian() * .03)).multiply(3);
            a.shoot(p, Material.IRON_NUGGET, v, false, dmg, null);
        });
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        m.sound(p.getLocation(), "bomb");
        double radius = cfg.getDouble("radius", 3), dmg = cfg.getDouble("damage", 7);
        a.shoot(p, Material.TNT, p.getEyeLocation().getDirection().multiply(1.2), true, 0, (loc, le) -> a.blast(p, loc, radius, dmg, 0.8));
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "sonar");
        int n = a.reveal(p, cfg.getDouble("radius", 30), cfg.getDouble("duration", 6));
        bar(p, "Radar: " + n + " target" + (n == 1 ? "" : "s"));
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "dash");
        m.standForward(p, 40);
        m.sound(p.getLocation(), "minigun");
        title(p, "AEROSMITH!", "Strafing run");
        int blasts = cfg.getInt("blasts", 8);
        double dmg = cfg.getDouble("damage-per-blast", 3);
        Location start = p.getLocation().add(0, 0.5, 0);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        for (int i = 0; i < blasts; i++) {
            Location at = start.clone().add(dir.clone().multiply(2 + i * 2));
            later(4L + i * 3L, () -> a.blast(p, at, 2.2, dmg, 0.3));
        }
        return true;
    }
}
