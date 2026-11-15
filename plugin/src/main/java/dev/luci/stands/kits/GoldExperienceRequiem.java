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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Gold Experience Requiem — Gold Experience, stronger, with Return to Zero instead of the old permanent full-heal. */
public final class GoldExperienceRequiem extends GoldExperience implements Listener {
    private final Map<UUID, Long> zero = new HashMap<>();

    public GoldExperienceRequiem(StandManager m, TimeStopManager ts, Abilities a) {
        super("ger", m, ts, a, "MUDA", "Life Giver", "Life Boost", "Return to Zero");
        Bukkit.getPluginManager().registerEvents(this, m.plugin());
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("window", 5);
        zero.put(p.getUniqueId(), System.currentTimeMillis() + (long) (secs * 1000));
        m.anim(p, "pose");
        m.sound(p.getLocation(), "ger");
        title(p, "RETURN TO ZERO", "The next killing blow never happens");
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 60, .5, .8, .5, .05);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = zero.get(p.getUniqueId());
        if (until == null || System.currentTimeMillis() > until || e.getFinalDamage() < p.getHealth()) return;
        zero.remove(p.getUniqueId());
        e.setCancelled(true);
        ConfigurationSection cfg = c("ultimate");
        a.heal(p, cfg.getDouble("heal", 6));
        m.sound(p.getLocation(), "ger");
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 80, .6, .9, .6, .1);
        bar(p, "This is... Requiem.");
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity src = by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : by.getDamager();
            if (src instanceof LivingEntity attacker && a.valid(p, attacker)) m.hit(p, attacker, cfg.getDouble("reflect-damage", 8));
        }
    }
}
