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

/** Hermit Purple — vines: whip, grapple, trap, and a sustain ultimate. */
public final class HermitPurple extends BaseKit {
    public HermitPurple(StandManager m, TimeStopManager ts, Abilities a) { super("hermitpurple", m, ts, a, "Whip", "Grapple", "Vine Trap", "Hermit Boost"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        double range = cfg.getDouble("range", 5);
        line(p, range, Particle.WITCH);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.6f);
        LivingEntity t = a.target(p, range);
        if (t != null) m.hit(p, t, cfg.getDouble("damage", 6));
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        org.bukkit.util.RayTraceResult r = p.rayTraceBlocks(cfg.getDouble("range", 14));
        if (r == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("Aim at a block to grapple.")); return false; }
        line(p, p.getEyeLocation().distance(r.getHitPosition().toLocation(p.getWorld())), Particle.WITCH);
        Vector v = r.getHitPosition().subtract(p.getEyeLocation().toVector()).normalize().multiply(cfg.getDouble("speed", 1.5));
        p.setVelocity(v.setY(v.getY() + 0.3));
        p.setFallDistance(0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        Location at = a.aim(p, cfg.getDouble("range", 8));
        bar(p, "Vines planted");
        double dmg = cfg.getDouble("damage", 3), secs = cfg.getDouble("root-seconds", 2);
        a.trap(p, at, 1.5, (int) (cfg.getDouble("lifetime", 12) * 20), Particle.WITCH, le -> { m.hit(p, le, dmg); a.root(le, secs); });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 8);
        m.sound(p.getLocation(), "reverse");
        title(p, "HERMIT PURPLE!", "");
        a.heal(p, cfg.getDouble("heal", 4));
        a.buff(p, secs, PotionEffectType.REGENERATION, 0);
        a.buff(p, secs, PotionEffectType.SPEED, 0);
        return true;
    }
}
