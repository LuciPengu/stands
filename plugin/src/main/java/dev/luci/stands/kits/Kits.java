package dev.luci.stands.kits;

import dev.luci.stands.Abilities;
import dev.luci.stands.StandKit;
import dev.luci.stands.StandManager;
import dev.luci.stands.TimeStopManager;

import java.util.List;

/** Every ported stand. Add new kits here. */
public final class Kits {
    private Kits() { }
    public static List<StandKit> all(StandManager m, TimeStopManager ts, Abilities a) {
        return List.of(
                new TheWorld(m, ts, a), new StarPlatinum(m, ts, a), new CrazyDiamond(m, ts, a),
                new StickyFingers(m, ts, a), new KingCrimson(m, ts, a), new SilverChariot(m, ts, a),
                new MagiciansRed(m, ts, a), new HierophantGreen(m, ts, a), new Aerosmith(m, ts, a),
                new TuskAct4(m, ts, a), new TheEmperor(m, ts, a), new WeatherReport(m, ts, a),
                new KillerQueen(m, ts, a), new TheHand(m, ts, a), new PurpleHaze(m, ts, a),
                new EchoesAct3(m, ts, a), new Metallica(m, ts, a), new HermitPurple(m, ts, a), new Anubis(m, ts, a),
                new GoldExperience(m, ts, a), new GoldExperienceRequiem(m, ts, a), new D4C(m, ts, a));
    }
}
