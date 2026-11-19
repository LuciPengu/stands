package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;

/** Crazy Diamond — brawler that outlasts you by fixing itself. */
public final class CrazyDiamond extends BaseKit {
    private static final List<Holder<MobEffect>> BAD = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS,
            MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.NAUSEA, MobEffects.MINING_FATIGUE);

    public CrazyDiamond(StandManager m, TimeStopManager ts, Abilities a) { super("crazydiamond", m, ts, a, "DORA", "Restore", "Fix Gear", "Full Restore"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "crazydiamond-rush", "DORARARARA!", "DORAAA!");
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "reverse");
        bar(p, "Restore!");
        a.heal(p, cfg.getDouble("instant-heal", 3));
        a.buff(p, cfg.getDouble("regen-seconds", 3), MobEffects.REGENERATION, 2);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        int fixed = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it.isEmpty() || !it.isDamageableItem() || !it.isDamaged()) continue;
            it.setDamageValue(0);
            fixed++;
        }
        if (fixed == 0) { Txt.bar(p, Txt.of("Nothing to fix.")); return false; }
        m.anim(p, "cast");
        m.sound(p, "reverse");
        Fx.particle(p.level(), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1, 0), 25, .5, .6, .5, 0);
        bar(p, "Fixed " + fixed + " item" + (fixed == 1 ? "" : "s") + "!");
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "pose");
        m.sound(p, "reverse");
        title(p, "CRAZY DIAMOND!", "Restore!");
        double amount = cfg.getDouble("heal", 12), radius = cfg.getDouble("radius", 6);
        restore(p, amount);
        // Teammates = players on the same scoreboard team. Never heals enemies.
        PlayerTeam team = p.getTeam();
        if (team != null) for (ServerPlayer o : p.level().players())
            if (o != p && o.position().distanceTo(p.position()) <= radius && o.getTeam() == team) restore(o, amount);
        return true;
    }

    private void restore(ServerPlayer p, double amount) {
        a.heal(p, amount);
        BAD.forEach(p::removeEffect);
        p.setRemainingFireTicks(0);
        Fx.particle(p.level(), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1, 0), 30, .5, .7, .5, 0);
    }
}
