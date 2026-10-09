package holylois;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Mod commands that register without any permission check but are only for admins: players neither see them in tab
 * completion nor can run them. CristelLib's /dump_runtime_pack writes files to any path a player types, the others are
 * diagnostics (LuckPerms checks its own subcommands, but its root shows up for everyone). Applied after every command
 * rebuild (start and /reload, AdminCommandsMixin). Operators and the console keep them.
 */
public final class AdminCommands {
    private AdminCommands() {}
    static final Set<String> ADMIN_ONLY = Set.of("dump_runtime_pack", "lp", "luckperms", "perm", "perms", "permission", "permissions",
        "spark", "modernfix", "amber");
    private static final Predicate<CommandSourceStack> GAMEMASTER = source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    public static void lock(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Field requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);
            for (String name : ADMIN_ONLY) {
                CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(name);
                if (node == null) continue;
                @SuppressWarnings("unchecked") var original = (Predicate<CommandSourceStack>) requirement.get(node);
                requirement.set(node, original.and(GAMEMASTER));
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("HolyLois").error("Holy Lois could not lock admin-only commands", error);
        }
    }
}
