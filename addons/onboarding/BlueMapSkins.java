package holylois;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

/** Supply selected skins through BlueMap's supported provider, retaining its icon factory. */
final class BlueMapSkins {
    private static final Map<String, BufferedImage> images = Collections.synchronizedMap(new LinkedHashMap<>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > 64; }
    });
    static void register() {
        if (!FabricLoader.getInstance().isModLoaded("bluemap") || !FabricLoader.getInstance().isModLoaded("skinsrestorer")) return;
        try {
            var apiType = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            var pluginType = Class.forName("de.bluecolored.bluemap.api.plugin.Plugin");
            var providerType = Class.forName("de.bluecolored.bluemap.api.plugin.SkinProvider");
            Consumer<Object> enable = api -> {
                try {
                    Object plugin = apiType.getMethod("getPlugin").invoke(api);
                    Object fallback = pluginType.getMethod("getSkinProvider").invoke(plugin);
                    Object selected = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType}, (proxy, method, args) -> {
                        if (method.getName().equals("load")) {
                            try {
                                String url = SkinStats.selectedTexture((UUID) args[0]);
                                if (url != null) return Optional.of(image(url));
                            } catch (Exception ignored) { /* Keep native fallback available during a provider outage. */ }
                            return providerType.getMethod("load", UUID.class).invoke(fallback, args[0]);
                        }
                        if (method.getName().equals("toString")) return "Holy Lois selected skin provider";
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        throw new UnsupportedOperationException(method.getName());
                    });
                    pluginType.getMethod("setSkinProvider", providerType).invoke(plugin, selected);
                    org.slf4j.LoggerFactory.getLogger("HolyLois").info("Selected skin provider ready for BlueMap");
                } catch (ReflectiveOperationException error) {
                    org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Could not connect selected skins to BlueMap; native provider retained");
                }
            };
            apiType.getMethod("onEnable", Consumer.class).invoke(null, enable);
        } catch (ReflectiveOperationException error) {
            org.slf4j.LoggerFactory.getLogger("HolyLois").warn("BlueMap skin API unavailable; native provider retained");
        }
    }
    private static BufferedImage image(String url) throws IOException {
        if (!url.matches("https://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}")) throw new IOException("Invalid texture host");
        var cached = images.get(url); if (cached != null) return cached;
        var connection = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Skin unavailable");
            byte[] bytes;
            try (var input = connection.getInputStream()) { bytes = input.readNBytes(131073); }
            if (bytes.length > 131072) throw new IOException("Skin too large");
            var decoded = decode(bytes); images.put(url, decoded); return decoded;
        } finally { connection.disconnect(); }
    }
    static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid skin image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) != 64
                        || (reader.getHeight(0) != 64 && reader.getHeight(0) != 32)) throw new IOException("Invalid skin dimensions");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
