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

/** Metallica — invisible stand: iron attacks from range, then vanish. */
public final class Metallica extends BaseKit {
    public Metallica(StandManager m, TimeStopManager ts, Abilities a) { super("metallica", m, ts, a, "Iron Spikes", "Razor Blades", "Iron Grab", "Vanish"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 6));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.8f);
        if (t == null) return true;
        m.hit(p, t, cfg.getDouble("damage", 5));
        t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 1, 0), 30, .3, .5, .3, Material.IRON_BLOCK.createBlockData());
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 8));
        if (t == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("No target.")); return false; }
        bar(p, "Razor blades!");
        p.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
        a.dot(p, t, cfg.getDouble("damage-per-second", 1), cfg.getInt("seconds", 5), 20, Particle.DAMAGE_INDICATOR);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        if (t == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("No target.")); return false; }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
        a.pull(p, t);
        m.hit(p, t, cfg.getDouble("damage", 2));
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        title(p, "METALLICA", "You can't be seen or hit, and you can't attack");
        a.buff(p, cfg.getDouble("duration", 5), PotionEffectType.SPEED, 0);
        a.ghost(p, (int) (cfg.getDouble("duration", 5) * 20), null);
        return true;
    }
}
