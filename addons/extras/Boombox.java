package holylois.boombox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

/**
 * Holy Lois boombox: a portable speaker that plays internet radio to everyone nearby through Simple Voice
 * Chat. The sound follows the player holding it (main hand or offhand). Right-click starts or switches the
 * station, sneak + right-click turns it off.
 */
public final class Boombox implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("HolyLoisBoombox");
    static final Identifier ID = Identifier.fromNamespaceAndPath("holylois", "boombox");
    static final String STATION_KEY = "holylois_station";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = Path.of("config", "holylois-boombox.json");
    static Item ITEM;
    static volatile VoicechatServerApi voice;

    public static final class Station { public String name, url; Station() {} Station(String n, String u) { name = n; url = u; } }
    public static final class Config {
        public List<Station> stations = new ArrayList<>(List.of(
            new Station("Radio SWH", "http://80.232.162.149:8000/swh96mp3"),
            new Station("Radio SWH+", "http://80.232.162.149:8000/plus96mp3"),
            new Station("Star FM", "http://starfm.live.advailo.com/audio/mp3/icecast.audio"),
            new Station("TOP radio", "https://topradio.live.advailo.com/topradio/mp3/icecast.audio"),
            new Station("Radio Paradise", "https://stream.radioparadise.com/mp3-128"),
            new Station("SomaFM Groove Salad", "https://ice2.somafm.com/groovesalad-128-mp3"),
            new Station("SomaFM Lush", "https://ice2.somafm.com/lush-128-mp3")));
        public float distance = 24f, volume = 0.55f;
        public int maxPlaying = 6;
    }

    record Session(RadioStream stream, AudioPlayer player, int station, long[] lastHeld) {}
    static Config config = new Config();
    static final Map<UUID, Session> sessions = new HashMap<>();

    @Override public void onInitialize() {
        var key = ResourceKey.create(Registries.ITEM, ID);
        ITEM = Registry.register(BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(key).stacksTo(1)
            .component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.holylois.boombox.tip1").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
                Component.translatable("item.holylois.boombox.tip2").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))))));
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
            .register(output -> output.accept(ITEM));
        loadConfig();
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (!stack.is(ITEM)) return InteractionResult.PASS;
            toggle(serverPlayer, stack);
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Boombox::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player.getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("boombox")
            .then(Commands.literal("stations").executes(context -> {
                var list = new StringBuilder();
                for (int i = 0; i < config.stations.size(); i++) list.append(i == 0 ? "" : ", ").append(i + 1).append(". ").append(config.stations.get(i).name);
                context.getSource().sendSystemMessage(Component.literal("Boombox stations: " + list).withStyle(ChatFormatting.GOLD));
                return config.stations.size();
            }))
            .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                loadConfig();
                context.getSource().sendSystemMessage(Component.literal("Boombox stations reloaded: " + config.stations.size()));
                return 1;
            }))));
    }

    static void loadConfig() {
        try {
            if (!Files.exists(CONFIG)) { Files.createDirectories(CONFIG.getParent()); Files.writeString(CONFIG, JSON.toJson(new Config())); }
            var loaded = JSON.fromJson(Files.readString(CONFIG), Config.class);
            if (loaded != null && loaded.stations != null && !loaded.stations.isEmpty()) config = loaded;
        } catch (Exception error) { LOG.warn("Using default boombox stations", error); }
    }

    static int station(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr(STATION_KEY, 0);
    }

    private static void toggle(ServerPlayer player, ItemStack stack) {
        var id = player.getUUID();
        var current = sessions.get(id);
        if (player.isShiftKeyDown()) {
            if (current != null) { stop(id); actionBar(player, "Boombox off"); }
            return;
        }
        if (voice == null) { actionBar(player, "Voice chat is not ready on the server yet"); return; }
        int next = current == null ? station(stack) : (current.station() + 1) % config.stations.size();
        next = Math.floorMod(next, config.stations.size());
        if (current == null && sessions.size() >= config.maxPlaying) { actionBar(player, "Too many boomboxes are playing right now"); return; }
        stop(id);
        var tag = new CompoundTag(); tag.putInt(STATION_KEY, next);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        start(player, next);
    }

    private static void start(ServerPlayer player, int index) {
        var station = config.stations.get(index);
        var stream = new RadioStream(station.url, config.volume);
        var channel = voice.createEntityAudioChannel(UUID.randomUUID(), voice.fromEntity(player));
        if (channel == null) { actionBar(player, "Voice chat could not open a channel"); return; }
        channel.setCategory(BoomboxPlugin.CATEGORY);
        channel.setDistance(config.distance);
        var audio = voice.createAudioPlayer(channel, voice.createEncoder(), stream::next);
        stream.start();
        audio.startPlaying();
        sessions.put(player.getUUID(), new Session(stream, audio, index, new long[] {player.level().getServer().getTickCount()}));
        actionBar(player, "♪ " + station.name + " (" + (index + 1) + "/" + config.stations.size() + ")");
        var advancement = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("holylois", "music/dj_lois"));
        if (advancement != null) player.getAdvancements().award(advancement, "done");
        LOG.info("{} tuned the boombox to {}", player.getGameProfile().name(), station.name);
    }

    static void stop(UUID id) {
        var session = sessions.remove(id);
        if (session == null) return;
        session.stream().stop();
        session.player().stopPlaying();
    }

    private static boolean holding(ServerPlayer player) {
        return player.getMainHandItem().is(ITEM) || player.getOffhandItem().is(ITEM);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 20 != 0 || sessions.isEmpty()) return;
        for (var id : List.copyOf(sessions.keySet())) {
            var session = sessions.get(id);
            var player = server.getPlayerList().getPlayer(id);
            if (player == null || session.stream().failed) {
                if (player != null) actionBar(player, "This station is not answering. Right-click for the next one.");
                stop(id); continue;
            }
            if (holding(player)) session.lastHeld()[0] = now;
            else if (now - session.lastHeld()[0] > 60) { stop(id); actionBar(player, "Boombox off"); continue; }
            if (now % 40 == 0 && holding(player)) {
                String song = session.stream().nowPlaying;
                actionBar(player, "♪ " + config.stations.get(session.station()).name + (song.isEmpty() ? "" : " - " + song));
            }
        }
    }

    static void actionBar(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
