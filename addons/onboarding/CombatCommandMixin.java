package holylois.mixins;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.ParseResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teleport commands (ours and Essential Commands') are refused while a player is in combat (holylois.CombatTag). */
@Mixin(value=Commands.class, remap=false)
public abstract class CombatCommandMixin {
    @Inject(method="performCommand", at=@At("HEAD"), cancellable=true, remap=false)
    private void holyLoisNoTeleportInCombat(ParseResults<CommandSourceStack> parsed, String command, CallbackInfo callback) {
        var player = parsed.getContext().getSource().getPlayer();
        if (player != null && holylois.CombatTag.refuse(player, command)) callback.cancel();
    }
}
