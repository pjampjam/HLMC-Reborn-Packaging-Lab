package holylois.mixins;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teleport commands (ours and Essential Commands') are refused while a player is in combat (holylois.CombatTag). */
@Mixin(value=Commands.class, remap=false)
public abstract class CombatCommandMixin {
    @Inject(method="performPrefixedCommand", at=@At("HEAD"), cancellable=true, remap=false)
    private void holyLoisNoTeleportInCombat(CommandSourceStack source, String command, CallbackInfo callback) {
        try {
            if (source.getPlayer() != null && holylois.CombatTag.refuse(source.getPlayer(), command)) callback.cancel();
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("HolyLois").error("Holy Lois combat command check failed", error);
        }
    }
}
