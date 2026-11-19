package dev.luci.stands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * PvP mode. Stand powers only affect another player when both have PvP on, or when they're in a duel together.
 * Duels end when one player would have died: nobody actually dies or drops items.
 */
public final class PvpManager {
    private static final class Duel {
        final UUID a, b;
        boolean live;
        Duel(UUID a, UUID b) { this.a = a; this.b = b; }
        UUID other(UUID x) { return x.equals(a) ? b : a; }
    }

    private final Map<UUID, UUID> challenges = new HashMap<>();   // challenged -> challenger
    private final Map<UUID, Duel> duels = new HashMap<>();

    private StandManager stands() { return StandsMod.stands(); }

    public boolean pvpOn(Player p) {
        Boolean b = p.getAttached(StandData.PVP);
        return b != null ? b : Config.get().getBoolean("pvp.default-on", false);
    }

    public boolean togglePvp(ServerPlayer p) {
        boolean on = !pvpOn(p);
        p.setAttached(StandData.PVP, on);
        return on;
    }

    public boolean inDuel(Player p) { return duels.containsKey(p.getUUID()); }

    /** Can `a`'s stand powers affect player `b`? */
    public boolean canFight(Player a, Player b) {
        if (a == null || a == b) return true;
        Duel d = duels.get(a.getUUID());
        if (d != null) return d.live && d.other(a.getUUID()).equals(b.getUUID());
        if (duels.containsKey(b.getUUID())) return false;      // don't interfere with someone else's duel
        return pvpOn(a) && pvpOn(b);
    }

    // ------------------------------------------------------------ duels

    public void challenge(ServerPlayer from, ServerPlayer to) {
        if (from == to) { Txt.msg(from, "You can't duel yourself.", ChatFormatting.RED); return; }
        if (stands().standOf(from) == null) { Txt.msg(from, "You need a stand to duel.", ChatFormatting.RED); return; }
        if (inDuel(from) || inDuel(to)) { Txt.msg(from, "One of you is already in a duel.", ChatFormatting.RED); return; }
        challenges.put(to.getUUID(), from.getUUID());
        Txt.msg(from, "Duel request sent to " + to.getName().getString() + ".", ChatFormatting.GOLD);
        Txt.msg(to, Txt.of(from.getName().getString() + " challenges you to a stand duel! ", ChatFormatting.GOLD)
                .append(Txt.of("[ACCEPT]", ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/stand accept"))))
                .append(Txt.of("  (expires in 30s)", ChatFormatting.GRAY)));
        UUID f = from.getUUID(), t = to.getUUID();
        Sched.later(600, () -> challenges.remove(t, f));
    }

    public void accept(ServerPlayer p) {
        UUID fromId = challenges.remove(p.getUUID());
        ServerPlayer from = fromId == null ? null : stands().player(fromId);
        if (from == null) { Txt.msg(p, "No duel request to accept.", ChatFormatting.RED); return; }
        if (stands().standOf(p) == null) { Txt.msg(p, "You need a stand to duel.", ChatFormatting.RED); return; }
        if (inDuel(from) || inDuel(p)) return;
        Duel d = new Duel(from.getUUID(), p.getUUID());
        duels.put(d.a, d);
        duels.put(d.b, d);
        for (ServerPlayer x : List.of(from, p)) { reset(x); stands().clearCooldowns(x); }
        int count = Config.get().getInt("pvp.duel-countdown", 5);
        String names = from.getName().getString() + " vs " + p.getName().getString();
        for (int i = 0; i <= count; i++) {
            final int left = count - i;
            Sched.later(i * 20L, () -> {
                if (duels.get(d.a) != d) return;
                for (UUID id : List.of(d.a, d.b)) {
                    ServerPlayer x = stands().player(id);
                    if (x == null) continue;
                    Txt.title(x, Txt.of(left > 0 ? String.valueOf(left) : "FIGHT!", ChatFormatting.GOLD, ChatFormatting.BOLD),
                            Txt.of(names, ChatFormatting.YELLOW), 0, 18, 2);
                    Fx.sound(x.level(), x.position(), left > 0 ? SoundEvents.NOTE_BLOCK_HAT.value() : SoundEvents.ENDER_DRAGON_GROWL, 1f, left > 0 ? 1f : 1.4f);
                }
                if (left == 0) d.live = true;
            });
        }
        long limit = Config.get().getInt("pvp.duel-time-limit", 180) * 20L + count * 20L;
        Sched.later(limit, () -> { if (duels.get(d.a) == d) end(d, null); });
    }

    private void reset(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.clearFire();
        p.removeAllEffects();
    }

    private void end(Duel d, UUID winnerId) {
        duels.remove(d.a);
        duels.remove(d.b);
        ServerPlayer a = stands().player(d.a), b = stands().player(d.b);
        ServerPlayer winner = winnerId == null ? null : stands().player(winnerId);
        ServerPlayer loser = winner == null ? null : (winner == a ? b : a);
        for (ServerPlayer x : Arrays.asList(a, b)) if (x != null) { reset(x); x.setInvulnerableTime(60); }
        if (winnerId == null) {
            for (ServerPlayer x : Arrays.asList(a, b)) if (x != null) Txt.msg(x, "Duel over: time ran out. Draw.", ChatFormatting.GRAY);
            return;
        }
        String wName = winner != null ? winner.getName().getString() : "?";
        String lName = loser != null ? loser.getName().getString() : "their opponent";
        if (winner != null) {
            winner.setAttached(StandData.WINS, StandData.wins(winner) + 1);
            stands().addXp(winner, Config.get().getDouble("xp.duel-win", 150));
            Txt.title(winner, Txt.of("VICTORY", ChatFormatting.GOLD, ChatFormatting.BOLD), Txt.of("You beat " + lName, ChatFormatting.YELLOW), 10, 50, 10);
        }
        if (loser != null) {
            loser.setAttached(StandData.LOSSES, StandData.losses(loser) + 1);
            stands().addXp(loser, Config.get().getDouble("xp.duel-loss", 30));
            Txt.title(loser, Txt.of("DEFEATED", ChatFormatting.RED, ChatFormatting.BOLD), Txt.of(wName + " wins", ChatFormatting.GRAY), 10, 50, 10);
        }
        String ws = winner == null ? "" : " 「" + stands().displayName(stands().standOf(winner)) + "」";
        var msg = Txt.of("⚔ " + wName + ws + " defeated " + lName + " in a stand duel!", ChatFormatting.GOLD);
        for (ServerPlayer x : stands().players()) Txt.msg(x, msg);
    }

    /** ALLOW_DAMAGE: no damage to duelists during the countdown. */
    boolean allowDamage(LivingEntity e) {
        Duel d = e instanceof ServerPlayer p ? duels.get(p.getUUID()) : null;
        return d == null || d.live;
    }

    /** ALLOW_DEATH: a killing blow in a duel ends the duel instead of killing. */
    boolean allowDeath(LivingEntity e, DamageSource src) {
        if (!(e instanceof ServerPlayer p)) return true;
        Duel d = duels.get(p.getUUID());
        if (d == null) return true;
        p.setHealth(1f);
        end(d, d.other(p.getUUID()));
        return false;
    }

    void onQuit(ServerPlayer p) {
        Duel d = duels.get(p.getUUID());
        if (d != null) end(d, d.other(p.getUUID()));
        challenges.remove(p.getUUID());
    }
}
