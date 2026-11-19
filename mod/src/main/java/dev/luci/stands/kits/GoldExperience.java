package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.Vec3;

/** Gold Experience — gives life: summons a wolf, buffs itself, reveals enemies. */
public class GoldExperience extends BaseKit {
    public GoldExperience(StandManager m, TimeStopManager ts, Abilities a) { this("goldenexperience", m, ts, a, "MUDA", "Life Giver", "Life Boost", "Life Sensor"); }
    protected GoldExperience(String id, StandManager m, TimeStopManager ts, Abilities a, String... labels) { super(id, m, ts, a, labels); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "ge-rush", "MUDA MUDA MUDA!", "MUDAAA!");
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "life");
        bar(p, "Life Giver!");
        LivingEntity t = a.target(p, 16);
        Vec3 at = a.aim(p, 3);
        ServerLevel l = p.level();
        BlockPos bp = BlockPos.containing(at);
        Vec3 spawn = l.getBlockState(bp).getCollisionShape(l, bp).isEmpty() ? at : p.position();
        // Never saved to disk: if the server stops before it vanishes, it doesn't come back.
        Wolf w = new Wolf(EntityTypes.WOLF, l) { @Override public boolean shouldBeSaved() { return false; } };
        w.snapTo(spawn.x, spawn.y, spawn.z, p.getYRot(), 0);
        w.setTame(true, false);
        w.setOwner(p);
        w.setCustomName(Component.literal(p.getName().getString() + "'s life"));
        l.addFreshEntity(w);
        if (t != null) w.setTarget(t);
        Fx.particle(l, ParticleTypes.HAPPY_VILLAGER, w.position().add(0, .5, 0), 20, .4, .4, .4, 0);
        later((long) (cfg.getDouble("lifetime", 8) * 20), () -> {
            if (!w.isRemoved()) { Fx.particle(l, ParticleTypes.HAPPY_VILLAGER, w.position(), 10, .3, .3, .3, 0); w.discard(); }
        });
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 6);
        m.anim(p, "pose");
        m.sound(p, "life");
        bar(p, "Life energy!");
        a.buff(p, secs, MobEffects.SPEED, 1);
        m.bonusNextHit(p, cfg.getDouble("next-hit-bonus", 3), (int) (secs * 20));
        Fx.particle(p.level(), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1, 0), 30, .4, .7, .4, 0);
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "life");
        int n = a.reveal(p, cfg.getDouble("radius", 40), cfg.getDouble("duration", 8));
        title(p, "LIFE SENSOR!", n + " life form" + (n == 1 ? "" : "s") + " found");
        return true;
    }
}
