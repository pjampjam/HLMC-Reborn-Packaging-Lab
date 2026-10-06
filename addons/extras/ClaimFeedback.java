package holylois.boombox;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Unique Holy Lois resources avoid depending on another mod's pack ordering. */
public final class ClaimFeedback {
    private static final Map<String, Map<String, String>> TEXT = Map.of(
        "en", load("en_us"), "ru", load("ru_ru"), "lv", load("lv_lv"));
    private static Map<String, String> load(String language) {
        String path = "/assets/holylois/claim_messages/" + language + ".json";
        try (var input = ClaimFeedback.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing claim message resource " + language);
            return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>(){}.getType());
        } catch (Exception error) { throw new IllegalStateException("Cannot load claim messages " + language, error); }
    }
    public static MutableComponent message(ServerPlayer player, String key) {
        String language = player == null ? "en" : player.clientInformation().language().split("_", 2)[0];
        String text = TEXT.getOrDefault(language, TEXT.get("en")).get(key);
        return text == null ? null : Component.literal(text);
    }
}
