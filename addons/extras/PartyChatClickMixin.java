package holylois.boombox.mixins;

import holylois.boombox.PartyHud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The HUD becomes clickable only while chat provides a cursor. */
@Mixin(ChatScreen.class)
public abstract class PartyChatClickMixin {
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true,require=1)
    private void holylois$partyClick(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> ci){
        if(PartyHud.click(event.x(),event.y(),event.button()))ci.setReturnValue(true);
    }
}
