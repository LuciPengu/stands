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

/** D4C — slips between dimensions: untouchable hops, a decoy, and Love Train damage reduction. */
public final class D4C extends BaseKit {
    public D4C(StandManager m, TimeStopManager ts, Abilities a) { super("d4c", m, ts, a, "Punch", "Dimension Hop", "Decoy", "Love Train"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "dash");
        m.sound(p.getLocation(), "dojyaan");
        bar(p, "Dojyaaan~!");
        a.blink(p, cfg.getDouble("distance", 8), true);
        a.ghost(p, (int) (cfg.getDouble("duration", 1.5) * 20), null);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        int ticks = (int) (cfg.getDouble("duration", 6) * 20);
        Location at = p.getLocation();
        ArmorStand decoy = p.getWorld().spawn(at, ArmorStand.class, as -> {
            as.setArms(true); as.setBasePlate(false); as.setInvulnerable(true); as.setPersistent(false);
            org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
            head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, sm -> sm.setOwningPlayer(p));
            as.getEquipment().setHelmet(head);
            as.getEquipment().setChestplate(p.getInventory().getChestplate());
            as.getEquipment().setLeggings(p.getInventory().getLeggings());
            as.getEquipment().setBoots(p.getInventory().getBoots());
            as.addDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());   // nobody can take the armor off the decoy
            as.customName(p.name()); as.setCustomNameVisible(true);
        });
        // You slip away while everything nearby loses track of you.
        for (Entity e : p.getNearbyEntities(20, 20, 20)) if (e instanceof Mob mob && p.equals(mob.getTarget())) mob.setTarget(null);
        a.buff(p, 2, PotionEffectType.INVISIBILITY, 0);
        a.buff(p, 2, PotionEffectType.SPEED, 1);
        m.hideStand(p, 40);
        m.sound(at, "dojyaan");
        later(ticks, () -> { if (decoy.isValid()) { decoy.getWorld().spawnParticle(Particle.CLOUD, decoy.getLocation().add(0, 1, 0), 15, .3, .6, .3, .02); decoy.remove(); } });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 5);
        m.anim(p, "pose");
        m.sound(p.getLocation(), "dojyaan");
        title(p, "LOVE TRAIN", "Damage against you is halved");
        m.setVulnerable(p, cfg.getDouble("damage-taken-multiplier", 0.5), (int) (secs * 20));
        for (int t = 0; t < secs * 20; t += 4) later(t, () -> { if (p.isOnline()) p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 12, .8, 1, .8, 0); });
        return true;
    }
}
