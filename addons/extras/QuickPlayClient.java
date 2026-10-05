package holylois.boombox;

import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Quick Play: the Holy Lois launcher leaves holylois-quickplay.json in the game folder right before it opens the player's Minecraft
 * launcher. On the first title screen of this run the game reads it once, deletes it and joins the server, for premium and offline
 * accounts alike. A note older than three minutes, or for any other address than ours, is ignored.
 */
public final class QuickPlayClient implements ClientModInitializer {
    static final String FILE = "holylois-quickplay.json";
    static final long MAX_AGE_SECONDS = 180;
    private static boolean done;

    record Note(String address) {}

    @Override public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (done || !(screen instanceof TitleScreen)) return;
            done = true;
            Note note = take(FabricLoader.getInstance().getGameDir().resolve(FILE), System.currentTimeMillis() / 1000);
            if (note == null) return;
            Boombox.LOG.info("Quick Play: joining {}", note.address());
            client.execute(() -> ConnectScreen.startConnecting(screen, client, ServerAddress.parseString(note.address()),
                new ServerData("Holy Lois: Reborn", note.address(), ServerData.Type.OTHER), true, null));
        });
    }

    /** Reads and deletes the note; null when there is none, it is stale or it points somewhere else. */
    static Note take(Path file, long nowSeconds) {
        try {
            if (!Files.exists(file)) return null;
            String json = Files.readString(file);
            Files.deleteIfExists(file);
            return parse(json, nowSeconds);
        } catch (Exception error) { return null; }
    }

    static Note parse(String json, long nowSeconds) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            String address = root.get("address").getAsString().strip();
            long created = root.get("created").getAsLong();
            if (!allowed(address) || nowSeconds - created > MAX_AGE_SECONDS || created - nowSeconds > 60) return null;
            return new Note(address);
        } catch (Exception error) { return null; }
    }

    static boolean allowed(String address) { return address.matches("(?i)(play|mc)\\.holylois\\.com(:\\d{1,5})?"); }
}
