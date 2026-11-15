package dev.luci.stands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /stand guide — a chest menu explaining the controls and every stand's abilities. */
public final class Guide implements Listener {
    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> stands = new HashMap<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private record Entry(Material icon, String role, String... abilities) { }
    private static final String[] KEYS = {"Right-click", "Sneak + Right-click", "F", "Sneak + F"};
    private static final String[] SECTIONS = {"basic", "skill", "skill2", "ultimate"};

    private static final Map<String, Entry> INFO = new HashMap<>();
    static {
        INFO.put("theworld", new Entry(Material.CLOCK, "Close range · time stopper",
                "MUDA rush: a flurry of punches, the last one launches", "Throw a fan of 5 knives",
                "Dash forward. During your time stop it becomes ROAD ROLLER", "ZA WARUDO: stop time for everyone nearby"));
        INFO.put("starplatinum", new Entry(Material.AMETHYST_SHARD, "Close range · hardest rush",
                "ORA rush: the strongest barrage in the game", "Star Finger: a long poke",
                "Throw a block that knocks enemies back", "Stop time (shorter than The World)"));
        INFO.put("crazydiamond", new Entry(Material.DIAMOND, "Close range · healer",
                "DORA rush", "Restore: heal yourself over a few seconds",
                "Repair every damaged item you carry", "Full Restore: big heal, clears poison and fire, heals teammates"));
        INFO.put("stickyfingers", new Entry(Material.SHEARS, "Close range · mobility and traps",
                "ARI rush", "Zipper Dash: teleport through walls",
                "Zipper Trap: damages and slows whoever steps on it", "Zip Away: vanish, untouchable, and heal"));
        INFO.put("kingcrimson", new Entry(Material.REDSTONE, "Close range · time tricks",
                "Rush", "Jab: one huge hit after a short wind-up",
                "Time Skip: short teleport", "Time Erase: become an untouchable ghost; your next hit after it lands harder"));
        INFO.put("silverchariot", new Entry(Material.IRON_SWORD, "Close range · fastest stand",
                "Lunge: dash and stab, very short cooldown", "Flurry of sword strikes",
                "Shoot the sword tip", "Armor Off: very fast, but you take more damage"));
        INFO.put("magiciansred", new Entry(Material.BLAZE_POWDER, "Ranged · fire",
                "Fireball that sets enemies on fire", "Flame Wall: a burning area",
                "Power Boost: speed and a stronger next hit", "Crossfire Hurricane: 5 fireballs at once"));
        INFO.put("heirophantgreen", new Entry(Material.EMERALD, "Ranged · control",
                "Emerald Splash: a spread of emeralds", "Webs: cobwebs in front of you",
                "Tendril Grab: root a far target in place", "20m Emerald Splash: two huge waves"));
        INFO.put("aerosmith", new Entry(Material.FIREWORK_ROCKET, "Ranged · plane",
                "Minigun burst", "Drop a bomb", "Radar: enemies glow through walls", "Strafing Run: a line of explosions"));
        INFO.put("tuskact4", new Entry(Material.GOLD_NUGGET, "Ranged · sniper",
                "Nail Shot, short cooldown", "Burst of 5 nails",
                "Golden Spin: heavy shot that spins the target", "Infinite Rotation: homing shot that spins and damages for seconds"));
        INFO.put("theemperor", new Entry(Material.CROSSBOW, "Ranged · gun (no stand body)",
                "Shoot: instant hit at long range", "Curving Shot: a homing bullet", "Slide dash", "Bullet Storm: 6 rapid shots"));
        INFO.put("weatherreport", new Entry(Material.LIGHTNING_ROD, "Ranged · weather",
                "Lightning strike where you aim", "Wind Boost: speed", "Gust: blow enemies away", "Heavy Weather: blind and slow everyone around you"));
        INFO.put("killerqueen", new Entry(Material.TNT, "Control · bombs",
                "Punch", "Plant a bomb on a player or block, use again to detonate",
                "Sheer Heart Attack: a slow homing bomb", "Bites the Dust: at low health, heal and teleport to your spawn"));
        INFO.put("thehand", new Entry(Material.ENDER_PEARL, "Control · erases space",
                "Punch", "Air Erase: pull an enemy to you (or teleport forward)",
                "Erase a 3x3 of blocks for a few seconds", "DELETE: a massive hit after a wind-up"));
        INFO.put("purplehaze", new Entry(Material.DRAGON_BREATH, "Control · poison",
                "Virus Punch: hit and poison", "Rage Mode: Virus Punch has almost no cooldown",
                "Throw a poison capsule", "Toxic Haze: wither cloud around you. It hurts you too"));
        INFO.put("echoesact3", new Entry(Material.TURTLE_EGG, "Control · words",
                "Punch", "FWOOSH: burn a target", "SPLASH: shove enemies back", "Three Freeze: pin one target to the ground"));
        INFO.put("metallica", new Entry(Material.IRON_NUGGET, "Control · invisible (no stand body)",
                "Iron Spikes at range", "Razor Blades: bleed over time", "Iron Grab: pull a target to you", "Vanish: unseen and untouchable, but you can't attack"));
        INFO.put("hermitpurple", new Entry(Material.VINE, "Control · vines (no stand body)",
                "Whip", "Grapple to a block", "Vine Trap: roots whoever steps on it", "Hermit Boost: heal, regen and speed"));
        INFO.put("anubis", new Entry(Material.NETHERITE_SWORD, "Close range · cursed sword (no stand body)",
                "Slash: stronger each time you hit the same target", "Anubis Sense: speed and jump", "Dash Slash", "Possession: long dash into a spinning cut"));
        INFO.put("goldenexperience", new Entry(Material.GOLDEN_APPLE, "Control · life",
                "MUDA rush", "Life Giver: a wolf fights for you", "Life Boost: speed and a stronger next hit",
                "Life Sensor: every enemy glows. Evolves with a Stand Arrow at level 3"));
        INFO.put("ger", new Entry(Material.ENCHANTED_GOLDEN_APPLE, "Close range · Requiem (evolved Gold Experience)",
                "Stronger MUDA rush", "Life Giver", "Life Boost", "Return to Zero: cancel the next killing blow and hit the attacker back"));
        INFO.put("d4c", new Entry(Material.RABBIT_FOOT, "Control · dimensions",
                "Punch", "Dimension Hop: untouchable teleport", "Decoy: leave a copy of yourself and slip away", "Love Train: halve damage against you"));
    }

