package holylois.boombox.mixins;

import holylois.boombox.FishLook;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory, hotbar, chests and backpacks: a soft rarity glow behind a trophy fish and its weight in the top-left corner. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class FishSlotMixin {
    @Shadow public abstract void fill(int x0, int y0, int x1, int y1, int color);
    @Shadow public abstract void text(Font font, String text, int x, int y, int color, boolean shadow);
    @Shadow public abstract org.joml.Matrix3x2fStack pose();

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void holyLoisFishGlow(LivingEntity owner, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo info) {
        String rarity = FishLook.rarity(stack);
        int color = rarity.equals("uncommon") ? 0 : FishLook.color(rarity);
        if (color == 0) return;
        int rgb = color & 0xFFFFFF;
        fill(x, y, x + 16, y + 16, 0x22000000 | rgb);
        fill(x + 2, y + 2, x + 14, y + 14, 0x30000000 | rgb);
        fill(x + 4, y + 4, x + 12, y + 12, 0x30000000 | rgb);
    }

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void holyLoisFishWeight(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo info) {
        String label = FishLook.label(stack);
        if (label == null) return;
        int color = FishLook.color(FishLook.rarity(stack));
        var pose = pose();
        pose.pushMatrix();
        pose.translate(x + 0.5f, y + 0.5f);
        pose.scale(0.5f, 0.5f);
        text(font, label, 0, 0, color == 0 ? 0xFFF3F0E8 : color, true);
        pose.popMatrix();
    }
}
