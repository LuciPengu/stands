package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Hierophant Green — long-range emeralds and control. */
public final class HierophantGreen extends BaseKit {
    public HierophantGreen(StandManager m, TimeStopManager ts, Abilities a) { super("heirophantgreen", m, ts, a, "Emerald Splash", "Webs", "Tendril Grab", "20m Emerald Splash"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "emerald-splash");
        bar(p, "Emerald Splash!");
        a.volley(p, StandsMod.look(Items.EMERALD), cfg.getInt("emeralds", 4), 14, 2.0, false, cfg.getDouble("damage-per-emerald", 2), null);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "web");
        ServerLevel l = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        int ticks = (int) (cfg.getDouble("duration", 5) * 20), placed = 0;
        for (double d = 3; d <= 6; d += 1) {
            BlockPos b = BlockPos.containing(eye.add(dir.scale(d)));
            if (l.getBlockState(b).isAir() && a.tempBlock(l, b, Blocks.COBWEB.defaultBlockState(), ticks)) placed++;
        }
        // Fallback when world edits are off or there's no room: a slowing zone.
        if (placed == 0) a.zone(p, a.aim(p, 5), 2, ticks, 10, ParticleTypes.ITEM_SLIME, le -> a.buff(le, 1, MobEffects.SLOWNESS, 3));
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        double range = cfg.getDouble("range", 12);
        m.anim(p, "cast");
        line(p, range, ParticleTypes.HAPPY_VILLAGER);
        LivingEntity t = a.target(p, range);
        if (t == null) return true;
        m.hit(p, t, cfg.getDouble("damage", 2));
        a.root(t, cfg.getDouble("root-seconds", 1.5));
        bar(p, "Caught!");
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        title(p, "20 METER EMERALD SPLASH!", "");
        int waves = cfg.getInt("waves", 2), per = cfg.getInt("emeralds-per-wave", 8);
        double dmg = cfg.getDouble("damage-per-emerald", 2);
        for (int w = 0; w < waves; w++) later(w * 6L, () -> {
            if (!online(p) || ts.isFrozen(p)) return;
            m.sound(p, "emerald-splash");
            a.volley(p, StandsMod.look(Items.EMERALD), per, 50, 2.3, false, dmg, null);
        });
        return true;
    }
}
