package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Star Platinum — the hardest-hitting rush, a shorter time stop than The World. */
public final class StarPlatinum extends BaseKit {
    public StarPlatinum(StandManager m, TimeStopManager ts, Abilities a) { super("starplatinum", m, ts, a, "ORA", "Star Finger", "Block Throw", "Time Stop"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "starplatinum-rush", "ORA ORA ORA!", "ORAAA!");
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        double range = cfg.getDouble("range", 7);
        m.anim(p, "cast");
        m.standForward(p, 10);
        m.sound(p, "ora");
        bar(p, "Star Finger!");
        line(p, range, ParticleTypes.END_ROD);
        LivingEntity t = a.target(p, range);
        if (t != null) m.hit(p, t, cfg.getDouble("damage", 6));
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        Fx.sound(p.level(), p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 1.2f);
        double kb = cfg.getDouble("knockback", 1.4);
        ServerLevel l = p.level();
        a.shoot(p, StandsMod.look(Items.COBBLESTONE), p.getLookAngle().scale(1.6), true, cfg.getDouble("damage", 5), (loc, le) -> {
            Fx.particle(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), loc, 30, .4, .4, .4, 0);
            Fx.sound(l, loc, SoundEvents.STONE_BREAK, 1.5f, 0.8f);
            if (le != null && !ts.isFrozen(le)) {
                Vec3 d = Fx.flatDir(p).scale(kb);
                Fx.push(le, new Vec3(d.x, 0.4, d.z));
            }
        });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        if (!ts.start(p, cfg.getDouble("duration", 3.5))) return false;
        m.sound(p, "timestop");
        m.standForward(p, 28);
        m.anim(p, "pose");
        title(p, "STAR PLATINUM!", "ZA WARUDO!");
        return true;
    }
}
