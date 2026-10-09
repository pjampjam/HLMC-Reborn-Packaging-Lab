package holylois;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test servers only (-Dholylois.commandAudit=true, set by gradlew :dev:runServer): writes every command path a player
 * without operator rights can use or tab-complete to holylois-player-commands.txt, so admin tools that leak are easy to spot.
 */
final class CommandAudit {
    private CommandAudit() {}

    static void run(MinecraftServer server) {
        if (!Boolean.getBoolean("holylois.commandAudit")) return;
        try {
            var player = FakePlayer.get(server.overworld());
            CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withPermission(PermissionSet.NO_PERMISSIONS);
            var lines = new ArrayList<String>();
            for (var node : server.getCommands().getDispatcher().getRoot().getChildren()) walk(node, source, "/" + node.getName(), 0, lines);
            lines.sort(null);
            Files.write(Path.of("holylois-player-commands.txt"), lines);
            var admin = source.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
            long kept = AdminCommands.ADMIN_ONLY.stream().map(name -> server.getCommands().getDispatcher().getRoot().getChild(name))
                .filter(node -> node != null && node.canUse(admin)).count();
            org.slf4j.LoggerFactory.getLogger("HolyLois").info("Holy Lois command audit: {} player command paths in holylois-player-commands.txt; operators keep {} admin-only commands",
                lines.size(), kept);
            // Lazily loaded mixin targets: load them now and confirm our handlers were woven in.
            try {
                var importer = Class.forName("de.maxhenkel.audioplayer.audioloader.importer.UrlImporter");
                long hooks = java.util.Arrays.stream(importer.getDeclaredMethods()).filter(m -> m.getName().contains("holyLois")).count();
                org.slf4j.LoggerFactory.getLogger("HolyLois").info("Holy Lois audio link guard: {} hooks in AudioPlayer's UrlImporter", hooks);
            } catch (ClassNotFoundException missing) {
                org.slf4j.LoggerFactory.getLogger("HolyLois").info("Holy Lois audio link guard: AudioPlayer not installed");
            }
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Holy Lois command audit failed", error);
        }
    }

    private static void walk(CommandNode<CommandSourceStack> node, CommandSourceStack source, String path, int depth, List<String> lines) {
        if (!node.canUse(source)) return;
        lines.add(path);
        if (depth < 2) for (var child : node.getChildren()) walk(child, source, path + " " + child.getUsageText(), depth + 1, lines);
    }
}
