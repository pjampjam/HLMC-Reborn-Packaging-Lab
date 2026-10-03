package holylois;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Latvian name days (vardadienas), shared by the welcome message and %holylois:nameday%. */
public final class NameDays {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Path FILE = Path.of("config", "holylois-namedays-lv.txt");
    private static final ZoneId RIGA = ZoneId.of("Europe/Riga");
    private static Map<String, List<String>> days = Map.of();
    private static long stamp = Long.MIN_VALUE;

    private NameDays() {}

    /** Lines are "MM-DD: Name, Name". Blank lines and # comments are ignored. */
    static Map<String, List<String>> parse(List<String> lines) {
        var result = new HashMap<String, List<String>>();
        for (String line : lines) {
            String value = line.strip();
            int split = value.indexOf(':');
            if (value.startsWith("#") || split != 5 || !value.substring(0, 5).matches("\\d{2}-\\d{2}")) continue;
            var names = Arrays.stream(value.substring(6).split(",")).map(String::strip).filter(n -> !n.isEmpty()).toList();
            if (!names.isEmpty()) result.put(value.substring(0, 5), names);
        }
        return result;
    }

    static List<String> on(Map<String, List<String>> calendar, LocalDate date) {
        return calendar.getOrDefault(String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()), List.of());
    }

    /** "Elza and Ilizana", "Ādams, Ieva and Jānis". */
    static String join(List<String> names) {
        if (names.size() <= 1) return names.isEmpty() ? "" : names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** Matches "Janis_LV" or "janis123" to Jānis: diacritics and case are ignored, the name must start the username. */
    static Optional<String> celebrating(List<String> names, String username) {
        String plain = fold(username);
        return names.stream().filter(name -> fold(name).length() >= 3 && plain.startsWith(fold(name))).findFirst();
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static synchronized List<String> today() {
        reload();
        return on(days, LocalDate.now(RIGA));
    }

    private static void reload() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                try (InputStream defaults = NameDays.class.getResourceAsStream("/holylois-namedays-lv.txt")) {
                    if (defaults != null) Files.copy(defaults, FILE);
                }
            }
            if (!Files.exists(FILE)) return;
            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified == stamp) return;
            days = Map.copyOf(parse(Files.readAllLines(FILE, StandardCharsets.UTF_8)));
            stamp = modified;
        } catch (Exception error) {
            LOG.warn("Keeping the previous name day list", error);
        }
    }
}
