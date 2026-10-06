package holylois;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Shared with the website stats generator; game saves remain untouched. */
final class StatsVisibility {
    private static final Path FILE = Path.of("config", "holylois-stats-hidden.json");
    private static final Pattern UNSAFE = Pattern.compile("n[i1!]gg|f[a@]gg?[o0]t|f[a@]g$|r[e3]t[a@]rd|p[i1]d[o0a]r|п[иі]д[оа]р|k[i1]ke|ch[i1]nk|tr[a@]nny", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final class Config { List<String> names = List.of("pjampjam", "pjamtest"); }
    static boolean visible(String name) {
        if (name.equalsIgnoreCase("pjampjam") || name.equalsIgnoreCase("pjamtest")) return false;
        try {
            if (Files.exists(FILE)) {
                var config = new Gson().fromJson(Files.readString(FILE), Config.class);
                if (config != null && config.names != null && config.names.stream().anyMatch(name::equalsIgnoreCase)) return false;
            }
        } catch (Exception error) { org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Could not read stats exclusions", error); }
        return true;
    }
    static String display(String name) { return UNSAFE.matcher(name).find() ? name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + "*****" : name; }
}
