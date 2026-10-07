package holylois.boombox;

import java.util.*;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.PrimedTnt;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

/** OPAC owns membership. Rally positions are explicit, transient and never broadcast globally. */
public final class PartySupport {
    private record Mark(UUID author, String name, String dimension, int x, int y, int z, long until) {}
    private static final Map<UUID, Mark> marks = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private static final Map<UUID, UUID> lastParty = new HashMap<>();
    private static final Pattern UNSAFE = Pattern.compile("n[i1!]gg|f[a@]gg?[o0]t|f[a@]g$|r[e3]t[a@]rd|p[i1]d[o0a]r|k[i1]ke|ch[i1]nk|tr[a@]nny", Pattern.CASE_INSENSITIVE);
    public static String display(String name) { return UNSAFE.matcher(name).find() ? name.substring(0, 1).toUpperCase(Locale.ROOT) + "*****" : name; }
    public static boolean ready(ServerPlayer p) {
        if (!FabricLoader.getInstance().isModLoaded("easyauth")) return true;
        if (Auth.method == null) return false;
        try { return (boolean) Auth.method.invoke(p); }
        catch (ReflectiveOperationException error) { return false; }
    }
    private static final class Auth {
        static final java.lang.reflect.Method method;
        static {
            java.lang.reflect.Method found = null;
            try { found = Class.forName("xyz.nikitacartes.easyauth.interfaces.PlayerAuth").getMethod("easyAuth$isAuthenticated"); }
            catch (ReflectiveOperationException ignored) {}
            method = found;
        }
    }
    public static IServerPartyAPI party(ServerPlayer p) {
        return FabricLoader.getInstance().isModLoaded("openpartiesandclaims") ? OpenPACServerAPI.get(p.level().getServer()).getPartyManager().getPartyByMember(p.getUUID()) : null;
    }
    public static boolean teammates(ServerPlayer first, ServerPlayer second) {
        if (first == second || !ready(first) || !ready(second)) return false;
        var party = party(first);
        return party != null && party.getMemberInfo(second.getUUID()) != null;
    }
    public static ServerPlayer owner(Entity entity) {
        if (entity instanceof ServerPlayer p) return p;
        if (entity instanceof Projectile p) return owner(p.getOwner());
        if (entity instanceof AreaEffectCloud cloud) return owner(cloud.getOwner());
        if (entity instanceof PrimedTnt tnt) return owner(tnt.getOwner());
        if (entity instanceof LightningBolt bolt) return bolt.getCause();
        return null;
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(PartyState.TYPE, PartyState.CODEC);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            var attacker = owner(source.getEntity());
            if (attacker == null) attacker = owner(source.getDirectEntity());
            return !(victim instanceof ServerPlayer player && attacker != null && teammates(attacker, player));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { marks.clear(); cooldown.clear(); lastParty.clear(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("rally")
            .executes(context -> rally(context.getSource().getPlayerOrException(), false))
            .then(Commands.literal("clear").executes(context -> rally(context.getSource().getPlayerOrException(), true)))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            long now = System.currentTimeMillis();
            marks.entrySet().removeIf(e -> e.getValue().until() <= now);
            cooldown.entrySet().removeIf(e -> e.getValue() + 30_000 < now);
            var online = new HashSet<UUID>();
            for (var player : server.getPlayerList().getPlayers()) {
                online.add(player.getUUID());
                var group = ready(player) ? party(player) : null;
                if (group == null) {
                    if (lastParty.remove(player.getUUID()) != null && ServerPlayNetworking.canSend(player, PartyState.TYPE)) ServerPlayNetworking.send(player, PartyState.empty());
                    continue;
                }
                lastParty.put(player.getUUID(), group.getId());
                if (ServerPlayNetworking.canSend(player, PartyState.TYPE)) ServerPlayNetworking.send(player, snapshot(player, group, now));
            }
            lastParty.keySet().retainAll(online);
        });
    }
    public static PartyState snapshot(ServerPlayer viewer, IServerPartyAPI group, long now) {
        if (!ready(viewer) || group.getMemberInfo(viewer.getUUID()) == null) return PartyState.empty();
        var server = viewer.level().getServer();
        var members = group.getMemberInfoStream().sorted(Comparator.comparing(m -> m.getUsername().toLowerCase(Locale.ROOT))).limit(64).map(m -> {
            var p = server.getPlayerList().getPlayer(m.getUUID());
            boolean online = p != null && ready(p);
            return new PartyState.Member(m.getUUID(), display(m.getUsername()), online, online && p.level() == viewer.level(),
                online ? p.getHealth() : 0, online ? p.getMaxHealth() : 20, online ? p.getAbsorptionAmount() : 0, online ? p.getFoodData().getFoodLevel() : 0);
        }).toList();
        var mark = marks.get(group.getId());
        // Leaving/kicking the sender revokes the mark immediately. A new party never inherits it.
        if (mark != null && (mark.until() <= now || group.getMemberInfo(mark.author()) == null)) { marks.remove(group.getId()); mark = null; }
        var rally = mark == null ? null : new PartyState.Rally(mark.author(), mark.name(), mark.dimension(), mark.x(), mark.y(), mark.z(), Math.max(1, (int)((mark.until() - now + 999) / 1000)));
        return new PartyState(group.getId(), members, rally);
    }
    public static int rally(ServerPlayer p, boolean clear) {
        if (!ready(p)) return 0;
        var group = party(p);
        if (group == null) { p.sendSystemMessage(Component.translatable("holylois.party.join_first")); return 0; }
        if (clear) {
            var previous = marks.get(group.getId());
            if (previous != null && !previous.author().equals(p.getUUID()) && !group.getOwner().getUUID().equals(p.getUUID())) return 0;
            marks.remove(group.getId()); return 1;
        }
        long now = System.currentTimeMillis();
        if (now < cooldown.getOrDefault(p.getUUID(), 0L)) { p.sendSystemMessage(Component.translatable("holylois.party.cooldown")); return 0; }
        var pos = p.blockPosition();
        marks.put(group.getId(), new Mark(p.getUUID(), display(p.getGameProfile().name()), p.level().dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), now + 90_000));
        cooldown.put(p.getUUID(), now + 10_000);
        p.sendSystemMessage(Component.translatable("holylois.party.rally_sent")); return 1;
    }
}
