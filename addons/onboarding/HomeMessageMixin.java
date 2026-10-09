package holylois.mixins;

import com.fibermc.essentialcommands.text.TextFormatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native timing and completion messages, shortening only the default home's label. */
@Pseudo
@Mixin(targets="com.fibermc.essentialcommands.text.ECTextImpl",remap=false)
public abstract class HomeMessageMixin {
    @Inject(method="buildText(Ljava/lang/String;Lcom/fibermc/essentialcommands/text/TextFormatType;Lcom/fibermc/essentialcommands/types/IStyleProvider;[Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;",at=@At("RETURN"),cancellable=true,require=1,remap=false)
    private void holylois$defaultHome(String key,TextFormatType format,com.fibermc.essentialcommands.types.IStyleProvider style,Component[] args,CallbackInfoReturnable<MutableComponent> ci){
        if(key.equals("cmd.home.location_name") && args.length==1 && args[0].getString().equalsIgnoreCase("home"))
            ci.setReturnValue(Component.literal("home").setStyle(ci.getReturnValue().getStyle()));
    }
}
