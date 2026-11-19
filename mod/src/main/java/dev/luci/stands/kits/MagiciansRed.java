package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Magician's Red — fire zoner. */
public final class MagiciansRed extends BaseKit {
    public MagiciansRed(StandManager m, TimeStopManager ts, Abilities a) { super("magiciansred", m, ts, a, "Fireball", "Flame Wall", "Power Boost", "Crossfire Hurricane"); }

    private void fireball(ServerPlayer p, Vec3 v, double dmg, int fireTicks) {
        ServerLevel l = p.level();
        a.shoot(p, StandsMod.look(Items.FIRE_CHARGE), v, false, dmg, (loc, le) -> {
            Fx.particle(l, ParticleTypes.FLAME, loc, 25, .4, .4, .4, .05);
            Fx.sound(l, loc, SoundEvents.BLAZE_HURT, 0.8f, 0.7f);
            if (le != null) le.setRemainingFireTicks(fireTicks);
        });
    }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        m.sound(p, "fire");
        fireball(p, p.getLookAngle().scale(1.7), cfg.getDouble("damage", 5), (int) (cfg.getDouble("burn-seconds", 3) * 20));
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        Vec3 at = a.aim(p, cfg.getDouble("range", 7));
        m.anim(p, "cast");
        m.sound(p.level(), at, "fire");
        bar(p, "Flame Wall!");
        double dmg = cfg.getDouble("damage-per-tick", 1.5);
        a.zone(p, at, cfg.getDouble("radius", 2.5), (int) (cfg.getDouble("duration", 4) * 20), 10, ParticleTypes.FLAME, le -> { m.hit(p, le, dmg); le.setRemainingFireTicks(40); });
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 6);
        m.anim(p, "pose");
        m.sound(p, "fire");
        bar(p, "Power Boost!");
        a.buff(p, secs, MobEffects.SPEED, 1);
        a.buff(p, secs, MobEffects.FIRE_RESISTANCE, 0);
        m.bonusNextHit(p, cfg.getDouble("next-hit-bonus", 4), (int) (secs * 20));
        Fx.particle(p.level(), ParticleTypes.FLAME, p.position().add(0, 1, 0), 40, .4, .7, .4, .05);
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        m.sound(p, "fire");
        title(p, "CROSSFIRE HURRICANE!", "");
        int n = cfg.getInt("fireballs", 5);
        for (int i = 0; i < n; i++) {
            float yaw = p.getYRot() - 20 + 40f * i / Math.max(1, n - 1);
            fireball(p, Vec3.directionFromRotation(p.getXRot(), yaw).scale(1.5), cfg.getDouble("damage", 4), 80);
        }
        return true;
    }
}
