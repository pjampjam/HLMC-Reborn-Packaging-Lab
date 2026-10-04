package holylois;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

/**
 * Random placement for first joins, random respawns and /rtp. Never lands in or next to a land claim: a spot is skipped
 * when any claim is within one chunk, then vanilla spreadplayers finds safe ground near it (no fire or liquid).
 */
final class RandomTeleport {
    static final int MIN_RADIUS = 250, ATTEMPTS = 15, COOLDOWN_SECONDS = 300, DELAY_SECONDS = 3;
    private final Random random = new Random();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private record Pending(int startTick, double x, double y, double z) {}
    private final Map<UUID, Pending> pending = new HashMap<>();

    /** A random point between min and max blocks from 0,0, uniform over the ring. */
    static int[] spot(Random random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(random.nextDouble() * ((double) max * max - (double) min * min) + (double) min * min);
        return new int[] {(int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance)};
    }

    /** Moves the player to a safe, unclaimed overworld spot. Returns false after all attempts fail. */
    boolean place(MinecraftServer server, ServerPlayer player, int maxRadius) {
        var overworld = Level.OVERWORLD.identifier();
        var source = server.createCommandSourceStack().withLevel(server.overworld()).withSuppressedOutput();
        String name = player.getGameProfile().name();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int[] xz = spot(random, Math.min(MIN_RADIUS, maxRadius / 2), maxRadius);
            if (Claims.nearClaim(server, overworld, xz[0] >> 4, xz[1] >> 4, 1)) continue;
            try {
                int moved = server.getCommands().getDispatcher().execute("spreadplayers " + xz[0] + " " + xz[1] + " 0 16 false " + name, source);
                if (moved < 1) continue;
            } catch (Exception noSafeGround) { continue; }
            if (player.level().dimension() == Level.OVERWORLD && !Claims.nearClaim(server, overworld, player.getBlockX() >> 4, player.getBlockZ() >> 4, 1)) return true;
        }
        return false;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp").executes(context -> request(context.getSource().getPlayerOrException())));
    }

    private int request(ServerPlayer player) {
        var server = player.level().getServer();
        if (player.level().dimension() != Level.OVERWORLD) { player.sendSystemMessage(Component.literal("/rtp works in the Overworld only.").withStyle(ChatFormatting.RED)); return 0; }
        long now = System.currentTimeMillis() / 1000, last = lastUse.getOrDefault(player.getUUID(), 0L);
        if (now - last < COOLDOWN_SECONDS) {
            long wait = COOLDOWN_SECONDS - (now - last);
            player.sendSystemMessage(Component.literal("/rtp is ready again in " + (wait / 60) + " min " + (wait % 60) + " s.").withStyle(ChatFormatting.RED));
            return 0;
        }
        pending.put(player.getUUID(), new Pending(server.getTickCount(), player.getX(), player.getY(), player.getZ()));
        player.sendSystemMessage(Component.literal("Random teleport in " + DELAY_SECONDS + " seconds, stand still...").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    /** Runs every second: cancels moved players, teleports the ones whose countdown finished. */
    void tick(MinecraftServer server, int maxRadius) {
        if (pending.isEmpty()) return;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending wait = entry.getValue();
            if (player == null) { iterator.remove(); continue; }
            if (player.distanceToSqr(wait.x(), wait.y(), wait.z()) > 1.0) {
                iterator.remove();
                player.sendSystemMessage(Component.literal("Random teleport cancelled: you moved.").withStyle(ChatFormatting.RED));
                continue;
            }
            if (server.getTickCount() - wait.startTick() < DELAY_SECONDS * 20) continue;
            iterator.remove();
            if (place(server, player, maxRadius)) {
                lastUse.put(player.getUUID(), System.currentTimeMillis() / 1000);
                player.sendSystemMessage(Component.literal("✦ Welcome to the wilderness. /home set NAME to remember this place.").withStyle(ChatFormatting.GREEN));
            } else player.sendSystemMessage(Component.literal("No safe unclaimed spot found, try again in a moment.").withStyle(ChatFormatting.RED));
        }
    }

    void forget(UUID id) { pending.remove(id); }
}
