package holylois.mixins;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After vanilla and every mod registered their commands (see holylois.AdminCommands). */
@Mixin(value=Commands.class, remap=false)
public abstract class AdminCommandsMixin {
    @Inject(method="<init>", at=@At("TAIL"), remap=false)
    private void holyLoisLockAdminCommands(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo callback) {
        holylois.AdminCommands.lock(((Commands)(Object)this).getDispatcher());
    }
}
