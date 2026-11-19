package dev.luci.stands;

import net.minecraft.server.level.ServerPlayer;

/** One stand's abilities. */
public interface StandKit {
    String id();
    void use(ServerPlayer p, StandManager.Slot slot);
    /** Short label for the HUD. */
    String label(ServerPlayer p, StandManager.Slot slot);
    /** Cooldown key shown on the HUD for this slot (can change, e.g. during time stop). */
    String cooldownKey(ServerPlayer p, StandManager.Slot slot);
    /** False for item stands (Anubis, Emperor, ...) that have no model following the user. */
    default boolean hasModel() { return true; }
    default void onSummon(ServerPlayer p) { }
    default void onUnsummon(ServerPlayer p) { }
}
