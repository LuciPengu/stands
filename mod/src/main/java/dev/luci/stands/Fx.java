package dev.luci.stands;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Particles, sounds and movement helpers shared by every stand. */
public final class Fx {
    private Fx() { }

    public static void particle(ServerLevel l, ParticleOptions o, Vec3 at, int count, double dx, double dy, double dz, double speed) {
        l.sendParticles(o, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    public static void particle(ServerLevel l, ParticleOptions o, Vec3 at, int count) { particle(l, o, at, count, 0, 0, 0, 0); }

    public static void sound(Level l, Vec3 at, SoundEvent s, float volume, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(Level l, Vec3 at, Holder<SoundEvent> s, float volume, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Sound by id ("jojo:timestop", "minecraft:entity.blaze.shoot"). Unknown ids still play if a resource pack defines them. */
    public static void sound(Level l, Vec3 at, String id, float volume, float pitch) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return;
        Holder<SoundEvent> h = BuiltInRegistries.SOUND_EVENT.get(rl).<Holder<SoundEvent>>map(r -> r)
                .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(rl)));
        sound(l, at, h, volume, pitch);
    }

    /** Set velocity and make sure the client hears about it (players control their own motion otherwise). */
    public static void push(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.syncVelocity = true;
        e.needsSync = true;
    }

    public static void teleport(Entity e, Vec3 to, float yaw, float pitch) {
        if (e instanceof ServerPlayer p) p.connection.teleport(to.x, to.y, to.z, yaw, pitch);
        else e.snapTo(to.x, to.y, to.z, yaw, pitch);
    }

    public static void teleport(Entity e, Vec3 to) { teleport(e, to, e.getYRot(), e.getXRot()); }

    public static Vec3 eye(Entity e) { return e.getEyePosition(); }
    public static Vec3 look(Entity e) { return e.getLookAngle(); }

    /** Flat (y = 0) facing direction from yaw. */
    public static Vec3 flatDir(Entity e) {
        double yaw = Math.toRadians(e.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    public static Vec3 center(Entity e) { return e.position().add(0, e.getBbHeight() / 2, 0); }

    public static float maxHealth(LivingEntity e) { return e.getMaxHealth(); }
}
