package holylois.boombox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Remembers whether a boombox plays nearby (MusicManagerMixin pauses the game music) and PvP deaths (DeathpointMixin); starts the client-side helpers. */
public final class BoomboxClient implements ClientModInitializer {
    public static volatile boolean near;
    /** Until when (epoch ms) the next minimap death marker is skipped; a stale signal expires on its own. */
    public static volatile long skipDeathpointUntil;
    /** Death loot the server reported gone; the minimap marker there is removed (XaeroDeathpoints). */
    private static final java.util.List<DeathLootGone> lootGone = new java.util.ArrayList<>();
    private static int lootGoneTries;

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BoomboxNear.TYPE, (payload, context) -> near = payload.near());
        ClientPlayNetworking.registerGlobalReceiver(PvpDeath.TYPE, (payload, context) -> skipDeathpointUntil = System.currentTimeMillis() + 60_000);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            // Handled on a later tick: right after joining, Xaero's session may not be ready (retries for about a minute).
            ClientPlayNetworking.registerGlobalReceiver(DeathLootGone.TYPE, (payload, context) -> { lootGone.add(payload); lootGoneTries = 60; });
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (lootGone.isEmpty() || client.level == null || client.level.getGameTime() % 20 != 0) return;
                lootGone.removeIf(gone -> XaeroDeathpoints.remove(gone.dimension(), gone.pos()) >= 0);
                if (--lootGoneTries <= 0) lootGone.clear();
            });
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { near = false; skipDeathpointUntil = 0; lootGone.clear(); });
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("firstperson")) FirstPersonFixes.register();
        ToolSwap.register();
        ShaderLights.register();
        PackCheckClient.register();
        PartyHud.register();
        TravelClient.register();
        AccountScreen.register();
        RareCatchHud.register();
        FishLook.register();
        FishLook.registerOffhandGhost();
        HeldSwing.register();
        ChestReplay.register();
        PackLoadingBar.register();
        BoomboxPulse.register();
        Capture.register();
        RelicTooltips.register();
        BoomboxUseGuard.register();
    }
}
