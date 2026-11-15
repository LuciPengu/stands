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

/** Killer Queen — plant a bomb, detonate it later. Bites the Dust is a last-chance escape. */
public final class KillerQueen extends BaseKit {
    private record Bomb(LivingEntity entity, Location spot, long expires) { }
    private final Map<UUID, Bomb> bombs = new HashMap<>();

    public KillerQueen(StandManager m, TimeStopManager ts, Abilities a) { super("killerqueen", m, ts, a, "Punch", "Plant Bomb", "Sheer Heart Attack", "Bites the Dust"); }

    private Bomb bomb(Player p) {
        Bomb b = bombs.get(p.getUniqueId());
        if (b != null && System.currentTimeMillis() > b.expires) { bombs.remove(p.getUniqueId()); return null; }
        return b;
    }

    @Override public String label(Player p, Slot slot) { return slot == Slot.SKILL && bomb(p) != null ? "DETONATE" : super.label(p, slot); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        Bomb b = bomb(p);
        if (b == null) {   // plant: no cooldown yet
            LivingEntity t = a.target(p, cfg.getDouble("plant-range", 5));
            Location spot = t != null ? null : a.aim(p, cfg.getDouble("plant-range", 5));
            bombs.put(p.getUniqueId(), new Bomb(t, spot, System.currentTimeMillis() + (long) (cfg.getDouble("fuse-seconds", 20) * 1000)));
            m.anim(p, "cast");
            Location fx = t != null ? t.getLocation().add(0, 1, 0) : spot;
            fx.getWorld().spawnParticle(Particle.ENCHANTED_HIT, fx, 15, .3, .3, .3, 0);
            fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.8f);
            bar(p, t != null ? "Bomb planted on " + t.getName() + " — use again to detonate" : "Bomb planted — use again to detonate");
            return false;
        }
        bombs.remove(p.getUniqueId());
        Location at = b.entity != null ? (b.entity.isValid() ? b.entity.getLocation().add(0, 1, 0) : null) : b.spot;
        m.anim(p, "pose");
        m.sound(p.getLocation(), "detonate");
        bar(p, "Click.");
        if (at != null) a.blast(p, at, cfg.getDouble("radius", 3), cfg.getDouble("damage", 8), 0.8);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        bar(p, "Sheer Heart Attack!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 1.4f);
        double radius = cfg.getDouble("radius", 2.5), dmg = cfg.getDouble("damage", 6);
        Snowball s = a.shoot(p, Material.SKELETON_SKULL, p.getEyeLocation().getDirection().multiply(0.7), false, 0, (loc, le) -> a.blast(p, loc, radius, dmg, 0.6));
        a.home(s, p, cfg.getDouble("homing-range", 12), 0.7);
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double threshold = cfg.getDouble("health-threshold", 8);
        if (p.getHealth() > threshold) { p.sendActionBar(net.kyori.adventure.text.Component.text("Bites the Dust only works at " + (int) (threshold / 2) + " hearts or less.")); return false; }
        m.anim(p, "pose");
        m.sound(p.getLocation(), "bites-the-dust");
        for (LivingEntity le : a.around(p, p.getLocation(), 12)) a.buff(le, 1.5, PotionEffectType.BLINDNESS, 0);
        p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), cfg.getDouble("heal-to", 14)));
        Location home = p.getRespawnLocation() != null ? p.getRespawnLocation() : p.getWorld().getSpawnLocation();
        p.setFallDistance(0);
        p.teleport(home);
        title(p, "BITES THE DUST!", "Time rewinds");
        return true;
    }
}
