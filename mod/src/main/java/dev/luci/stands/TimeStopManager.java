package dev.luci.stands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * PvP time stop: freezes living entities and projectiles in a radius for a few seconds.
 * Damage dealt to frozen targets is stored (capped vs stand users) and applied when time resumes.
 */
public final class TimeStopManager {

    static final Set<String> TIME_STOPPERS = Set.of("theworld", "starplatinum");
    private static final Identifier MOD_ID = StandsMod.id("timestop");
    private static final List<Holder<Attribute>> FROZEN_ATTRS = List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.GRAVITY);

    final class Stop {
        final UUID caster;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final long start = Sched.now();
        final long end;
        final Set<UUID> frozen = new HashSet<>();
        final Set<UUID> released = new HashSet<>();
        final Map<UUID, Double> stored = new HashMap<>();
        final Map<UUID, Vec3> projectiles = new HashMap<>();
        final Map<UUID, Boolean> projNoGravity = new HashMap<>();
        final Map<UUID, Vec3> holdAt = new HashMap<>();          // where frozen players are pinned
        final Map<UUID, Boolean> mobAi = new HashMap<>();
        final Map<UUID, Long> ownShots = new HashMap<>();       // caster's projectiles: freeze 3 ticks after launch
        final ServerBossEvent bar;

        Stop(ServerPlayer caster, double seconds, double radius) {
            this.caster = caster.getUUID();
            this.level = caster.level();
            this.center = caster.position();
            this.radius = radius;
            this.end = start + Math.round(seconds * 20);
            this.bar = new ServerBossEvent(UUID.randomUUID(), Txt.of("TIME HAS STOPPED", ChatFormatting.GOLD), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        }

        boolean contains(Vec3 p) { return p.distanceTo(center) <= radius; }
    }

    /** After a stop ends, the caster's damage to its victims still counts toward the cap for a moment. */
    private record PostWindow(UUID caster, Map<UUID, Double> capLeft, long until) { }

    private final List<Stop> stops = new ArrayList<>();
    private final List<PostWindow> windows = new ArrayList<>();
    private boolean applying;

    private double cap() { return Config.get().getDouble("time-stop.stored-damage-cap", 10.0); }

    private Entity entity(ServerLevel l, UUID id) { return l.getEntity(id); }

    // ------------------------------------------------------------ queries

    public boolean isFrozen(Entity e) {
        if (e == null) return false;
        for (Stop s : stops) if (s.frozen.contains(e.getUUID()) && !s.released.contains(e.getUUID())) return true;
        return false;
    }

    public Stop stopOf(Player caster) {
        for (Stop s : stops) if (s.caster.equals(caster.getUUID())) return s;
        return null;
    }

    public boolean hasStop(Player caster) { return stopOf(caster) != null; }

    public boolean anyNear(ServerLevel l, Vec3 p) {
        for (Stop s : stops) if (s.level == l && s.center.distanceTo(p) <= s.radius + 5) return true;
        return false;
    }

    // ------------------------------------------------------------ start / end

    public boolean start(ServerPlayer caster, double seconds) {
        if (anyNear(caster.level(), caster.position())) {
            Txt.bar(caster, "Time is already stopped here.", ChatFormatting.RED);
            return false;
        }
        double radius = Config.get().getDouble("time-stop.radius", 30);
        Stop s = new Stop(caster, seconds, radius);
        stops.add(s);
        for (Entity e : s.level.getEntities(caster, new AABB(s.center, s.center).inflate(radius), e -> s.contains(e.position()))) {
            if (e instanceof ServerPlayer other && !StandsMod.pvp().canFight(caster, other)) continue;   // PvP off: they aren't frozen
            freeze(s, e);
        }
        // Visuals: expanding ring + boss bar for everyone nearby.
        for (int i = 0; i < 10; i++) {
            final double r = 2 + i * (radius / 10.0);
            Sched.later(i, () -> ring(s, r));
        }
        for (ServerPlayer p : s.level.players()) if (p.position().distanceTo(s.center) <= radius + 10) s.bar.addPlayer(p);
        return true;
    }

    private void ring(Stop s, double r) {
        int pts = (int) Math.max(24, r * 6);
        Vec3 c = s.center.add(0, 1, 0);
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            Fx.particle(s.level, ParticleTypes.REVERSE_PORTAL, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0.6, 0, 0);
        }
    }

    private void freeze(Stop s, Entity e) {
        if (e instanceof ArmorStand || e instanceof dev.luci.stands.entity.StandEntity) return;
        if (e instanceof Projectile pr) {
            s.projectiles.put(pr.getUUID(), pr.getDeltaMovement());
            s.projNoGravity.put(pr.getUUID(), pr.isNoGravity());
            pr.setDeltaMovement(Vec3.ZERO);
            pr.setNoGravity(true);
            pr.needsSync = true;
            s.frozen.add(pr.getUUID());
            return;
        }
        if (!(e instanceof LivingEntity le)) return;
        s.frozen.add(le.getUUID());
        Fx.push(le, Vec3.ZERO);
        if (le instanceof ServerPlayer p) {
            for (Holder<Attribute> a : FROZEN_ATTRS) addMod(p, a);
            s.holdAt.put(p.getUUID(), p.position());
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0, false, false, false), null);
        } else if (le instanceof Mob m) {
            s.mobAi.put(m.getUUID(), m.isNoAi());
            m.setNoAi(true);
            m.setNoGravity(true);
        }
    }

    private void addMod(LivingEntity p, Holder<Attribute> a) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null || inst.getModifier(MOD_ID) != null) return;
        inst.addTransientModifier(new AttributeModifier(MOD_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Remove the freeze modifiers. Safe to call on anyone. */
    public void clearMods(LivingEntity p) {
        for (Holder<Attribute> a : FROZEN_ATTRS) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && inst.getModifier(MOD_ID) != null) inst.removeModifier(MOD_ID);
        }
    }

    private void unfreeze(Stop s, UUID id) {
        Entity e = entity(s.level, id);
        if (e == null) return;
        if (e instanceof ServerPlayer p) clearMods(p);
        else if (e instanceof Mob m) { m.setNoAi(s.mobAi.getOrDefault(id, false)); m.setNoGravity(false); }
        else if (e instanceof Projectile pr) {
            pr.setNoGravity(s.projNoGravity.getOrDefault(id, false));
            Vec3 v = s.projectiles.get(id);
            if (v != null) { pr.setDeltaMovement(v); pr.needsSync = true; }
        }
    }

    private void end(Stop s) {
        stops.remove(s);
        ServerPlayer caster = StandsMod.stands().player(s.caster);
        for (UUID id : s.frozen) if (!s.released.contains(id)) unfreeze(s, id);
        s.bar.removeAllPlayers();
        Fx.sound(s.level, s.center, SoundEvents.BEACON_DEACTIVATE, 3f, 0.6f);
        // Apply stored damage all at once, with knockback away from the caster.
        for (var en : s.stored.entrySet()) {
            if (!(entity(s.level, en.getKey()) instanceof LivingEntity le) || !le.isAlive()) continue;
            applying = true;
            try { le.setInvulnerableTime(0); StandsMod.stands().damageRaw(caster, le, en.getValue()); } finally { applying = false; }
            if (caster != null) {
                Vec3 v = new Vec3(le.getX() - caster.getX(), 0, le.getZ() - caster.getZ());
                if (v.lengthSqr() > 0.001) { v = v.normalize().scale(0.9); Fx.push(le, new Vec3(v.x, 0.35, v.z)); }
            }
            Fx.particle(s.level, ParticleTypes.CRIT, le.position().add(0, 1, 0), 30, .3, .5, .3, .3);
        }
        Map<UUID, Double> left = new HashMap<>();
        for (UUID id : s.frozen) {
            Entity e = entity(s.level, id);
            if (e != null && StandsMod.stands().standRules(caster, e)) left.put(id, Math.max(0, cap() - s.stored.getOrDefault(id, 0.0)));
        }
        windows.add(new PostWindow(s.caster, left, Sched.now() + 20));
        if (caster != null) Txt.bar(caster, "Time resumes.", ChatFormatting.GOLD);
    }

    public void endAll() { new ArrayList<>(stops).forEach(this::end); }

    void tick() {
        long now = Sched.now();
        windows.removeIf(w -> now > w.until);
        long counterAfter = Math.round(Config.get().getDouble("time-stop.counter-after", 1.0) * 20);
        for (Stop s : new ArrayList<>(stops)) {
            ServerPlayer caster = StandsMod.stands().player(s.caster);
            if (caster == null || !caster.isAlive() || caster.level() != s.level || now >= s.end) { end(s); continue; }
            s.bar.setProgress(Math.max(0f, Math.min(1f, (s.end - now) / (float) (s.end - s.start))));
            // "I can move in stopped time": other time-stop stand users break free after a moment.
            if (now - s.start >= counterAfter) for (UUID id : s.frozen) {
                if (s.released.contains(id) || !(entity(s.level, id) instanceof ServerPlayer p)) continue;
                String stand = StandsMod.stands().standOf(p);
                if (stand != null && TIME_STOPPERS.contains(stand)) {
                    s.released.add(id);
                    clearMods(p);
                    Txt.bar(p, "You can move in stopped time!", ChatFormatting.LIGHT_PURPLE);
                }
            }
            // New projectiles in the zone: the caster's (and released users') hang after a few ticks, everyone else's at once.
            for (Projectile pr : s.level.getEntitiesOfClass(Projectile.class, new AABB(s.center, s.center).inflate(s.radius), pr -> s.contains(pr.position()))) {
                if (s.frozen.contains(pr.getUUID())) continue;
                Entity owner = pr.getOwner();
                boolean free = owner != null && (owner.getUUID().equals(s.caster) || s.released.contains(owner.getUUID()));
                if (!free) { freeze(s, pr); continue; }
                long seen = s.ownShots.computeIfAbsent(pr.getUUID(), k -> now);
                if (now - seen >= 3) freeze(s, pr);
            }
            // Keep frozen things still.
            for (UUID id : s.frozen) {
                if (s.released.contains(id)) continue;
                Entity e = entity(s.level, id);
                if (e == null) continue;
                if (e instanceof ServerPlayer p) {
                    Vec3 hold = s.holdAt.get(id);
                    if (hold != null && p.position().distanceToSqr(hold) > 0.01) Fx.teleport(p, hold);
                } else {
                    e.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
    }

    // ------------------------------------------------------------ damage hooks

    private static Entity attackerOf(DamageSource src) { return src.getEntity() != null ? src.getEntity() : src.getDirectEntity(); }

    /** ALLOW_DAMAGE: false cancels. Hits on frozen targets are stored instead. */
    boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
        if (applying) return true;
        Entity attacker = attackerOf(src);
        if (attacker != null && isFrozen(attacker)) return false;
        for (Stop s : stops) {
            if (!s.frozen.contains(victim.getUUID()) || s.released.contains(victim.getUUID())) continue;
            double have = s.stored.getOrDefault(victim.getUUID(), 0.0);
            double total = have + amount;
            // Stand users: capped, so a time stop can't delete them. Everyone else: no mercy.
            s.stored.put(victim.getUUID(), StandsMod.stands().standRules(attacker instanceof ServerPlayer ap ? ap : null, victim) ? Math.min(cap(), total) : total);
            Fx.particle(s.level, ParticleTypes.CRIT, victim.position().add(0, 1, 0), 6, .2, .4, .2, .1);
            Fx.sound(s.level, victim.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
            return false;
        }
        return true;
    }

    /** Just after a stop: the caster's follow-up damage still respects the cap. */
    float modifyDamage(LivingEntity victim, DamageSource src, float amount) {
        if (applying || windows.isEmpty()) return amount;
        Entity attacker = attackerOf(src);
        if (attacker == null) return amount;
        for (PostWindow w : windows) {
            if (!w.caster.equals(attacker.getUUID())) continue;
            Double left = w.capLeft.get(victim.getUUID());
            if (left == null) continue;
            double dmg = Math.min(amount, left);
            w.capLeft.put(victim.getUUID(), left - dmg);
            return (float) dmg;
        }
        return amount;
    }

    void onQuit(ServerPlayer p) {
        clearMods(p);
        Stop own = stopOf(p);
        if (own != null) end(own);
    }
}
