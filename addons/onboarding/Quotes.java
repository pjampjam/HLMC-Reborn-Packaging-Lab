package holylois;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Quote of the day, shared by the welcome message and the %holylois:quote% placeholder. */
public final class Quotes {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Path FILE = Path.of("config", "holylois-quotes.txt");
    private static final String FALLBACK = "Build something worth coming back to. | Holy Lois";
    private static List<String> quotes = List.of(FALLBACK);
    private static long stamp = Long.MIN_VALUE;

    private Quotes() {}

    /** Lines are "quote | author". Blank lines and # comments are ignored. */
    static List<String> parse(List<String> lines) {
        var result = new ArrayList<String>();
        for (String line : lines) {
            String value = line.strip();
            if (value.isEmpty() || value.startsWith("#") || value.length() > 160) continue;
            result.add(value);
        }
        return result;
    }

    /** Everyone sees the same quote all day; the UTC date picks it. */
    static String pick(List<String> list, long epochDay) {
        return list.isEmpty() ? FALLBACK : list.get((int)Math.floorMod(epochDay, (long)list.size()));
    }

    static String text(String entry) {
        int split = entry.lastIndexOf('|');
        return (split < 0 ? entry : entry.substring(0, split)).strip();
    }

    static String author(String entry) {
        int split = entry.lastIndexOf('|');
        return split < 0 ? "" : entry.substring(split + 1).strip();
    }

    public static synchronized String today() {
        reload();
        return pick(quotes, LocalDate.now(ZoneOffset.UTC).toEpochDay());
    }

    private static void reload() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                try (InputStream defaults = Quotes.class.getResourceAsStream("/holylois-quotes.txt")) {
                    if (defaults != null) Files.copy(defaults, FILE);
                }
            }
            if (!Files.exists(FILE)) return;
            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified == stamp) return;
            var parsed = parse(Files.readAllLines(FILE, StandardCharsets.UTF_8));
            if (!parsed.isEmpty()) quotes = List.copyOf(parsed);
            stamp = modified;
        } catch (Exception error) {
            LOG.warn("Keeping the previous quote list", error);
        }
    }
}
