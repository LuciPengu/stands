package dev.luci.stands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/** /stand guide: controls, leveling, PvP, and every stand's abilities (hover a name). */
public final class Guide {
    private record Entry(String role, String... abilities) { }
    private static final String[] SECTIONS = {"basic", "skill", "skill2", "ultimate"};
    private static final Map<String, Entry> INFO = new HashMap<>();

    static {
        INFO.put("theworld", new Entry("Close range · time stopper",
                "MUDA rush: a flurry of punches, the last one launches", "Throw a fan of 5 knives",
                "Dash forward. During your time stop it becomes ROAD ROLLER", "ZA WARUDO: stop time for everyone nearby"));
        INFO.put("starplatinum", new Entry("Close range · hardest rush",
                "ORA rush: the strongest barrage in the game", "Star Finger: a long poke",
                "Throw a block that knocks enemies back", "Stop time (shorter than The World)"));
        INFO.put("crazydiamond", new Entry("Close range · healer",
                "DORA rush", "Restore: heal yourself over a few seconds",
                "Repair every damaged item you carry", "Full Restore: big heal, clears poison and fire, heals teammates"));
        INFO.put("stickyfingers", new Entry("Close range · mobility and traps",
                "ARI rush", "Zipper Dash: teleport through walls",
                "Zipper Trap: damages and slows whoever steps on it", "Zip Away: vanish, untouchable, and heal"));
        INFO.put("kingcrimson", new Entry("Close range · time tricks",
                "Rush", "Jab: one huge hit after a short wind-up",
                "Time Skip: short teleport", "Time Erase: become an untouchable ghost; your next hit after it lands harder"));
        INFO.put("silverchariot", new Entry("Close range · fastest stand",
                "Lunge: dash and stab, very short cooldown", "Flurry of sword strikes",
                "Shoot the sword tip", "Armor Off: very fast, but you take more damage"));
        INFO.put("magiciansred", new Entry("Ranged · fire",
                "Fireball that sets enemies on fire", "Flame Wall: a burning area",
                "Power Boost: speed and a stronger next hit", "Crossfire Hurricane: 5 fireballs at once"));
        INFO.put("heirophantgreen", new Entry("Ranged · control",
                "Emerald Splash: a spread of emeralds", "Webs: cobwebs in front of you",
                "Tendril Grab: root a far target in place", "20m Emerald Splash: two huge waves"));
        INFO.put("aerosmith", new Entry("Ranged · plane",
                "Minigun burst", "Drop a bomb", "Radar: enemies glow through walls", "Strafing Run: a line of explosions"));
        INFO.put("tuskact4", new Entry("Ranged · sniper",
                "Nail Shot, short cooldown", "Burst of 5 nails",
                "Golden Spin: heavy shot that spins the target", "Infinite Rotation: homing shot that spins and damages for seconds"));
        INFO.put("theemperor", new Entry("Ranged · gun (no stand body)",
                "Shoot: instant hit at long range", "Curving Shot: a homing bullet", "Slide dash", "Bullet Storm: 6 rapid shots"));
        INFO.put("weatherreport", new Entry("Ranged · weather",
                "Lightning strike where you aim", "Wind Boost: speed", "Gust: blow enemies away", "Heavy Weather: blind and slow everyone around you"));
        INFO.put("killerqueen", new Entry("Control · bombs",
                "Punch", "Plant a bomb on a player or block, use again to detonate",
                "Sheer Heart Attack: a slow homing bomb", "Bites the Dust: at low health, heal and teleport to your spawn"));
        INFO.put("thehand", new Entry("Control · erases space",
                "Punch", "Air Erase: pull an enemy to you (or teleport forward)",
                "Erase a 3x3 of blocks for a few seconds", "DELETE: a massive hit after a wind-up"));
        INFO.put("purplehaze", new Entry("Control · poison",
                "Virus Punch: hit and poison", "Rage Mode: Virus Punch has almost no cooldown",
                "Throw a poison capsule", "Toxic Haze: wither cloud around you. It hurts you too"));
        INFO.put("echoesact3", new Entry("Control · words",
                "Punch", "FWOOSH: burn a target", "SPLASH: shove enemies back", "Three Freeze: pin one target to the ground"));
        INFO.put("metallica", new Entry("Control · invisible (no stand body)",
                "Iron Spikes at range", "Razor Blades: bleed over time", "Iron Grab: pull a target to you", "Vanish: unseen and untouchable, but you can't attack"));
        INFO.put("hermitpurple", new Entry("Control · vines (no stand body)",
                "Whip", "Grapple to a block", "Vine Trap: roots whoever steps on it", "Hermit Boost: heal, regen and speed"));
        INFO.put("anubis", new Entry("Close range · cursed sword (no stand body)",
                "Slash: stronger each time you hit the same target", "Anubis Sense: speed and jump", "Dash Slash", "Possession: long dash into a spinning cut"));
        INFO.put("goldenexperience", new Entry("Control · life",
                "MUDA rush", "Life Giver: a wolf fights for you", "Life Boost: speed and a stronger next hit",
                "Life Sensor: every enemy glows. Evolves with a Stand Arrow at level 3"));
        INFO.put("ger", new Entry("Close range · Requiem (evolved Gold Experience)",
                "Stronger MUDA rush", "Life Giver", "Life Boost", "Return to Zero: cancel the next killing blow and hit the attacker back"));
        INFO.put("d4c", new Entry("Control · dimensions",
                "Punch", "Dimension Hop: untouchable teleport", "Decoy: leave a copy of yourself and slip away", "Love Train: halve damage against you"));
    }

