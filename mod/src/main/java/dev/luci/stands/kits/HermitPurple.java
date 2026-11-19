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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Hermit Purple — vines: whip, grapple, trap, and a sustain ultimate. */
public final class HermitPurple extends BaseKit {
    public HermitPurple(StandManager m, TimeStopManager ts, Abilities a) { super("hermitpurple", m, ts, a, "Whip", "Grapple", "Vine Trap", "Hermit Boost"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        double range = cfg.getDouble("range", 5);
        line(p, range, ParticleTypes.WITCH);
        Fx.sound(p.level(), p.position(), SoundEvents.FISHING_BOBBER_THROW, 1f, 0.6f);
        LivingEntity t = a.target(p, range);
        if (t != null) m.hit(p, t, cfg.getDouble("damage", 6));
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        BlockHitResult r = a.aimBlock(p, cfg.getDouble("range", 14));
        if (r == null) { Txt.bar(p, "Aim at a block to grapple."); return false; }
        line(p, p.getEyePosition().distanceTo(r.getLocation()), ParticleTypes.WITCH);
        Vec3 v = r.getLocation().subtract(p.getEyePosition()).normalize().scale(cfg.getDouble("speed", 1.5));
        Fx.push(p, v.add(0, 0.3, 0));
        p.resetFallDistance();
        Fx.sound(p.level(), p.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        Vec3 at = a.aim(p, cfg.getDouble("range", 8));
        bar(p, "Vines planted");
        double dmg = cfg.getDouble("damage", 3), secs = cfg.getDouble("root-seconds", 2);
        a.trap(p, at, 1.5, (int) (cfg.getDouble("lifetime", 12) * 20), ParticleTypes.WITCH, le -> { m.hit(p, le, dmg); a.root(le, secs); });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 8);
        m.sound(p, "reverse");
        title(p, "HERMIT PURPLE!", "");
        a.heal(p, cfg.getDouble("heal", 4));
        a.buff(p, secs, MobEffects.REGENERATION, 0);
        a.buff(p, secs, MobEffects.SPEED, 0);
        return true;
    }
}
