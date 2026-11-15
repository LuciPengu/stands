package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** King Crimson — one huge punch and time tricks instead of a freeze. */
public final class KingCrimson extends BaseKit {
    public KingCrimson(StandManager m, TimeStopManager ts, Abilities a) { super("kingcrimson", m, ts, a, "Rush", "Jab", "Time Skip", "Time Erase"); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "crimson-jab", "KING CRIMSON!", null);
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        // Wind-up so the big hit can be dodged.
        m.standForward(p, 20);
        bar(p, "...");
        later((long) (cfg.getDouble("windup", 0.4) * 20), () -> {
            if (!p.isOnline() || p.isDead() || ts.isFrozen(p)) return;
            m.sound(p.getLocation(), "crimson-jab");
            if (a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 10), cfg.getDouble("knockback", 1.2)) != null) bar(p, "King Crimson Jab!");
        });
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        if (!a.blink(p, cfg.getDouble("distance", 9), false)) return false;
        m.anim(p, "dash");
        m.sound(p.getLocation(), "time-skip");
        bar(p, "Time Skip");
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        double secs = cfg.getDouble("duration", 4);
        int ticks = (int) (secs * 20);
        double bonus = cfg.getDouble("epitaph-bonus", 6), radius = cfg.getDouble("radius", 20);
        m.anim(p, "pose");
        m.sound(p.getLocation(), "time-erase");
        title(p, "KING CRIMSON!", "Time is erased. Nothing can touch you.");
        a.buff(p, secs, PotionEffectType.SPEED, 2);
        // Everyone nearby loses this stretch of time: they can't see, and you see them through walls.
        for (org.bukkit.entity.LivingEntity le : a.around(p, p.getLocation(), radius)) {
            a.buff(le, secs, PotionEffectType.GLOWING, 0);
            a.buff(le, secs, PotionEffectType.SLOWNESS, 1);
            if (le instanceof Player) { a.buff(le, secs, PotionEffectType.BLINDNESS, 0); a.buff(le, secs, PotionEffectType.DARKNESS, 0); }
        }
        // Countdown bar and a crimson aura so the caster can see it's active.
        var bar = net.kyori.adventure.bossbar.BossBar.bossBar(net.kyori.adventure.text.Component.text("TIME ERASED", net.kyori.adventure.text.format.NamedTextColor.RED),
                1f, net.kyori.adventure.bossbar.BossBar.Color.RED, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        for (int t = 0; t < ticks; t += 2) {
            final float left = 1f - (float) t / ticks;
            later(t, () -> {
                if (!p.isOnline()) return;
                bar.progress(Math.max(0f, left));
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIMSON_SPORE, p.getLocation().add(0, 1, 0), 25, 3, 1.5, 3, 0);
                p.spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 0.2, 0), 2, .3, 0, .3, 0);
            });
        }
        a.ghost(p, ticks, () -> {
            p.hideBossBar(bar);
            m.bonusNextHit(p, bonus, 60);
            m.cooldown(p, cooldownKey(p, dev.luci.stands.StandManager.Slot.BASIC), 0);
            m.cooldown(p, cooldownKey(p, dev.luci.stands.StandManager.Slot.SKILL), 0);
            title(p, "EPITAPH", "Your next hit lands harder");
        });
        return true;
    }
}
