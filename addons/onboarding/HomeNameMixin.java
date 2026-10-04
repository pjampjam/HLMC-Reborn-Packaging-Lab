package holylois.mixins;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Home names ignore capitals: "home" and "Home" cannot be two different homes. */
@Pseudo
@Mixin(targets = "com.fibermc.essentialcommands.playerdata.PlayerData", remap = false)
public abstract class HomeNameMixin {
    @Inject(method = "addHome", at = @At("HEAD"), remap = false, require = 0)
    private void holyLoisOneSpelling(String name, com.fibermc.essentialcommands.types.MinecraftLocation location, CallbackInfo callback)
            throws CommandSyntaxException {
        for (String existing : ((PlayerData) (Object) this).getHomeNames()) {
            if (!existing.equals(name) && existing.equalsIgnoreCase(name)) {
                var text = Component.literal("You already have a home called \"" + existing + "\". Use that name, or /home delete "
                    + existing + " first.").withStyle(ChatFormatting.RED);
                throw new CommandSyntaxException(new SimpleCommandExceptionType(text), text);
            }
        }
    }
}
