package dev.luci.stands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.*;

/**
 * PvP mode. Stand powers only affect another player when both have PvP on, or when they're in a duel together.
 * Duels end when one player would have died: nobody actually dies or drops items.
 */
public final class PvpManager implements Listener {
    private static final class Duel {
        final UUID a, b; boolean live; long started;
        Duel(UUID a, UUID b) { this.a = a; this.b = b; }
        UUID other(UUID x) { return x.equals(a) ? b : a; }
    }

    private final StandsPlugin plugin;
    private final NamespacedKey kPvp, kWins, kLosses;
    private final Map<UUID, UUID> challenges = new HashMap<>();   // challenged -> challenger
    private final Map<UUID, Duel> duels = new HashMap<>();

    public PvpManager(StandsPlugin plugin) {
        this.plugin = plugin;
        kPvp = new NamespacedKey(plugin, "pvp");
        kWins = new NamespacedKey(plugin, "duel_wins");
        kLosses = new NamespacedKey(plugin, "duel_losses");
    }

    public boolean pvpOn(Player p) {
        Byte b = p.getPersistentDataContainer().get(kPvp, PersistentDataType.BYTE);
        return b != null ? b == 1 : plugin.getConfig().getBoolean("pvp.default-on", false);
    }

    public boolean togglePvp(Player p) {
        boolean on = !pvpOn(p);
        p.getPersistentDataContainer().set(kPvp, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        return on;
    }

    public int wins(Player p) { return p.getPersistentDataContainer().getOrDefault(kWins, PersistentDataType.INTEGER, 0); }
    public int losses(Player p) { return p.getPersistentDataContainer().getOrDefault(kLosses, PersistentDataType.INTEGER, 0); }
    public boolean inDuel(Player p) { return duels.containsKey(p.getUniqueId()); }

    /** Can `a`'s stand powers affect player `b`? */
    public boolean canFight(Player a, Player b) {
        if (a == null || a.equals(b)) return true;
        Duel d = duels.get(a.getUniqueId());
        if (d != null) return d.live && d.other(a.getUniqueId()).equals(b.getUniqueId());
        if (duels.containsKey(b.getUniqueId())) return false;      // don't interfere with someone else's duel
        return pvpOn(a) && pvpOn(b);
    }

    // ------------------------------------------------------------ duels

    public void challenge(Player from, Player to) {
        if (from.equals(to)) { from.sendMessage(Component.text("You can't duel yourself.", NamedTextColor.RED)); return; }
        if (plugin.stands().standOf(from) == null) { from.sendMessage(Component.text("You need a stand to duel.", NamedTextColor.RED)); return; }
        if (inDuel(from) || inDuel(to)) { from.sendMessage(Component.text("One of you is already in a duel.", NamedTextColor.RED)); return; }
        challenges.put(to.getUniqueId(), from.getUniqueId());
        from.sendMessage(Component.text("Duel request sent to " + to.getName() + ".", NamedTextColor.GOLD));
        to.sendMessage(Component.text(from.getName() + " challenges you to a stand duel! ", NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/stand accept")))
                .append(Component.text("  (expires in 30s)", NamedTextColor.GRAY)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> challenges.remove(to.getUniqueId(), from.getUniqueId()), 600L);
    }

    public void accept(Player p) {
        UUID fromId = challenges.remove(p.getUniqueId());
        Player from = fromId == null ? null : Bukkit.getPlayer(fromId);
        if (from == null) { p.sendMessage(Component.text("No duel request to accept.", NamedTextColor.RED)); return; }
        if (plugin.stands().standOf(p) == null) { p.sendMessage(Component.text("You need a stand to duel.", NamedTextColor.RED)); return; }
        if (inDuel(from) || inDuel(p)) return;
        Duel d = new Duel(from.getUniqueId(), p.getUniqueId());
        duels.put(d.a, d); duels.put(d.b, d);
        for (Player x : List.of(from, p)) { reset(x); plugin.stands().clearCooldowns(x); }
        int count = plugin.getConfig().getInt("pvp.duel-countdown", 5);
        for (int i = 0; i <= count; i++) {
            final int left = count - i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (duels.get(d.a) != d) return;
                for (UUID id : List.of(d.a, d.b)) {
                    Player x = Bukkit.getPlayer(id);
                    if (x == null) continue;
                    x.showTitle(Title.title(Component.text(left > 0 ? String.valueOf(left) : "FIGHT!", NamedTextColor.GOLD, TextDecoration.BOLD),
                            Component.text(from.getName() + " vs " + p.getName(), NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    x.playSound(x.getLocation(), left > 0 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, left > 0 ? 1f : 1.4f);
                }
                if (left == 0) { d.live = true; d.started = System.currentTimeMillis(); }
            }, i * 20L);
        }
        long limit = plugin.getConfig().getInt("pvp.duel-time-limit", 180) * 20L + count * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (duels.get(d.a) == d) end(d, null); }, limit);
    }

    private void reset(Player p) {
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20); p.setFireTicks(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
    }

    private void end(Duel d, UUID winnerId) {
        duels.remove(d.a); duels.remove(d.b);
        Player a = Bukkit.getPlayer(d.a), b = Bukkit.getPlayer(d.b);
        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        Player loser = winner == null ? null : (winner.equals(a) ? b : a);
        for (Player x : Arrays.asList(a, b)) if (x != null) { reset(x); x.setNoDamageTicks(60); }
        if (winnerId == null) {
            for (Player x : Arrays.asList(a, b)) if (x != null) x.sendMessage(Component.text("Duel over: time ran out. Draw.", NamedTextColor.GRAY));
            return;
        }
        String wName = winner != null ? winner.getName() : "?", lName = loser != null ? loser.getName() : "their opponent";
        if (winner != null) {
            winner.getPersistentDataContainer().set(kWins, PersistentDataType.INTEGER, wins(winner) + 1);
            plugin.stands().addXp(winner, plugin.getConfig().getDouble("xp.duel-win", 150));
            winner.showTitle(Title.title(Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD), Component.text("You beat " + lName, NamedTextColor.YELLOW)));
        }
        if (loser != null) {
            loser.getPersistentDataContainer().set(kLosses, PersistentDataType.INTEGER, losses(loser) + 1);
            plugin.stands().addXp(loser, plugin.getConfig().getDouble("xp.duel-loss", 30));
            loser.showTitle(Title.title(Component.text("DEFEATED", NamedTextColor.RED, TextDecoration.BOLD), Component.text(wName + " wins", NamedTextColor.GRAY)));
        }
        String ws = winner == null ? "" : " 「" + plugin.stands().displayName(plugin.stands().standOf(winner)) + "」";
        Bukkit.broadcast(Component.text("⚔ " + wName + ws + " defeated " + lName + " in a stand duel!", NamedTextColor.GOLD));
    }

    /** A killing blow in a duel ends the duel instead of killing. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return;
        if (!d.live) { e.setCancelled(true); return; }             // no damage during the countdown
        if (e.getFinalDamage() < p.getHealth()) return;
        e.setCancelled(true);
        end(d, d.other(p.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Duel d = duels.get(e.getPlayer().getUniqueId());
        if (d != null) end(d, d.other(e.getPlayer().getUniqueId()));
        challenges.remove(e.getPlayer().getUniqueId());
    }
}
