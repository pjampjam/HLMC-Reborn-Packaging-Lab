package holylois.auth;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.RequestSecretPacket;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/** Retry the mod's own handshake after login; never fabricate connection state or change audio settings. */
final class VoiceRecovery {
    private static boolean ready;
    private static long since, lastRequest;
    private static int attempts;
    static void ready(boolean value) {
        if (ready == value) return;
        ready = value; since = System.currentTimeMillis(); attempts = 0; lastRequest = 0;
    }
    static boolean connected() {
        var client = ClientManager.getClient();
        return client != null && client.getConnection() != null && client.getConnection().isConnected();
    }
    static Component notice() {
        if (!ready || connected() || System.currentTimeMillis() - since < 3_000) return null;
        return Component.translatable(System.currentTimeMillis() - since < 20_000 ? "holylois.voice.connecting" : "holylois.voice.unavailable");
    }
    private static int request(boolean manual) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!ready || mc.getConnection() == null || connected()) return 0;
        long now = System.currentTimeMillis();
        if (now - lastRequest < 3_000) return 0;
        var client = ClientManager.getClient();
        var connection = client == null ? null : client.getConnection();
        // An active native connection owns its authentication/retries. Do not repeatedly replace it.
        if (!manual && connection != null && connection.isAlive()) return 0;
        ClientServerNetManager.sendToServer(new RequestSecretPacket(Voicechat.COMPATIBILITY_VERSION));
        lastRequest = now; attempts++; return 1;
    }
    static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ready(false));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!ready || mc.player == null || mc.getConnection() == null || attempts >= 3) return;
            long wait = attempts == 0 ? 2_000 : attempts == 1 ? 7_000 : 17_000;
            if (System.currentTimeMillis() - since >= wait) request(false);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommands.literal("voicefix").executes(c -> {
            int sent = request(true);
            c.getSource().sendFeedback(Component.translatable(sent > 0 ? "holylois.voice.retry" : connected() ? "holylois.voice.connected" : "holylois.voice.wait"));
            return sent;
        })));
    }
}
