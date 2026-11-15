package dev.luci.stands;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * PvP time stop: freezes living entities and projectiles in a radius for a few seconds.
 * Damage dealt to frozen targets is stored (capped) and applied when time resumes.
 */
public final class TimeStopManager implements Listener {

    static final Set<String> TIME_STOPPERS = Set.of("theworld", "starplatinum");

    final class Stop {
        final UUID caster;
        final Location center;
        final double radius;
        final long start = System.currentTimeMillis();
        final long end;
        final Set<UUID> frozen = new HashSet<>();
        final Set<UUID> released = new HashSet<>();
        final Map<UUID, Double> stored = new HashMap<>();
        final Map<UUID, Vector> projectiles = new HashMap<>();
        final Map<UUID, Boolean> mobAi = new HashMap<>();
        final BossBar bar;
        Stop(Player caster, double seconds, double radius) {
            this.caster = caster.getUniqueId();
            this.center = caster.getLocation();
            this.radius = radius;
            this.end = start + (long) (seconds * 1000);
            this.bar = BossBar.bossBar(Component.text("TIME HAS STOPPED", NamedTextColor.GOLD), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
    }

    /** After a stop ends, the caster's damage to its victims still counts toward the cap for a moment. */
    private record PostWindow(UUID caster, Map<UUID, Double> capLeft, long until) { }

    private final StandsPlugin plugin;
    private final NamespacedKey modKey;
    private final List<Stop> stops = new ArrayList<>();
    private final List<PostWindow> windows = new ArrayList<>();
    private boolean applying;

    public TimeStopManager(StandsPlugin plugin) {
        this.plugin = plugin;
        this.modKey = new NamespacedKey(plugin, "timestop");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private double cap() { return plugin.getConfig().getDouble("time-stop.stored-damage-cap", 10.0); }

    // ------------------------------------------------------------ queries

    public boolean isFrozen(Entity e) {
        for (Stop s : stops) if (s.frozen.contains(e.getUniqueId()) && !s.released.contains(e.getUniqueId())) return true;
        return false;
    }

    public Stop stopOf(Player caster) {
        for (Stop s : stops) if (s.caster.equals(caster.getUniqueId())) return s;
        return null;
    }

    public boolean hasStop(Player caster) { return stopOf(caster) != null; }

    public boolean anyNear(Location l) {
        for (Stop s : stops) if (s.center.getWorld() == l.getWorld() && s.center.distance(l) <= s.radius + 5) return true;
        return false;
    }

    // ------------------------------------------------------------ start / end

    public boolean start(Player caster, double seconds) {
        if (anyNear(caster.getLocation())) {
            caster.sendActionBar(Component.text("Time is already stopped here.", NamedTextColor.RED));
            return false;
        }
        double radius = plugin.getConfig().getDouble("time-stop.radius", 30);
        Stop s = new Stop(caster, seconds, radius);
        stops.add(s);
        for (Entity e : caster.getWorld().getNearbyEntities(caster.getLocation(), radius, radius, radius)) {
            if (e.equals(caster) || e.getLocation().distance(s.center) > radius) continue;
            if (e instanceof Player other && !plugin.pvp().canFight(caster, other)) continue;   // PvP off: they aren't frozen
            freeze(s, e);
        }
        // Visuals: expanding sphere + boss bar for everyone nearby.
        for (int i = 0; i < 10; i++) {
            final double r = 2 + i * (radius / 10.0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> ring(s.center.clone().add(0, 1, 0), r), i);
        }
        for (Player p : caster.getWorld().getPlayers()) if (p.getLocation().distance(s.center) <= radius + 10) p.showBossBar(s.bar);
        return true;
    }

    private void ring(Location c, double r) {
        int pts = (int) Math.max(24, r * 6);
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            c.getWorld().spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0.6, 0, 0);
        }
    }

    private void freeze(Stop s, Entity e) {
        if (e instanceof ArmorStand || e instanceof Display || e instanceof Interaction) return;
        if (e instanceof Projectile pr) {
            s.projectiles.put(pr.getUniqueId(), pr.getVelocity());
            pr.setVelocity(new Vector());
            pr.setGravity(false);
            s.frozen.add(pr.getUniqueId());
            return;
        }
        if (!(e instanceof LivingEntity le)) return;
        s.frozen.add(le.getUniqueId());
        le.setVelocity(new Vector());
        if (le instanceof Player p) {
            addMod(p, Attribute.MOVEMENT_SPEED);
            addMod(p, Attribute.JUMP_STRENGTH);
            addMod(p, Attribute.GRAVITY);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
        } else if (le instanceof Mob m) {
            s.mobAi.put(m.getUniqueId(), m.hasAI());
            m.setAI(false);
            m.setGravity(false);
        }
    }

