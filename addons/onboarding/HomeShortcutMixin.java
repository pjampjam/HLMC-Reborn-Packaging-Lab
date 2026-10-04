package holylois.mixins;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plain /home: the only home, otherwise the one named "home" (any capitals). With several homes and none named home, the
 * player gets a clickable list and a hint to name one.
 */
@Pseudo
@Mixin(targets = "com.fibermc.essentialcommands.commands.HomeCommand", remap = false)
public abstract class HomeShortcutMixin {
    @Inject(method = "getSoleHomeName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void holyLoisMainHome(PlayerData data, CallbackInfoReturnable<String> callback) throws CommandSyntaxException {
        var names = data.getHomeNames();
        if (names.isEmpty()) return;
        if (names.size() == 1) { callback.setReturnValue(names.iterator().next()); return; }
        for (String name : names) if (name.equalsIgnoreCase("home")) { callback.setReturnValue(name); return; }
        var text = Component.literal("You have " + names.size() + " homes. Pick one: ").withStyle(ChatFormatting.YELLOW);
        names.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(name -> text.append(Component.literal("[" + name + "] ")
            .withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand("/home tp " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Teleport to " + name))))));
        text.append(Component.literal("\nTip: name your main one \"home\" (/home set home) and plain /home goes there.").withStyle(ChatFormatting.GRAY));
        throw new CommandSyntaxException(new SimpleCommandExceptionType(text), text);
    }
}
