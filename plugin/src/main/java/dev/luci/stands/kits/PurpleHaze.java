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

/** Purple Haze — poison brawler. The ultimate hurts the user too. */
public final class PurpleHaze extends BaseKit {
    private final Map<UUID, Long> rage = new HashMap<>();
    public PurpleHaze(StandManager m, TimeStopManager ts, Abilities a) { super("purplehaze", m, ts, a, "Virus Punch", "Rage Mode", "Capsule", "Toxic Haze"); }

    private boolean raging(Player p) { Long u = rage.get(p.getUniqueId()); return u != null && System.currentTimeMillis() < u; }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.sound(p.getLocation(), "virus-punch");
        LivingEntity t = a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), 0.5);
        if (t != null) {
            a.buff(t, cfg.getDouble("poison-seconds", 4), PotionEffectType.POISON, 1);
            t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 20, .3, .5, .3, 0);
        }
        if (raging(p)) { m.cooldown(p, cooldownKey(p, Slot.BASIC), cfg.getDouble("rage-cooldown", 1)); return false; }
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 8);
        rage.put(p.getUniqueId(), System.currentTimeMillis() + (long) (secs * 1000));
        m.anim(p, "pose");
        m.sound(p.getLocation(), "rage");
        title(p, "RAGE MODE", "Virus Punch has almost no cooldown");
        a.buff(p, secs, PotionEffectType.SPEED, 0);
        m.cooldown(p, cooldownKey(p, Slot.BASIC), 0);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        double radius = cfg.getDouble("radius", 3), secs = cfg.getDouble("cloud-seconds", 4);
        a.shoot(p, Material.PURPLE_DYE, p.getEyeLocation().getDirection().multiply(1.3), true, 0, (loc, le) -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
            a.zone(p, loc, radius, (int) (secs * 20), 10, Particle.WITCH, t -> a.buff(t, 3, PotionEffectType.POISON, 1));
        });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        m.sound(p.getLocation(), "ultimate-virus");
        title(p, "TOXIC HAZE!", "It hurts you too");
        double radius = cfg.getDouble("radius", 5), secs = cfg.getDouble("duration", 5);
        Location c = p.getLocation();
        for (LivingEntity le : a.around(p, c, radius)) m.hit(p, le, cfg.getDouble("damage", 4));
        a.zone(p, c, radius, (int) (secs * 20), 10, Particle.WITCH, t -> a.buff(t, 2, PotionEffectType.WITHER, 1));
        a.buff(p, cfg.getDouble("self-wither-seconds", 3), PotionEffectType.WITHER, 0);
        return true;
    }
}
