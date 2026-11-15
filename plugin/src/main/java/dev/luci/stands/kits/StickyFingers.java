package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Sticky Fingers — slippery brawler: zips through walls, traps, and vanishes. */
public final class StickyFingers extends BaseKit {
    public StickyFingers(StandManager m, TimeStopManager ts, Abilities a) { super("stickyfingers", m, ts, a, "ARI", "Zipper Dash", "Zipper Trap", "Zip Away"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "ari", "ARI ARI ARI!", "ARRIVEDERCI!");
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        if (!a.blink(p, cfg.getDouble("distance", 7), true)) { p.sendActionBar(net.kyori.adventure.text.Component.text("No room to zip there.")); return false; }
        m.anim(p, "dash");
        m.sound(p.getLocation(), "zipper-zip");
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        Location at = a.aim(p, cfg.getDouble("range", 8));
        m.anim(p, "cast");
        m.sound(at, "zipper-place");
        bar(p, "Zipper placed");
        double dmg = cfg.getDouble("damage", 6), slow = cfg.getDouble("slow-seconds", 2);
        a.trap(p, at, 1.5, (int) (cfg.getDouble("lifetime", 12) * 20), Particle.ENCHANTED_HIT, le -> {
            m.sound(at, "zipper-zip");
            m.hit(p, le, dmg);
            a.buff(le, slow, PotionEffectType.SLOWNESS, 3);
            at.getWorld().spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 30, .4, .6, .4, .2);
        });
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        m.anim(p, "dash");
        m.sound(p.getLocation(), "zipper-zip");
        title(p, "STICKY FINGERS!", "Zipped away");
        int ticks = (int) (cfg.getDouble("duration", 3) * 20);
        a.heal(p, cfg.getDouble("heal", 6));
        a.buff(p, ticks / 20.0, PotionEffectType.SPEED, 1);
        a.ghost(p, ticks, () -> m.sound(p.getLocation(), "zipper-zip"));
        return true;
    }
}
