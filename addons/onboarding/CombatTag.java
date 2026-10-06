package holylois;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Enemy;
import java.util.*;

/**
 * Combat tag, PvP only: hitting or being hit by a player tags both for 20 s. Tagged players cannot teleport away and die
 * when they log out (loot unlocked). Mob hits only block teleports for 5 s, so /home cannot cancel a fall or a creeper.
 */
public final class CombatTag {
    private CombatTag() {}
    static final long PVP_MILLIS = 20_000, MOB_MILLIS = 5_000;
    static final Set<String> BLOCKED = Set.of("rtp", "randomteleport", "home", "homes", "tpa", "tpahere", "tpask", "tpaccept", "spawn", "back", "warp", "wild");
    private static final Map<UUID, Long> pvpUntil = new HashMap<>(), mobUntil = new HashMap<>();
    private static final Set<UUID> pvpDeath = new HashSet<>(), lastDeathPvp = new HashSet<>();

    static void onDamage(ServerPlayer victim, DamageSource source) {
        long now = System.currentTimeMillis();
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            tag(victim, now); tag(attacker, now);
        } else if (source.getEntity() instanceof Enemy) {
            mobUntil.merge(victim.getUUID(), now + MOB_MILLIS, Math::max);
        }
    }

    private static void tag(ServerPlayer player, long now) {
        Long before = pvpUntil.put(player.getUUID(), now + PVP_MILLIS);
        if (before == null || before < now)
            player.sendSystemMessage(Component.literal("⚔ In combat: logging out now kills you, and teleports are blocked for 20 s.").withStyle(ChatFormatting.RED));
    }

    static boolean inPvp(UUID id) { return pvpUntil.getOrDefault(id, 0L) > System.currentTimeMillis(); }

    /** Seconds until this player may teleport again, 0 when free. */
    public static long teleportWait(UUID id) {
        long until = Math.max(pvpUntil.getOrDefault(id, 0L), mobUntil.getOrDefault(id, 0L));
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    /** For the command mixin: true when the command must be refused. */
    public static boolean refuse(ServerPlayer player, String command) {
        if (!blocksCommand(command)) return false;
        return refuseTeleport(player);
    }

    static boolean blocksCommand(String command) {
        String root = command.strip();
        if (root.startsWith("/")) root = root.substring(1);
        root = root.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        return BLOCKED.contains(root);
    }

    public static boolean refuseTeleport(ServerPlayer player) {
        long wait = teleportWait(player.getUUID());
        if (wait == 0) return false;
        player.sendSystemMessage(Component.literal("⚔ You are in combat. Teleporting works again in " + wait + " s.").withStyle(ChatFormatting.RED));
        return true;
    }

    /**
     * Called once at death, right before the drops: a PvP death (killed by a player, during a fight, or a combat
     * logout) is remembered for the death notice, and the tags are cleared.
     */
    static boolean diedInPvp(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean pvp = pvpDeath.remove(id) || player.getKillCredit() instanceof ServerPlayer || inPvp(id);
        if (pvp) lastDeathPvp.add(id);
        pvpUntil.remove(id); mobUntil.remove(id);
        return pvp;
    }

    /** At respawn: was the last death a PvP one? (Then the death notice leaves out the coordinates.) */
    static boolean lastDeathWasPvp(UUID id) { return lastDeathPvp.remove(id); }

    /** Logout while tagged: the player dies on the spot with unlocked loot, and everyone hears about it. */
    static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        boolean tagged = inPvp(id);
        pvpUntil.remove(id); mobUntil.remove(id);
        if (!tagged || !server.isRunning() || !player.isAlive()) return;
        pvpDeath.add(id);
        player.kill(player.level());
        var message = Component.literal("⚔ " + player.getGameProfile().name() + " logged out during a fight and died.").withStyle(ChatFormatting.RED);
        for (var other : server.getPlayerList().getPlayers()) if (other != player) other.sendSystemMessage(message);
    }

    /** Action bar countdown while tagged (called every second). */
    static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (var player : server.getPlayerList().getPlayers()) {
            long left = pvpUntil.getOrDefault(player.getUUID(), 0L) - now;
            if (left > 0) player.sendOverlayMessage(Component.literal("⚔ Combat " + ((left + 999) / 1000) + " s").withStyle(ChatFormatting.RED));
        }
        pvpUntil.values().removeIf(until -> until < now - 1000);
        mobUntil.values().removeIf(until -> until < now);
    }
}