    private final StandsPlugin plugin;
    public Guide(StandsPlugin plugin) { this.plugin = plugin; }

    private static Component plain(String s, NamedTextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(meta -> { meta.displayName(name); meta.lore(lore); meta.addItemFlags(ItemFlag.values()); });
        return it;
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Stand Guide"));
        h.inv = inv;
        StandManager sm = plugin.stands();
        var cfg = plugin.getConfig();

        inv.setItem(2, item(Material.BOOK, plain("How to play", NamedTextColor.GOLD), List.of(
                plain("1. Get a stand: craft a Stand Arrow and right-click it.", NamedTextColor.WHITE),
                plain("   (2 gold + soul sand + gold + stick. It can reject you!)", NamedTextColor.GRAY),
                plain("2. Hold your Stand Disc.", NamedTextColor.WHITE),
                plain("   Q = summon / unsummon your stand", NamedTextColor.YELLOW),
                plain("   Right-click = basic attack", NamedTextColor.YELLOW),
                plain("   Sneak + Right-click = skill", NamedTextColor.YELLOW),
                plain("   F = skill 2 (stand level 2)", NamedTextColor.YELLOW),
                plain("   Sneak + F = ultimate (stand level 3)", NamedTextColor.YELLOW),
                plain("3. The bar above your hotbar shows cooldowns.", NamedTextColor.WHITE),
                plain("Lost your disc? /stand disc", NamedTextColor.GRAY))));

        String s = sm.standOf(p);
        int lvl = sm.levelOf(p);
        List<Component> you = new ArrayList<>();
        if (s == null) you.add(plain("You don't have a stand yet.", NamedTextColor.GRAY));
        else {
            you.add(plain(sm.displayName(s) + " — level " + lvl + " / 3", NamedTextColor.WHITE));
            you.add(lvl >= 3 ? plain("Max level", NamedTextColor.GREEN) : plain(String.format("%.0f / %.0f XP to level %d", sm.xpOf(p), sm.xpFor(lvl + 1), lvl + 1), NamedTextColor.YELLOW));
        }
        you.add(plain("", NamedTextColor.GRAY));
        you.add(plain("How stands level up:", NamedTextColor.GOLD));
        you.add(plain("• Dealing damage with stand abilities", NamedTextColor.WHITE));
        you.add(plain("• Kills (mobs a little, players a lot)", NamedTextColor.WHITE));
        you.add(plain("• Duels: winning gives the most", NamedTextColor.WHITE));
        you.add(plain("• Or pay XP levels: /stand levelup (" + cfg.getInt("levels.level-2-cost", 30) + " then " + cfg.getInt("levels.level-3-cost", 60) + ")", NamedTextColor.WHITE));
        you.add(plain("Level 2 unlocks skill 2. Level 3 unlocks the ultimate.", NamedTextColor.GRAY));
        inv.setItem(4, item(Material.EXPERIENCE_BOTTLE, plain("Your stand & leveling", NamedTextColor.GOLD), you));

        inv.setItem(6, item(Material.DIAMOND_SWORD, plain("PvP & duels", NamedTextColor.GOLD), List.of(
                plain("Your PvP: " + (plugin.pvp().pvpOn(p) ? "ON" : "OFF") + "  (click to toggle)", plugin.pvp().pvpOn(p) ? NamedTextColor.GREEN : NamedTextColor.RED),
                plain("Stand powers only affect players who also have PvP on.", NamedTextColor.WHITE),
                plain("", NamedTextColor.GRAY),
                plain("Duels: /stand duel <player>", NamedTextColor.YELLOW),
                plain("• Works even with PvP off", NamedTextColor.WHITE),
                plain("• Full heal, cooldowns reset, countdown, then fight", NamedTextColor.WHITE),
                plain("• Nobody dies or drops items: it ends at the killing blow", NamedTextColor.WHITE),
                plain("Your record: " + plugin.pvp().wins(p) + " won, " + plugin.pvp().losses(p) + " lost", NamedTextColor.GRAY),
                plain("", NamedTextColor.GRAY),
                plain("Stand vs stand ignores armor, so gear doesn't decide fights.", NamedTextColor.GRAY))));

        int slot = 18;
        double cdMult = cfg.getDouble("power.cooldown-multiplier", 1.0);
        for (StandKit kit : sm.kits()) {
            Entry e = INFO.get(kit.id());
            if (e == null) continue;
            List<Component> lore = new ArrayList<>();
            lore.add(plain(e.role, NamedTextColor.GRAY));
            lore.add(plain("", NamedTextColor.GRAY));
            for (int i = 0; i < 4; i++) {
                var sec = sm.cfg(kit.id()) == null ? null : sm.cfg(kit.id()).getConfigurationSection(SECTIONS[i]);
                String cd = sec == null ? "" : String.format("  (%.0fs%s)", sec.getDouble("cooldown", 0) * cdMult, i == 2 ? ", Lv2" : i == 3 ? ", Lv3" : "");
                lore.add(plain(KEYS[i] + cd, NamedTextColor.YELLOW));
                lore.add(plain("  " + e.abilities[i], NamedTextColor.WHITE));
            }
            if (kit.id().equals(s)) { lore.add(plain("", NamedTextColor.GRAY)); lore.add(plain("This is your stand", NamedTextColor.GREEN)); }
            if (p.hasPermission("stands.admin")) { lore.add(plain("", NamedTextColor.GRAY)); lore.add(plain("Admin: shift-click to equip at level 3", NamedTextColor.DARK_GRAY)); }
            ItemStack it = item(e.icon, plain(sm.displayName(kit.id()), NamedTextColor.GOLD), lore);
            if (kit.id().equals(s)) it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            h.stands.put(slot, kit.id());
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        if (e.getSlot() == 6) {
            boolean on = plugin.pvp().togglePvp(p);
            p.sendMessage(plain("Stand PvP is now " + (on ? "ON" : "OFF") + ".", on ? NamedTextColor.GREEN : NamedTextColor.RED));
            open(p);
            return;
        }
        String stand = h.stands.get(e.getSlot());
        if (stand != null && e.isShiftClick() && p.hasPermission("stands.admin")) {
            plugin.stands().setStand(p, stand);
            plugin.stands().setLevel(p, 3);
            p.closeInventory();
            p.sendMessage(plain("Equipped " + plugin.stands().displayName(stand) + " (level 3).", NamedTextColor.GREEN));
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> e.getPlayer().sendMessage(
                Component.text("Stands: ", NamedTextColor.GOLD).append(Component.text("/stand guide", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/stand guide")))
                        .append(Component.text(" shows the controls and every stand's abilities.", NamedTextColor.GRAY))), 60L);
    }
}
