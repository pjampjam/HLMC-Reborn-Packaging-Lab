package holylois.boombox;

import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Holy Lois runs on the pack and nothing else: a joining client reports its mod ids during configuration and the server compares
 * them with config/holylois-mods.json. Mods that are not in the pack are turned away with a clear message; missing mods are only
 * logged until the owner switches "missing" to "kick". Players named in "exempt" skip the check (the owner's workshop profile).
 * A client that is too old to answer is let in while "legacy" is "allow". No file, or mode "off", means no check.
 *
 * {"mode": "enforce" | "warn" | "off", "missing": "warn" | "kick", "legacy": "allow" | "block", "exempt": ["name"], "allowed": ["modid"]}
 *
 * This is a guard against accidents and casual extras, not against a determined cheat client, which can fake its list.
 */
public final class ModCheck {
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "mod_check"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(List<String> ids) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "mod_list"));
        public static final StreamCodec<FriendlyByteBuf, Reply> CODEC = CustomPacketPayload.codec((value, buffer) -> {
            buffer.writeVarInt(value.ids().size());
            for (String id : value.ids()) buffer.writeUtf(id, 128);
        }, buffer -> {
            int count = buffer.readVarInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Too many mods");
            var ids = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) ids.add(buffer.readUtf(128));
            return new Reply(ids);
        });
        @Override public Type<Reply> type() { return TYPE; }
    }

    record Task() implements ConfigurationTask {
        static final Type TYPE = new Type("holylois:mod_check");
        @Override public void start(Consumer<Packet<?>> sender) { sender.accept(ServerConfigurationNetworking.createClientboundPacket(new Request())); }
        @Override public Type type() { return TYPE; }
    }

    /** Parsed settings; allowed is empty when the file is missing or broken. */
    record Config(String mode, String missing, String legacy, Set<String> exempt, Set<String> allowed) {}

    /** What to do with one client: the ids it has that are not allowed, and the allowed ones it lacks. */
    record Verdict(TreeSet<String> unknown, TreeSet<String> missing) {}

    private static final Path CONFIG = Path.of("config", "holylois-mods.json");

    static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Reply.TYPE, Reply.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            var config = load();
            if (config == null || config.mode().equals("off")) return;
            String name = handler.getOwner().name();
            if (config.exempt().stream().anyMatch(name::equalsIgnoreCase)) return;
            if (!ServerConfigurationNetworking.canSend(handler, Request.TYPE)) {
                if (config.legacy().equals("block")) handler.disconnect(old());
                else Boombox.LOG.info("{} joined with a launcher too old to report its mods; allowed", name);
                return;
            }
            ((FabricServerConfigurationPacketListenerImpl) (Object) handler).addTask(new Task());
        });
        ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> {
            ServerConfigurationPacketListenerImpl handler = context.packetListener();
            var config = load();
            if (config != null && !config.mode().equals("off")) {
                String name = handler.getOwner().name();
                var verdict = check(config, reply.ids());
                if (!verdict.unknown().isEmpty()) {
                    Boombox.LOG.info("{} has mods that are not part of the pack: {}", name, verdict.unknown());
                    if (config.mode().equals("enforce")) { handler.disconnect(extras(verdict.unknown())); return; }
                }
                if (!verdict.missing().isEmpty()) {
                    Boombox.LOG.info("{} is missing pack mods: {}", name, verdict.missing());
                    if (config.missing().equals("kick") && config.mode().equals("enforce")) { handler.disconnect(lacking(verdict.missing())); return; }
                }
            }
            ((FabricServerConfigurationPacketListenerImpl) (Object) handler).completeTask(Task.TYPE);
        });
    }

    static Verdict check(Config config, Collection<String> ids) {
        var unknown = new TreeSet<String>(); var missing = new TreeSet<String>(config.allowed());
        for (String id : ids) { if (!config.allowed().contains(id)) unknown.add(id); missing.remove(id); }
        return new Verdict(unknown, missing);
    }

    static Config parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        var allowed = new TreeSet<String>(); var exempt = new TreeSet<String>();
        if (root.has("allowed")) root.getAsJsonArray("allowed").forEach(e -> allowed.add(e.getAsString()));
        if (root.has("exempt")) root.getAsJsonArray("exempt").forEach(e -> exempt.add(e.getAsString()));
        return new Config(text(root, "mode", "enforce"), text(root, "missing", "warn"), text(root, "legacy", "allow"), exempt, allowed);
    }

    private static String text(JsonObject root, String key, String fallback) { return root.has(key) ? root.get(key).getAsString() : fallback; }

    private static Config load() {
        try {
            if (!Files.exists(CONFIG)) return null;
            var config = parse(Files.readString(CONFIG));
            return config.allowed().isEmpty() ? null : config; // an empty list would turn away everybody
        } catch (Exception error) { Boombox.LOG.warn("Ignoring a broken {}", CONFIG, error); return null; }
    }

    private static Component extras(Collection<String> ids) {
        String list = String.join(", ", ids.stream().limit(8).toList()) + (ids.size() > 8 ? " ..." : "");
        return Component.literal("Holy Lois only runs the mods of its pack. Not part of it: " + list + "\n\nOpen the Holy Lois launcher and click Repair / check files, "
            + "then join again.\n\nHoly Lois работает только с модами сборки. Лишние: " + list + "\nНажмите Repair / check files в лаунчере.\n"
            + "Holy Lois darbojas tikai ar komplekta modiem. Liekie: " + list + "\nLaunchera nospied Repair / check files.");
    }

    private static Component lacking(Collection<String> ids) {
        String list = String.join(", ", ids.stream().limit(8).toList()) + (ids.size() > 8 ? " ..." : "");
        return Component.literal("Some mods of the Holy Lois pack are missing or switched off: " + list + "\n\nOpen the Holy Lois launcher and click Repair / check files, then join again.\n\n"
            + "Не хватает модов сборки: " + list + "\nНажмите Repair / check files.\nTrūkst komplekta modu: " + list + "\nNospied Repair / check files.");
    }

    private static Component old() {
        return Component.literal("Open the Holy Lois launcher and click Update, then join again.\n\nОткройте лаунчер Holy Lois и нажмите Update.\nAtver Holy Lois palaidēju un nospied Update.");
    }
}
