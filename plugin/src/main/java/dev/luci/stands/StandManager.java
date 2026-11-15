package dev.luci.stands;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class StandManager implements Listener {

    public enum Slot { BASIC, SKILL, SKILL2, ULTIMATE }

    /** Runtime state for a player whose stand is summoned. */
    static final class Session {
        ArmorStand anchor;
        EntityTracker tracker;
        int frontTicks;            // >0 = stand is in front of the user (rushing)
        int hiddenTicks;           // >0 = stand is tucked out of sight (ghost abilities)
    }

    private final StandsPlugin plugin;
    private final TimeStopManager timeStop;
    private final NamespacedKey kStand, kLevel, kItem, kXp;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<String, StandKit> kits = new LinkedHashMap<>();
    private final Map<UUID, Long> untargetable = new HashMap<>();
    private final Map<UUID, double[]> vulnerable = new HashMap<>();   // [multiplier, untilMillis]
    private final Map<UUID, double[]> bonus = new HashMap<>();        // [extra damage, untilMillis]
    private final Set<UUID> testers = new HashSet<>();
    private final Abilities abilities;
    private long tickCount;

    public StandManager(StandsPlugin plugin, TimeStopManager timeStop) {
        this.plugin = plugin;
        this.timeStop = timeStop;
        kStand = new NamespacedKey(plugin, "stand");
        kLevel = new NamespacedKey(plugin, "level");
        kItem = new NamespacedKey(plugin, "item");
        kXp = new NamespacedKey(plugin, "xp");
        abilities = new Abilities(this, timeStop);
        for (StandKit k : dev.luci.stands.kits.Kits.all(this, timeStop, abilities)) kits.put(k.id(), k);
    }

    // ---------------------------------------------------------------- data

    public StandsPlugin plugin() { return plugin; }
    public Set<String> knownStands() { return kits.keySet(); }
    public String standOf(Player p) { return p.getPersistentDataContainer().get(kStand, PersistentDataType.STRING); }
    public int levelOf(Player p) { return p.getPersistentDataContainer().getOrDefault(kLevel, PersistentDataType.INTEGER, 1); }
    public boolean isSummoned(Player p) { return sessions.containsKey(p.getUniqueId()); }
    private static final ConfigurationSection NO_CONFIG = new org.bukkit.configuration.MemoryConfiguration();
    /** Never null: a stand missing from config.yml falls back to the defaults in code. */
    public ConfigurationSection cfg(String stand) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("stands." + stand);
        return c != null ? c : NO_CONFIG;
    }
    public String displayName(String stand) { var c = cfg(stand); return c == null ? stand : c.getString("name", stand); }

    public void setStand(Player p, String stand) {
        unsummon(p, false);
        for (ItemStack it : p.getInventory().getContents()) if (isItem(it, "disc")) it.setAmount(0);
        p.getPersistentDataContainer().set(kStand, PersistentDataType.STRING, stand);
        p.getPersistentDataContainer().set(kLevel, PersistentDataType.INTEGER, 1);
        p.getPersistentDataContainer().set(kXp, PersistentDataType.DOUBLE, 0.0);
        cooldowns.remove(p.getUniqueId());
        giveDisc(p);
    }

    public void setLevel(Player p, int level) { p.getPersistentDataContainer().set(kLevel, PersistentDataType.INTEGER, level); }

    public void removeStand(Player p) {
        unsummon(p, false);
        p.getPersistentDataContainer().remove(kStand);
        p.getPersistentDataContainer().remove(kLevel);
        for (ItemStack it : p.getInventory().getContents()) if (isItem(it, "disc")) it.setAmount(0);
    }

    // ---------------------------------------------------------------- items

    private ItemStack tagged(Material m, String tag, String model, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("jojo", model));
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
            meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, tag);
            meta.setEnchantmentGlintOverride(false);
        });
        return it;
    }

    public boolean isItem(ItemStack it, String tag) {
        return it != null && it.hasItemMeta() && tag.equals(it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING));
    }

    public ItemStack arrowItem() {
        return tagged(Material.ARROW, "arrow", "standarrow", Component.text("Stand Arrow", NamedTextColor.GOLD),
                List.of(Component.text("Right-click to pierce yourself.", NamedTextColor.GRAY),
                        Component.text("Only the worthy awaken a Stand.", NamedTextColor.DARK_GRAY)));
    }

    public void giveDisc(Player p) {
        String s = standOf(p);
        if (s == null) { p.sendMessage(Component.text("You don't have a stand.", NamedTextColor.RED)); return; }
        for (ItemStack it : p.getInventory().getContents()) if (isItem(it, "disc")) return;
        p.getInventory().addItem(tagged(Material.MUSIC_DISC_13, "disc", java.util.Set.of("anubis", "hermitpurple", "theemperor").contains(s) ? s : "disc", Component.text("Stand Disc: " + displayName(s), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("Hold it to control your stand", NamedTextColor.GRAY),
                        Component.text("Q  summon / unsummon", NamedTextColor.YELLOW),
                        Component.text("Right-click  basic   ·  Sneak+Right-click  skill", NamedTextColor.YELLOW),
                        Component.text("F  skill 2 (Lv2)   ·  Sneak+F  ultimate (Lv3)", NamedTextColor.YELLOW))));
    }

    public void registerArrowRecipe() {
        if (!plugin.getConfig().getBoolean("arrow.craftable", true)) return;
        var key = new NamespacedKey(plugin, "stand_arrow");
        if (Bukkit.getRecipe(key) != null) return;
        var r = new ShapedRecipe(key, arrowItem());
        r.shape("GG ", "SG ", "T  ");
        r.setIngredient('G', Material.GOLD_INGOT);
        r.setIngredient('S', Material.SOUL_SAND);
        r.setIngredient('T', Material.STICK);
        Bukkit.addRecipe(r);
    }

    @EventHandler
    public void onArrow(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isItem(it, "arrow")) return;
        e.setCancelled(true);
        // Requiem: a level 3 Gold Experience pierced by the arrow evolves.
        if ("goldenexperience".equals(standOf(p)) && kits.containsKey("ger")) {
            if (levelOf(p) < 3) { p.sendActionBar(Component.text("Gold Experience must be level 3 to reach Requiem.", NamedTextColor.RED)); return; }
            it.subtract();
            setStand(p, "ger");
            setLevel(p, 3);
            sound(p.getLocation(), "ger");
            p.showTitle(Title.title(Component.text("「Gold Experience Requiem」", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("You will never reach the truth", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
            return;
        }
        if (standOf(p) != null) { p.sendActionBar(Component.text("You already have a stand.", NamedTextColor.RED)); return; }
        it.subtract();
        var c = plugin.getConfig();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.6f);
        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 8, .3, .5, .3, 0);
        if (ThreadLocalRandom.current().nextDouble() < c.getDouble("arrow.fail-chance", 0.1)) {
            p.sendMessage(Component.text("The arrow rejects you...", NamedTextColor.DARK_RED));
            p.damage(c.getDouble("arrow.fail-damage", 30));
            return;
        }
        p.damage(3.0);
        List<String> pool = new ArrayList<>(c.getStringList("enabled-stands"));
        pool.retainAll(kits.keySet());
        if (pool.isEmpty()) { p.sendMessage(Component.text("No stands are enabled.", NamedTextColor.RED)); return; }
        String s = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        setStand(p, s);
        p.showTitle(Title.title(Component.text("「" + displayName(s) + "」", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Your stand has awakened", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
        sound(p.getLocation(), "summon");
    }

    // ---------------------------------------------------------------- summon

    public void toggle(Player p) {
        if (standOf(p) == null) return;
        if (isSummoned(p)) unsummon(p, true); else summon(p);
    }

    public void summon(Player p) {
        String s = standOf(p);
        if (s == null || isSummoned(p)) return;
        var c = cfg(s);
        String model = c == null ? s : c.getString("model", s);
        boolean hasModel = kits.get(s) == null || kits.get(s).hasModel();
        var renderer = hasModel ? BetterModel.model(model) : java.util.Optional.<kr.toxicity.model.api.data.renderer.ModelRenderer>empty();
        if (hasModel && renderer.isEmpty()) { p.sendMessage(Component.text("Model '" + model + "' isn't loaded in BetterModel.", NamedTextColor.RED)); return; }
        Session ses = new Session();
        ses.anchor = p.getWorld().spawn(anchorLocation(p, ses), ArmorStand.class, as -> {
            as.setInvisible(true); as.setMarker(true); as.setGravity(false); as.setInvulnerable(true);
            as.setSilent(true); as.setPersistent(false); as.setCollidable(false); as.setCanTick(false);
            as.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, "anchor");
        });
        sessions.put(p.getUniqueId(), ses);
        if (renderer.isPresent()) {
            ses.tracker = renderer.get().create(BukkitAdapter.adapt(ses.anchor));
            ses.tracker.animate("idle");
            ses.tracker.animate("summon", kr.toxicity.model.api.animation.AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        }
        if (kits.get(s) != null) kits.get(s).onSummon(p);
        sound(p.getLocation(), "summon");
        p.getWorld().spawnParticle(Particle.FLASH, ses.anchor.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.WHITE);
        p.sendActionBar(Component.text(displayName(s) + "!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    public void unsummon(Player p, boolean effects) {
        Session ses = sessions.remove(p.getUniqueId());
        if (ses == null) return;
        StandKit kit = standOf(p) == null ? null : kits.get(standOf(p));
        if (kit != null) kit.onUnsummon(p);
        if (effects) {
            sound(ses.anchor.getLocation(), "unsummon");
            p.getWorld().spawnParticle(Particle.CLOUD, ses.anchor.getLocation().add(0, 1, 0), 12, .2, .5, .2, .02);
        }
        try { if (ses.tracker != null) ses.tracker.close(); } catch (Exception ignored) { }
        ses.anchor.remove();
    }

    public void unsummonAll() { new ArrayList<>(sessions.keySet()).forEach(id -> { Player p = Bukkit.getPlayer(id); if (p != null) unsummon(p, false); }); }

    /** Play a one-shot stand animation (punch, pose, summon). */
    public void anim(Player p, String name) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.tracker != null) s.tracker.animate(name, kr.toxicity.model.api.animation.AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
    }

    /** Play a looping stand animation (rush) for a number of ticks, then stop it. */
    public void animFor(Player p, String name, int ticks) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.tracker == null) return;
        s.tracker.animate(name);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (sessions.get(p.getUniqueId()) == s) s.tracker.stopAnimation(name); }, ticks);
    }

    /** Make the stand rush in front of the user for a few ticks. */
    public void standForward(Player p, int ticks) { Session s = sessions.get(p.getUniqueId()); if (s != null) s.frontTicks = Math.max(s.frontTicks, ticks); }

    private Location anchorLocation(Player p, Session ses) {
        Location l = p.getLocation();
        Vector dir = new Vector(-Math.sin(Math.toRadians(l.getYaw())), 0, Math.cos(Math.toRadians(l.getYaw())));
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        Location out;
        if (ses.hiddenTicks > 0) { out = l.clone(); out.setY(l.getWorld().getMinHeight() - 40); return out; }
        if (ses.frontTicks > 0) out = l.clone().add(dir.multiply(1.3)).add(0, 0.1, 0);
        else out = l.clone().add(dir.clone().multiply(-0.75)).add(right.multiply(-0.55)).add(0, 0.2 + 0.06 * Math.sin(tickCount / 8.0), 0);
        out.setYaw(l.getYaw()); out.setPitch(0);
        return out;
    }

    void tick() {
        tickCount++;
        for (var it = sessions.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Session s = en.getValue();
            if (p == null || !p.isValid() || !s.anchor.isValid() || s.anchor.getWorld() != p.getWorld()) {
                try { if (s.tracker != null) s.tracker.close(); } catch (Exception ignored) { }
                s.anchor.remove(); it.remove(); continue;
            }
            if (s.frontTicks > 0) s.frontTicks--;
            if (s.hiddenTicks > 0) s.hiddenTicks--;
            s.anchor.teleport(anchorLocation(p, s));
        }
        if (tickCount % 5 == 0) for (Player p : Bukkit.getOnlinePlayers()) {
            if (standOf(p) != null && isItem(p.getInventory().getItemInMainHand(), "disc")) p.sendActionBar(hud(p));
        }
    }

    // ---------------------------------------------------------------- abilities

    public void use(Player p, Slot slot) {
        String s = standOf(p);
        StandKit kit = s == null ? null : kits.get(s);
        if (kit == null) return;
        if (!isSummoned(p)) { p.sendActionBar(Component.text("Summon your stand first (Q with the disc).", NamedTextColor.GRAY)); return; }
        if (timeStop.isFrozen(p) || isUntargetable(p)) return;
        int need = switch (slot) { case SKILL2 -> cfg(s).getInt("skill2.level", 2); case ULTIMATE -> cfg(s).getInt("ultimate.level", 3); default -> 1; };
        if (levelOf(p) < need) { p.sendActionBar(Component.text("Needs stand level " + need + " (/stand levelup)", NamedTextColor.RED)); return; }
        kit.use(p, slot);
    }

    public boolean ready(Player p, String key) { return remaining(p, key) <= 0; }
    public double remaining(Player p, String key) {
        Long until = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).get(key);
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }
    public void cooldown(Player p, String key, double seconds) {
        seconds *= plugin.getConfig().getDouble("power.cooldown-multiplier", 1.0);
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean isStandUser(org.bukkit.entity.Entity e) { return e instanceof Player p && standOf(p) != null; }

    /** Does this hit use the fair stand-vs-stand rules? (Test mode: mobs count as stand users for that attacker.) */
    public boolean standRules(Player attacker, org.bukkit.entity.Entity target) {
        return isStandUser(target) || (attacker != null && testers.contains(attacker.getUniqueId()) && !(target instanceof Player));
    }

    public boolean toggleTestMode(Player p) {
        if (testers.remove(p.getUniqueId())) return false;
        testers.add(p.getUniqueId());
        return true;
    }

    /**
     * Deal stand damage. Vs stand users: true damage (armor/Protection ignored) at the PvP number.
     * Vs everyone else: multiplied, normal armor rules — it should feel unfair.
     */
    public void hit(Player attacker, org.bukkit.entity.LivingEntity target, double base) {
        if (target instanceof Player tp && (isUntargetable(tp) || !plugin.pvp().canFight(attacker, tp))) return;
        addXp(attacker, base * plugin.getConfig().getDouble(target instanceof Player ? "xp.per-damage-vs-players" : "xp.per-damage-vs-mobs", 1.0));
        var c = plugin.getConfig();
        double[] extra = bonus.remove(attacker.getUniqueId());
        if (extra != null && System.currentTimeMillis() <= extra[1]) base += extra[0];
        double mult = standRules(attacker, target) ? c.getDouble("power.vs-stand-users", 1.0)
                : target instanceof Player ? c.getDouble("power.vs-players-without-stand", 3.0)
                : c.getDouble("power.vs-mobs", 4.0);
        target.setNoDamageTicks(0);
        damageRaw(attacker, target, base * mult);
    }

    /** Apply an already-scaled amount with the right damage type for this target. */
    public void damageRaw(Player attacker, org.bukkit.entity.LivingEntity target, double amount) {
        var type = standRules(attacker, target) ? org.bukkit.damage.DamageType.SONIC_BOOM   // bypasses armor + enchantments
                : org.bukkit.damage.DamageType.PLAYER_ATTACK;
        var src = org.bukkit.damage.DamageSource.builder(type);
        if (attacker != null) src.withCausingEntity(attacker).withDirectEntity(attacker);
        target.damage(amount, src.build());
    }

    // ---------------------------------------------------------------- stand XP

    public double xpOf(Player p) { return p.getPersistentDataContainer().getOrDefault(kXp, PersistentDataType.DOUBLE, 0.0); }
    /** Total XP needed to reach `level` (2 or 3). */
    public double xpFor(int level) { return plugin.getConfig().getDouble(level == 2 ? "levels.level-2-xp" : "levels.level-3-xp", level == 2 ? 300 : 1000); }
    public void clearCooldowns(Player p) { cooldowns.remove(p.getUniqueId()); }

    /** Stands level up by being used: XP from damage dealt, kills and duels. */
    public void addXp(Player p, double amount) {
        if (p == null || standOf(p) == null || levelOf(p) >= 3 || amount <= 0) return;
        double xp = xpOf(p) + amount;
        p.getPersistentDataContainer().set(kXp, PersistentDataType.DOUBLE, xp);
        while (levelOf(p) < 3 && xp >= xpFor(levelOf(p) + 1)) {
            int lvl = levelOf(p) + 1;
            setLevel(p, lvl);
            sound(p.getLocation(), "summon");
            p.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.WHITE);
            p.showTitle(Title.title(Component.text(displayName(standOf(p)) + " — Level " + lvl, NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(lvl == 2 ? "Skill 2 unlocked (F)" : "Ultimate unlocked (Sneak + F)", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
        }
    }

    @EventHandler
    public void onKillXp(org.bukkit.event.entity.EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || standOf(k) == null || !isSummoned(k)) return;
        addXp(k, plugin.getConfig().getDouble(e.getEntity() instanceof Player ? "xp.player-kill" : "xp.mob-kill", e.getEntity() instanceof Player ? 60 : 8));
    }

    // ---------------------------------------------------------------- status flags used by kits

    public boolean isUntargetable(Player p) { Long u = untargetable.get(p.getUniqueId()); return u != null && System.currentTimeMillis() < u; }
    public void setUntargetable(Player p, int ticks) { untargetable.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L); }
    /** Player takes `multiplier`x damage for a while (Armor Off etc.). */
    public void setVulnerable(Player p, double multiplier, int ticks) { vulnerable.put(p.getUniqueId(), new double[]{multiplier, System.currentTimeMillis() + ticks * 50L}); }
    /** The player's next stand hit within `ticks` deals extra base damage. */
    public void bonusNextHit(Player p, double extra, int ticks) { bonus.put(p.getUniqueId(), new double[]{extra, System.currentTimeMillis() + ticks * 50L}); }
    public void hideStand(Player p, int ticks) { Session s = sessions.get(p.getUniqueId()); if (s != null) s.hiddenTicks = Math.max(s.hiddenTicks, ticks); }
    public Abilities abilities() { return abilities; }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (isUntargetable(p)) { e.setCancelled(true); return; }
        double[] v = vulnerable.get(p.getUniqueId());
        if (v != null && System.currentTimeMillis() < v[1]) e.setDamage(e.getDamage() * v[0]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGhostAttack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isUntargetable(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && isUntargetable(p)) e.setCancelled(true);
    }

    /** Returns true (and shows a message) if the ability is still cooling down. */
    public boolean onCooldown(Player p, String key, String label) {
        double r = remaining(p, key);
        if (r <= 0) return false;
        p.sendActionBar(Component.text(label + " — " + String.format("%.1fs", r), NamedTextColor.RED));
        return true;
    }

    private Component hud(Player p) {
        String s = standOf(p);
        StandKit kit = kits.get(s);
        if (!isSummoned(p)) return Component.text("[Q] Summon " + displayName(s), NamedTextColor.GRAY);
        Component c = Component.empty();
        String[] keys = {"RMB", "Sh+RMB", "F", "Sh+F"};
        Slot[] slots = Slot.values();
        for (int i = 0; i < slots.length; i++) {
            String key = kit.cooldownKey(p, slots[i]);
            double r = remaining(p, key);
            int need = slots[i] == Slot.SKILL2 ? cfg(s).getInt("skill2.level", 2) : slots[i] == Slot.ULTIMATE ? cfg(s).getInt("ultimate.level", 3) : 1;
            NamedTextColor col = levelOf(p) < need ? NamedTextColor.DARK_GRAY : r > 0 ? NamedTextColor.RED : NamedTextColor.GREEN;
            String state = levelOf(p) < need ? "Lv" + need : r > 0 ? String.format("%.0fs", Math.ceil(r)) : "✔";
            if (i > 0) c = c.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            c = c.append(Component.text("[" + keys[i] + "] ", NamedTextColor.GRAY)).append(Component.text(kit.label(p, slots[i]) + " " + state, col));
        }
        return c;
    }

    // ---------------------------------------------------------------- levels / info

    public void levelUp(Player p) {
        if (standOf(p) == null) { p.sendMessage(Component.text("You don't have a stand.", NamedTextColor.RED)); return; }
        int lvl = levelOf(p);
        if (lvl >= 3) { p.sendMessage(Component.text("Your stand can't level up any further.", NamedTextColor.GOLD)); return; }
        int cost = plugin.getConfig().getInt(lvl == 1 ? "levels.level-2-cost" : "levels.level-3-cost", lvl == 1 ? 30 : 60);
        if (p.getLevel() < cost) { p.sendMessage(Component.text("You need " + cost + " XP levels to reach level " + (lvl + 1) + ".", NamedTextColor.RED)); return; }
        p.setLevel(p.getLevel() - cost);
        setLevel(p, lvl + 1);
        sound(p.getLocation(), "summon");
        p.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.WHITE);
        p.sendMessage(Component.text(displayName(standOf(p)) + " reached level " + (lvl + 1) + "!", NamedTextColor.GOLD));
    }

    public Component describe(Player p) {
        String s = standOf(p);
        if (s == null) return Component.text("You don't have a stand. Use a Stand Arrow, or see /stand guide.", NamedTextColor.GRAY);
        int lvl = levelOf(p);
        String prog = lvl >= 3 ? "max level" : String.format("%.0f / %.0f XP to level %d", xpOf(p), xpFor(lvl + 1), lvl + 1);
        return Component.text("Stand: " + displayName(s) + "  (level " + lvl + "/3, " + prog + ")\n", NamedTextColor.GOLD)
                .append(Component.text("PvP: " + (plugin.pvp().pvpOn(p) ? "ON" : "OFF") + "   Duels: " + plugin.pvp().wins(p) + " won, " + plugin.pvp().losses(p) + " lost\n", NamedTextColor.GRAY))
                .append(Component.text("Open /stand guide for controls and every stand's abilities.", NamedTextColor.YELLOW));
    }

    public Collection<StandKit> kits() { return kits.values(); }

    // ---------------------------------------------------------------- misc helpers

    public void sound(Location at, String key) {
        var c = plugin.getConfig();
        String id = c.getString("sounds." + key);
        if (id == null || id.isBlank()) return;
        at.getWorld().playSound(at, id, SoundCategory.PLAYERS, (float) c.getDouble("sounds.volume", 3.0), 1f);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { unsummon(e.getPlayer(), false); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        var src = e.getDamageSource();
        if (src.getDamageType() == org.bukkit.damage.DamageType.SONIC_BOOM && src.getCausingEntity() instanceof Player killer && standOf(killer) != null)
            e.deathMessage(Component.text(e.getPlayer().getName() + " was defeated by " + killer.getName() + "'s 「" + displayName(standOf(killer)) + "」", NamedTextColor.GOLD));
        unsummon(e.getPlayer(), true);
        e.getDrops().removeIf(it -> isItem(it, "disc"));
    }
    @EventHandler public void onRespawnWorld(PlayerChangedWorldEvent e) { unsummon(e.getPlayer(), false); }
    @EventHandler public void onJoinGiveDisc(org.bukkit.event.player.PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (standOf(e.getPlayer()) != null) giveDisc(e.getPlayer()); });
    }
}