    private void addMod(Player p, Attribute a) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null || inst.getModifier(modKey) != null) return;
        inst.addModifier(new AttributeModifier(modKey, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    private void clearMods(Player p) {
        for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.GRAVITY)) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && inst.getModifier(modKey) != null) inst.removeModifier(modKey);
        }
    }

    private void unfreeze(Stop s, UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e == null) return;
        if (e instanceof Player p) clearMods(p);
        else if (e instanceof Mob m) { m.setAI(s.mobAi.getOrDefault(id, true)); m.setGravity(true); }
        else if (e instanceof Projectile pr) { pr.setGravity(true); Vector v = s.projectiles.get(id); if (v != null) pr.setVelocity(v); }
    }

    private void end(Stop s) {
        stops.remove(s);
        Player caster = Bukkit.getPlayer(s.caster);
        for (UUID id : s.frozen) if (!s.released.contains(id)) unfreeze(s, id);
        for (Player p : s.center.getWorld().getPlayers()) p.hideBossBar(s.bar);
        s.center.getWorld().playSound(s.center, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 3f, 0.6f);
        // Apply stored damage all at once, with knockback away from the caster.
        Map<UUID, Double> left = new HashMap<>();
        for (var en : s.stored.entrySet()) {
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            applying = true;
            try { plugin.stands().damageRaw(caster, le, en.getValue()); } finally { applying = false; }
            if (caster != null) le.setVelocity(le.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0).normalize().multiply(0.9).setY(0.35));
            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 30, .3, .5, .3, .3);
        }
        for (UUID id : s.frozen) if (Bukkit.getEntity(id) != null && plugin.stands().standRules(caster, Bukkit.getEntity(id))) left.put(id, Math.max(0, cap() - s.stored.getOrDefault(id, 0.0)));
        windows.add(new PostWindow(s.caster, left, System.currentTimeMillis() + 1000));
        if (caster != null) caster.sendActionBar(Component.text("Time resumes.", NamedTextColor.GOLD));
    }

    public void endAll() { new ArrayList<>(stops).forEach(this::end); }

    private void tick() {
        long now = System.currentTimeMillis();
        windows.removeIf(w -> now > w.until);
        double counterAfter = plugin.getConfig().getDouble("time-stop.counter-after", 1.0) * 1000;
        for (Stop s : new ArrayList<>(stops)) {
            Player caster = Bukkit.getPlayer(s.caster);
            if (caster == null || caster.isDead() || now >= s.end) { end(s); continue; }
            s.bar.progress(Math.max(0f, Math.min(1f, (s.end - now) / (float) (s.end - s.start))));
            // "I can move in stopped time": other time-stop stand users break free after a moment.
            if (now - s.start >= counterAfter) for (UUID id : s.frozen) {
                if (s.released.contains(id) || !(Bukkit.getEntity(id) instanceof Player p)) continue;
                String stand = plugin.stands().standOf(p);
                if (stand != null && TIME_STOPPERS.contains(stand)) {
                    s.released.add(id); clearMods(p);
                    p.sendActionBar(Component.text("You can move in stopped time!", NamedTextColor.LIGHT_PURPLE));
                }
            }
            // Keep frozen things still.
            for (UUID id : s.frozen) {
                if (s.released.contains(id)) continue;
                Entity e = Bukkit.getEntity(id);
                if (e != null && !(e instanceof Player)) e.setVelocity(new Vector());
            }
        }
    }

    // ------------------------------------------------------------ events

    /** Projectiles thrown by a time-stop caster hang in the air after a few ticks. */
    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        if (isFrozen(shooter)) { e.setCancelled(true); return; }
        for (Stop s : stops) {
            if (!s.caster.equals(shooter.getUniqueId()) && !s.released.contains(shooter.getUniqueId())) continue;
            Projectile pr = e.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (pr.isValid() && stops.contains(s)) freeze(s, pr); }, 3L);
        }
    }

    private Entity attackerOf(Entity damager) {
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity sh) return sh;
        return damager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (applying) return;
        Entity attacker = attackerOf(e.getDamager());
        if (isFrozen(attacker)) { e.setCancelled(true); return; }
        Entity victim = e.getEntity();
        for (Stop s : stops) {
            if (!s.frozen.contains(victim.getUniqueId()) || s.released.contains(victim.getUniqueId())) continue;
            e.setCancelled(true);
            double have = s.stored.getOrDefault(victim.getUniqueId(), 0.0);
            // Stand users: capped, so a time stop can't delete them. Everyone else: no mercy.
            double total = have + e.getDamage();
            s.stored.put(victim.getUniqueId(), plugin.stands().standRules(attacker instanceof Player ap ? ap : null, victim) ? Math.min(cap(), total) : total);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 6, .2, .4, .2, .1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
            return;
        }
        for (PostWindow w : windows) {
            if (!w.caster.equals(attacker.getUniqueId())) continue;
            Double left = w.capLeft.get(victim.getUniqueId());
            if (left == null) continue;
            double dmg = Math.min(e.getDamage(), left);
            w.capLeft.put(victim.getUniqueId(), left - dmg);
            if (dmg <= 0) e.setCancelled(true); else e.setDamage(dmg);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!isFrozen(e.getPlayer())) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fixed = from.clone(); fixed.setYaw(to.getYaw()); fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onInteract(PlayerInteractEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onInteractEntity(PlayerInteractEntityEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onSwap(PlayerSwapHandItemsEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onDrop(PlayerDropItemEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBow(EntityShootBowEvent e) { if (isFrozen(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBreak(BlockBreakEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onPlace(BlockPlaceEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onConsume(PlayerItemConsumeEvent e) { if (isFrozen(e.getPlayer())) e.setCancelled(true); }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        clearMods(e.getPlayer());
        Stop own = stopOf(e.getPlayer());
        if (own != null) end(own);
    }
    /** Safety net: never leave someone stuck if the server stopped mid time-stop. */
    @EventHandler public void onJoin(PlayerJoinEvent e) { clearMods(e.getPlayer()); }
}