    private Guide() { }

    private static MutableComponent line(String s, ChatFormatting c) { return Txt.of(s + "\n", c); }

    public static void open(ServerPlayer p, boolean admin) {
        StandManager sm = StandsMod.stands();
        Cfg cfg = Config.get();
        String[] keys = StandsMod.keyLabels();
        MutableComponent out = Component.empty()
                .append(Txt.of("━━━━━━━━ Stand Guide ━━━━━━━━\n", ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(line("1. Get a stand: craft a Stand Arrow (2 gold / soul sand + gold / stick) and use it. It can reject you!", ChatFormatting.WHITE))
                .append(line("2. Controls (change them in Options → Controls → Stands):", ChatFormatting.WHITE))
                .append(line("   " + keys[4] + " summon / unsummon   ·   " + keys[0] + " basic   ·   " + keys[1] + " skill", ChatFormatting.YELLOW))
                .append(line("   " + keys[2] + " skill 2 (Lv2)   ·   " + keys[3] + " ultimate (Lv3)", ChatFormatting.YELLOW))
                .append(line("3. The bar above your hotbar shows cooldowns while your stand is out.", ChatFormatting.WHITE));

        String s = sm.standOf(p);
        int lvl = sm.levelOf(p);
        if (s == null) out.append(line("You don't have a stand yet.", ChatFormatting.GRAY));
        else out.append(line("Your stand: " + sm.displayName(s) + " (level " + lvl + "/3" + (lvl >= 3 ? ", max" : String.format(", %.0f / %.0f XP", sm.xpOf(p), sm.xpFor(lvl + 1))) + ")", ChatFormatting.GREEN));
        out.append(line("Level up by fighting (damage, kills, duels) or /stand levelup (" + cfg.getInt("levels.level-2-cost", 30) + " then " + cfg.getInt("levels.level-3-cost", 60) + " XP levels).", ChatFormatting.GRAY));

        boolean on = StandsMod.pvp().pvpOn(p);
        out.append(Txt.of("Stand PvP: " + (on ? "ON" : "OFF") + " ", on ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Txt.of("[toggle]", ChatFormatting.AQUA).withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/stand pvp"))))
                .append(Txt.of("   Duels: /stand duel <player> (nobody dies)\n", ChatFormatting.GRAY));

        out.append(Txt.of("Stands (hover for abilities" + (admin ? ", click to equip" : "") + "):\n", ChatFormatting.GOLD));
        double cdMult = cfg.getDouble("power.cooldown-multiplier", 1.0);
        boolean first = true;
        for (StandKit kit : sm.kits()) {
            Entry e = INFO.get(kit.id());
            if (e == null) continue;
            MutableComponent hover = Txt.of(sm.displayName(kit.id()) + "\n", ChatFormatting.GOLD, ChatFormatting.BOLD).append(Txt.of(e.role + "\n", ChatFormatting.GRAY));
            for (int i = 0; i < 4; i++) {
                Cfg sec = sm.cfg(kit.id()).section(SECTIONS[i]);
                String cd = String.format("  (%.0fs%s)", sec.getDouble("cooldown", 0) * cdMult, i == 2 ? ", Lv2" : i == 3 ? ", Lv3" : "");
                hover.append(Txt.of("\n" + keys[i] + cd + "\n", ChatFormatting.YELLOW)).append(Txt.of("  " + e.abilities[i], ChatFormatting.WHITE));
            }
            final MutableComponent h = hover;
            MutableComponent name = Txt.of(sm.displayName(kit.id()), kit.id().equals(s) ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                    .withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(h)));
            if (admin) name = name.withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/stand switch " + kit.id())));
            if (!first) out.append(Txt.of(" · ", ChatFormatting.DARK_GRAY));
            out.append(name);
            first = false;
        }
        Txt.msg(p, out);
    }
}
