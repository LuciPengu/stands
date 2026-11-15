package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Star Platinum — the hardest-hitting rush, a shorter time stop than The World. */
public final class StarPlatinum extends BaseKit {
    public StarPlatinum(StandManager m, TimeStopManager ts, Abilities a) { super("starplatinum", m, ts, a, "ORA", "Star Finger", "Block Throw", "Time Stop"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "starplatinum-rush", "ORA ORA ORA!", "ORAAA!");
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        double range = cfg.getDouble("range", 7);
        m.anim(p, "cast");
        m.standForward(p, 10);
        m.sound(p.getLocation(), "ora");
        bar(p, "Star Finger!");
        line(p, range, Particle.END_ROD);
        LivingEntity t = a.target(p, range);
        if (t != null) m.hit(p, t, cfg.getDouble("damage", 6));
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.2f);
        double kb = cfg.getDouble("knockback", 1.4);
        a.shoot(p, Material.COBBLESTONE, p.getEyeLocation().getDirection().multiply(1.6), true, cfg.getDouble("damage", 5), (loc, le) -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, .4, .4, .4, Material.COBBLESTONE.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 1.5f, 0.8f);
            if (le != null && !ts.isFrozen(le)) le.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(kb).setY(0.4));
        });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        if (!ts.start(p, cfg.getDouble("duration", 3.5))) return false;
        m.sound(p.getLocation(), "timestop");
        m.standForward(p, 28);
        m.anim(p, "pose");
        title(p, "STAR PLATINUM!", "ZA WARUDO!");
        return true;
    }
}
