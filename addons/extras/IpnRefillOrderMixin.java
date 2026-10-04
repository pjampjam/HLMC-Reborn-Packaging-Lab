package holylois.boombox.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/**
 * When a tool breaks, Inventory Profiles Next prefers the best spare. Holy Lois wears out the cheap ones first: same tool group,
 * unenchanted before enchanted, then the lowest material, then the most worn. A soft chime and a short line replace IPN's alert.
 * IPN's result index is the inventory menu slot minus 9 (main inventory, then hotbar).
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.autorefill.AutoRefillHandler$ItemSlotMonitor$Companion", remap = false)
public abstract class IpnRefillOrderMixin {
    @Unique private static final List<TagKey<Item>> GROUPS = List.of(ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS);
    @Unique private static long lastNotice;

    @Inject(method = "findCorrespondingSlot", at = @At("RETURN"), cancellable = true, remap = false)
    private void holyLoisCheapestFirst(CallbackInfoReturnable<Integer> callback) {
        Integer picked = callback.getReturnValue();
        var player = Minecraft.getInstance().player;
        if (picked == null || player == null) return;
        var slots = player.inventoryMenu.slots;
        if (picked + 9 < 9 || picked + 9 >= 45) return;
        var chosen = slots.get(picked + 9).getItem();
        TagKey<Item> group = GROUPS.stream().filter(chosen::is).findFirst().orElse(null);
        if (group == null || !chosen.isDamageableItem()) return;
        int hand = 36 + player.getInventory().getSelectedSlot(), best = picked + 9;
        for (int i = 9; i < 45; i++) {
            var stack = slots.get(i).getItem();
            if (i == hand || stack.isEmpty() || !stack.isDamageableItem() || !stack.is(group)) continue;
            int left = stack.getMaxDamage() - stack.getDamageValue();
            if (left <= Math.max(1, stack.getMaxDamage() / 20)) continue; // almost broken spares are skipped
            if (holyLoisCheaper(stack, slots.get(best).getItem())) best = i;
        }
        callback.setReturnValue(best - 9);
        long now = System.currentTimeMillis();
        if (now - lastNotice > 1500) {
            lastNotice = now;
            player.sendOverlayMessage(Component.literal("⛏ Swapped in ").withStyle(ChatFormatting.GOLD)
                .append(slots.get(best).getItem().getHoverName().copy().withStyle(ChatFormatting.YELLOW)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 0.45f));
        }
    }

    @Unique
    private static boolean holyLoisCheaper(ItemStack a, ItemStack b) {
        if (a.isEnchanted() != b.isEnchanted()) return !a.isEnchanted();
        if (a.getMaxDamage() != b.getMaxDamage()) return a.getMaxDamage() < b.getMaxDamage();
        return a.getMaxDamage() - a.getDamageValue() < b.getMaxDamage() - b.getDamageValue();
    }
}
