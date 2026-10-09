package holylois;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** /seen NAME, operators only (players asked for no stalking): online now (and AFK) or when the player was last on. */
final class Seen {
    private Seen() {}
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(Redeem.ZONE);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seen")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                var source = context.getSource();
                source.sendSystemMessage(Component.literal(describe(source.getServer(), StringArgumentType.getString(context, "player"))).withStyle(ChatFormatting.GOLD));
                return 1;
            })));
    }

    static String describe(net.minecraft.server.MinecraftServer server, String name) {
        var online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getGameProfile().name() + " is online now" + (Afk.afkNow(online) ? " (AFK)." : ".");
        var known = server.services().nameToIdCache().get(name);
        if (known.isEmpty()) return "Nobody called " + name + " has played here.";
        var data = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(known.get().id() + ".dat");
        try {
            if (!Files.exists(data)) return known.get().name() + " is known but has no saved player data.";
            long last = Files.getLastModifiedTime(data).toMillis();
            return lastSeen(known.get().name(), last, System.currentTimeMillis());
        } catch (Exception error) {
            return "Could not read when " + known.get().name() + " was last on.";
        }
    }

    static String lastSeen(String name, long lastMillis, long nowMillis) {
        long seconds = Math.max(0, (nowMillis - lastMillis) / 1000);
        String ago = seconds >= 2 * 86_400 ? seconds / 86_400 + " days" : Redeem.duration(seconds);
        return name + " was last on " + ago + " ago (" + WHEN.format(Instant.ofEpochMilli(lastMillis)) + " Riga time).";
    }
}
