package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Purple Haze — poison brawler. The ultimate hurts the user too. */
public final class PurpleHaze extends BaseKit {
    private final Map<UUID, Long> rage = new HashMap<>();
    public PurpleHaze(StandManager m, TimeStopManager ts, Abilities a) { super("purplehaze", m, ts, a, "Virus Punch", "Rage Mode", "Capsule", "Toxic Haze"); }

    private boolean raging(ServerPlayer p) { Long u = rage.get(p.getUUID()); return u != null && System.currentTimeMillis() < u; }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        m.sound(p, "virus-punch");
        LivingEntity t = a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 5), 0.5);
        if (t != null) {
            a.buff(t, cfg.getDouble("poison-seconds", 4), MobEffects.POISON, 1);
            if (t.level() instanceof ServerLevel l) Fx.particle(l, ParticleTypes.WITCH, t.position().add(0, 1, 0), 20, .3, .5, .3, 0);
        }
        if (raging(p)) { m.cooldown(p, cooldownKey(p, Slot.BASIC), cfg.getDouble("rage-cooldown", 1)); return false; }
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        double secs = cfg.getDouble("duration", 8);
        rage.put(p.getUUID(), System.currentTimeMillis() + (long) (secs * 1000));
        m.anim(p, "pose");
        m.sound(p, "rage");
        title(p, "RAGE MODE", "Virus Punch has almost no cooldown");
        a.buff(p, secs, MobEffects.SPEED, 0);
        m.cooldown(p, cooldownKey(p, Slot.BASIC), 0);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        double radius = cfg.getDouble("radius", 3), secs = cfg.getDouble("cloud-seconds", 4);
        a.shoot(p, StandsMod.look(Items.DYE.pick(net.minecraft.world.item.DyeColor.PURPLE)), p.getLookAngle().scale(1.3), true, 0, (loc, le) -> {
            Fx.sound(p.level(), loc, SoundEvents.GLASS_BREAK, 1f, 1.2f);
            a.zone(p, loc, radius, (int) (secs * 20), 10, ParticleTypes.WITCH, t -> a.buff(t, 3, MobEffects.POISON, 1));
        });
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        m.sound(p, "ultimate-virus");
        title(p, "TOXIC HAZE!", "It hurts you too");
        double radius = cfg.getDouble("radius", 5), secs = cfg.getDouble("duration", 5);
        Vec3 c = p.position();
        for (LivingEntity le : a.around(p, c, radius)) m.hit(p, le, cfg.getDouble("damage", 4));
        a.zone(p, c, radius, (int) (secs * 20), 10, ParticleTypes.WITCH, t -> a.buff(t, 2, MobEffects.WITHER, 1));
        a.buff(p, cfg.getDouble("self-wither-seconds", 3), MobEffects.WITHER, 0);
        return true;
    }
}
