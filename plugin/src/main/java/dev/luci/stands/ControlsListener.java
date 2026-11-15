package dev.luci.stands;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Vanilla-client controls, all while holding the Stand Disc:
 *   Q = summon/unsummon · RMB = basic · Sneak+RMB = skill · F = skill 2 · Sneak+F = ultimate
 */
public final class ControlsListener implements Listener {
    private final StandManager stands;
    public ControlsListener(StandManager stands) { this.stands = stands; }

    private boolean holdingDisc(Player p) { return stands.isItem(p.getInventory().getItemInMainHand(), "disc"); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (!stands.isItem(e.getItemDrop().getItemStack(), "disc")) return;
        e.setCancelled(true);
        stands.toggle(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!holdingDisc(p)) return;
        e.setCancelled(true);
        stands.use(p, p.isSneaking() ? StandManager.Slot.SKILL : StandManager.Slot.BASIC);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!holdingDisc(p)) return;
        e.setCancelled(true);
        stands.use(p, p.isSneaking() ? StandManager.Slot.ULTIMATE : StandManager.Slot.SKILL2);
    }
}
