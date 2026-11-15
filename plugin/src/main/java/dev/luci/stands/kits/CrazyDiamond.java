package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.List;

/** Crazy Diamond — brawler that outlasts you by fixing itself. */
public final class CrazyDiamond extends BaseKit {
    private static final List<PotionEffectType> BAD = List.of(PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.MINING_FATIGUE);

    public CrazyDiamond(StandManager m, TimeStopManager ts, Abilities a) { super("crazydiamond", m, ts, a, "DORA", "Restore", "Fix Gear", "Full Restore"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "crazydiamond-rush", "DORARARARA!", "DORAAA!");
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "cast");
        m.sound(p.getLocation(), "reverse");
        bar(p, "Restore!");
        a.heal(p, cfg.getDouble("instant-heal", 3));
        a.buff(p, cfg.getDouble("regen-seconds", 3), PotionEffectType.REGENERATION, 2);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        int fixed = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !(it.getItemMeta() instanceof Damageable d) || !d.hasDamage()) continue;
            it.editMeta(Damageable.class, meta -> meta.setDamage(0));
            fixed++;
        }
        if (fixed == 0) { p.sendActionBar(net.kyori.adventure.text.Component.text("Nothing to fix.")); return false; }
        m.anim(p, "cast");
        m.sound(p.getLocation(), "reverse");
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, .5, .6, .5, 0);
        bar(p, "Fixed " + fixed + " item" + (fixed == 1 ? "" : "s") + "!");
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "pose");
        m.sound(p.getLocation(), "reverse");
        title(p, "CRAZY DIAMOND!", "Restore!");
        double amount = cfg.getDouble("heal", 12), radius = cfg.getDouble("radius", 6);
        restore(p, amount);
        // Teammates = players on the same scoreboard team. Never heals enemies.
        Team team = p.getScoreboard().getEntryTeam(p.getName());
        if (team != null) for (Player o : p.getWorld().getPlayers())
            if (!o.equals(p) && o.getLocation().distance(p.getLocation()) <= radius && team.hasEntry(o.getName())) restore(o, amount);
        return true;
    }

    private void restore(Player p, double amount) {
        a.heal(p, amount);
        BAD.forEach(p::removePotionEffect);
        p.setFireTicks(0);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, .5, .7, .5, 0);
    }
}
