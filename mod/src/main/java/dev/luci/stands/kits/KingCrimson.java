package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** King Crimson — one huge punch and time tricks instead of a freeze. */
public final class KingCrimson extends BaseKit {
    public KingCrimson(StandManager m, TimeStopManager ts, Abilities a) { super("kingcrimson", m, ts, a, "Rush", "Jab", "Time Skip", "Time Erase"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "crimson-jab", "KING CRIMSON!", null);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        // Wind-up so the big hit can be dodged.
        m.standForward(p, 20);
        bar(p, "...");
        later((long) (cfg.getDouble("windup", 0.4) * 20), () -> {
            if (!online(p) || !p.isAlive() || ts.isFrozen(p)) return;
            m.sound(p, "crimson-jab");
            if (a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 10), cfg.getDouble("knockback", 1.2)) != null) bar(p, "King Crimson Jab!");
        });
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        if (!a.blink(p, cfg.getDouble("distance", 9), false)) return false;
        m.anim(p, "dash");
        m.sound(p, "time-skip");
        bar(p, "Time Skip");
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 4);
        int ticks = (int) (secs * 20);
        double bonus = cfg.getDouble("epitaph-bonus", 6), radius = cfg.getDouble("radius", 20);
        m.anim(p, "pose");
        m.sound(p, "time-erase");
        title(p, "KING CRIMSON!", "Time is erased. Nothing can touch you.");
        a.buff(p, secs, MobEffects.SPEED, 2);
        // Everyone nearby loses this stretch of time: they can't see, and you see them through walls.
        for (LivingEntity le : a.around(p, p.position(), radius)) {
            a.buff(le, secs, MobEffects.GLOWING, 0);
            a.buff(le, secs, MobEffects.SLOWNESS, 1);
            if (le instanceof Player) { a.buff(le, secs, MobEffects.BLINDNESS, 0); a.buff(le, secs, MobEffects.DARKNESS, 0); }
        }
        // Countdown bar and a crimson aura so the caster can see it's active.
        ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Txt.of("TIME ERASED", ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(p);
        for (int t = 0; t < ticks; t += 2) {
            final float left = 1f - (float) t / ticks;
            later(t, () -> {
                if (!online(p)) return;
                bar.setProgress(Math.max(0f, left));
                Fx.particle(p.level(), ParticleTypes.CRIMSON_SPORE, p.position().add(0, 1, 0), 25, 3, 1.5, 3, 0);
                Vec3 at = p.position().add(0, 0.2, 0);
                // Only the caster sees these.
                p.level().sendParticles(p, ParticleTypes.DAMAGE_INDICATOR, false, false, at.x, at.y, at.z, 2, .3, 0, .3, 0);
            });
        }
        a.ghost(p, ticks, () -> {
            bar.removeAllPlayers();
            m.bonusNextHit(p, bonus, 60);
            m.cooldown(p, cooldownKey(p, Slot.BASIC), 0);
            m.cooldown(p, cooldownKey(p, Slot.SKILL), 0);
            title(p, "EPITAPH", "Your next hit lands harder");
        });
        // If the caster logs out mid-erase, ghost's end callback won't run: clear the bar anyway.
        later(ticks + 1, bar::removeAllPlayers);
        return true;
    }
}
