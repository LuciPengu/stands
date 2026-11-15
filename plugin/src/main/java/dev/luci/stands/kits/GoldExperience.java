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

/** Gold Experience — gives life: summons a wolf, buffs itself, reveals enemies. */
public class GoldExperience extends BaseKit {
    public GoldExperience(StandManager m, TimeStopManager ts, Abilities a) { this("goldenexperience", m, ts, a, "MUDA", "Life Giver", "Life Boost", "Life Sensor"); }
    protected GoldExperience(String id, StandManager m, TimeStopManager ts, Abilities a, String... labels) { super(id, m, ts, a, labels); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "ge-rush", "MUDA MUDA MUDA!", "MUDAAA!");
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "life");
        bar(p, "Life Giver!");
        LivingEntity t = a.target(p, 16);
        Location at = a.aim(p, 3);
        Wolf w = p.getWorld().spawn(at.getBlock().isPassable() ? at : p.getLocation(), Wolf.class, wolf -> {
            wolf.setTamed(true); wolf.setOwner(p); wolf.setPersistent(false);
            wolf.customName(net.kyori.adventure.text.Component.text(p.getName() + "'s life"));
        });
        if (t != null) w.setTarget(t);
        w.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, w.getLocation().add(0, .5, 0), 20, .4, .4, .4, 0);
        later((long) (cfg.getDouble("lifetime", 8) * 20), () -> { if (w.isValid()) { w.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, w.getLocation(), 10, .3, .3, .3, 0); w.remove(); } });
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 6);
        m.anim(p, "pose");
        m.sound(p.getLocation(), "life");
        bar(p, "Life energy!");
        a.buff(p, secs, PotionEffectType.SPEED, 1);
        m.bonusNextHit(p, cfg.getDouble("next-hit-bonus", 3), (int) (secs * 20));
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, .4, .7, .4, 0);
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "life");
        int n = a.reveal(p, cfg.getDouble("radius", 40), cfg.getDouble("duration", 8));
        title(p, "LIFE SENSOR!", n + " life form" + (n == 1 ? "" : "s") + " found");
        return true;
    }
}
