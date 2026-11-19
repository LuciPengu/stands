package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.Sched;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Gold Experience Requiem — Gold Experience, stronger, with Return to Zero instead of the old permanent full-heal. */
public final class GoldExperienceRequiem extends GoldExperience {
    private final Map<UUID, Long> zero = new HashMap<>();   // player -> until (server tick)

    public GoldExperienceRequiem(StandManager m, TimeStopManager ts, Abilities a) {
        super("ger", m, ts, a, "MUDA", "Life Giver", "Life Boost", "Return to Zero");
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer p)) return true;
            Long until = zero.get(p.getUUID());
            if (until == null || Sched.now() > until) return true;
            zero.remove(p.getUUID());
            Cfg cfg = c("ultimate");
            p.setHealth(1f);
            a.heal(p, cfg.getDouble("heal", 6));
            m.sound(p, "ger");
            Fx.particle(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 80, .6, .9, .6, .1);
            bar(p, "This is... Requiem.");
            Entity src = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            if (src instanceof LivingEntity attacker && a.valid(p, attacker)) m.hit(p, attacker, cfg.getDouble("reflect-damage", 8));
            return false;
        });
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("window", 5);
        zero.put(p.getUUID(), Sched.now() + Math.round(secs * 20));
        m.anim(p, "pose");
        m.sound(p, "ger");
        title(p, "RETURN TO ZERO", "The next killing blow never happens");
        Fx.particle(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 60, .5, .8, .5, .05);
        return true;
    }
}
