package holylois;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** "/Home ..." (a capital from phone-style typing) runs "/home ..." from Essential Commands. */
final class HomeAlias {
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("Home")
            .executes(context -> run(context.getSource(), "home"))
            .then(Commands.argument("rest", StringArgumentType.greedyString())
                .executes(context -> run(context.getSource(), "home " + StringArgumentType.getString(context, "rest")))));
    }

    private static int run(CommandSourceStack source, String command) {
        source.getServer().getCommands().performPrefixedCommand(source, command);
        return 1;
    }
}
