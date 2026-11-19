package dev.luci.stands;

import dev.luci.stands.entity.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class StandManager {

    public enum Slot { BASIC, SKILL, SKILL2, ULTIMATE }

    /** Runtime state for a player whose stand is summoned. */
    static final class Session {
        StandEntity stand;          // null for item stands (Anubis, Emperor, ...)
        int frontTicks;             // >0 = stand is in front of the user (rushing)
        int hiddenTicks;            // >0 = stand is tucked out of sight (ghost abilities)
        int rushTicks;              // >0 = rush loop animation playing
    }

    private final TimeStopManager timeStop;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<String, StandKit> kits = new LinkedHashMap<>();
    private final Map<UUID, Long> untargetable = new HashMap<>();
    private final Map<UUID, double[]> vulnerable = new HashMap<>();   // [multiplier, untilTick]
    private final Map<UUID, double[]> bonus = new HashMap<>();        // [extra damage, untilTick]
    private final Set<UUID> testers = new HashSet<>();
    private Abilities abilities;
    private MinecraftServer server;
    private long tickCount;

    public StandManager(TimeStopManager timeStop) { this.timeStop = timeStop; }

    void init(Abilities abilities) {
        this.abilities = abilities;
        for (StandKit k : dev.luci.stands.kits.Kits.all(this, timeStop, abilities)) kits.put(k.id(), k);
    }

    void setServer(MinecraftServer s) { this.server = s; }
    public MinecraftServer server() { return server; }
    public ServerPlayer player(UUID id) { return server == null ? null : server.getPlayerList().getPlayer(id); }
    public List<ServerPlayer> players() { return server == null ? List.of() : server.getPlayerList().getPlayers(); }

    // ---------------------------------------------------------------- data

    public Set<String> knownStands() { return kits.keySet(); }
    public String standOf(Player p) { return StandData.stand(p); }
    public int levelOf(Player p) { return StandData.level(p); }
    public boolean isSummoned(Player p) { return sessions.containsKey(p.getUUID()); }
    public StandKit kit(String id) { return kits.get(id); }
    public Collection<StandKit> kits() { return kits.values(); }

    /** Never empty-handed: a stand missing from the config falls back to the defaults in code. */
    public Cfg cfg(String stand) { return Config.get().section("stands." + stand); }
    public String displayName(String stand) { return stand == null ? "?" : cfg(stand).getString("name", stand); }

    public void setStand(ServerPlayer p, String stand) {
        unsummon(p, false);
        p.setAttached(StandData.STAND, stand);
        p.setAttached(StandData.LEVEL, 1);
        p.setAttached(StandData.XP, 0.0);
        cooldowns.remove(p.getUUID());
    }

    public void setLevel(ServerPlayer p, int level) { p.setAttached(StandData.LEVEL, level); }

    public void removeStand(ServerPlayer p) {
        unsummon(p, false);
        p.removeAttached(StandData.STAND);
        p.removeAttached(StandData.LEVEL);
        p.removeAttached(StandData.XP);
    }

    // ---------------------------------------------------------------- stand arrow

    /** Right-click with a Stand Arrow. Returns true if the arrow was used up. */
    public boolean useArrow(ServerPlayer p, ItemStack it) {
        // Requiem: a level 3 Gold Experience pierced by the arrow evolves.
        if ("goldenexperience".equals(standOf(p)) && kits.containsKey("ger")) {
            if (levelOf(p) < 3) { Txt.bar(p, "Gold Experience must be level 3 to reach Requiem.", ChatFormatting.RED); return false; }
            it.shrink(1);
            setStand(p, "ger");
            setLevel(p, 3);
            sound(p, "ger");
            Txt.title(p, Txt.of("「Gold Experience Requiem」", ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Txt.of("You will never reach the truth", ChatFormatting.GRAY), 4, 60, 16);
            return true;
        }
        if (standOf(p) != null) { Txt.bar(p, "You already have a stand.", ChatFormatting.RED); return false; }
        it.shrink(1);
        Cfg c = Config.get();
        ServerLevel l = p.level();
        Fx.sound(l, p.position(), SoundEvents.ARROW_HIT_PLAYER, 1f, 0.6f);
        Fx.particle(l, ParticleTypes.DAMAGE_INDICATOR, p.position().add(0, 1, 0), 8, .3, .5, .3, 0);
        if (ThreadLocalRandom.current().nextDouble() < c.getDouble("arrow.fail-chance", 0.1)) {
            Txt.msg(p, "The arrow rejects you...", ChatFormatting.DARK_RED);
            p.hurtServer(l, l.damageSources().magic(), (float) c.getDouble("arrow.fail-damage", 30));
            return true;
        }
        p.hurtServer(l, l.damageSources().magic(), 3f);
        List<String> pool = new ArrayList<>(c.getStringList("enabled-stands"));
        pool.retainAll(kits.keySet());
        if (pool.isEmpty()) { Txt.msg(p, "No stands are enabled.", ChatFormatting.RED); return true; }
        String s = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        setStand(p, s);
        Txt.title(p, Txt.of("「" + displayName(s) + "」", ChatFormatting.GOLD, ChatFormatting.BOLD),
                Txt.of("Your stand has awakened. Press " + "M" + " to summon it.", ChatFormatting.GRAY), 4, 60, 16);
        sound(p, "summon");
        return true;
    }

    // ---------------------------------------------------------------- summon

    public void toggle(ServerPlayer p) {
        if (standOf(p) == null) { Txt.bar(p, "You don't have a stand. Craft a Stand Arrow (see /stand guide).", ChatFormatting.GRAY); return; }
        if (timeStop.isFrozen(p)) return;
        if (isSummoned(p)) unsummon(p, true); else summon(p);
    }

    public void summon(ServerPlayer p) {
        String s = standOf(p);
        if (s == null || isSummoned(p)) return;
        StandKit kit = kits.get(s);
        Session ses = new Session();
        sessions.put(p.getUUID(), ses);
        if (kit == null || kit.hasModel()) {
            StandEntity e = new StandEntity(StandsMod.STAND, p.level());
            e.setStandId(cfg(s).getString("model", s));
            e.setOwnerId(p.getId());
            Vec3 at = anchor(p, ses);
            e.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
            p.level().addFreshEntity(e);
            ses.stand = e;
            e.play("summon");
            Fx.particle(p.level(), ParticleTypes.CLOUD, at.add(0, 1, 0), 10, .2, .4, .2, .02);
        }
        if (kit != null) kit.onSummon(p);
        sound(p, "summon");
        Txt.bar(p, displayName(s) + "!", ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public void unsummon(ServerPlayer p, boolean effects) {
        Session ses = sessions.remove(p.getUUID());
        if (ses == null) return;
        StandKit kit = standOf(p) == null ? null : kits.get(standOf(p));
        if (kit != null) kit.onUnsummon(p);
        if (effects) {
            Vec3 at = ses.stand != null ? ses.stand.position() : p.position();
            sound(p.level(), at, "unsummon");
            Fx.particle(p.level(), ParticleTypes.CLOUD, at.add(0, 1, 0), 12, .2, .5, .2, .02);
        }
        if (ses.stand != null) ses.stand.discard();
    }

    public void unsummonAll() { for (UUID id : new ArrayList<>(sessions.keySet())) { ServerPlayer p = player(id); if (p != null) unsummon(p, false); } sessions.clear(); }

    /** Is this stand entity one we're currently driving? Orphans discard themselves. */
    public boolean owns(StandEntity e) {
        for (Session s : sessions.values()) if (s.stand == e) return true;
        return false;
    }

    /** Play a one-shot stand animation (punch, pose, throw, cast, dash, summon). */
    public void anim(ServerPlayer p, String name) {
        Session s = sessions.get(p.getUUID());
        if (s == null || s.stand == null) return;
        if ("rush".equals(name)) { animFor(p, name, 12); return; }
        s.stand.play(name);
    }

    /** Play a looping stand animation (rush) for a number of ticks, then stop it. */
    public void animFor(ServerPlayer p, String name, int ticks) {
        Session s = sessions.get(p.getUUID());
        if (s == null || s.stand == null) return;
        if (!"rush".equals(name)) { s.stand.play(name); return; }
        s.rushTicks = Math.max(s.rushTicks, ticks);
        s.stand.setRushing(true);
    }

    /** Make the stand rush in front of the user for a few ticks. */
    public void standForward(ServerPlayer p, int ticks) { Session s = sessions.get(p.getUUID()); if (s != null) s.frontTicks = Math.max(s.frontTicks, ticks); }

    /** The stand entity, if summoned and visible (kits can use its position for effects). */
    public StandEntity standEntity(ServerPlayer p) { Session s = sessions.get(p.getUUID()); return s == null ? null : s.stand; }

    private Vec3 anchor(ServerPlayer p, Session ses) {
        Vec3 l = p.position();
        Vec3 dir = Fx.flatDir(p);
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        if (ses.frontTicks > 0) return l.add(dir.scale(1.3)).add(0, 0.1, 0);
        return l.add(dir.scale(-0.75)).add(right.scale(-0.55)).add(0, 0.2 + 0.06 * Math.sin(tickCount / 8.0), 0);
    }

    void tick() {
        tickCount++;
        for (var it = sessions.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = player(en.getKey());
            Session s = en.getValue();
            if (p == null || !p.isAlive() || p.isRemoved()) {
                if (s.stand != null) s.stand.discard();
                it.remove();
                continue;
            }
            if (s.frontTicks > 0) s.frontTicks--;
            if (s.hiddenTicks > 0) s.hiddenTicks--;
            if (s.rushTicks > 0 && --s.rushTicks == 0 && s.stand != null) s.stand.setRushing(false);
            if (s.stand != null) {
                if (s.stand.isRemoved() || s.stand.level() != p.level()) {
                    // Changed dimension (or the entity got cleaned up): bring a fresh one along.
                    if (!s.stand.isRemoved()) s.stand.discard();
                    StandEntity e = new StandEntity(StandsMod.STAND, p.level());
                    e.setStandId(s.stand.standId());
                    e.setOwnerId(p.getId());
                    Vec3 at = anchor(p, s);
                    e.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
                    p.level().addFreshEntity(e);
                    s.stand = e;
                }
                Vec3 at = anchor(p, s);
                s.stand.setInvisible(s.hiddenTicks > 0);
                s.stand.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
                s.stand.setYHeadRot(p.getYRot());
            }
        }
        if (tickCount % 5 == 0) for (ServerPlayer p : players()) {
            if (standOf(p) != null && isSummoned(p)) Txt.bar(p, hud(p));
        }
    }

    // ---------------------------------------------------------------- abilities

    public void use(ServerPlayer p, Slot slot) {
        String s = standOf(p);
        StandKit kit = s == null ? null : kits.get(s);
        if (kit == null) return;
        if (!isSummoned(p)) { Txt.bar(p, "Summon your stand first (M).", ChatFormatting.GRAY); return; }
        if (timeStop.isFrozen(p) || isUntargetable(p) || p.isSpectator()) return;
        int need = switch (slot) { case SKILL2 -> cfg(s).getInt("skill2.level", 2); case ULTIMATE -> cfg(s).getInt("ultimate.level", 3); default -> 1; };
        if (levelOf(p) < need) { Txt.bar(p, "Needs stand level " + need + " (/stand levelup)", ChatFormatting.RED); return; }
        kit.use(p, slot);
    }

    public boolean ready(ServerPlayer p, String key) { return remaining(p, key) <= 0; }
    public double remaining(ServerPlayer p, String key) {
        Long until = cooldowns.getOrDefault(p.getUUID(), Map.of()).get(key);
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }
    public void cooldown(ServerPlayer p, String key, double seconds) {
        seconds *= Config.get().getDouble("power.cooldown-multiplier", 1.0);
        cooldowns.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }
    public void clearCooldowns(ServerPlayer p) { cooldowns.remove(p.getUUID()); }

    public boolean isStandUser(Entity e) { return e instanceof Player p && standOf(p) != null; }

    /** Does this hit use the fair stand-vs-stand rules? (Test mode: mobs count as stand users for that attacker.) */
    public boolean standRules(ServerPlayer attacker, Entity target) {
        return isStandUser(target) || (attacker != null && testers.contains(attacker.getUUID()) && !(target instanceof Player));
    }

    public boolean toggleTestMode(ServerPlayer p) {
        if (testers.remove(p.getUUID())) return false;
        testers.add(p.getUUID());
        return true;
    }

    /**
     * Deal stand damage. Vs stand users: true damage (armor/Protection ignored) at the PvP number.
     * Vs everyone else: multiplied, normal armor rules. It should feel unfair.
     */
    public void hit(ServerPlayer attacker, LivingEntity target, double base) {
        if (target instanceof ServerPlayer tp && (isUntargetable(tp) || !StandsMod.pvp().canFight(attacker, tp))) return;
        Cfg c = Config.get();
        addXp(attacker, base * c.getDouble(target instanceof Player ? "xp.per-damage-vs-players" : "xp.per-damage-vs-mobs", 1.0));
        double[] extra = attacker == null ? null : bonus.remove(attacker.getUUID());
        if (extra != null && tickCount <= extra[1]) base += extra[0];
        double mult = standRules(attacker, target) ? c.getDouble("power.vs-stand-users", 1.0)
                : target instanceof Player ? c.getDouble("power.vs-players-without-stand", 3.0)
                : c.getDouble("power.vs-mobs", 4.0);
        target.setInvulnerableTime(0);
        damageRaw(attacker, target, base * mult);
    }

    /** Apply an already-scaled amount with the right damage type for this target. */
    public void damageRaw(ServerPlayer attacker, LivingEntity target, double amount) {
        if (!(target.level() instanceof ServerLevel l)) return;
        DamageSource src = standRules(attacker, target) ? StandsMod.standDamage(l, attacker)
                : attacker != null ? l.damageSources().playerAttack(attacker) : l.damageSources().magic();
        target.hurtServer(l, src, (float) amount);
    }

    // ---------------------------------------------------------------- stand XP

    public double xpOf(Player p) { return StandData.xp(p); }
    /** Total XP needed to reach `level` (2 or 3). */
    public double xpFor(int level) { return Config.get().getDouble(level == 2 ? "levels.level-2-xp" : "levels.level-3-xp", level == 2 ? 300 : 1000); }

    /** Stands level up by being used: XP from damage dealt, kills and duels. */
    public void addXp(ServerPlayer p, double amount) {
        if (p == null || standOf(p) == null || levelOf(p) >= 3 || amount <= 0) return;
        double xp = xpOf(p) + amount;
        p.setAttached(StandData.XP, xp);
        while (levelOf(p) < 3 && xp >= xpFor(levelOf(p) + 1)) {
            int lvl = levelOf(p) + 1;
            setLevel(p, lvl);
            sound(p, "summon");
            Fx.particle(p.level(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 30, .4, .6, .4, .3);
            Txt.title(p, Txt.of(displayName(standOf(p)) + " — Level " + lvl, ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Txt.of(lvl == 2 ? "Skill 2 unlocked" : "Ultimate unlocked", ChatFormatting.YELLOW), 4, 60, 12);
        }
    }

    /** Kill XP. Called from the death event. */
    void onDeath(LivingEntity dead, DamageSource src) {
        if (src.getEntity() instanceof ServerPlayer k && standOf(k) != null && isSummoned(k) && k != dead)
            addXp(k, Config.get().getDouble(dead instanceof Player ? "xp.player-kill" : "xp.mob-kill", dead instanceof Player ? 60 : 8));
        if (dead instanceof ServerPlayer p) unsummon(p, true);
    }

    // ---------------------------------------------------------------- status flags used by kits

    public boolean isUntargetable(Player p) { Long u = untargetable.get(p.getUUID()); return u != null && tickCount < u; }
    public void setUntargetable(ServerPlayer p, int ticks) { untargetable.put(p.getUUID(), tickCount + ticks); }
    /** Player takes `multiplier`x damage for a while (Armor Off etc.). */
    public void setVulnerable(ServerPlayer p, double multiplier, int ticks) { vulnerable.put(p.getUUID(), new double[]{multiplier, tickCount + ticks}); }
    /** The player's next stand hit within `ticks` deals extra base damage. */
    public void bonusNextHit(ServerPlayer p, double extra, int ticks) { bonus.put(p.getUUID(), new double[]{extra, tickCount + ticks}); }
    public void hideStand(ServerPlayer p, int ticks) { Session s = sessions.get(p.getUUID()); if (s != null) s.hiddenTicks = Math.max(s.hiddenTicks, ticks); }
    public Abilities abilities() { return abilities; }

    /** Damage multiplier from Armor Off and similar. */
    float vulnerability(LivingEntity e) {
        double[] v = vulnerable.get(e.getUUID());
        return v != null && tickCount < v[1] ? (float) v[0] : 1f;
    }

    /** Returns true (and shows a message) if the ability is still cooling down. */
    public boolean onCooldown(ServerPlayer p, String key, String label) {
        double r = remaining(p, key);
        if (r <= 0) return false;
        Txt.bar(p, label + " — " + String.format("%.1fs", r), ChatFormatting.RED);
        return true;
    }

    private Component hud(ServerPlayer p) {
        String s = standOf(p);
        StandKit kit = kits.get(s);
        if (kit == null) return Component.empty();
        MutableComponent c = Component.empty();
        String[] keys = StandsMod.keyLabels();
        Slot[] slots = Slot.values();
        for (int i = 0; i < slots.length; i++) {
            String key = kit.cooldownKey(p, slots[i]);
            double r = remaining(p, key);
            int need = slots[i] == Slot.SKILL2 ? cfg(s).getInt("skill2.level", 2) : slots[i] == Slot.ULTIMATE ? cfg(s).getInt("ultimate.level", 3) : 1;
            ChatFormatting col = levelOf(p) < need ? ChatFormatting.DARK_GRAY : r > 0 ? ChatFormatting.RED : ChatFormatting.GREEN;
            String state = levelOf(p) < need ? "Lv" + need : r > 0 ? String.format("%.0fs", Math.ceil(r)) : "✔";
            if (i > 0) c.append(Txt.of("  |  ", ChatFormatting.DARK_GRAY));
            c.append(Txt.of("[" + keys[i] + "] ", ChatFormatting.GRAY)).append(Txt.of(kit.label(p, slots[i]) + " " + state, col));
        }
        return c;
    }

    // ---------------------------------------------------------------- levels / info

    public void levelUp(ServerPlayer p) {
        if (standOf(p) == null) { Txt.msg(p, "You don't have a stand.", ChatFormatting.RED); return; }
        int lvl = levelOf(p);
        if (lvl >= 3) { Txt.msg(p, "Your stand can't level up any further.", ChatFormatting.GOLD); return; }
        int cost = Config.get().getInt(lvl == 1 ? "levels.level-2-cost" : "levels.level-3-cost", lvl == 1 ? 30 : 60);
        if (p.experienceLevel < cost && !p.isCreative()) { Txt.msg(p, "You need " + cost + " XP levels to reach level " + (lvl + 1) + ".", ChatFormatting.RED); return; }
        if (!p.isCreative()) p.giveExperienceLevels(-cost);
        setLevel(p, lvl + 1);
        sound(p, "summon");
        Fx.particle(p.level(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 30, .4, .6, .4, .3);
        Txt.msg(p, displayName(standOf(p)) + " reached level " + (lvl + 1) + "!", ChatFormatting.GOLD);
    }

    public Component describe(ServerPlayer p) {
        String s = standOf(p);
        if (s == null) return Txt.of("You don't have a stand. Use a Stand Arrow, or see /stand guide.", ChatFormatting.GRAY);
        int lvl = levelOf(p);
        String prog = lvl >= 3 ? "max level" : String.format("%.0f / %.0f XP to level %d", xpOf(p), xpFor(lvl + 1), lvl + 1);
        return Txt.of("Stand: " + displayName(s) + "  (level " + lvl + "/3, " + prog + ")\n", ChatFormatting.GOLD)
                .append(Txt.of("PvP: " + (StandsMod.pvp().pvpOn(p) ? "ON" : "OFF") + "   Duels: " + StandData.wins(p) + " won, " + StandData.losses(p) + " lost\n", ChatFormatting.GRAY))
                .append(Txt.of("Open /stand guide for controls and every stand's abilities.", ChatFormatting.YELLOW));
    }

    // ---------------------------------------------------------------- misc helpers

    /** Play a config sound (sounds.<key>) at a point. */
    public void sound(ServerLevel l, Vec3 at, String key) {
        Cfg c = Config.get();
        String id = c.getString("sounds." + key);
        if (id == null || id.isBlank()) return;
        Fx.sound(l, at, id, (float) c.getDouble("sounds.volume", 3.0), 1f);
    }

    public void sound(Entity at, String key) { if (at.level() instanceof ServerLevel l) sound(l, at.position(), key); }

    void onQuit(ServerPlayer p) { unsummon(p, false); untargetable.remove(p.getUUID()); }
}
