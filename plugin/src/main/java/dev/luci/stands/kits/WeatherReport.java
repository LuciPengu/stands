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

/** Weather Report — lightning, wind, and an area blind. */
public final class WeatherReport extends BaseKit {
    public WeatherReport(StandManager m, TimeStopManager ts, Abilities a) { super("weatherreport", m, ts, a, "Lightning", "Wind Boost", "Gust", "Heavy Weather"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        double range = cfg.getDouble("range", 20);
        LivingEntity t = a.target(p, range);
        Location at = t != null ? t.getLocation() : a.aim(p, range);
        m.anim(p, "cast");
        at.getWorld().strikeLightningEffect(at);
        for (LivingEntity le : a.around(p, at, cfg.getDouble("radius", 2))) m.hit(p, le, cfg.getDouble("damage", 5));
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "dash");
        bar(p, "Wind Boost!");
        a.buff(p, cfg.getDouble("duration", 5), PotionEffectType.SPEED, 1);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 30, .5, .2, .5, .1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "punch");
        m.standForward(p, 10);
        double kb = cfg.getDouble("knockback", 1.8);
        Location fx = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2));
        p.getWorld().spawnParticle(Particle.CLOUD, fx, 50, 1, .6, 1, .25);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.7f);
        Vector push = p.getLocation().getDirection().setY(0).normalize();
        for (LivingEntity le : a.cone(p, cfg.getDouble("range", 7), 0.5)) {
            m.hit(p, le, cfg.getDouble("damage", 2));
            if (!ts.isFrozen(le)) le.setVelocity(push.clone().multiply(kb).setY(0.5));
        }
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        title(p, "HEAVY WEATHER!", "");
        double radius = cfg.getDouble("radius", 10), secs = cfg.getDouble("duration", 4);
        Random r = new Random();
        for (int i = 0; i < 4; i++) later(i * 6L, () -> p.getWorld().strikeLightningEffect(p.getLocation().add(r.nextDouble() * radius * 2 - radius, 0, r.nextDouble() * radius * 2 - radius)));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 2, 0), 200, radius / 2, 1.5, radius / 2, .02);
        for (LivingEntity le : a.around(p, p.getLocation(), radius)) {
            m.hit(p, le, cfg.getDouble("damage", 4));
            a.buff(le, secs, PotionEffectType.BLINDNESS, 0);
            a.buff(le, secs, PotionEffectType.SLOWNESS, 1);
        }
        return true;
    }
}
