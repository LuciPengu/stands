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

/** Anubis — cursed sword: hits harder the more times it hits the same target. */
public final class Anubis extends BaseKit {
    private record Memory(UUID target, int stacks) { }
    private final Map<UUID, Memory> memory = new HashMap<>();
    public Anubis(StandManager m, TimeStopManager ts, Abilities a) { super("anubis", m, ts, a, "Slash", "Anubis Sense", "Dash Slash", "Possession"); }
    @Override public boolean hasModel() { return false; }

    private void slash(Player p, double range, double base, double perStack, int maxStacks) {
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.8)), 2, .3, .2, .3, 0);
        m.sound(p.getLocation(), "chariot-slash");
        for (LivingEntity le : a.cone(p, range, 0.5)) {
            Memory mem = memory.get(p.getUniqueId());
            int stacks = mem != null && mem.target.equals(le.getUniqueId()) ? mem.stacks : 0;
            m.hit(p, le, base + stacks * perStack);
            memory.put(p.getUniqueId(), new Memory(le.getUniqueId(), Math.min(maxStacks, stacks + 1)));
            if (stacks > 0) bar(p, "Anubis remembers: +" + stacks);
            break;
        }
    }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        slash(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), cfg.getDouble("damage-per-stack", 1), cfg.getInt("max-stacks", 4));
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 5);
        bar(p, "Anubis Sense!");
        a.buff(p, secs, PotionEffectType.SPEED, 1);
        a.buff(p, secs, PotionEffectType.JUMP_BOOST, 1);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        a.dash(p, cfg.getDouble("distance", 5));
        ConfigurationSection b = c("basic");
        later(4, () -> { if (p.isOnline() && !ts.isFrozen(p)) slash(p, 3.5, cfg.getDouble("damage", 5), b.getDouble("damage-per-stack", 1), b.getInt("max-stacks", 4)); });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        title(p, "ANUBIS!", "");
        a.dash(p, cfg.getDouble("distance", 8));
        double dmg = cfg.getDouble("damage", 9);
        later(5, () -> {
            if (!p.isOnline() || ts.isFrozen(p)) return;
            m.sound(p.getLocation(), "chariot-slash");
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 8, 1.5, .3, 1.5, 0);
            for (LivingEntity le : a.around(p, p.getLocation(), 3.5)) m.hit(p, le, dmg);
        });
        return true;
    }
}
