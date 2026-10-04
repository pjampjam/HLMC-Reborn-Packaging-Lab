package holylois.mixins;

import holylois.PortalMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players return through the portal pair they used (see PortalMemory). Mobs and items keep vanilla linking. */
@Mixin(NetherPortalBlock.class)
public abstract class PortalReturnMixin {
    @Inject(method = "getPortalDestination", at = @At("RETURN"), cancellable = true)
    private void holyLoisSamePortal(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> callback) {
        if (!(entity instanceof ServerPlayer player) || callback.getReturnValue() == null) return;
        try { callback.setReturnValue(PortalMemory.route(player, level, pos, callback.getReturnValue())); }
        catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Portal memory skipped", error); }
    }
}
