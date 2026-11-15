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

/** Magician's Red — fire zoner. */
public final class MagiciansRed extends BaseKit {
    public MagiciansRed(StandManager m, TimeStopManager ts, Abilities a) { super("magiciansred", m, ts, a, "Fireball", "Flame Wall", "Power Boost", "Crossfire Hurricane"); }

    private void fireball(Player p, Vector v, double dmg, int fireTicks) {
        a.shoot(p, Material.FIRE_CHARGE, v, false, dmg, (loc, le) -> {
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 25, .4, .4, .4, .05);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.8f, 0.7f);
            if (le != null) le.setFireTicks(fireTicks);
        });
    }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        m.sound(p.getLocation(), "fire");
        fireball(p, p.getEyeLocation().getDirection().multiply(1.7), cfg.getDouble("damage", 5), (int) (cfg.getDouble("burn-seconds", 3) * 20));
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        Location at = a.aim(p, cfg.getDouble("range", 7));
        m.anim(p, "cast");
        m.sound(at, "fire");
        bar(p, "Flame Wall!");
        double dmg = cfg.getDouble("damage-per-tick", 1.5);
        a.zone(p, at, cfg.getDouble("radius", 2.5), (int) (cfg.getDouble("duration", 4) * 20), 10, Particle.FLAME, le -> { m.hit(p, le, dmg); le.setFireTicks(40); });
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 6);
        m.anim(p, "pose");
        m.sound(p.getLocation(), "fire");
        bar(p, "Power Boost!");
        a.buff(p, secs, PotionEffectType.SPEED, 1);
        a.buff(p, secs, PotionEffectType.FIRE_RESISTANCE, 0);
        m.bonusNextHit(p, cfg.getDouble("next-hit-bonus", 4), (int) (secs * 20));
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, .4, .7, .4, .05);
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        m.sound(p.getLocation(), "fire");
        title(p, "CROSSFIRE HURRICANE!", "");
        int n = cfg.getInt("fireballs", 5);
        Location eye = p.getEyeLocation();
        for (int i = 0; i < n; i++) {
            Location l = eye.clone();
            l.setYaw(l.getYaw() - 20 + 40f * i / Math.max(1, n - 1));
            fireball(p, l.getDirection().multiply(1.5), cfg.getDouble("damage", 4), 80);
        }
        return true;
    }
}
