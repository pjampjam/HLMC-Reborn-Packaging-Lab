package holylois;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Export only selected public texture URLs. SkinsRestorer remains the persistent skin authority. */
final class SkinStats {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static int ticks;
    static void register() {
        if (!FabricLoader.getInstance().isModLoaded("skinsrestorer")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> refresh(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (++ticks % 200 == 0) refresh(server); });
    }
    static String texture(String value) {
        try {
            if (value == null || value.length() > 16384) return null;
            var decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            var skin = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");
            return url.matches("https://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}") ? url : null;
        } catch (Exception ignored) { return null; }
    }
    static String selectedTexture(UUID id) throws ReflectiveOperationException {
        var apiType = Class.forName("net.skinsrestorer.api.SkinsRestorer");
        var api = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider").getMethod("get").invoke(null);
        var storage = apiType.getMethod("getPlayerStorage").invoke(api);
        var selected = (Optional<?>) Class.forName("net.skinsrestorer.api.storage.PlayerStorage")
                .getMethod("getSkinOfPlayer", UUID.class).invoke(storage, id);
        return selected.isEmpty() ? null : texture((String) Class.forName("net.skinsrestorer.api.property.SkinProperty")
                .getMethod("getValue").invoke(selected.get()));
    }
    private static void refresh(net.minecraft.server.MinecraftServer server) {
        if (!BUSY.compareAndSet(false, true)) return;
        Path destination = server.getWorldPath(LevelResource.ROOT).resolve("holylois/skin-textures.json");
        Thread.startVirtualThread(() -> {
            try {
                var apiType = Class.forName("net.skinsrestorer.api.SkinsRestorer");
                var api = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider").getMethod("get").invoke(null);
                var storage = apiType.getMethod("getPlayerStorage").invoke(api);
                var getSkin = Class.forName("net.skinsrestorer.api.storage.PlayerStorage").getMethod("getSkinOfPlayer", UUID.class);
                var getValue = Class.forName("net.skinsrestorer.api.property.SkinProperty").getMethod("getValue");
                Map<String, String> textures = new TreeMap<>();
                for (var entry : JsonParser.parseString(Files.readString(Path.of("usercache.json"))).getAsJsonArray()) {
                    UUID id = UUID.fromString(entry.getAsJsonObject().get("uuid").getAsString());
                    var skin = (Optional<?>) getSkin.invoke(storage, id);
                    if (skin.isPresent()) {
                        var url = texture((String)getValue.invoke(skin.get()));
                        if (url != null) textures.put(id.toString(), url);
                    }
                }
                String json = new Gson().toJson(textures) + "\n";
                if (Files.exists(destination) && Files.readString(destination).equals(json)) return;
                Files.createDirectories(destination.getParent());
                var temp = Files.createTempFile(destination.getParent(), "skin-textures-", ".tmp");
                Files.writeString(temp, json);
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception error) {
                org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Could not refresh public skin textures; previous cache retained", error);
            } finally { BUSY.set(false); }
        });
    }
}
