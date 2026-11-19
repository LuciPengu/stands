package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Aerosmith — plane with a minigun, bombs and radar. */
public final class Aerosmith extends BaseKit {
    public Aerosmith(StandManager m, TimeStopManager ts, Abilities a) { super("aerosmith", m, ts, a, "Minigun", "Bomb", "CO2 Radar", "Strafing Run"); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        int shots = cfg.getInt("shots", 8);
        double dmg = cfg.getDouble("damage-per-shot", 1);
        m.animFor(p, "rush", shots * 2);
        m.standForward(p, shots * 2 + 6);
        m.sound(p, "minigun");
        bar(p, "VOLA VOLA VOLA!");
        Random r = new Random();
        for (int i = 0; i < shots; i++) later(i * 2L, () -> {
            if (!online(p) || ts.isFrozen(p)) return;
            Vec3 v = p.getLookAngle().add(r.nextGaussian() * .03, r.nextGaussian() * .03, r.nextGaussian() * .03).scale(3);
            a.shoot(p, StandsMod.look(Items.IRON_NUGGET), v, false, dmg, null);
        });
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        m.sound(p, "bomb");
        double radius = cfg.getDouble("radius", 3), dmg = cfg.getDouble("damage", 7);
        a.shoot(p, StandsMod.look(Items.TNT), p.getLookAngle().scale(1.2), true, 0, (loc, le) -> a.blast(p, loc, radius, dmg, 0.8));
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        m.anim(p, "cast");
        m.sound(p, "sonar");
        int n = a.reveal(p, cfg.getDouble("radius", 30), cfg.getDouble("duration", 6));
        bar(p, "Radar: " + n + " target" + (n == 1 ? "" : "s"));
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        m.anim(p, "dash");
        m.standForward(p, 40);
        m.sound(p, "minigun");
        title(p, "AEROSMITH!", "Strafing run");
        int blasts = cfg.getInt("blasts", 8);
        double dmg = cfg.getDouble("damage-per-blast", 3);
        Vec3 start = p.position().add(0, 0.5, 0);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() > 0 ? dir.normalize() : dev.luci.stands.Fx.flatDir(p);
        for (int i = 0; i < blasts; i++) {
            Vec3 at = start.add(dir.scale(2 + i * 2));
            later(4L + i * 3L, () -> a.blast(p, at, 2.2, dmg, 0.3));
        }
        return true;
    }
}
