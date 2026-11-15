package dev.luci.stands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shared building blocks for stand abilities. All damage goes through StandManager.hit(). */
public final class Abilities implements Listener {

    private record Shot(double damage, BiConsumer<Location, LivingEntity> onHit) { }

    /** Projectile materials that use the original mod's textures. */
    private static final Map<Material, String> SKINS = Map.of(Material.IRON_SWORD, "knife", Material.EMERALD, "emerald",
            Material.IRON_NUGGET, "nail", Material.STICK, "swordtip");
    private final StandManager m;
    private final TimeStopManager ts;
    private final NamespacedKey kShot;
    private final Map<String, Shot> shots = new HashMap<>();
    private final Map<Location, BlockData> tempBlocks = new HashMap<>();
    private int shotSeq;

    public Abilities(StandManager m, TimeStopManager ts) {
        this.m = m;
        this.ts = ts;
        this.kShot = new NamespacedKey(m.plugin(), "shot");
        Bukkit.getPluginManager().registerEvents(this, m.plugin());
    }

    private void later(long ticks, Runnable r) { Bukkit.getScheduler().runTaskLater(m.plugin(), r, ticks); }

    // ------------------------------------------------------------ targeting

    public boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity) || e.equals(p) || e instanceof ArmorStand || e.isDead()) return false;
        if (e instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || m.isUntargetable(o) || !m.plugin().pvp().canFight(p, o))) return false;
        return true;
    }

    /** Living things in a cone in front of the player. */
    public List<LivingEntity> cone(Player p, double range, double minDot) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getWorld().getNearbyEntities(eye, range, range, range)) {
            if (!valid(p, e)) continue;
            Vector to = e.getLocation().add(0, e.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            if (to.length() <= range && (to.length() < 1.2 || to.clone().normalize().dot(dir) >= minDot)) out.add((LivingEntity) e);
        }
        return out;
    }

    /** The living thing the player is looking at, or null. */
    public LivingEntity target(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), range,
                FluidCollisionMode.NEVER, true, 0.7, e -> valid(p, e));
        return r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    public List<LivingEntity> around(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) if (valid(p, e) && e.getLocation().distance(c) <= r + 0.5) out.add((LivingEntity) e);
        return out;
    }

    /** Where the player is pointing: the block/entity hit, or the point `range` blocks out. */
    public Location aim(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        return r != null ? r.getHitPosition().toLocation(p.getWorld()) : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
    }

    // ------------------------------------------------------------ melee

    /** Cone barrage: cfg keys damage-total, hits, range, finisher-knockback. Last hit launches. */
    public void rush(Player p, ConfigurationSection cfg, String soundKey, String shout, String finisherShout) {
        int hits = cfg.getInt("hits", 8);
        double per = cfg.getDouble("damage-total", 9.0) / hits;
        double range = cfg.getDouble("range", 4.0);
        double finisher = cfg.getDouble("finisher-knockback", 1.6);
        m.standForward(p, hits * 3 + 12);
        m.animFor(p, "rush", hits * 3);
        m.sound(p.getLocation(), soundKey);
        p.sendActionBar(Component.text(shout, NamedTextColor.GOLD, TextDecoration.BOLD));
        for (int i = 0; i < hits; i++) {
            final boolean last = i == hits - 1;
            later(2L + i * 3L, () -> {
                if (!p.isOnline() || p.isDead() || ts.isFrozen(p)) return;
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                Location fx = eye.clone().add(dir.clone().multiply(1.6));
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, fx, 2, .4, .3, .4, 0);
                p.getWorld().spawnParticle(Particle.CRIT, fx, 10, .5, .4, .5, .2);
                p.getWorld().playSound(fx, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 1.3f);
                Vector push = dir.clone().setY(0);
                if (push.lengthSquared() > 0) push.normalize();
                for (LivingEntity le : cone(p, range, 0.55)) {
                    m.hit(p, le, per);
                    if (ts.isFrozen(le)) continue;
                    if (last) {
                        le.setVelocity(push.clone().multiply(finisher).setY(0.55));
                        le.getWorld().spawnParticle(Particle.EXPLOSION, le.getLocation().add(0, 1, 0), 1);
                        le.getWorld().playSound(le.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
                    } else le.setVelocity(le.getVelocity().multiply(0.3).add(push.clone().multiply(0.12)));
                }
                if (last) {
                    m.anim(p, "punch");
                    if (finisherShout != null) p.sendActionBar(Component.text(finisherShout, NamedTextColor.GOLD, TextDecoration.BOLD));
                }
            });
        }
    }

    /** One heavy hit on whatever the player is looking at. Returns the target, or null on a miss. */
    public LivingEntity strike(Player p, double range, double damage, double knockback) {
        m.standForward(p, 12);
        m.anim(p, "punch");
        LivingEntity t = target(p, range);
        Location fx = t != null ? t.getLocation().add(0, t.getHeight() / 2, 0) : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(Math.min(range, 2.5)));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, fx, 1);
        p.getWorld().playSound(fx, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
        if (t == null) return null;
        m.hit(p, t, damage);
        p.getWorld().spawnParticle(Particle.CRIT, fx, 20, .3, .4, .3, .3);
        if (knockback > 0 && !ts.isFrozen(t)) {
            Vector push = p.getEyeLocation().getDirection().setY(0);
            if (push.lengthSquared() > 0) t.setVelocity(push.normalize().multiply(knockback).setY(0.35));
        }
        return t;
    }

    /** Damage everything around a point. */
    public void blast(Player p, Location c, double radius, double damage, double knockback) {
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3, radius / 3, .3, radius / 3, 0);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1f);
        for (LivingEntity le : around(p, c, radius)) {
            m.hit(p, le, damage);
            if (knockback > 0 && !ts.isFrozen(le)) {
                Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(knockback).setY(0.4));
            }
        }
    }

    // ------------------------------------------------------------ projectiles

    /** Throw a projectile that looks like `item`. onHit gets the impact point and the entity hit (null for a block). */
    public Snowball shoot(Player p, Material item, Vector velocity, boolean gravity, double damage, BiConsumer<Location, LivingEntity> onHit) {
        Snowball s = p.launchProjectile(Snowball.class, velocity);
        ItemStack skin = new ItemStack(item);
        String model = SKINS.get(item);
        if (model != null) skin.editMeta(meta -> meta.setItemModel(new NamespacedKey("jojo", model)));
        s.setItem(skin);
        s.setGravity(gravity);
        String id = Integer.toString(++shotSeq);
        s.getPersistentDataContainer().set(kShot, PersistentDataType.STRING, id);
        shots.put(id, new Shot(damage, onHit));
        later(100L, () -> { if (shots.remove(id) != null && s.isValid() && !ts.isFrozen(s)) s.remove(); });
        return s;
    }

    /** A fan of projectiles across `spreadDeg` degrees. */
    public void volley(Player p, Material item, int count, double spreadDeg, double speed, boolean gravity, double damage, BiConsumer<Location, LivingEntity> onHit) {
        Location eye = p.getEyeLocation();
        for (int i = 0; i < count; i++) {
            Location l = eye.clone();
            l.setYaw(l.getYaw() + (float) (count == 1 ? 0 : -spreadDeg / 2 + spreadDeg * i / (count - 1)));
            shoot(p, item, l.getDirection().multiply(speed), gravity, damage, onHit);
        }
    }

    @EventHandler
    public void onShotHit(ProjectileHitEvent e) {
        String id = e.getEntity().getPersistentDataContainer().get(kShot, PersistentDataType.STRING);
        if (id == null) return;
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        Shot shot = shots.get(id);
        if (shot == null) { e.getEntity().remove(); return; }
        if (e.getHitEntity() != null) {
            e.setCancelled(true);
            if (!valid(shooter, e.getHitEntity())) return;   // passes through the shooter / stands
            LivingEntity le = (LivingEntity) e.getHitEntity();
            shots.remove(id);
            e.getEntity().remove();
            if (shot.damage > 0) m.hit(shooter, le, shot.damage);
            le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1.4f);
            if (shot.onHit != null) shot.onHit.accept(le.getLocation().add(0, le.getHeight() / 2, 0), le);
        } else {
            shots.remove(id);
            if (shot.onHit != null) shot.onHit.accept(e.getEntity().getLocation(), null);
        }
    }

    /** Steer a projectile toward the nearest valid target within `range` of it. */
    public void home(Projectile s, Player p, double range, double speed) {
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(m.plugin(), task -> {
            if (!s.isValid() || n[0]++ > 60) { task.cancel(); return; }
            if (ts.isFrozen(s)) return;
            LivingEntity best = null; double bd = range;
            for (Entity e : s.getNearbyEntities(range, range, range)) {
                if (!valid(p, e)) continue;
                double d = e.getLocation().distance(s.getLocation());
                if (d < bd) { bd = d; best = (LivingEntity) e; }
            }
            if (best == null) return;
            Vector want = best.getLocation().add(0, best.getHeight() / 2, 0).toVector().subtract(s.getLocation().toVector()).normalize().multiply(speed);
            s.setVelocity(s.getVelocity().multiply(0.6).add(want.multiply(0.4)));
        }, 2L, 2L);
    }

    /** Hitscan shot: damages what the player is aiming at and draws a tracer. */
    public LivingEntity hitscan(Player p, double range, double damage, Particle tracer) {
        LivingEntity t = target(p, range);
        Location eye = p.getEyeLocation();
        double len = t != null ? eye.distance(t.getLocation().add(0, t.getHeight() / 2, 0)) : eye.distance(aim(p, range));
        Vector d = eye.getDirection();
        for (double x = 1; x <= len; x += 0.6) p.getWorld().spawnParticle(tracer, eye.clone().add(d.clone().multiply(x)), 1, 0, 0, 0, 0);
        if (t != null) m.hit(p, t, damage);
        return t;
    }

    /** Damage over time. */
    public void dot(Player p, LivingEntity t, double damage, int times, int period, Particle particle) {
        for (int i = 1; i <= times; i++) later((long) i * period, () -> {
            if (!p.isOnline() || !valid(p, t)) return;
            m.hit(p, t, damage);
            t.getWorld().spawnParticle(particle, t.getLocation().add(0, 1, 0), 8, .3, .4, .3, 0.02);
        });
    }

    /** Make every valid target near the player glow through walls. */
    public int reveal(Player p, double radius, double seconds) {
        int n = 0;
        for (LivingEntity le : around(p, p.getLocation(), radius)) { buff(le, seconds, PotionEffectType.GLOWING, 0); n++; }
        return n;
    }

    public void root(LivingEntity e, double seconds) {
        buff(e, seconds, PotionEffectType.SLOWNESS, 9);
        if (!ts.isFrozen(e)) e.setVelocity(new Vector(0, -0.2, 0));
    }

    // ------------------------------------------------------------ areas

    /** Repeating area effect. `each` runs for every valid target inside, every `period` ticks. */
    public void zone(Player owner, Location c, double radius, int ticks, int period, Particle particle, Consumer<LivingEntity> each) {
        for (int t = 0; t < ticks; t += period) later(t, () -> {
            c.getWorld().spawnParticle(particle, c.clone().add(0, 0.3, 0), (int) (radius * 12), radius / 2, 0.3, radius / 2, 0.01);
            if (!owner.isOnline()) return;
            for (LivingEntity le : around(owner, c, radius)) each.accept(le);
        });
    }

    /** One-shot trap: fires on the first valid target that walks in, then disappears. */
    public void trap(Player owner, Location c, double radius, int ticks, Particle particle, Consumer<LivingEntity> onTrigger) {
        boolean[] done = {false};
        for (int t = 10; t < ticks; t += 4) later(t, () -> {
            if (done[0] || !owner.isOnline()) return;
            c.getWorld().spawnParticle(particle, c.clone().add(0, 0.2, 0), 4, radius / 2, 0.05, radius / 2, 0);
            List<LivingEntity> in = around(owner, c, radius);
            if (in.isEmpty()) return;
            done[0] = true;
            onTrigger.accept(in.get(0));
        });
    }

    // ------------------------------------------------------------ movement

    public void dash(Player p, double distance) {
        Vector v = p.getLocation().getDirection().setY(0);
        if (v.lengthSquared() == 0) return;
        p.setVelocity(v.normalize().multiply(distance * 0.26).setY(0.18));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, .3, .1, .3, .05);
    }

    /** Teleport along the look direction. throughWalls: ignore blocks and land in the furthest free spot. */
    public boolean blink(Player p, double distance, boolean throughWalls) {
        Location start = p.getLocation();
        Vector dir = throughWalls ? start.getDirection().setY(0) : start.getDirection();
        if (dir.lengthSquared() == 0) return false;
        dir.normalize();
        Location best = null;
        for (double d = 0.5; d <= distance; d += 0.5) {
            Location l = start.clone().add(dir.clone().multiply(d));
            boolean free = l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
            if (free) best = l; else if (!throughWalls) break;
        }
        if (best == null) return false;
        p.getWorld().spawnParticle(Particle.PORTAL, start.clone().add(0, 1, 0), 30, .3, .6, .3, .2);
        p.setFallDistance(0);
        p.teleport(best);
        p.getWorld().spawnParticle(Particle.PORTAL, best.clone().add(0, 1, 0), 30, .3, .6, .3, .2);
        return true;
    }

    /** Yank a target to just in front of the player. */
    public void pull(Player p, LivingEntity t) {
        if (ts.isFrozen(t)) return;
        Location dest = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        dest.setYaw(t.getLocation().getYaw()); dest.setPitch(t.getLocation().getPitch());
        if (dest.getBlock().isPassable()) t.teleport(dest);
    }

    // ------------------------------------------------------------ misc

    public void buff(LivingEntity e, double seconds, PotionEffectType type, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, (int) (seconds * 20), amplifier, false, true, true));
    }

    public void heal(LivingEntity e, double amount) {
        double max = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        e.setHealth(Math.min(max, e.getHealth() + amount));
        e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, 1.6, 0), 5, .4, .3, .4, 0);
    }

    /** Untargetable + invisible to other players for a while (stand hidden too). */
    public void ghost(Player p, int ticks, Runnable onEnd) {
        m.setUntargetable(p, ticks);
        m.hideStand(p, ticks);
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) o.hidePlayer(m.plugin(), p);
        for (Entity e : p.getNearbyEntities(32, 32, 32)) if (e instanceof Mob mob && p.equals(mob.getTarget())) mob.setTarget(null);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 25, .3, .6, .3, .02);
        later(ticks, () -> {
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) o.showPlayer(m.plugin(), p);
            if (!p.isOnline()) return;
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 25, .3, .6, .3, .02);
            if (onEnd != null) onEnd.run();
        });
    }

    /** Place a block for a while, then put back what was there. Respects world-edits: off. */
    public boolean tempBlock(Block b, Material material, int ticks) {
        if ("off".equalsIgnoreCase(m.plugin().getConfig().getString("world-edits", "temporary"))) return false;
        Location key = b.getLocation();
        if (tempBlocks.containsKey(key) || b.getType() == Material.BEDROCK || b.getState() instanceof org.bukkit.block.Container) return false;
        tempBlocks.put(key, b.getBlockData());
        b.setType(material, false);
        later(ticks, () -> restore(key));
        return true;
    }

    private void restore(Location key) {
        BlockData old = tempBlocks.remove(key);
        if (old != null) key.getBlock().setBlockData(old, false);
    }

    /** Called on plugin disable so no temporary block is left behind. */
    public void restoreAll() { new ArrayList<>(tempBlocks.keySet()).forEach(this::restore); }
}
