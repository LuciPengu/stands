package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/** Metallica — invisible stand: iron attacks from range, then vanish. */
public final class Metallica extends BaseKit {
    public Metallica(StandManager m, TimeStopManager ts, Abilities a) { super("metallica", m, ts, a, "Iron Spikes", "Razor Blades", "Iron Grab", "Vanish"); }
    @Override public boolean hasModel() { return false; }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 6));
        Fx.sound(p.level(), p.position(), SoundEvents.ANVIL_PLACE, 0.5f, 1.8f);
        if (t == null) return true;
        m.hit(p, t, cfg.getDouble("damage", 5));
        Fx.particle(p.level(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), t.position().add(0, 1, 0), 30, .3, .5, .3, 0);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 8));
        if (t == null) { Txt.bar(p, "No target."); return false; }
        bar(p, "Razor blades!");
        Fx.sound(p.level(), t.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
        a.dot(p, t, cfg.getDouble("damage-per-second", 1), cfg.getInt("seconds", 5), 20, ParticleTypes.DAMAGE_INDICATOR);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        LivingEntity t = a.target(p, cfg.getDouble("range", 10));
        if (t == null) { Txt.bar(p, "No target."); return false; }
        Fx.sound(p.level(), p.position(), SoundEvents.CHAIN_PLACE, 1f, 0.6f);
        a.pull(p, t);
        m.hit(p, t, cfg.getDouble("damage", 2));
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        title(p, "METALLICA", "You can't be seen or hit, and you can't attack");
        a.buff(p, cfg.getDouble("duration", 5), MobEffects.SPEED, 0);
        a.ghost(p, (int) (cfg.getDouble("duration", 5) * 20), null);
        return true;
    }
}
