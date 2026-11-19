package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Anubis — cursed sword: hits harder the more times it hits the same target. */
public final class Anubis extends BaseKit {
    private record Memory(UUID target, int stacks) { }
    private final Map<UUID, Memory> memory = new HashMap<>();
    public Anubis(StandManager m, TimeStopManager ts, Abilities a) { super("anubis", m, ts, a, "Slash", "Anubis Sense", "Dash Slash", "Possession"); }
    @Override public boolean hasModel() { return false; }

    private void slash(ServerPlayer p, double range, double base, double perStack, int maxStacks) {
        Fx.particle(p.level(), ParticleTypes.SWEEP_ATTACK, p.getEyePosition().add(p.getLookAngle().scale(1.8)), 2, .3, .2, .3, 0);
        m.sound(p, "chariot-slash");
        for (LivingEntity le : a.cone(p, range, 0.5)) {
            Memory mem = memory.get(p.getUUID());
            int stacks = mem != null && mem.target.equals(le.getUUID()) ? mem.stacks : 0;
            m.hit(p, le, base + stacks * perStack);
            memory.put(p.getUUID(), new Memory(le.getUUID(), Math.min(maxStacks, stacks + 1)));
            if (stacks > 0) bar(p, "Anubis remembers: +" + stacks);
            break;
        }
    }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        slash(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), cfg.getDouble("damage-per-stack", 1), cfg.getInt("max-stacks", 4));
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 5);
        bar(p, "Anubis Sense!");
        a.buff(p, secs, MobEffects.SPEED, 1);
        a.buff(p, secs, MobEffects.JUMP_BOOST, 1);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        a.dash(p, cfg.getDouble("distance", 5));
        Cfg b = c("basic");
        later(4, () -> { if (online(p) && !ts.isFrozen(p)) slash(p, 3.5, cfg.getDouble("damage", 5), b.getDouble("damage-per-stack", 1), b.getInt("max-stacks", 4)); });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        title(p, "ANUBIS!", "");
        a.dash(p, cfg.getDouble("distance", 8));
        double dmg = cfg.getDouble("damage", 9);
        later(5, () -> {
            if (!online(p) || ts.isFrozen(p)) return;
            m.sound(p, "chariot-slash");
            Fx.particle(p.level(), ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 8, 1.5, .3, 1.5, 0);
            for (LivingEntity le : a.around(p, p.position(), 3.5)) m.hit(p, le, dmg);
        });
        return true;
    }
}
