package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/** The Hand — erases space: pulls enemies in, deletes blocks, one giant swipe. */
public final class TheHand extends BaseKit {
    public TheHand(StandManager m, TimeStopManager ts, Abilities a) { super("thehand", m, ts, a, "Punch", "Air Erase", "Erase Blocks", "DELETE"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.sound(p, "ora");
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        m.anim(p, "cast");
        m.sound(p, "hand-teleport");
        if (t != null) { a.pull(p, t); bar(p, "Erased the space between you!"); return true; }
        return a.blink(p, cfg.getDouble("self-distance", 5), false);   // no target: erase the space in front of you
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        BlockHitResult r = a.aimBlock(p, cfg.getDouble("range", 5));
        if (r == null) { Txt.bar(p, "Aim at a block."); return false; }
        ServerLevel l = p.level();
        BlockPos c = r.getBlockPos();
        int ticks = (int) (cfg.getDouble("restore-seconds", 5) * 20), n = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos b = c.offset(x, y, z);
            if (!l.getBlockState(b).isAir() && a.tempBlock(l, b, Blocks.AIR.defaultBlockState(), ticks)) n++;
        }
        if (n == 0) { Txt.bar(p, "Block erasing is turned off on this server."); return false; }
        m.anim(p, "punch");
        m.sound(p, "hand-erase");
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.standForward(p, 24);
        m.anim(p, "cast");
        title(p, "DELETE!", "");
        later((long) (cfg.getDouble("windup", 0.5) * 20), () -> {
            if (!online(p) || !p.isAlive() || ts.isFrozen(p)) return;
            m.sound(p, "hand-erase");
            LivingEntity t = a.strike(p, cfg.getDouble("range", 4), cfg.getDouble("damage", 12), 0);
            if (t != null && t.level() instanceof ServerLevel l) Fx.particle(l, ParticleTypes.REVERSE_PORTAL, t.position().add(0, 1, 0), 60, .4, .8, .4, .1);
        });
        return true;
    }
}
