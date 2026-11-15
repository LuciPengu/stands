package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Silver Chariot — fastest stand: short cooldowns, lunges, and a glass-cannon ultimate. */
public final class SilverChariot extends BaseKit {
    public SilverChariot(StandManager m, TimeStopManager ts, Abilities a) { super("silverchariot", m, ts, a, "Lunge", "Flurry", "Sword Tip", "Armor Off"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        m.anim(p, "dash");
        m.standForward(p, 12);
        m.sound(p.getLocation(), "chariot-slash");
        a.dash(p, cfg.getDouble("distance", 4));
        double dmg = cfg.getDouble("damage", 6), range = cfg.getDouble("range", 3.5);
        later(4, () -> {
            if (!p.isOnline() || p.isDead() || ts.isFrozen(p)) return;
            m.anim(p, "punch");
            line(p, range, Particle.CRIT);
            for (LivingEntity le : a.cone(p, range, 0.7)) m.hit(p, le, dmg);
        });
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "chariot-slash", "HORA HORA HORA!", null);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        m.sound(p.getLocation(), "chariot-slash");
        bar(p, "Sword Ejection!");
        double kb = cfg.getDouble("knockback", 1.0);
        a.shoot(p, Material.STICK, p.getEyeLocation().getDirection().multiply(2.6), false, cfg.getDouble("damage", 8), (loc, le) -> {
            if (le != null && !ts.isFrozen(le)) le.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(kb).setY(0.3));
        });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 8);
        m.anim(p, "pose");
        m.sound(p.getLocation(), "armor-off");
        title(p, "ARMOR OFF!", "Faster, but you take more damage");
        a.buff(p, secs, PotionEffectType.SPEED, 2);
        a.buff(p, secs, PotionEffectType.JUMP_BOOST, 1);
        m.setVulnerable(p, cfg.getDouble("damage-taken-multiplier", 1.2), (int) (secs * 20));
        return true;
    }
}
