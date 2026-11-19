package dev.luci.stands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Chat, action bar and title helpers. */
public final class Txt {
    private Txt() { }

    public static MutableComponent of(String text, ChatFormatting... style) { return Component.literal(text).withStyle(style); }

    public static void bar(ServerPlayer p, Component c) { p.sendSystemMessage(c, true); }
    public static void bar(ServerPlayer p, String text, ChatFormatting... style) { bar(p, of(text, style)); }

    public static void msg(ServerPlayer p, Component c) { p.sendSystemMessage(c); }
    public static void msg(ServerPlayer p, String text, ChatFormatting... style) { msg(p, of(text, style)); }

    /** Title with times in ticks. */
    public static void title(ServerPlayer p, Component big, Component small, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(small == null ? Component.empty() : small));
        p.connection.send(new ClientboundSetTitleTextPacket(big));
    }

    /** Gold bold title + yellow subtitle, the look every stand uses. */
    public static void title(ServerPlayer p, String big, String small) {
        title(p, of(big, ChatFormatting.GOLD, ChatFormatting.BOLD), of(small == null ? "" : small, ChatFormatting.YELLOW), 0, 30, 8);
    }
}
