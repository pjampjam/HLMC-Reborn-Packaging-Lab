package holylois.boombox;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.JsonParser;
import java.nio.file.Files;

/** Answers the server's pack check with the version from the launcher receipt (empty when the profile was not made by it). */
final class PackCheckClient {
    static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(PackCheck.Request.TYPE,
            (request, context) -> context.responseSender().sendPacket(new PackCheck.Reply(installed())));
        // The mod list for the server's mod check: every loaded mod id, including the ones inside other jars.
        ClientConfigurationNetworking.registerGlobalReceiver(ModCheck.Request.TYPE, (request, context) -> context.responseSender().sendPacket(
            new ModCheck.Reply(FabricLoader.getInstance().getAllMods().stream().map(mod -> mod.getMetadata().getId()).sorted().toList())));
    }

    private static String installed() {
        try {
            var receipt = FabricLoader.getInstance().getGameDir().resolve("holylois-pack-receipt.json");
            if (!Files.exists(receipt)) return "";
            var version = JsonParser.parseString(Files.readString(receipt)).getAsJsonObject().get("version");
            return version == null ? "" : version.getAsString();
        } catch (Exception error) { return ""; }
    }
}
