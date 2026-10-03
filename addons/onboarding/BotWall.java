package holylois;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Read-only view of config/holylois-botwall.json, written by the server's SSH bot-wall collector. */
public final class BotWall {
    private static final Path FILE = Path.of("config", "holylois-botwall.json");
    private static final Gson JSON = new Gson();
    private static Data data = new Data();
    private static long stamp = Long.MIN_VALUE;

    public static final class Entry { public String name = ""; public int count; public String lastSeen = ""; }
    public static final class Data {
        public int today, allTime, banned;
        public List<String> latest = List.of();
        public List<Entry> top = List.of();
    }

    private BotWall() {}

    static Data parse(String json) {
        Data parsed = JSON.fromJson(json, Data.class);
        if (parsed == null) return new Data();
        if (parsed.latest == null) parsed.latest = List.of();
        if (parsed.top == null) parsed.top = List.of();
        return parsed;
    }

    /** Usernames come from strangers: keep them short and printable, never formatting codes. */
    static String clean(String name) {
        String plain = name == null ? "" : name.replaceAll("[^A-Za-z0-9_.@-]", "");
        return plain.length() > 16 ? plain.substring(0, 16) : plain;
    }

    static String latest(Data value) {
        var names = value.latest.stream().map(BotWall::clean).filter(n -> !n.isEmpty()).limit(3).toList();
        return names.isEmpty() ? "nobody yet" : String.join(", ", names);
    }

    public static synchronized Data get() {
        try {
            if (!Files.exists(FILE)) return data;
            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified != stamp) { data = parse(Files.readString(FILE, StandardCharsets.UTF_8)); stamp = modified; }
        } catch (java.io.IOException | JsonParseException ignored) {
            // Keep the last good snapshot; the collector rewrites the file atomically.
        }
        return data;
    }
}
