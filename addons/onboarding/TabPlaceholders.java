package holylois;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Loaded only when Placeholder API is present, so the addon still runs without it. */
final class TabPlaceholders {
    private TabPlaceholders() {}

    static String duration(long seconds) {
        long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60;
        return d > 0 ? d + "d " + h + "h " + m + "m" : h > 0 ? h + "h " + m + "m" : m + "m";
    }

    static void register() {
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "nameday"), (context, argument) -> {
            String names = NameDays.join(NameDays.today());
            return PlaceholderResult.value(Component.literal(names.isEmpty() ? "no name day today" : names));
        });
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "bots_today"),
            (context, argument) -> PlaceholderResult.value(Component.literal(String.format("%,d", BotWall.get().today))));
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "bots_latest"),
            (context, argument) -> PlaceholderResult.value(Component.literal(BotWall.latest(BotWall.get()))));
        // Matches the server list (MiniMOTD "just x more"): always one free slot shown, e.g. 3/4.
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "slots"),
            (context, argument) -> PlaceholderResult.value(Component.literal(String.valueOf(context.server().getPlayerCount() + 1))));
        // Played time without AFK, like %player:playtime% (days, hours, minutes): %holylois:playtime%
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "playtime"), (context, argument) -> {
            if (!context.hasServerPlayer()) return PlaceholderResult.invalid("No player");
            var player = context.serverPlayer();
            long raw = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            return PlaceholderResult.value(Component.literal(duration(Afk.effectiveTicks(player.getUUID(), raw) / 20)));
        });
        // %holylois:top title% advances the viewer's category when the page reappears; %holylois:top 1..3% are the rows.
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "top"), (context, argument) -> {
            if (!context.hasServerPlayer()) return PlaceholderResult.invalid("No player");
            var category = Leaderboards.view(context.serverPlayer().getUUID(), context.server().getTickCount());
            String arg = argument == null ? "title" : argument.strip();
            if (arg.equals("title")) return PlaceholderResult.value(Component.literal(category.title()));
            try { return PlaceholderResult.value(Component.literal(Leaderboards.row(category, Integer.parseInt(arg)))); }
            catch (NumberFormatException error) { return PlaceholderResult.invalid("Use title, 1, 2 or 3"); }
        });
    }
}
