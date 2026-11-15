package dev.luci.stands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class StandsPlugin extends JavaPlugin implements TabCompleter {

    private StandManager stands;
    private TimeStopManager timeStop;
    private PackServer packServer;
    private PvpManager pvp;
    private Guide guide;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pvp = new PvpManager(this);
        guide = new Guide(this);
        timeStop = new TimeStopManager(this);
        stands = new StandManager(this, timeStop);
        installModels();
        packServer = new PackServer(this);
        packServer.start();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(stands, this);
        pm.registerEvents(timeStop, this);
        pm.registerEvents(new ControlsListener(stands), this);
        pm.registerEvents(packServer, this);
        pm.registerEvents(pvp, this);
        pm.registerEvents(guide, this);

        var cmd = getCommand("stand");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        stands.registerArrowRecipe();
        Bukkit.getScheduler().runTaskTimer(this, stands::tick, 1L, 1L);
        getLogger().info("Stands enabled: " + getConfig().getStringList("enabled-stands"));
    }

    @Override
    public void onDisable() {
        if (timeStop != null) timeStop.endAll();
        if (stands != null) { stands.unsummonAll(); stands.abilities().restoreAll(); }
        if (packServer != null) packServer.stop();
    }

    public StandManager stands() { return stands; }

    /** First run on a new server: copy the bundled stand models into BetterModel and reload it. */
    private void installModels() {
        java.io.File dir = new java.io.File(getDataFolder().getParentFile(), "BetterModel/models");
        dir.mkdirs();
        int copied = 0;
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(getFile())) {
            for (var en : java.util.Collections.list(jar.entries())) {
                if (en.isDirectory() || !en.getName().startsWith("models/") || !en.getName().endsWith(".bbmodel")) continue;
                java.io.File out = new java.io.File(dir, en.getName().substring("models/".length()));
                if (out.exists()) continue;
                try (var in = jar.getInputStream(en)) { java.nio.file.Files.copy(in, out.toPath()); }
                copied++;
            }
        } catch (java.io.IOException e) {
            getLogger().warning("Couldn't install stand models: " + e.getMessage());
        }
        if (copied > 0) {
            getLogger().info("Installed " + copied + " stand models into BetterModel. Reloading it...");
            Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bettermodel reload"), 40L);
        }
    }
    public PvpManager pvp() { return pvp; }

    private static Component msg(String s, NamedTextColor c) { return Component.text(s, c); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "guide" : args[0].toLowerCase();
        boolean admin = sender.hasPermission("stands.admin");
        Player self = sender instanceof Player p ? p : null;
        switch (sub) {
            case "info" -> {
                if (self == null) return false;
                sender.sendMessage(stands.describe(self));
            }
            case "help" -> sender.sendMessage(msg("""
                    /stand guide — controls and every stand's abilities
                    /stand info — your stand, level and XP
                    /stand pvp — turn stand PvP on or off
                    /stand duel <player> — challenge someone  ·  /stand accept
                    /stand levelup — spend XP to level your stand
                    /stand disc — get your Stand Disc back
                    Admin: /stand give <player> <stand> | arrow [player] | setlevel <player> <1-3> | remove <player> | list | reload | testmode | switch <stand>""", NamedTextColor.GOLD));
            case "guide" -> { if (self != null) guide.open(self); }
            case "pvp" -> {
                if (self == null) return false;
                boolean on = pvp.togglePvp(self);
                sender.sendMessage(msg("Stand PvP is now " + (on ? "ON: stand powers work between you and other players with PvP on." : "OFF: other players' stand powers can't touch you (duels still work)."), on ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "duel" -> {
                if (self == null || args.length < 2) return false;
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(msg("Player not found", NamedTextColor.RED)); return true; }
                pvp.challenge(self, t);
            }
            case "accept" -> { if (self != null) pvp.accept(self); }
            case "levelup" -> { if (self != null) stands.levelUp(self); }
            case "disc" -> { if (self != null) stands.giveDisc(self); }
            case "list" -> sender.sendMessage(msg("Stands: " + stands.knownStands() + "  enabled: " + getConfig().getStringList("enabled-stands"), NamedTextColor.GOLD));
            case "give" -> {
                if (!admin || args.length < 3) return false;
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(msg("Player not found", NamedTextColor.RED)); return true; }
                if (!stands.knownStands().contains(args[2].toLowerCase())) { sender.sendMessage(msg("Unknown stand", NamedTextColor.RED)); return true; }
                stands.setStand(t, args[2].toLowerCase());
                sender.sendMessage(msg("Gave " + t.getName() + " " + args[2], NamedTextColor.GREEN));
            }
            case "arrow" -> {
                if (!admin) return false;
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : self;
                if (t == null) return false;
                t.getInventory().addItem(stands.arrowItem());
            }
            case "setlevel" -> {
                if (!admin || args.length < 3) return false;
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) return false;
                int lvl;
                try { lvl = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(msg("Level must be 1, 2 or 3", NamedTextColor.RED)); return true; }
                stands.setLevel(t, Math.max(1, Math.min(3, lvl)));
                sender.sendMessage(msg("Set " + t.getName() + " to level " + args[2], NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!admin) return false;
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : self;
                if (t == null) return false;
                stands.removeStand(t);
                sender.sendMessage(msg("Removed " + t.getName() + "'s stand", NamedTextColor.GREEN));
            }
            case "switch" -> {
                if (!admin || self == null || args.length < 2) return false;
                if (!stands.knownStands().contains(args[1].toLowerCase())) { sender.sendMessage(msg("Unknown stand. /stand list", NamedTextColor.RED)); return true; }
                stands.setStand(self, args[1].toLowerCase());
                stands.setLevel(self, 3);
                sender.sendMessage(msg("Switched to " + stands.displayName(args[1].toLowerCase()) + " (level 3)", NamedTextColor.GREEN));
            }
            case "testmode" -> {
                if (!admin || self == null) return false;
                boolean on = stands.toggleTestMode(self);
                sender.sendMessage(msg(on ? "Test mode ON: mobs take stand-vs-stand damage from you (armor ignored, PvP numbers)." : "Test mode OFF: mobs take the unfair damage again.", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (!admin) return false;
                reloadConfig();
                sender.sendMessage(msg("Stands config reloaded", NamedTextColor.GREEN));
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("guide", "info", "help", "levelup", "disc", "pvp", "duel", "accept"));
            if (sender.hasPermission("stands.admin")) out.addAll(List.of("give", "arrow", "setlevel", "remove", "list", "reload", "testmode", "switch"));
        } else if (args.length == 2 && List.of("give", "arrow", "setlevel", "remove", "duel").contains(args[0].toLowerCase())) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("switch")) {
            out.addAll(stands.knownStands());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(stands.knownStands());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) {
            out.addAll(List.of("1", "2", "3"));
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
