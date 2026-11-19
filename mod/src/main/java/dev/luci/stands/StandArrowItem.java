package dev.luci.stands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to pierce yourself: a random stand, or the arrow rejects you. Also evolves a level 3 Gold Experience. */
public class StandArrowItem extends Item {
    public StandArrowItem(Properties props) { super(props); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer p && StandsMod.stands().useArrow(p, player.getItemInHand(hand))) return InteractionResult.SUCCESS_SERVER;
        return InteractionResult.FAIL;
    }
}
