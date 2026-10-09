package holylois.boombox;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;

/**
 * One-time client setting changes that the launcher cannot merge (REI keeps a JSON5 file with comments).
 * Each change runs before any mod reads its config, only once per player, and only while the player still has
 * REI's original value, so a choice made in REI's own settings is never overwritten.
 */
public final class ClientDefaults implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        var loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT) return;
        var marker = loader.getConfigDir().resolve("holylois-extras-defaults.txt");
        try {
            var done = new LinkedHashSet<>(Files.exists(marker) ? Files.readAllLines(marker) : java.util.List.of());
            var rei = loader.getConfigDir().resolve("roughlyenoughitems/config.json5");
            if (Files.exists(rei)) {
                String text = Files.readString(rei, StandardCharsets.UTF_8), before = text;
                // The item list on the right appears only while searching.
                if (done.add("rei-hide-idle")) text = text.replace("\"hideEntryPanelIfIdle\": false", "\"hideEntryPanelIfIdle\": true");
                // The developer "Tags" tab is hidden from recipe views.
                if (done.add("rei-hide-tags")) text = text.replace("\"hiddenCategories\": []", "\"hiddenCategories\": [\"minecraft:plugins/tag\"]");
                // A scrolling list instead of pages: no arrows and no "0/1" counter, only the search bar.
                if (done.add("rei-scrolling")) text = text.replace("\"scrollingEntryListWidget\": false", "\"scrollingEntryListWidget\": true");
                if (!text.equals(before)) Files.writeString(rei, text, StandardCharsets.UTF_8);
            }
            // Alt-tab keeps the game running instead of opening the pause menu (F3+P turns pausing back on). Only once options.txt
            // exists: creating it here would stop YOSBR from copying the shipped defaults for a new player.
            var options = loader.getGameDir().resolve("options.txt");
            if (!done.contains("no-pause-on-alt-tab") && Files.exists(options)) {
                String text = Files.readString(options, StandardCharsets.UTF_8);
                String next = text.contains("pauseOnLostFocus:") ? text.replace("pauseOnLostFocus:true", "pauseOnLostFocus:false")
                    : text + (text.endsWith(System.lineSeparator()) || text.endsWith("\n") ? "" : System.lineSeparator()) + "pauseOnLostFocus:false" + System.lineSeparator();
                if (!next.equals(text)) Files.writeString(options, next, StandardCharsets.UTF_8);
                done.add("no-pause-on-alt-tab");
            }
            Files.write(marker, done);
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("HolyLoisExtras").warn("Could not apply Holy Lois client defaults", error);
        }
    }
}
