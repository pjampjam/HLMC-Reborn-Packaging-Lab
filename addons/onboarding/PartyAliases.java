package holylois;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** OPAC remains the sole command and party authority. */
final class PartyAliases {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> install(server.getCommands().getDispatcher()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) install(server.getCommands().getDispatcher());
        });
    }
    static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        var original = dispatcher.getRoot().getChild("oparties");
        if (original == null) return;
        for (String name : new String[] {"party", "group"}) {
            if (dispatcher.getRoot().getChild(name) != null) continue;
            var alias = Commands.literal(name).requires(original.getRequirement()).redirect(original);
            if (original.getCommand() != null) alias.executes(original.getCommand());
            dispatcher.register(alias);
        }
    }
}
