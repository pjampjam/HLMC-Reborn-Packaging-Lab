package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** One press that eats or drinks must not become an off-hand radio click afterward. */
public final class BoomboxUseGuard {
    private static boolean consumedWhileHeld;
    public static void released(){consumedWhileHeld=false;}
    static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.player==null || !mc.options.keyUse.isDown()){consumedWhileHeld=false;return;}
            if(mc.player.isUsingItem()){
                var action=mc.player.getUseItem().getUseAnimation();
                if(action==net.minecraft.world.item.ItemUseAnimation.EAT || action==net.minecraft.world.item.ItemUseAnimation.DRINK)consumedWhileHeld=true;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->consumedWhileHeld=false);
    }
    public static boolean blockFollowup(){
        var mc=Minecraft.getInstance();
        if(!consumedWhileHeld || mc.player==null || mc.player.isUsingItem() || !mc.options.keyUse.isDown()
                || !mc.player.getOffhandItem().is(Boombox.ITEM))return false;
        var main=mc.player.getMainHandItem();
        var food=main.get(net.minecraft.core.component.DataComponents.FOOD);
        if(food!=null && mc.player.canEat(food.canAlwaysEat()))return false;
        if(main.getUseAnimation()==net.minecraft.world.item.ItemUseAnimation.DRINK)return false;
        return true;
    }
}
