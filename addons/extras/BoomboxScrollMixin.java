package holylois.boombox.mixins;

import holylois.boombox.Boombox;
import holylois.boombox.BoomboxVolume;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + mouse wheel sets the volume of the boombox in the crosshair, or of the one in hand, instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class BoomboxScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisBoomboxVolume(long window, double xOffset, double yOffset, CallbackInfo callback) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null || !player.isShiftKeyDown() || yOffset == 0) return;
        if (!ClientPlayNetworking.canSend(BoomboxVolume.TYPE)) return;
        int step = yOffset > 0 ? 1 : -1;
        if (mc.hitResult instanceof BlockHitResult hit && mc.level.getBlockState(hit.getBlockPos()).is(Boombox.BLOCK)) {
            ClientPlayNetworking.send(new BoomboxVolume(hit.getBlockPos(), step));
            callback.cancel();
        } else if (player.getMainHandItem().is(Boombox.ITEM) || player.getOffhandItem().is(Boombox.ITEM)) {
            ClientPlayNetworking.send(new BoomboxVolume(null, step));
            callback.cancel();
        }
    }
}
