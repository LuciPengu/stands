package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Echoes Act 3 — control: burn, shove, and pin one target to the ground. */
public final class EchoesAct3 extends BaseKit {
    public EchoesAct3(StandManager m, TimeStopManager ts, Abilities a) { super("echoesact3", m, ts, a, "Punch", "FWOOSH", "SPLASH", "Three Freeze"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), 0.6);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        double range = cfg.getDouble("range", 8);
        m.anim(p, "cast");
        m.sound(p, "echoes");
        bar(p, "FWOOSH!");
        line(p, range, ParticleTypes.FLAME);
        LivingEntity t = a.target(p, range);
        if (t != null) { m.hit(p, t, cfg.getDouble("damage", 3)); t.setRemainingFireTicks((int) (cfg.getDouble("burn-seconds", 4) * 20)); }
        else { Vec3 at = a.aim(p, range); a.zone(p, at, 1.5, 60, 10, ParticleTypes.FLAME, le -> le.setRemainingFireTicks(40)); }   // no real fire block: it could spread
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "punch");
        m.sound(p, "echoes");
        bar(p, "SPLASH!");
        Vec3 fx = p.getEyePosition().add(p.getLookAngle().scale(2));
        Fx.particle(p.level(), ParticleTypes.SPLASH, fx, 120, 1, .6, 1, .3);
        Fx.sound(p.level(), fx, SoundEvents.GENERIC_SPLASH, 1.5f, 0.9f);
        Vec3 push = Fx.flatDir(p);
        double kb = cfg.getDouble("knockback", 1.5);
        for (LivingEntity le : a.cone(p, cfg.getDouble("range", 6), 0.5)) {
            m.hit(p, le, cfg.getDouble("damage", 2));
            le.setRemainingFireTicks(0);
            a.buff(le, 2, MobEffects.SLOWNESS, 0);
            if (!ts.isFrozen(le)) Fx.push(le, new Vec3(push.x * kb, 0.4, push.z * kb));
        }
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        if (t == null) { Txt.bar(p, "No target."); return false; }
        m.anim(p, "pose");
        m.sound(p, "echoes");
        title(p, "THREE FREEZE!", "");
        m.hit(p, t, cfg.getDouble("damage", 4));
        a.root(t, cfg.getDouble("duration", 3));
        Fx.particle(p.level(), ParticleTypes.ENCHANTED_HIT, t.position().add(0, 1, 0), 60, .4, .8, .4, 0);
        return true;
    }
}
