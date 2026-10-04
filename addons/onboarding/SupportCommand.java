package holylois;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import java.net.URI;
import java.util.List;

/** /support: how to chip in for the server. Donations never buy anything in game (Minecraft server rules). */
final class SupportCommand {
    private SupportCommand() {}

    /** label is shown in chat; help appears when hovering the label or the address. */
    record Wallet(String label, String address, String help) {}
    // Public receiving addresses of the server owner.
    static final List<Wallet> WALLETS = List.of(
        new Wallet("NEAR", "holylois.near", "NEAR Protocol. holylois.near is a readable account name: send NEAR or tokens on NEAR."),
        new Wallet("Solana", "Lv4hNPTamrenSL8DA9gwun4p1vQ4dT6v2EtHkCKFxvj", "Solana network: SOL or Solana tokens such as USDC."),
        new Wallet("TON", "UQAyixj0K6K9Nm2quzxM29VK5c2c-sjB1jKVKjhO1XNF7V92", "The Open Network (Telegram wallets): TON or USDT on TON."),
        new Wallet("TRON", "TWWuxbKAtJccPUBP8St2GnTHJJvRkhmBZH", "TRON network: TRX or USDT (TRC-20). Send only on TRON."),
        new Wallet("Bitcoin (SegWit)", "bc1q27h48nld84vp86ry6m5feu02yztrk9l7m3095f",
            "Native SegWit address (starts with bc1). Every modern Bitcoin wallet can send to it. Only BTC on the Bitcoin network."),
        new Wallet("EVM", "0xE161999E0779267689cB9F74f7beC0f7eB9b1c2A",
            "One address for every EVM network: Ethereum, Base, Arbitrum, Optimism, BNB Chain, Polygon. "
                + "Send ETH, USDC, USDT and similar; choose the network in your wallet."));

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("support").executes(context -> {
            context.getSource().sendSystemMessage(message());
            return 1;
        }));
    }

    static MutableComponent message() {
        var text = Component.literal("♥ Support Holy Lois").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal("\nHoly Lois is run by pjampjam for friends. If you want to help with the domain and future upgrades, "
                + "you can send crypto to one of these. It never buys anything in game; it just keeps the lights on.")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
        for (var wallet : WALLETS)
            text.append(Component.literal("\n" + wallet.label() + ": ").withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(wallet.help())))))
                .append(Component.literal(wallet.address()).withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(false)
                    .withClickEvent(new ClickEvent.CopyToClipboard(wallet.address()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy\n").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(wallet.help()).withStyle(ChatFormatting.GRAY))))));
        text.append(Component.literal("\nHover a network for details, click an address to copy it. Thank you! ").withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)))
            .append(Component.literal("holylois.com/support").withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(false).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://holylois.com/support")))));
        return text;
    }
}
