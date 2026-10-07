package holylois.boombox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Remembers whether a boombox plays nearby (MusicManagerMixin pauses the game music) and PvP deaths (DeathpointMixin); starts the client-side helpers. */
public final class BoomboxClient implements ClientModInitializer {
    public static volatile boolean near;
    /** Until when (epoch ms) the next minimap death marker is skipped; a stale signal expires on its own. */
    public static volatile long skipDeathpointUntil;

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BoomboxNear.TYPE, (payload, context) -> near = payload.near());
        ClientPlayNetworking.registerGlobalReceiver(PvpDeath.TYPE, (payload, context) -> skipDeathpointUntil = System.currentTimeMillis() + 60_000);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { near = false; skipDeathpointUntil = 0; });
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("firstperson")) FirstPersonFixes.register();
        ToolSwap.register();
        ShaderLights.register();
        PackCheckClient.register();
        PartyHud.register();
    }
}
