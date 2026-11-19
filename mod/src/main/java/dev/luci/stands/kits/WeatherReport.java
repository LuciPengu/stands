package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Weather Report — lightning, wind, and an area blind. */
public final class WeatherReport extends BaseKit {
    public WeatherReport(StandManager m, TimeStopManager ts, Abilities a) { super("weatherreport", m, ts, a, "Lightning", "Wind Boost", "Gust", "Heavy Weather"); }

    /** Visual-only lightning (no fire, no vanilla damage), like Bukkit's strikeLightningEffect. */
    private static void lightningEffect(ServerLevel l, Vec3 at) {
        LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, l);
        bolt.setVisualOnly(true);
        bolt.snapTo(at.x, at.y, at.z, 0, 0);
        l.addFreshEntity(bolt);
    }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        double range = cfg.getDouble("range", 20);
        LivingEntity t = a.target(p, range);
        Vec3 at = t != null ? t.position() : a.aim(p, range);
        m.anim(p, "cast");
        lightningEffect(p.level(), at);
        for (LivingEntity le : a.around(p, at, cfg.getDouble("radius", 2))) m.hit(p, le, cfg.getDouble("damage", 5));
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "dash");
        bar(p, "Wind Boost!");
        a.buff(p, cfg.getDouble("duration", 5), MobEffects.SPEED, 1);
        Fx.particle(p.level(), ParticleTypes.CLOUD, p.position(), 30, .5, .2, .5, .1);
        Fx.sound(p.level(), p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 1f);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "punch");
        m.standForward(p, 10);
        double kb = cfg.getDouble("knockback", 1.8);
        Vec3 fx = p.getEyePosition().add(p.getLookAngle().scale(2));
        Fx.particle(p.level(), ParticleTypes.CLOUD, fx, 50, 1, .6, 1, .25);
        Fx.sound(p.level(), p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.5f, 0.7f);
        Vec3 look = p.getLookAngle();
        Vec3 push = new Vec3(look.x, 0, look.z);
        push = push.lengthSqr() > 0 ? push.normalize() : Fx.flatDir(p);
        for (LivingEntity le : a.cone(p, cfg.getDouble("range", 7), 0.5)) {
            m.hit(p, le, cfg.getDouble("damage", 2));
            if (!ts.isFrozen(le)) Fx.push(le, new Vec3(push.x * kb, 0.5, push.z * kb));
        }
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        title(p, "HEAVY WEATHER!", "");
        double radius = cfg.getDouble("radius", 10), secs = cfg.getDouble("duration", 4);
        Random r = new Random();
        for (int i = 0; i < 4; i++) later(i * 6L, () -> {
            if (!online(p)) return;
            lightningEffect(p.level(), p.position().add(r.nextDouble() * radius * 2 - radius, 0, r.nextDouble() * radius * 2 - radius));
        });
        Fx.particle(p.level(), ParticleTypes.CLOUD, p.position().add(0, 2, 0), 200, radius / 2, 1.5, radius / 2, .02);
        for (LivingEntity le : a.around(p, p.position(), radius)) {
            m.hit(p, le, cfg.getDouble("damage", 4));
            a.buff(le, secs, MobEffects.BLINDNESS, 0);
            a.buff(le, secs, MobEffects.SLOWNESS, 1);
        }
        return true;
    }
}
