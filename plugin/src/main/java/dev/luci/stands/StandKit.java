package dev.luci.stands;

import org.bukkit.entity.Player;

/** One stand's abilities. */
public interface StandKit {
    String id();
    void use(Player p, StandManager.Slot slot);
    /** Short label for the HUD. */
    String label(Player p, StandManager.Slot slot);
    /** Cooldown key shown on the HUD for this slot (can change, e.g. during time stop). */
    String cooldownKey(Player p, StandManager.Slot slot);
    /** False for item stands (Anubis, Emperor, ...) that have no model following the user. */
    default boolean hasModel() { return true; }
    default void onSummon(Player p) { }
    default void onUnsummon(Player p) { }
}
