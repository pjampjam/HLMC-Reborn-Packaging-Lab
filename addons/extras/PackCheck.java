package holylois.boombox;

import net.fabricmc.fabric.api.event.Event;
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
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * An outdated client gets a plain "update in the launcher" message instead of registry errors. The check runs as the first
 * configuration task, before Fabric's registry sync. The minimum pack comes from config/holylois-pack.json ({"minimum": "1.7.5"});
 * without that file nothing is checked. A client without a launcher receipt (a hand-made profile) is let through.
 */
public final class PackCheck {
    public record Request(String minimum) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "pack_check"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC =
            CustomPacketPayload.codec((value, buffer) -> buffer.writeUtf(value.minimum()), buffer -> new Request(buffer.readUtf(32)));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(String version) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "pack_version"));
        public static final StreamCodec<FriendlyByteBuf, Reply> CODEC =
            CustomPacketPayload.codec((value, buffer) -> buffer.writeUtf(value.version()), buffer -> new Reply(buffer.readUtf(32)));
        @Override public Type<Reply> type() { return TYPE; }
    }

    record Task(String minimum) implements ConfigurationTask {
        static final Type TYPE = new Type("holylois:pack_check");
        @Override public void start(Consumer<Packet<?>> sender) { sender.accept(ServerConfigurationNetworking.createClientboundPacket(new Request(minimum))); }
        @Override public Type type() { return TYPE; }
    }

    private static final Identifier EARLY = Identifier.fromNamespaceAndPath("holylois", "pack_check_first");
    private static final Path CONFIG = Path.of("config", "holylois-pack.json");

    static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Reply.TYPE, Reply.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        ServerConfigurationConnectionEvents.CONFIGURE.register(EARLY, (handler, server) -> {
            String minimum = minimum();
            if (minimum == null) return;
            if (!ServerConfigurationNetworking.canSend(handler, Request.TYPE)) { handler.disconnect(outdated(minimum)); return; }
            ((FabricServerConfigurationPacketListenerImpl) (Object) handler).addTask(new Task(minimum));
        });
        ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> {
            ServerConfigurationPacketListenerImpl handler = context.packetListener();
            String minimum = minimum();
            if (minimum != null && !reply.version().isEmpty() && older(reply.version(), minimum)) {
                Boombox.LOG.info("Turned away a client with pack {} (needs {})", reply.version(), minimum);
                handler.disconnect(outdated(minimum));
                return;
            }
            ((FabricServerConfigurationPacketListenerImpl) (Object) handler).completeTask(Task.TYPE);
        });
    }

    private static String minimum() {
        try {
            if (!Files.exists(CONFIG)) return null;
            var value = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject().get("minimum");
            return value == null ? null : value.getAsString();
        } catch (Exception error) { Boombox.LOG.warn("Ignoring a broken {}", CONFIG, error); return null; }
    }

    static boolean older(String version, String minimum) {
        String[] a = version.split("\\."), b = minimum.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? number(a[i]) : 0, y = i < b.length ? number(b[i]) : 0;
            if (x != y) return x < y;
        }
        return false;
    }

    private static int number(String part) {
        try { return Integer.parseInt(part.replaceAll("[^0-9].*$", "")); } catch (NumberFormatException error) { return 0; }
    }

    private static Component outdated(String minimum) {
        return Component.literal("Holy Lois was updated (pack " + minimum + ").\n\nOpen the Holy Lois launcher and click Update, then join again.\n\n"
            + "Откройте лаунчер Holy Lois и нажмите Update.\nAtver Holy Lois palaidēju un nospied Update.");
    }
}
