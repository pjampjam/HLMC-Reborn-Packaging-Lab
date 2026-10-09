package holylois;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AudioPlayer's /audioplayer url (AudioUrlMixin): https only, never to local or internal addresses (also after
 * redirects), one new download per player every 20 seconds, a size cap and timeouts, and a log of who added what
 * (world/holylois/audio-urls.log). Operators skip the cooldown only.
 */
public final class AudioUrlGuard {
    private AudioUrlGuard() {}
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    static final long COOLDOWN_MS = 20_000;
    static final int MAX_REDIRECTS = 5;
    private static final Map<UUID, Long> lastDownload = new ConcurrentHashMap<>();
    private static Path logFile;

    static void load(Path worldRoot) { logFile = worldRoot.resolve("holylois/audio-urls.log"); }

    /** Before AudioPlayer reads the address: throws with the message the player sees. */
    public static void check(ServerPlayer player, String address) {
        URI uri = parse(address);
        long now = System.currentTimeMillis();
        boolean op = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        long wait = op ? 0 : waitMs(lastDownload.get(player.getUUID()), now);
        if (wait > 0) throw new IllegalArgumentException("Please wait " + (wait + 999) / 1000 + " s before adding another song.");
        requirePublic(uri.getHost());
        lastDownload.put(player.getUUID(), now);
        record(player, uri.toString());
    }

    /** Replaces AudioPlayer's unbounded download: every redirect is checked again, the body stops at the size limit. */
    public static byte[] download(String address, long maxBytes) throws IOException {
        URI uri = parse(address);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            requirePublic(uri.getHost());
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "AudioPlayer");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Broken redirect");
                uri = parse(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) throw new IOException("The link answered with HTTP " + status);
            if (connection.getContentLengthLong() > maxBytes) throw new IOException(tooBig(maxBytes));
            try (InputStream in = connection.getInputStream()) {
                var out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) != -1; ) {
                    if (out.size() + read > maxBytes) throw new IOException(tooBig(maxBytes));
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
        throw new IOException("Too many redirects");
    }

    static URI parse(String address) {
        URI uri;
        try { uri = new URI(address.strip()); } catch (Exception error) { throw new IllegalArgumentException("That is not a valid link."); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Only https:// links to an mp3 or wav file work.");
        return uri;
    }

    static long waitMs(Long last, long now) { return last == null ? 0 : Math.max(0, last + COOLDOWN_MS - now); }

    private static void requirePublic(String host) {
        InetAddress[] addresses;
        try { addresses = InetAddress.getAllByName(host); } catch (Exception error) { throw new IllegalArgumentException("That website could not be found."); }
        for (InetAddress address : addresses)
            if (!isPublic(address)) throw new IllegalArgumentException("That link points to a private address and is blocked.");
    }

    /** False for loopback, private, link-local (cloud metadata), carrier-grade NAT, unique-local, multicast and unspecified. */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff, second = b[1] & 0xff;
            return first != 0 && !(first == 100 && second >= 64 && second < 128) && !(first == 192 && second == 0 && (b[2] & 0xff) == 0)
                && !(first == 198 && (second == 18 || second == 19)) && first < 224;
        }
        if ((b[0] & 0xfe) == 0xfc) return false; // fc00::/7 unique local
        boolean mapped = true;
        for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
        if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            try { return isPublic(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]})); } catch (Exception error) { return false; }
        }
        return true;
    }

    private static String tooBig(long maxBytes) { return "That file is bigger than " + maxBytes / 1_000_000 + " MB."; }

    private static void record(ServerPlayer player, String url) {
        String line = Instant.now() + " " + player.getGameProfile().name() + " " + player.getUUID() + " " + url;
        LOG.info("Holy Lois audio link: {}", line);
        if (logFile == null) return;
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) { LOG.warn("Cannot write the audio link log", error); }
    }
}
