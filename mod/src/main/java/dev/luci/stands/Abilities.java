package dev.luci.stands;

import dev.luci.stands.entity.StandEntity;
import dev.luci.stands.entity.StandShot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shared building blocks for stand abilities. All damage goes through StandManager.hit(). */
public final class Abilities {

    private final StandManager m;
    private final TimeStopManager ts;
    private final Map<TempKey, BlockState> tempBlocks = new HashMap<>();

    private record TempKey(ServerLevel level, BlockPos pos) { }

    public Abilities(StandManager m, TimeStopManager ts) {
        this.m = m;
        this.ts = ts;
    }

    private void later(long ticks, Runnable r) { Sched.later(ticks, r); }

    private static boolean online(ServerPlayer p) { return !p.isRemoved() && !p.hasDisconnected(); }

    // ------------------------------------------------------------ targeting

    public boolean valid(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || e instanceof ArmorStand || e instanceof StandEntity || !le.isAlive()) return false;
        if (e instanceof ServerPlayer o && (o.isSpectator() || m.isUntargetable(o) || !StandsMod.pvp().canFight(p, o))) return false;
        return true;
    }

    /** Living things in a cone in front of the player. */
    public List<LivingEntity> cone(ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.level().getEntities(p, new AABB(eye, eye).inflate(range), e -> valid(p, e))) {
            Vec3 to = Fx.center(e).subtract(eye);
            if (to.length() <= range && (to.length() < 1.2 || to.normalize().dot(dir) >= minDot)) out.add((LivingEntity) e);
        }
        return out;
    }

    /** The living thing the player is looking at, or null. Blocks stop the ray. */
    public LivingEntity target(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box, e -> valid(p, e), 0.35f);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    public List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.level().getEntities((Entity) null, new AABB(c, c).inflate(r), e -> valid(p, e)))
            if (e.position().distanceTo(c) <= r + 0.5) out.add((LivingEntity) e);
        return out;
    }

    /** Where the player is pointing: the block hit, or the point `range` blocks out. */
    public Vec3 aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult r = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() != HitResult.Type.MISS ? r.getLocation() : end;
    }

    /** The block the player is pointing at, or null. */
    public BlockHitResult aimBlock(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult r = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS ? null : r;
    }

    // ------------------------------------------------------------ melee

    /** Cone barrage: cfg keys damage-total, hits, range, finisher-knockback. Last hit launches. */
    public void rush(ServerPlayer p, Cfg cfg, String soundKey, String shout, String finisherShout) {
        int hits = cfg.getInt("hits", 8);
        double per = cfg.getDouble("damage-total", 9.0) / hits;
        double range = cfg.getDouble("range", 4.0);
        double finisher = cfg.getDouble("finisher-knockback", 1.6);
        m.standForward(p, hits * 3 + 12);
        m.animFor(p, "rush", hits * 3);
        m.sound(p, soundKey);
        Txt.bar(p, shout, ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (int i = 0; i < hits; i++) {
            final boolean last = i == hits - 1;
            later(2L + i * 3L, () -> {
                if (!online(p) || !p.isAlive() || ts.isFrozen(p)) return;
                ServerLevel l = p.level();
                Vec3 eye = p.getEyePosition();
                Vec3 dir = p.getLookAngle();
                Vec3 fx = eye.add(dir.scale(1.6));
                Fx.particle(l, ParticleTypes.SWEEP_ATTACK, fx, 2, .4, .3, .4, 0);
                Fx.particle(l, ParticleTypes.CRIT, fx, 10, .5, .4, .5, .2);
                Fx.sound(l, fx, SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 1.3f);
                Vec3 push = new Vec3(dir.x, 0, dir.z);
                if (push.lengthSqr() > 0) push = push.normalize();
                for (LivingEntity le : cone(p, range, 0.55)) {
                    m.hit(p, le, per);
                    if (ts.isFrozen(le)) continue;
                    if (last) {
                        Fx.push(le, new Vec3(push.x * finisher, 0.55, push.z * finisher));
                        Fx.particle(l, ParticleTypes.EXPLOSION, le.position().add(0, 1, 0), 1);
                        Fx.sound(l, le.position(), SoundEvents.GENERIC_EXPLODE, 0.7f, 1.6f);
                    } else Fx.push(le, le.getDeltaMovement().scale(0.3).add(push.scale(0.12)));
                }
                if (last) {
                    m.anim(p, "punch");
                    if (finisherShout != null) Txt.bar(p, finisherShout, ChatFormatting.GOLD, ChatFormatting.BOLD);
                }
            });
        }
    }

    /** One heavy hit on whatever the player is looking at. Returns the target, or null on a miss. */
    public LivingEntity strike(ServerPlayer p, double range, double damage, double knockback) {
        m.standForward(p, 12);
        m.anim(p, "punch");
        LivingEntity t = target(p, range);
        ServerLevel l = p.level();
        Vec3 fx = t != null ? Fx.center(t) : p.getEyePosition().add(p.getLookAngle().scale(Math.min(range, 2.5)));
        Fx.particle(l, ParticleTypes.SWEEP_ATTACK, fx, 1);
        Fx.sound(l, fx, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
        if (t == null) return null;
        m.hit(p, t, damage);
        Fx.particle(l, ParticleTypes.CRIT, fx, 20, .3, .4, .3, .3);
        if (knockback > 0 && !ts.isFrozen(t)) {
            Vec3 d = p.getLookAngle();
            Vec3 push = new Vec3(d.x, 0, d.z);
            if (push.lengthSqr() > 0) { push = push.normalize().scale(knockback); Fx.push(t, new Vec3(push.x, 0.35, push.z)); }
        }
        return t;
    }

    /** Damage everything around a point. */
    public void blast(ServerPlayer p, Vec3 c, double radius, double damage, double knockback) {
        ServerLevel l = p.level();
        Fx.particle(l, ParticleTypes.EXPLOSION, c, 3, radius / 3, .3, radius / 3, 0);
        Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE, 1.5f, 1f);
        for (LivingEntity le : around(p, c, radius)) {
            m.hit(p, le, damage);
            if (knockback > 0 && !ts.isFrozen(le)) {
                Vec3 v = new Vec3(le.getX() - c.x, 0, le.getZ() - c.z);
                if (v.lengthSqr() > 0.01) { v = v.normalize().scale(knockback); Fx.push(le, new Vec3(v.x, 0.4, v.z)); }
            }
        }
    }

    // ------------------------------------------------------------ projectiles

    /** Throw a projectile that looks like `look`. onHit gets the impact point and the entity hit (null for a block). */
    public StandShot shoot(ServerPlayer p, ItemStack look, Vec3 velocity, boolean gravity, double damage, BiConsumer<Vec3, LivingEntity> onHit) {
        StandShot s = new StandShot(p.level(), p, look.copy(), damage, onHit);
        s.setNoGravity(!gravity);
        s.setDeltaMovement(velocity);
        p.level().addFreshEntity(s);
        return s;
    }

    /** A fan of projectiles across `spreadDeg` degrees. */
    public void volley(ServerPlayer p, ItemStack look, int count, double spreadDeg, double speed, boolean gravity, double damage, BiConsumer<Vec3, LivingEntity> onHit) {
        for (int i = 0; i < count; i++) {
            float yaw = p.getYRot() + (float) (count == 1 ? 0 : -spreadDeg / 2 + spreadDeg * i / (count - 1));
            shoot(p, look, Vec3.directionFromRotation(p.getXRot(), yaw).scale(speed), gravity, damage, onHit);
        }
    }

    /** Steer a projectile toward the nearest valid target within `range` of it. */
    public void home(Projectile s, ServerPlayer p, double range, double speed) {
        int[] n = {0};
        Sched.timer(2, 2, () -> {
            if (s.isRemoved() || n[0]++ > 60) return false;
            if (ts.isFrozen(s)) return true;
            LivingEntity best = null;
            double bd = range;
            for (Entity e : s.level().getEntities(s, s.getBoundingBox().inflate(range), e -> valid(p, e))) {
                double d = e.position().distanceTo(s.position());
                if (d < bd) { bd = d; best = (LivingEntity) e; }
            }
            if (best == null) return true;
            Vec3 want = Fx.center(best).subtract(s.position()).normalize().scale(speed);
            s.setDeltaMovement(s.getDeltaMovement().scale(0.6).add(want.scale(0.4)));
            s.needsSync = true;
            return true;
        });
    }

    /** Hitscan shot: damages what the player is aiming at and draws a tracer. */
    public LivingEntity hitscan(ServerPlayer p, double range, double damage, ParticleOptions tracer) {
        LivingEntity t = target(p, range);
        Vec3 eye = p.getEyePosition();
        double len = t != null ? eye.distanceTo(Fx.center(t)) : eye.distanceTo(aim(p, range));
        Vec3 d = p.getLookAngle();
        for (double x = 1; x <= len; x += 0.6) Fx.particle(p.level(), tracer, eye.add(d.scale(x)), 1);
        if (t != null) m.hit(p, t, damage);
        return t;
    }

    /** Damage over time. */
    public void dot(ServerPlayer p, LivingEntity t, double damage, int times, int period, ParticleOptions particle) {
        for (int i = 1; i <= times; i++) later((long) i * period, () -> {
            if (!online(p) || !valid(p, t)) return;
            m.hit(p, t, damage);
            if (t.level() instanceof ServerLevel l) Fx.particle(l, particle, t.position().add(0, 1, 0), 8, .3, .4, .3, 0.02);
        });
    }

    /** Make every valid target near the player glow through walls. */
    public int reveal(ServerPlayer p, double radius, double seconds) {
        int n = 0;
        for (LivingEntity le : around(p, p.position(), radius)) { buff(le, seconds, MobEffects.GLOWING, 0); n++; }
        return n;
    }

    public void root(LivingEntity e, double seconds) {
        buff(e, seconds, MobEffects.SLOWNESS, 9);
        if (!ts.isFrozen(e)) Fx.push(e, new Vec3(0, -0.2, 0));
    }

    // ------------------------------------------------------------ areas

    /** Repeating area effect. `each` runs for every valid target inside, every `period` ticks. */
    public void zone(ServerPlayer owner, Vec3 c, double radius, int ticks, int period, ParticleOptions particle, Consumer<LivingEntity> each) {
        ServerLevel l = owner.level();
        for (int t = 0; t < ticks; t += period) later(t, () -> {
            Fx.particle(l, particle, c.add(0, 0.3, 0), (int) (radius * 12), radius / 2, 0.3, radius / 2, 0.01);
            if (!online(owner) || owner.level() != l) return;
            for (LivingEntity le : around(owner, c, radius)) each.accept(le);
        });
    }

    /** One-shot trap: fires on the first valid target that walks in, then disappears. */
    public void trap(ServerPlayer owner, Vec3 c, double radius, int ticks, ParticleOptions particle, Consumer<LivingEntity> onTrigger) {
        boolean[] done = {false};
        ServerLevel l = owner.level();
        for (int t = 10; t < ticks; t += 4) later(t, () -> {
            if (done[0] || !online(owner) || owner.level() != l) return;
            Fx.particle(l, particle, c.add(0, 0.2, 0), 4, radius / 2, 0.05, radius / 2, 0);
            List<LivingEntity> in = around(owner, c, radius);
            if (in.isEmpty()) return;
            done[0] = true;
            onTrigger.accept(in.get(0));
        });
    }

    // ------------------------------------------------------------ movement

    public void dash(ServerPlayer p, double distance) {
        Vec3 v = Fx.flatDir(p);
        Fx.push(p, new Vec3(v.x * distance * 0.26, 0.18, v.z * distance * 0.26));
        Fx.particle(p.level(), ParticleTypes.CLOUD, p.position(), 12, .3, .1, .3, .05);
    }

    private boolean passable(ServerLevel l, BlockPos pos) { return l.getBlockState(pos).getCollisionShape(l, pos).isEmpty(); }

    /** Teleport along the look direction. throughWalls: ignore blocks and land in the furthest free spot. */
    public boolean blink(ServerPlayer p, double distance, boolean throughWalls) {
        Vec3 start = p.position();
        Vec3 dir = throughWalls ? Fx.flatDir(p) : p.getLookAngle();
        if (dir.lengthSqr() == 0) return false;
        dir = dir.normalize();
        ServerLevel l = p.level();
        Vec3 best = null;
        for (double d = 0.5; d <= distance; d += 0.5) {
            Vec3 at = start.add(dir.scale(d));
            BlockPos b = BlockPos.containing(at);
            boolean free = passable(l, b) && passable(l, b.above());
            if (free) best = at; else if (!throughWalls) break;
        }
        if (best == null) return false;
        Fx.particle(l, ParticleTypes.PORTAL, start.add(0, 1, 0), 30, .3, .6, .3, .2);
        p.resetFallDistance();
        Fx.teleport(p, best);
        Fx.particle(l, ParticleTypes.PORTAL, best.add(0, 1, 0), 30, .3, .6, .3, .2);
        return true;
    }

    /** Yank a target to just in front of the player. */
    public void pull(ServerPlayer p, LivingEntity t) {
        if (ts.isFrozen(t)) return;
        Vec3 dest = p.position().add(Fx.flatDir(p).scale(1.5));
        if (passable(p.level(), BlockPos.containing(dest))) Fx.teleport(t, dest);
    }

    // ------------------------------------------------------------ misc

    public void buff(LivingEntity e, double seconds, Holder<MobEffect> type, int amplifier) {
        e.addEffect(new MobEffectInstance(type, (int) (seconds * 20), amplifier, false, true, true), null);
    }

    public void heal(LivingEntity e, double amount) {
        e.setHealth((float) Math.min(e.getMaxHealth(), e.getHealth() + amount));
        if (e.level() instanceof ServerLevel l) Fx.particle(l, ParticleTypes.HEART, e.position().add(0, 1.6, 0), 5, .4, .3, .4, 0);
    }

    /** Untargetable + invisible for a while (stand hidden too). */
    public void ghost(ServerPlayer p, int ticks, Runnable onEnd) {
        m.setUntargetable(p, ticks);
        m.hideStand(p, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, false), null);
        for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), mob -> mob.getTarget() == p)) mob.setTarget(null);
        Fx.particle(p.level(), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 25, .3, .6, .3, .02);
        later(ticks, () -> {
            if (!online(p)) return;
            p.removeEffect(MobEffects.INVISIBILITY);
            Fx.particle(p.level(), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 25, .3, .6, .3, .02);
            if (onEnd != null) onEnd.run();
        });
    }

    /** Place a block for a while, then put back what was there. Respects world-edits: off. */
    public boolean tempBlock(ServerLevel l, BlockPos pos, BlockState state, int ticks) {
        if ("off".equalsIgnoreCase(Config.get().getString("world-edits", "temporary"))) return false;
        TempKey key = new TempKey(l, pos.immutable());
        BlockState old = l.getBlockState(pos);
        if (tempBlocks.containsKey(key) || old.is(Blocks.BEDROCK) || l.getBlockEntity(pos) != null) return false;
        tempBlocks.put(key, old);
        l.setBlock(pos, state, 2);
        later(ticks, () -> restore(key));
        return true;
    }

    private void restore(TempKey key) {
        BlockState old = tempBlocks.remove(key);
        if (old != null) key.level.setBlock(key.pos, old, 2);
    }

    /** Called on server stop so no temporary block is left behind. */
    public void restoreAll() { new ArrayList<>(tempBlocks.keySet()).forEach(this::restore); }
}
