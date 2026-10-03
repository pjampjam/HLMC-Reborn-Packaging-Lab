package holylois;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Loaded only when Placeholder API is present, so the addon still runs without it. */
final class QuotePlaceholder {
    private QuotePlaceholder() {}

    static void register() {
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "quote"),
            (context, argument) -> PlaceholderResult.value(Component.literal(Quotes.text(Quotes.today()))));
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "quote_author"),
            (context, argument) -> PlaceholderResult.value(Component.literal(Quotes.author(Quotes.today()))));
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "nameday"), (context, argument) -> {
            String names = NameDays.join(NameDays.today());
            return PlaceholderResult.value(Component.literal(names.isEmpty() ? "no name day today" : names));
        });
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "bots_today"),
            (context, argument) -> PlaceholderResult.value(Component.literal(String.format("%,d", BotWall.get().today))));
        Placeholders.registerServer(Identifier.fromNamespaceAndPath("holylois", "bots_latest"),
            (context, argument) -> PlaceholderResult.value(Component.literal(BotWall.latest(BotWall.get()))));
    }
}
