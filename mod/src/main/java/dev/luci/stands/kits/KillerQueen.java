package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import dev.luci.stands.entity.StandShot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Killer Queen — plant a bomb, detonate it later. Bites the Dust is a last-chance escape. */
public final class KillerQueen extends BaseKit {
    private record Bomb(LivingEntity entity, ServerLevel level, Vec3 spot, long expires) { }
    private final Map<UUID, Bomb> bombs = new HashMap<>();

    public KillerQueen(StandManager m, TimeStopManager ts, Abilities a) { super("killerqueen", m, ts, a, "Punch", "Plant Bomb", "Sheer Heart Attack", "Bites the Dust"); }

    private Bomb bomb(ServerPlayer p) {
        Bomb b = bombs.get(p.getUUID());
        if (b != null && System.currentTimeMillis() > b.expires) { bombs.remove(p.getUUID()); return null; }
        return b;
    }

    @Override public String label(ServerPlayer p, Slot slot) { return slot == Slot.SKILL && bomb(p) != null ? "DETONATE" : super.label(p, slot); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.strike(p, cfg.getDouble("range", 3.5), cfg.getDouble("damage", 6), 0.6);
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        Bomb b = bomb(p);
        if (b == null) {   // plant: no cooldown yet
            LivingEntity t = a.target(p, cfg.getDouble("plant-range", 5));
            Vec3 spot = t != null ? null : a.aim(p, cfg.getDouble("plant-range", 5));
            bombs.put(p.getUUID(), new Bomb(t, p.level(), spot, System.currentTimeMillis() + (long) (cfg.getDouble("fuse-seconds", 20) * 1000)));
            m.anim(p, "cast");
            Vec3 fx = t != null ? t.position().add(0, 1, 0) : spot;
            Fx.particle(p.level(), ParticleTypes.ENCHANTED_HIT, fx, 15, .3, .3, .3, 0);
            Fx.sound(p.level(), fx, SoundEvents.NOTE_BLOCK_HAT.value(), 1f, 1.8f);
            bar(p, t != null ? "Bomb planted on " + t.getName().getString() + " — use again to detonate" : "Bomb planted — use again to detonate");
            return false;
        }
        bombs.remove(p.getUUID());
        Vec3 at = b.entity != null ? (b.entity.isAlive() && !b.entity.isRemoved() && b.entity.level() == p.level() ? b.entity.position().add(0, 1, 0) : null)
                : (b.level == p.level() ? b.spot : null);
        m.anim(p, "pose");
        m.sound(p, "detonate");
        bar(p, "Click.");
        if (at != null) a.blast(p, at, cfg.getDouble("radius", 3), cfg.getDouble("damage", 8), 0.8);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        bar(p, "Sheer Heart Attack!");
        Fx.sound(p.level(), p.position(), SoundEvents.CREEPER_PRIMED, 1f, 1.4f);
        double radius = cfg.getDouble("radius", 2.5), dmg = cfg.getDouble("damage", 6);
        StandShot s = a.shoot(p, StandsMod.look(Items.SKELETON_SKULL), p.getLookAngle().scale(0.7), false, 0, (loc, le) -> a.blast(p, loc, radius, dmg, 0.6));
        a.home(s, p, cfg.getDouble("homing-range", 12), 0.7);
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        double threshold = cfg.getDouble("health-threshold", 8);
        if (p.getHealth() > threshold) { Txt.bar(p, "Bites the Dust only works at " + (int) (threshold / 2) + " hearts or less."); return false; }
        m.anim(p, "pose");
        m.sound(p, "bites-the-dust");
        for (LivingEntity le : a.around(p, p.position(), 12)) a.buff(le, 1.5, MobEffects.BLINDNESS, 0);
        p.setHealth((float) Math.min(p.getMaxHealth(), cfg.getDouble("heal-to", 14)));
        // Bed / respawn anchor if set, otherwise world spawn (vanilla respawn rules, any dimension).
        TeleportTransition home = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
        p.resetFallDistance();
        p.teleport(home);
        title(p, "BITES THE DUST!", "Time rewinds");
        return true;
    }
}
