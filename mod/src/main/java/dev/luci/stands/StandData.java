package dev.luci.stands;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/** Per-player saved data. Survives death and relogs (copyOnDeath + persistent). */
public final class StandData {
    private StandData() { }

    public static final AttachmentType<String> STAND = AttachmentRegistry.create(StandsMod.id("stand"),
            b -> b.persistent(Codec.STRING).copyOnDeath());
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(StandsMod.id("level"),
            b -> b.persistent(Codec.INT).copyOnDeath());
    public static final AttachmentType<Double> XP = AttachmentRegistry.create(StandsMod.id("xp"),
            b -> b.persistent(Codec.DOUBLE).copyOnDeath());
    public static final AttachmentType<Boolean> PVP = AttachmentRegistry.create(StandsMod.id("pvp"),
            b -> b.persistent(Codec.BOOL).copyOnDeath());
    public static final AttachmentType<Integer> WINS = AttachmentRegistry.create(StandsMod.id("duel_wins"),
            b -> b.persistent(Codec.INT).copyOnDeath());
    public static final AttachmentType<Integer> LOSSES = AttachmentRegistry.create(StandsMod.id("duel_losses"),
            b -> b.persistent(Codec.INT).copyOnDeath());

    static void init() { }

    public static String stand(Player p) { return p.getAttached(STAND); }
    public static int level(Player p) { Integer l = p.getAttached(LEVEL); return l == null ? 1 : l; }
    public static double xp(Player p) { Double x = p.getAttached(XP); return x == null ? 0 : x; }
    public static int wins(Player p) { Integer w = p.getAttached(WINS); return w == null ? 0 : w; }
    public static int losses(Player p) { Integer l = p.getAttached(LOSSES); return l == null ? 0 : l; }
}
