package holylois.boombox;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death loot goes back where it was (owner 2026-10-10). When a player dies, each dropped stack remembers its inventory slot;
 * when that same player picks it up again and the slot is empty, it lands there (hotbar, armor and off-hand included).
 * Anyone else, or a taken slot, gets the normal pickup. Kept in memory only, so a server restart forgets it.
 */
public final class DeathSlots {
    private DeathSlots() {}
    private record Slot(UUID owner, int index, long at) {}
    private static final Map<UUID, Slot> slots = new ConcurrentHashMap<>();
    private static final long FORGET_MS = 60 * 60 * 1000L;

    /** A stack from inventory slot index of owner was dropped as item. */
    public static void dropped(Player owner, int index, ItemEntity item) {
        if (item == null || owner.level().isClientSide()) return;
        long now = System.currentTimeMillis();
        slots.values().removeIf(slot -> now - slot.at() > FORGET_MS);
        slots.put(item.getUUID(), new Slot(owner.getUUID(), index, now));
    }

    /** Armor or off-hand stack dropped from the equipment: find which inventory slot held exactly this stack. */
    public static void droppedEquipment(Player owner, ItemStack stack, ItemEntity item) {
        for (var entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
            EquipmentSlot slot = entry.getValue();
            if (owner.getItemBySlot(slot) == stack) { dropped(owner, entry.getIntKey(), item); return; }
        }
    }

    /** On pickup: puts the stack into its old slot if it was this player's and the slot is empty. True when it did. */
    public static boolean restore(ItemEntity item, Player player, ItemStack stack) {
        var slot = slots.get(item.getUUID());
        if (slot == null || !slot.owner().equals(player.getUUID())) return false;
        slots.remove(item.getUUID());
        var inventory = player.getInventory();
        if (slot.index() < 0 || slot.index() >= inventory.getContainerSize() || !inventory.getItem(slot.index()).isEmpty()) return false;
        inventory.setItem(slot.index(), stack.copy());
        stack.setCount(0);
        return true;
    }

    /** Remembered stacks (gametest). */
    public static int remembered() { return slots.size(); }
}
