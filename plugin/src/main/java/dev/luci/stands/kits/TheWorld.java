package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.TimeStopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;

/** The World — close-range time stopper. */
public final class TheWorld extends BaseKit {
    public TheWorld(StandManager m, TimeStopManager ts, Abilities a) { super("theworld", m, ts, a, "MUDA", "Knives", "Dash", "ZA WARUDO"); }

    @Override public String label(Player p, Slot slot) { return slot == Slot.SKILL2 && ts.hasStop(p) ? "ROAD ROLLER" : super.label(p, slot); }
    @Override protected String section(Player p, Slot slot) { return slot == Slot.SKILL2 && ts.hasStop(p) ? "roadroller" : super.section(p, slot); }

    @Override protected boolean basic(Player p, ConfigurationSection cfg) {
        a.rush(p, cfg, "theworld-rush", "MUDA MUDA MUDA!", "WRYYYYY!");
        return true;
    }

    @Override protected boolean skill(Player p, ConfigurationSection cfg) {
        m.anim(p, "throw");
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.6f);
        bar(p, "Throwing Knives!");
        a.volley(p, Material.IRON_SWORD, cfg.getInt("knives", 5), 24, 1.7, true, cfg.getDouble("damage-per-knife", 4.0), null);
        return true;
    }

    @Override protected boolean skill2(Player p, ConfigurationSection cfg) {
        if (ts.hasStop(p)) return roadRoller(p, cfg);
        m.anim(p, "dash");
        a.dash(p, cfg.getDouble("distance", 5));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.2f);
        return true;
    }

    private boolean roadRoller(Player p, ConfigurationSection cfg) {
        Block g = a.aim(p, 16).getBlock();
        for (int i = 0; i < 20 && g.getY() > p.getWorld().getMinHeight() && g.isPassable(); i++) g = g.getRelative(0, -1, 0);
        final Block ground = g;
        Location land = ground.getLocation().add(0.5, 1, 0.5);
        m.sound(p.getLocation(), "roadroller");
        m.anim(p, "pose");
        for (Player near : p.getWorld().getPlayers()) if (near.getLocation().distance(land) < 40)
            near.showTitle(Title.title(Component.text("ROAD ROLLER DA!", NamedTextColor.GOLD, TextDecoration.BOLD), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
        BlockDisplay roller = p.getWorld().spawn(land.clone().add(0, 9, 0), BlockDisplay.class, d -> {
            d.setBlock(Material.YELLOW_CONCRETE.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-1.5f, 0, -1f), new AxisAngle4f(), new Vector3f(3f, 1.6f, 2f), new AxisAngle4f()));
            d.setTeleportDuration(10);
            d.setPersistent(false);
        });
        later(2, () -> roller.teleport(land));
        later(12, () -> {
            land.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, land, 1);
            land.getWorld().spawnParticle(Particle.BLOCK, land, 60, 1.5, .3, 1.5, ground.getBlockData());
            a.blast(p, land, cfg.getDouble("radius", 4.5), cfg.getDouble("damage", 14.0), 0);
        });
        later(70, roller::remove);
        return true;
    }

    @Override protected boolean ultimate(Player p, ConfigurationSection cfg) {
        if (!ts.start(p, cfg.getDouble("duration", 5.0))) return false;
        m.sound(p.getLocation(), "timestop");
        m.standForward(p, 28);
        m.anim(p, "pose");
        title(p, "ZA WARUDO!", "Toki yo tomare!");
        return true;
    }
}
