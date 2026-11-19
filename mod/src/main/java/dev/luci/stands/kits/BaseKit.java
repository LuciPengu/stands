package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.Sched;
import dev.luci.stands.StandKit;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Common plumbing: cooldown check, config lookup, labels. Each ability returns true if it actually fired. */
public abstract class BaseKit implements StandKit {
    protected final String id;
    protected final StandManager m;
    protected final TimeStopManager ts;
    protected final Abilities a;
    private final String[] labels;

    protected BaseKit(String id, StandManager m, TimeStopManager ts, Abilities a, String... labels) {
        this.id = id; this.m = m; this.ts = ts; this.a = a; this.labels = labels;
    }

    @Override public String id() { return id; }
    @Override public String label(ServerPlayer p, Slot slot) { return labels[slot.ordinal()]; }

    /** Config section (and cooldown) used for a slot. Override when a slot changes behaviour. */
    protected String section(ServerPlayer p, Slot slot) {
        return switch (slot) { case BASIC -> "basic"; case SKILL -> "skill"; case SKILL2 -> "skill2"; case ULTIMATE -> "ultimate"; };
    }

    @Override public String cooldownKey(ServerPlayer p, Slot slot) { return id + "." + section(p, slot); }

    protected Cfg c(String path) { return m.cfg(id).section(path); }

    @Override
    public void use(ServerPlayer p, Slot slot) {
        String sec = section(p, slot);
        String key = id + "." + sec;
        if (m.onCooldown(p, key, label(p, slot))) return;
        Cfg cfg = c(sec);
        boolean fired = switch (slot) {
            case BASIC -> basic(p, cfg);
            case SKILL -> skill(p, cfg);
            case SKILL2 -> skill2(p, cfg);
            case ULTIMATE -> ultimate(p, cfg);
        };
        if (fired) m.cooldown(p, key, cfg.getDouble("cooldown", 5));
    }

    protected abstract boolean basic(ServerPlayer p, Cfg cfg);
    protected abstract boolean skill(ServerPlayer p, Cfg cfg);
    protected abstract boolean skill2(ServerPlayer p, Cfg cfg);
    protected abstract boolean ultimate(ServerPlayer p, Cfg cfg);

    // ---- small helpers
    protected void later(long ticks, Runnable r) { Sched.later(ticks, r); }
    protected void bar(ServerPlayer p, String text) { Txt.bar(p, text, ChatFormatting.GOLD, ChatFormatting.BOLD); }
    protected void title(ServerPlayer p, String big, String small) { Txt.title(p, big, small); }
    /** Player still in the game (not logged out / removed). */
    protected boolean online(ServerPlayer p) { return !p.isRemoved() && !p.hasDisconnected(); }
    /** Particle line from the player's eyes along their aim. */
    protected void line(ServerPlayer p, double length, ParticleOptions particle) {
        Vec3 eye = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        for (double t = 1; t <= length; t += 0.4) Fx.particle(p.level(), particle, eye.add(d.scale(t)), 1);
    }
}
