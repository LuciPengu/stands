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

/** The Hand — erases space: pulls enemies in, deletes blocks, one giant swipe. */
public final class TheHand extends BaseKit {
    public TheHand(StandManager m, TimeStopManager ts, Abilities a) { super("thehand", m, ts, a, "Punch", "Air Erase", "Erase Blocks", "DELETE"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.sound(p.getLocation(), "ora");
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        m.anim(p, "cast");
        m.sound(p.getLocation(), "hand-teleport");
        if (t != null) { a.pull(p, t); bar(p, "Erased the space between you!"); return true; }
        return a.blink(p, cfg.getDouble("self-distance", 5), false);   // no target: erase the space in front of you
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        org.bukkit.util.RayTraceResult r = p.rayTraceBlocks(cfg.getDouble("range", 5));
        if (r == null || r.getHitBlock() == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("Aim at a block.")); return false; }
        Block c = r.getHitBlock();
        int ticks = (int) (cfg.getDouble("restore-seconds", 5) * 20), n = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            Block b = c.getRelative(x, y, z);
            if (!b.getType().isAir() && a.tempBlock(b, Material.AIR, ticks)) n++;
        }
        if (n == 0) { p.sendActionBar(net.kyori.adventure.text.Component.text("Block erasing is turned off on this server.")); return false; }
        m.anim(p, "punch");
        m.sound(p.getLocation(), "hand-erase");
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.standForward(p, 24);
        m.anim(p, "cast");
        title(p, "DELETE!", "");
        later((long) (cfg.getDouble("windup", 0.5) * 20), () -> {
            if (!p.isOnline() || p.isDead() || ts.isFrozen(p)) return;
            m.sound(p.getLocation(), "hand-erase");
            LivingEntity t = a.strike(p, cfg.getDouble("range", 4), cfg.getDouble("damage", 12), 0);
            if (t != null) t.getWorld().spawnParticle(Particle.REVERSE_PORTAL, t.getLocation().add(0, 1, 0), 60, .4, .8, .4, .1);
        });
        return true;
    }
}
