package holylois.boombox.mixins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Invisible item frames are structure decoration (dungeon loot shelves): punching one still drops the item inside, but the frame
 * itself never drops as an item frame (owner 2026-10-10). Survival players cannot make invisible frames, so nothing is lost.
 */
@Mixin(ItemFrame.class)
public abstract class InvisibleFrameDropMixin {
    @ModifyVariable(method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean holyLoisKeepInvisibleFrame(boolean dropFrame, ServerLevel level, Entity breaker) {
        return dropFrame && !((Entity) (Object) this).isInvisible();
    }
}
