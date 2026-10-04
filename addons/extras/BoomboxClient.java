package holylois.boombox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Remembers whether a boombox plays nearby; MusicManagerMixin pauses the game music meanwhile. */
public final class BoomboxClient implements ClientModInitializer {
    public static volatile boolean near;

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BoomboxNear.TYPE, (payload, context) -> near = payload.near());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> near = false);
    }
}
