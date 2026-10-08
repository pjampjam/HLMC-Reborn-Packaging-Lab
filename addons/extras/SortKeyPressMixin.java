package holylois.boombox.mixins;

import holylois.boombox.SortKeyPress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyboardHandler.class)
public abstract class SortKeyPressMixin {
    @WrapMethod(method="keyPress",require=1)
    private void holylois$keyPress(long window,int action,KeyEvent event,Operation<Void> original){
        SortKeyPress.begin(event.key(),action,Minecraft.getInstance().gui.screen());
        try{original.call(window,action,event);}
        finally{SortKeyPress.end();}
    }
}
