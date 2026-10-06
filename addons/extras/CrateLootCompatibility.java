package holylois.boombox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.loot.LootTable;
import java.nio.file.Files;
import java.util.Set;

/** Translate installed legacy crate data, without bundling another mod's loot tables. */
final class CrateLootCompatibility {
    private static final Set<String> TABLES = Set.of("blocks/wooden_crate", "blocks/iron_crate", "blocks/golden_crate");
    static void register() {
        LootTableEvents.REPLACE.register((key, original, source, registries) -> {
            var id = key.identifier();
            if (!source.isBuiltin() || !id.getNamespace().equals("fishing_crates") || !TABLES.contains(id.getPath())) return null;
            var mod = FabricLoader.getInstance().getModContainer("fishing_crates");
            if (mod.isEmpty() || !mod.get().getMetadata().getVersion().getFriendlyString().equals("1.3.1")) return null;
            try {
                var path = mod.get().findPath("data/fishing_crates/loot_table/" + id.getPath() + ".json").orElseThrow();
                var raw = JsonParser.parseString(Files.readString(path));
                var table = LootTable.DIRECT_CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), convert(raw)).getOrThrow();
                Boombox.LOG.info("Holy Lois updated legacy crate loot fields: {}", id);
                return table;
            } catch (Exception error) {
                throw new IllegalStateException("Cannot decode corrected crate table " + id, error);
            }
        });
    }

    static JsonElement convert(JsonElement value) {
        if (value.isJsonArray()) {
            var array = new JsonArray();
            value.getAsJsonArray().forEach(e -> array.add(convert(e)));
            return array;
        }
        if (!value.isJsonObject()) return value.deepCopy();
        JsonObject old = value.getAsJsonObject(), result = new JsonObject();
        boolean predicate = old.has("condition") && old.get("condition").isJsonPrimitive();
        old.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (key.equals("function") || predicate && key.equals("condition")) key = "type";
            else if (key.equals("functions") && !(old.has("function") && old.get("function").getAsString().equals("minecraft:sequence"))) key = "modifier";
            if (key.equals("conditions")) {
                JsonArray terms = convert(entry.getValue()).getAsJsonArray();
                if (terms.size() == 1) result.add("condition", terms.get(0));
                else if (!terms.isEmpty()) {
                    var all = new JsonObject(); all.addProperty("type", "minecraft:all_of"); all.add("terms", terms);
                    result.add("condition", all);
                }
            } else result.add(key, convert(entry.getValue()));
        });
        return result;
    }
}
