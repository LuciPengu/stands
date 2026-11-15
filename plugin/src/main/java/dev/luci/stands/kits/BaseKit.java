package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandKit;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.TimeStopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;

/** Common plumbing: cooldown check, config lookup, labels. Each ability returns true if it actually fired. */
public abstract class BaseKit implements StandKit {
    private static final ConfigurationSection EMPTY = new MemoryConfiguration();
    protected final String id;
    protected final StandManager m;
    protected final TimeStopManager ts;
    protected final Abilities a;
    private final String[] labels;

    protected BaseKit(String id, StandManager m, TimeStopManager ts, Abilities a, String... labels) {
        this.id = id; this.m = m; this.ts = ts; this.a = a; this.labels = labels;
    }

    @Override public String id() { return id; }
    @Override public String label(Player p, Slot slot) { return labels[slot.ordinal()]; }

    /** Config section (and cooldown) used for a slot. Override when a slot changes behaviour. */
    protected String section(Player p, Slot slot) {
        return switch (slot) { case BASIC -> "basic"; case SKILL -> "skill"; case SKILL2 -> "skill2"; case ULTIMATE -> "ultimate"; };
    }

    @Override public String cooldownKey(Player p, Slot slot) { return id + "." + section(p, slot); }

    protected ConfigurationSection c(String path) {
        ConfigurationSection s = m.cfg(id);
        ConfigurationSection r = s == null ? null : s.getConfigurationSection(path);
        return r != null ? r : EMPTY;
    }

    @Override
    public void use(Player p, Slot slot) {
        String sec = section(p, slot);
        String key = id + "." + sec;
        if (m.onCooldown(p, key, label(p, slot))) return;
        ConfigurationSection cfg = c(sec);
        boolean fired = switch (slot) {
            case BASIC -> basic(p, cfg);
            case SKILL -> skill(p, cfg);
            case SKILL2 -> skill2(p, cfg);
            case ULTIMATE -> ultimate(p, cfg);
        };
        if (fired) m.cooldown(p, key, cfg.getDouble("cooldown", 5));
    }

    protected abstract boolean basic(Player p, ConfigurationSection cfg);
    protected abstract boolean skill(Player p, ConfigurationSection cfg);
    protected abstract boolean skill2(Player p, ConfigurationSection cfg);
    protected abstract boolean ultimate(Player p, ConfigurationSection cfg);

    // ---- small helpers
    protected void later(long ticks, Runnable r) { Bukkit.getScheduler().runTaskLater(m.plugin(), r, ticks); }
    protected void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.GOLD, TextDecoration.BOLD)); }
    protected void title(Player p, String big, String small) {
        p.showTitle(Title.title(Component.text(big, NamedTextColor.GOLD, TextDecoration.BOLD), Component.text(small, NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400))));
    }
    /** Particle line from the player's eyes along their aim. */
    protected void line(Player p, double length, Particle particle) {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        for (double t = 1; t <= length; t += 0.4) p.getWorld().spawnParticle(particle, eye.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
    }
}
