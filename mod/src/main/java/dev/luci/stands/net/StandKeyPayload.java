package dev.luci.stands.net;

import dev.luci.stands.StandsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a stand key was pressed. 0-3 = ability slot (basic, skill, skill 2, ultimate), 4 = summon toggle. */
public record StandKeyPayload(int action) implements CustomPacketPayload {
    public static final int SUMMON = 4;
    public static final Type<StandKeyPayload> TYPE = new Type<>(StandsMod.id("key"));
    public static final StreamCodec<ByteBuf, StandKeyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, StandKeyPayload::action, StandKeyPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
