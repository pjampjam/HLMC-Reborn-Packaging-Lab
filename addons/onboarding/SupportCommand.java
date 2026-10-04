package holylois;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * /support MESSAGE asks the staff for help, /report PLAYER REASON reports someone. Both go to the private Discord #support
 * channel (the bot reads the HOLYLOIS-SUPPORT log line and pings the owner) and to online operators in game.
 */
final class SupportCommand {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    static final int COOLDOWN_SECONDS = 300;
    static final List<String> CATEGORIES = List.of("bug", "help", "grief", "other");
    private final Map<String, Long> lastUse = new HashMap<>();

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("support")
            .executes(context -> { usage(context.getSource().getPlayerOrException()); return 1; })
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(context -> send(context.getSource().getPlayerOrException(), "help", null, StringArgumentType.getString(context, "message")))));
        dispatcher.register(Commands.literal("report")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(context -> send(context.getSource().getPlayerOrException(), "report",
                        EntityArgument.getPlayer(context, "player").getGameProfile().name(), StringArgumentType.getString(context, "reason"))))));
    }

    private static void usage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Need help?").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal("\n/support MESSAGE - pjampjam gets it on Discord right away and replies in game."
                + "\nStart with bug, help or grief if it fits, e.g. /support grief someone broke my farm"
                + "\n/report PLAYER REASON - report a player privately. /donate - help pay for the server.")
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false))));
    }

    /** First word as category when it is one of CATEGORIES, otherwise "other". */
    static String category(String message) {
        String first = message.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return CATEGORIES.contains(first) ? first : "other";
    }

    private int send(ServerPlayer player, String kind, String target, String message) {
        message = message.strip();
        if (message.length() < 3) { usage(player); return 0; }
        String key = kind + ":" + player.getUUID();
        long now = System.currentTimeMillis() / 1000;
        long wait = COOLDOWN_SECONDS - (now - lastUse.getOrDefault(key, 0L));
        if (wait > 0) {
            player.sendSystemMessage(Component.literal("You can send another " + (kind.equals("report") ? "report" : "request") + " in " + (wait / 60) + " min " + (wait % 60) + " s.").withStyle(ChatFormatting.RED));
            return 0;
        }
        lastUse.put(key, now);
        var level = player.level();
        String dimension = level.dimension() == Level.NETHER ? "Nether" : level.dimension() == Level.END ? "End" : "Overworld";
        var json = new JsonObject();
        json.addProperty("kind", kind);
        json.addProperty("player", player.getGameProfile().name());
        if (target != null) json.addProperty("target", target);
        json.addProperty("category", kind.equals("report") ? "report" : category(message));
        json.addProperty("message", message.length() > 500 ? message.substring(0, 500) : message);
        json.addProperty("dimension", dimension);
        json.addProperty("x", player.getBlockX()); json.addProperty("y", player.getBlockY()); json.addProperty("z", player.getBlockZ());
        LOG.info("HOLYLOIS-SUPPORT {}", json);
        String name = player.getGameProfile().name();
        var note = Component.literal(kind.equals("report") ? "[Report] " : "[Help] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            .append(Component.literal(name + (target != null ? " about " + target : "") + ": " + message).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false)
                .withClickEvent(new ClickEvent.SuggestCommand("/tp " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(dimension + " " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ() + "\nClick to teleport there")))));
        var server = level.getServer();
        for (var other : server.getPlayerList().getPlayers())
            if (other != player && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(other.createCommandSourceStack())) other.sendSystemMessage(note);
        player.sendSystemMessage(Component.literal(kind.equals("report")
            ? "✦ Report sent privately to the staff. Thank you."
            : "✦ Sent! pjampjam gets it on Discord now and will reply here or there.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
