package holylois.boombox;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.function.Predicate;

/**
 * When the tool in your hand breaks, a spare of the same kind takes its place: never an enchanted one (those stay for you to
 * pick by hand), the lowest material first, then the most worn. A soft chime and a short line say what happened.
 * Inventory Profiles Next is told to leave these tools alone (IpnToolSkipMixin), so only one system ever acts.
 */
public final class ToolSwap {
    private static final java.util.List<Predicate<ItemStack>> KINDS = java.util.List.of(
        stack -> stack.is(ItemTags.PICKAXES), stack -> stack.is(ItemTags.AXES), stack -> stack.is(ItemTags.SHOVELS),
        stack -> stack.is(ItemTags.HOES), stack -> stack.is(ItemTags.SWORDS), stack -> stack.is(Items.SHEARS));

    private static ItemStack last = ItemStack.EMPTY;
    private static int lastSlot = -1;
    private static int activeTicks;
    private static long lastNotice;
    /** Nanotime of the last keyboard sort by Inventory Profiles Next (read by ReiSortGuardMixin). */
    public static volatile long sortRanAt;

    private ToolSwap() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ToolSwap::tick);
    }

    /** True for the tools this system manages, so Inventory Profiles Next skips them. */
    public static boolean managed(Item item) {
        var stack = item.getDefaultInstance();
        return stack.isDamageableItem() && kind(stack) != null;
    }

    private static Predicate<ItemStack> kind(ItemStack stack) {
        for (var kind : KINDS) if (kind.test(stack)) return kind;
        return null;
    }

    private static int remaining(ItemStack stack) { return stack.getMaxDamage() - stack.getDamageValue(); }

    private static void tick(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) { last = ItemStack.EMPTY; lastSlot = -1; return; }
        if (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) activeTicks = 20; else if (activeTicks > 0) activeTicks--;
        int selected = player.getInventory().getSelectedSlot();
        var held = player.getInventory().getItem(selected);
        if (!held.isEmpty()) {
            boolean tool = held.isDamageableItem() && kind(held) != null;
            last = tool ? held.copy() : ItemStack.EMPTY;
            lastSlot = selected;
            return;
        }
        if (last.isEmpty() || lastSlot != selected) { last = ItemStack.EMPTY; return; }
        var broken = last;
        last = ItemStack.EMPTY;
        // A broken tool was almost used up, you were using it, nothing is open and it is not simply elsewhere in the inventory.
        if (remaining(broken) > Math.max(3, broken.getMaxDamage() / 20) || activeTicks == 0) return;
        if (mc.gui.screen() != null || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) return;
        var menu = player.inventoryMenu;
        for (int slot = 9; slot < 46; slot++) {
            var stack = menu.slots.get(slot).getItem();
            if (stack.getItem() == broken.getItem() && stack.getDamageValue() == broken.getDamageValue()) return;
        }
        var kind = kind(broken);
        int best = -1;
        for (int slot = 9; slot < 45; slot++) {
            if (slot == 36 + selected) continue;
            var stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.isEnchanted() || !kind.test(stack)) continue;
            if (remaining(stack) <= Math.max(1, stack.getMaxDamage() / 20)) continue; // almost used up spares are skipped
            if (best < 0 || cheaper(stack, menu.slots.get(best).getItem())) best = slot;
        }
        if (best < 0) return;
        var chosen = menu.slots.get(best).getItem();
        var name = chosen.getHoverName().copy().withStyle(ChatFormatting.YELLOW);
        mc.gameMode.handleContainerInput(menu.containerId, best, selected, ContainerInput.SWAP, player);
        long now = System.currentTimeMillis();
        if (now - lastNotice > 1500) {
            lastNotice = now;
            player.sendOverlayMessage(Component.literal("⛏ Switched to ").withStyle(ChatFormatting.GOLD).append(name));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 0.45f));
        }
    }

    /** Lowest material first (smaller durability), then the most worn. */
    static boolean cheaper(ItemStack a, ItemStack b) {
        if (a.getMaxDamage() != b.getMaxDamage()) return a.getMaxDamage() < b.getMaxDamage();
        return remaining(a) < remaining(b);
    }
}
