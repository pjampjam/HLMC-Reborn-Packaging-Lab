package holylois;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /botwall: operators see which usernames SSH bots tried, how often and when last. No IPs are kept. */
final class BotWallCommand {
    private BotWallCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("botwall")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(context -> {
                var data = BotWall.get();
                var source = context.getSource();
                source.sendSuccess(() -> Component.literal("Bot wall: " + String.format("%,d", data.today) + " SSH bot attempts today, "
                    + String.format("%,d", data.allTime) + " in total, " + data.banned + " currently banned. None got in.")
                    .withStyle(ChatFormatting.GOLD), false);
                int rank = 0;
                for (var entry : data.top) {
                    if (++rank > 10) break;
                    String name = BotWall.clean(entry.name);
                    int place = rank;
                    source.sendSuccess(() -> Component.literal(place + ". " + name + "  x" + entry.count + "  last " + entry.lastSeen)
                        .withStyle(ChatFormatting.GRAY), false);
                }
                return Math.max(1, rank);
            }));
    }
}
