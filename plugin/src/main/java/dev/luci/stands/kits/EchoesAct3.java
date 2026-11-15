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

/** Echoes Act 3 — control: burn, shove, and pin one target to the ground. */
public final class EchoesAct3 extends BaseKit {
    public EchoesAct3(StandManager m, TimeStopManager ts, Abilities a) { super("echoesact3", m, ts, a, "Punch", "FWOOSH", "SPLASH", "Three Freeze"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), 0.6);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        double range = cfg.getDouble("range", 8);
        m.anim(p, "cast");
        m.sound(p.getLocation(), "echoes");
        bar(p, "FWOOSH!");
        line(p, range, Particle.FLAME);
        LivingEntity t = a.target(p, range);
        if (t != null) { m.hit(p, t, cfg.getDouble("damage", 3)); t.setFireTicks((int) (cfg.getDouble("burn-seconds", 4) * 20)); }
        else { Location at = a.aim(p, range); a.zone(p, at, 1.5, 60, 10, Particle.FLAME, le -> le.setFireTicks(40)); }   // no real fire block: it could spread
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "punch");
        m.sound(p.getLocation(), "echoes");
        bar(p, "SPLASH!");
        Location fx = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2));
        p.getWorld().spawnParticle(Particle.SPLASH, fx, 120, 1, .6, 1, .3);
        p.getWorld().playSound(fx, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.9f);
        Vector push = p.getLocation().getDirection().setY(0).normalize();
        for (LivingEntity le : a.cone(p, cfg.getDouble("range", 6), 0.5)) {
            m.hit(p, le, cfg.getDouble("damage", 2));
            le.setFireTicks(0);
            a.buff(le, 2, PotionEffectType.SLOWNESS, 0);
            if (!ts.isFrozen(le)) le.setVelocity(push.clone().multiply(cfg.getDouble("knockback", 1.5)).setY(0.4));
        }
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        if (t == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("No target.")); return false; }
        m.anim(p, "pose");
        m.sound(p.getLocation(), "echoes");
        title(p, "THREE FREEZE!", "");
        m.hit(p, t, cfg.getDouble("damage", 4));
        a.root(t, cfg.getDouble("duration", 3));
        t.getWorld().spawnParticle(Particle.ENCHANTED_HIT, t.getLocation().add(0, 1, 0), 60, .4, .8, .4, 0);
        return true;
    }
}
