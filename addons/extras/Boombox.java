package holylois.boombox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

/**
 * Holy Lois boombox: plays internet radio to everyone nearby through Simple Voice Chat.
 * Held (main hand or offhand): right-click in the air plays or switches the station, sneak + right-click turns it off,
 * and the sound follows the player. It stops at once when it leaves the player's inventory and after two seconds
 * when it is no longer held. Placed: right-click plays or switches, sneak + empty hand turns it off; it streams while
 * someone is within earshot and resumes after a restart.
 */
public final class Boombox implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("HolyLoisBoombox");
    static final Identifier ID = Identifier.fromNamespaceAndPath("holylois", "boombox");
    static final String STATION_KEY = "holylois_station", VOLUME_KEY = "holylois_volume";
    static final int DEFAULT_VOLUME = 5;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = Path.of("config", "holylois-boombox.json");
    public static Item ITEM;
    public static BoomboxBlock BLOCK;
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
            new Station("SomaFM Lush", "https://ice2.somafm.com/lush-128-mp3"),
            new Station("SomaFM Dub Step Beyond", "https://ice2.somafm.com/dubstep-128-mp3"),
            new Station("Bassdrive (drum and bass)", "http://ice.bassdrive.net/stream"),
            new Station("laut.fm Trap", "https://stream.laut.fm/trap"),
            new Station("laut.fm Lo-fi", "https://stream.laut.fm/lofi"),
            new Station("laut.fm Hardstyle", "https://stream.laut.fm/hardstyle"),
            new Station("laut.fm Techno", "https://stream.laut.fm/techno")));
        public float distance = 24f, volume = 0.55f;
        public int maxPlaying = 6, maxPlayingPerChunk = 6;
    }

    record Session(RadioStream stream, AudioPlayer player, AudioChannel channel, int station, long[] lastHeld, float[] range) {}
    record Spot(String dimension, int x, int y, int z) {
        static Spot of(Level level, BlockPos pos) { return new Spot(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ()); }
        BlockPos pos() { return new BlockPos(x, y, z); }
        Vec3 center() { return new Vec3(x + 0.5, y + 0.5, z + 0.5); }
    }
    static Config config = new Config();
    /** Held boomboxes by player. */
    static final Map<UUID, Session> sessions = new HashMap<>();
    /** Placed boomboxes that are streaming right now. */
    static final Map<Spot, Session> speakers = new HashMap<>();
    /** Every placed boombox switched on (persisted), streaming or waiting for a listener. */
    static final Set<Spot> placed = new LinkedHashSet<>();
    private static final Map<UUID, Boolean> sentNear = new HashMap<>();
    private static Path placedFile;

    @Override public void onInitialize() {
        var blockKey = ResourceKey.create(Registries.BLOCK, ID);
        BLOCK = Registry.register(BuiltInRegistries.BLOCK, ID, new BoomboxBlock(BlockBehaviour.Properties.of().setId(blockKey)
            .strength(0.8f, 3f).sound(SoundType.METAL).noOcclusion()));
        var key = ResourceKey.create(Registries.ITEM, ID);
        ITEM = Registry.register(BuiltInRegistries.ITEM, ID, new BlockItem(BLOCK, new Item.Properties().setId(key).stacksTo(1)
            .component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.holylois.boombox.tip1").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
                Component.translatable("item.holylois.boombox.tip2").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))))));
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
            .register(output -> output.accept(ITEM));
        PayloadTypeRegistry.clientboundPlay().register(BoomboxNear.TYPE, BoomboxNear.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RareCatchNotice.TYPE,RareCatchNotice.CODEC);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()==net.fabricmc.api.EnvType.CLIENT) {
            PayloadTypeRegistry.clientboundPlay().register(HomeState.TYPE,HomeState.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(MenuOpen.TYPE,MenuOpen.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(TravelIntent.TYPE,TravelIntent.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(AccountNotice.TYPE,AccountNotice.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(AccountIntent.TYPE,AccountIntent.CODEC);
        }
        PvpDeath.register();
        PackCheck.register();
        ModCheck.register();
        Legends.register();
        PartySupport.register();
        RelicPowers.register();
        PayloadTypeRegistry.serverboundPlay().register(BoomboxVolume.TYPE, BoomboxVolume.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BoomboxVolume.TYPE, (payload, context) -> changeVolume(context.player(), payload));
        loadConfig();
        // Right-click in the air plays the held boombox; right-click on a block places it (BlockItem).
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (!stack.is(ITEM)) return InteractionResult.PASS;
            toggle(serverPlayer, stack);
            return InteractionResult.SUCCESS;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Boombox::loadPlaced);
        ServerTickEvents.END_SERVER_TICK.register(Boombox::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { stop(handler.player.getUUID()); sentNear.remove(handler.player.getUUID()); });
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

    private static void loadPlaced(MinecraftServer server) {
        placedFile = server.getWorldPath(LevelResource.ROOT).resolve("holylois/boomboxes.json");
        try {
            if (Files.exists(placedFile)) placed.addAll(Arrays.asList(JSON.fromJson(Files.readString(placedFile), Spot[].class)));
        } catch (Exception error) { LOG.warn("Cannot read placed boomboxes", error); }
    }

    private static void savePlaced() {
        if (placedFile == null) return;
        try {
            Files.createDirectories(placedFile.getParent());
            var temporary = placedFile.resolveSibling("boomboxes.json.tmp");
            Files.writeString(temporary, JSON.toJson(placed));
            Files.move(temporary, placedFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { LOG.warn("Cannot save placed boomboxes", error); }
    }

    static int station(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr(STATION_KEY, 0);
    }

    static int volume(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return Math.clamp(data == null ? DEFAULT_VOLUME : data.copyTag().getIntOr(VOLUME_KEY, DEFAULT_VOLUME), 1, 10);
    }

    private static void setTag(ItemStack stack, String key, int value) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putInt(key, value);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    static final float MIN_RANGE = 16f, MAX_RANGE = 48f;

    /** Volume 5 is the old fixed loudness; 10 is about 1.8x as loud. Volume 1 carries 16 blocks and volume 10 carries 48. */
    static float gain(int volume) { return config.volume * (0.2f + 0.16f * volume); }
    static float range(int volume) { return MIN_RANGE + (MAX_RANGE - MIN_RANGE) * (Math.clamp(volume, 1, 10) - 1) / 9f; }

    private static void apply(Session session, int volume) {
        if (session == null) return;
        session.stream().gain = gain(volume);
        session.range()[0] = range(volume);
        if (session.channel() instanceof de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel entity) entity.setDistance(range(volume));
        if (session.channel() instanceof de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel located) located.setDistance(range(volume));
    }

    /** Sneak + scroll from the client: the placed boombox in reach, or the one in hand. */
    private static void changeVolume(ServerPlayer player, BoomboxVolume request) {
        int volume;
        if (request.pos() != null) {
            var level = player.level();
            var pos = request.pos();
            if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 64 || !level.isLoaded(pos)) return;
            var state = level.getBlockState(pos);
            if (!state.is(BLOCK)) return;
            if (!ClaimAccess.canUse(player, level, pos)) { actionBar(player, "You do not have permission to use this boombox here."); return; }
            volume = Math.clamp(state.getValue(BoomboxBlock.VOLUME) + request.step(), 1, 10);
            if (volume == state.getValue(BoomboxBlock.VOLUME)) { actionBar(player, volumeText(volume)); return; }
            level.setBlock(pos, state.setValue(BoomboxBlock.VOLUME, volume), 3);
            apply(speakers.get(Spot.of(level, pos)), volume);
        } else {
            var stack = player.getMainHandItem().is(ITEM) ? player.getMainHandItem() : player.getOffhandItem();
            if (!stack.is(ITEM)) return;
            volume = Math.clamp(volume(stack) + request.step(), 1, 10);
            setTag(stack, VOLUME_KEY, volume);
            apply(sessions.get(player.getUUID()), volume);
        }
        actionBar(player, volumeText(volume));
    }

    private static String volumeText(int volume) { return "♪ Volume " + "|".repeat(volume) + " ".repeat(10 - volume) + " " + volume + "/10"; }

    private static int stationCount() { return Math.min(16, config.stations.size()); }
    private static int playing() { return sessions.size() + speakers.size(); }

    private static void toggle(ServerPlayer player, ItemStack stack) {
        var id = player.getUUID();
        var current = sessions.get(id);
        if (player.isShiftKeyDown()) {
            if (current != null) { stop(id); actionBar(player, "Boombox off"); }
            return;
        }
        if (voice == null) { actionBar(player, "Voice chat is not ready on the server yet"); return; }
        int next = Math.floorMod(current == null ? station(stack) : current.station() + 1, stationCount());
        if (current == null && playing() >= config.maxPlaying) { actionBar(player, "Too many boomboxes are playing right now"); return; }
        stop(id);
        setTag(stack, STATION_KEY, next);
        var session = open(voice.createEntityAudioChannel(UUID.randomUUID(), voice.fromEntity(player)), next, volume(stack));
        if (session == null) { actionBar(player, "Voice chat could not open a channel"); return; }
        session.lastHeld()[0] = player.level().getServer().getTickCount();
        sessions.put(id, session);
        tuned(player, next);
    }

    /** Right-click on a placed boombox. */
    static void useSpeaker(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        if (!ClaimAccess.canUse(player, (ServerLevel)level, pos)) { actionBar(player, "You do not have permission to use this boombox here."); return; }
        var spot = Spot.of(level, pos);
        boolean on = state.getValue(BoomboxBlock.PLAYING);
        if (player.isShiftKeyDown()) {
            if (on) {
                level.setBlock(pos, state.setValue(BoomboxBlock.PLAYING, false), 3);
                stopSpeaker(spot);
                if (placed.remove(spot)) savePlaced();
                actionBar(player, "Boombox off");
            }
            return;
        }
        if (voice == null) { actionBar(player, "Voice chat is not ready on the server yet"); return; }
        if (!speakers.containsKey(spot) && playing() >= config.maxPlaying) { actionBar(player, "Too many boomboxes are playing right now"); return; }
        if (!on && placed.stream().filter(other -> sameChunk(other, spot)).count() >= Math.clamp(config.maxPlayingPerChunk, 1, 6)) {
            actionBar(player, "This chunk already has its maximum number of active boomboxes."); return;
        }
        int next = Math.floorMod(on ? state.getValue(BoomboxBlock.STATION) + 1 : state.getValue(BoomboxBlock.STATION), stationCount());
        level.setBlock(pos, state.setValue(BoomboxBlock.PLAYING, true).setValue(BoomboxBlock.STATION, next), 3);
        stopSpeaker(spot);
        if (placed.add(spot)) savePlaced();
        if (!startSpeaker((ServerLevel)level, spot, next, state.getValue(BoomboxBlock.VOLUME))) { actionBar(player, "Voice chat could not open a channel"); return; }
        tuned(player, next);
    }

    static boolean sameChunk(Spot a, Spot b) {
        return a.dimension().equals(b.dimension()) && Math.floorDiv(a.x(), 16) == Math.floorDiv(b.x(), 16)
            && Math.floorDiv(a.z(), 16) == Math.floorDiv(b.z(), 16);
    }

    private static boolean startSpeaker(ServerLevel level, Spot spot, int station, int volume) {
        if (speakers.keySet().stream().filter(other -> sameChunk(other, spot)).count() >= Math.clamp(config.maxPlayingPerChunk, 1, 6)) return false;
        var center = spot.center();
        var session = open(voice.createLocationalAudioChannel(UUID.randomUUID(), voice.fromServerLevel(level),
            voice.createPosition(center.x, center.y, center.z)), station, volume);
        if (session == null) return false;
        speakers.put(spot, session);
        return true;
    }

    private static Session open(AudioChannel channel, int index, int volume) {
        if (channel == null) return null;
        channel.setCategory(BoomboxPlugin.CATEGORY);
        if (channel instanceof de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel entity) entity.setDistance(range(volume));
        if (channel instanceof de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel located) located.setDistance(range(volume));
        var stream = new RadioStream(config.stations.get(index).url, gain(volume));
        var audio = voice.createAudioPlayer(channel, voice.createEncoder(), stream::next);
        stream.start();
        audio.startPlaying();
        return new Session(stream, audio, channel, index, new long[] {0}, new float[] {range(volume)});
    }

    private static void tuned(ServerPlayer player, int index) {
        var station = config.stations.get(index);
        actionBar(player, "♪ " + station.name + " (" + (index + 1) + "/" + stationCount() + ")");
        award(player, "music/dj_lois");
        LOG.info("{} tuned a boombox to {}", player.getGameProfile().name(), station.name);
    }

    static void award(ServerPlayer player, String path) {
        var advancement = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("holylois", path));
        if (advancement != null) player.getAdvancements().award(advancement, "done");
    }

    static void stop(UUID id) { close(sessions.remove(id)); }
    static void stopSpeaker(Spot spot) { close(speakers.remove(spot)); }
    static void stopAll() {
        for (var id : List.copyOf(sessions.keySet())) stop(id);
        for (var spot : List.copyOf(speakers.keySet())) stopSpeaker(spot);
    }

    private static void close(Session session) {
        if (session == null) return;
        session.stream().stop();
        session.player().stopPlaying();
    }

    private static boolean holding(ServerPlayer player) {
        return player.getMainHandItem().is(ITEM) || player.getOffhandItem().is(ITEM);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 10 != 0) return;
        for (var id : List.copyOf(sessions.keySet())) {
            var session = sessions.get(id);
            var player = server.getPlayerList().getPlayer(id);
            if (player == null || session.stream().failed) {
                if (player != null) actionBar(player, "This station is not answering. Right-click for the next one.");
                stop(id); continue;
            }
            if (holding(player)) session.lastHeld()[0] = now;
            else if (!player.getInventory().contains(stack -> stack.is(ITEM)) || now - session.lastHeld()[0] > 40) {
                stop(id); actionBar(player, "Boombox off"); continue;
            }
            if (now % 40 == 0 && holding(player)) {
                String song = session.stream().nowPlaying;
                actionBar(player, "♪ " + config.stations.get(session.station()).name + (song.isEmpty() ? "" : " - " + song));
            }
        }
        boolean changed = false;
        for (var spot : List.copyOf(placed)) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(spot.dimension())));
            var pos = spot.pos();
            if (level == null || !level.hasChunkAt(pos)) { stopSpeaker(spot); continue; }
            var state = level.getBlockState(pos);
            if (!state.is(BLOCK) || !state.getValue(BoomboxBlock.PLAYING)) { stopSpeaker(spot); placed.remove(spot); changed = true; continue; }
            var session = speakers.get(spot);
            if (session != null && session.stream().failed) {
                stopSpeaker(spot); placed.remove(spot); changed = true;
                level.setBlock(pos, state.setValue(BoomboxBlock.PLAYING, false), 3);
                continue;
            }
            double reach = MAX_RANGE + 8;
            boolean listener = level.players().stream().anyMatch(p -> p.position().distanceToSqr(spot.center()) < reach * reach);
            if (!listener) stopSpeaker(spot);
            else if (session == null && voice != null && playing() < config.maxPlaying)
                startSpeaker(level, spot, Math.floorMod(state.getValue(BoomboxBlock.STATION), stationCount()), state.getValue(BoomboxBlock.VOLUME));
            if (session != null && now % 40 == 0) {
                String song = session.stream().nowPlaying;
                var text = "♪ " + config.stations.get(session.station()).name + (song.isEmpty() ? "" : " - " + song);
                for (var player : level.players())
                    if (player.position().distanceToSqr(spot.center()) < 36 && !sessions.containsKey(player.getUUID())) actionBar(player, text);
            }
        }
        if (changed) savePlaced();
        if (now % 20 == 0) tellListeners(server);
    }

    private static double square(float value) { return (double) value * value; }

    /** Clients pause the game music while a boombox plays within earshot; two at once earns Surround Sound. */
    private static void tellListeners(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            int heard = 0;
            for (var entry : sessions.entrySet()) {
                var owner = server.getPlayerList().getPlayer(entry.getKey());
                if (owner != null && owner.level() == player.level() && owner.position().distanceToSqr(player.position()) < square(entry.getValue().range()[0])) heard++;
            }
            var dimension = player.level().dimension().identifier().toString();
            for (var entry : speakers.entrySet())
                if (entry.getKey().dimension().equals(dimension) && entry.getKey().center().distanceToSqr(player.position()) < square(entry.getValue().range()[0])) heard++;
            if (heard >= 2) award(player, "music/surround_sound");
            boolean near = heard > 0;
            if (sentNear.getOrDefault(player.getUUID(), false) != near && ServerPlayNetworking.canSend(player, BoomboxNear.TYPE)) {
                ServerPlayNetworking.send(player, new BoomboxNear(near));
                sentNear.put(player.getUUID(), near);
            }
        }
    }

    static void actionBar(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
