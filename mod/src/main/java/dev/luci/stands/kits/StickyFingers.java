package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Sticky Fingers — slippery brawler: zips through walls, traps, and vanishes. */
public final class StickyFingers extends BaseKit {
    public StickyFingers(StandManager m, TimeStopManager ts, Abilities a) { super("stickyfingers", m, ts, a, "ARI", "Zipper Dash", "Zipper Trap", "Zip Away"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "ari", "ARI ARI ARI!", "ARRIVEDERCI!");
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        if (!a.blink(p, cfg.getDouble("distance", 7), true)) { Txt.bar(p, Txt.of("No room to zip there.")); return false; }
        m.anim(p, "dash");
        m.sound(p, "zipper-zip");
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        Vec3 at = a.aim(p, cfg.getDouble("range", 8));
        ServerLevel l = p.level();
        m.anim(p, "cast");
        m.sound(l, at, "zipper-place");
        bar(p, "Zipper placed");
        double dmg = cfg.getDouble("damage", 6), slow = cfg.getDouble("slow-seconds", 2);
        a.trap(p, at, 1.5, (int) (cfg.getDouble("lifetime", 12) * 20), ParticleTypes.ENCHANTED_HIT, le -> {
            m.sound(l, at, "zipper-zip");
            m.hit(p, le, dmg);
            a.buff(le, slow, MobEffects.SLOWNESS, 3);
            Fx.particle(l, ParticleTypes.CRIT, at.add(0, 1, 0), 30, .4, .6, .4, .2);
        });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "dash");
        m.sound(p, "zipper-zip");
        title(p, "STICKY FINGERS!", "Zipped away");
        int ticks = (int) (cfg.getDouble("duration", 3) * 20);
        a.heal(p, cfg.getDouble("heal", 6));
        a.buff(p, ticks / 20.0, MobEffects.SPEED, 1);
        a.ghost(p, ticks, () -> m.sound(p, "zipper-zip"));
        return true;
    }
}
