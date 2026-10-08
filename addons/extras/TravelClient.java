package holylois.boombox;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Explicit menus release the cursor; ordinary gameplay never does. */
public final class TravelClient {
    static HomeState homes=new HomeState(false,3,0,java.util.List.of());
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HomeState.TYPE,(payload,context)->{
            homes=payload;
            if(context.client().gui.screen() instanceof HomesScreen screen)screen.update(payload);
            else if(payload.open())context.client().gui.setScreen(new HomesScreen(payload));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->homes=new HomeState(false,3,0,java.util.List.of()));
        ClientPlayNetworking.registerGlobalReceiver(MenuOpen.TYPE,(payload,context)->context.client().gui.setScreen(payload.menu()==1?new PartyScreen():new RallyScreen()));
    }
    static boolean send(int action,String name,String next,long revision) {
        if(!ClientPlayNetworking.canSend(TravelIntent.TYPE)) {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("holylois.travel.unavailable"));return false;
        }
        ClientPlayNetworking.send(new TravelIntent(action,name,next,revision));return true;
    }
    static void openHomes(){send(TravelIntent.HOMES,"","",0);}
}
