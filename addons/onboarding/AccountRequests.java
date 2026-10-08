package holylois;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.authlib.GameProfile;
import holylois.boombox.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.storage.LevelResource;
import xyz.nikitacartes.easyauth.EasyAuth;
import xyz.nikitacartes.easyauth.interfaces.PlayerAuth;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;
import xyz.nikitacartes.easyauth.utils.PlayersCache;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** One-use administrator requests. Renames keep the verified UUID, using EasyAuth's native forced UUID. */
public final class AccountRequests {
    static final Gson GSON=new Gson();
    static record Alias(UUID uuid,String canonical) {}
    static final class Grant { UUID id,uuid;int kind,attempts;long expires,nextAttemptAt;String issuer,tokenHash;boolean processing; }
    static final class Store { Map<String,Alias> aliases=new java.util.concurrent.ConcurrentHashMap<>();Map<String,Grant> grants=new HashMap<>(); }
    private static volatile Store store=new Store();private static Path file,secretDirectory,backupDirectory;
    private static final Map<UUID,Long> reminders=new HashMap<>(),rate=new HashMap<>();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static String key(UUID id,int kind){return id+":"+kind;}
    private static String lower(String name){return name.toLowerCase(Locale.ROOT);}
    public static GameProfile canonical(GameProfile profile){
        if(profile==null)return null;
        var alias=store.aliases.get(lower(profile.name()));
        return alias!=null && alias.uuid().equals(profile.id())?new GameProfile(profile.id(),alias.canonical(),profile.properties()):profile;
    }
    public static boolean acceptsRecord(PlayerEntryV1 entry){
        if(entry==null||entry.username==null)return false;
        var alias=store.aliases.get(lower(entry.username));
        if(alias==null || alias.uuid().equals(entry.uuid))return true;
        try{return alias.uuid().equals(UUID.fromString(entry.forcedUuid));}catch(Exception invalid){return false;}
    }
    public static boolean reserved(String name){return name!=null&&store.aliases.containsKey(lower(name));}
    private static boolean admin(CommandSourceStack s){return (s.getPlayer()==null || PartySupport.ready(s.getPlayer()))
        && ((PermissionContextOwner)(Object)s).checkPermission(Identifier.fromNamespaceAndPath("holylois","account.admin"),PermissionLevel.byId(4));}
    private static UUID identity(PlayerEntryV1 entry,MinecraftServer server){
        var online=server.getPlayerList().getPlayerByName(entry.username);if(online!=null)return online.getUUID();
        var alias=store.aliases.get(lower(entry.username));if(alias!=null)return alias.uuid();
        if(entry.forcedUuid!=null&&!entry.forcedUuid.isEmpty())return UUID.fromString(entry.forcedUuid);
        return entry.onlineAccount==PlayerEntryV1.OnlineAccount.TRUE?entry.uuid:UUID.nameUUIDFromBytes(("OfflinePlayer:"+entry.username).getBytes(StandardCharsets.UTF_8));
    }
    private static Grant pending(UUID id,int kind){var g=store.grants.get(key(id,kind));return g!=null && !g.processing && g.expires>System.currentTimeMillis()?g:null;}
    private static void atomic(Path p,String text)throws java.io.IOException{
        Files.createDirectories(p.getParent());var temp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(temp,text,StandardCharsets.UTF_8);
        try{Files.setPosixFilePermissions(temp,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
        try{Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void save()throws java.io.IOException{
        atomic(file,GSON.toJson(store));
        var names=new HashMap<String,String>();var aliases=new HashMap<String,String>();
        store.aliases.forEach((name,alias)->{names.put(alias.uuid().toString(),alias.canonical());aliases.put(name,alias.uuid().toString());});
        // Exporters read this identity-only cache, never the grant store or authentication records.
        try{atomic(file.resolveSibling("account-profile-names.json"),GSON.toJson(Map.of("names",names,"aliases",aliases)));}
        catch(java.io.IOException error){org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Account display-name cache could not refresh.");}
    }
    static String digest(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static PlayerEntryV1 copy(PlayerEntryV1 entry){
        var copy=PlayerEntryV1.gson.fromJson(entry.toJson(),PlayerEntryV1.class);
        // EasyAuth deliberately excludes its database key/derived identity from the stored JSON.
        copy.username=entry.username;copy.usernameLowerCase=lower(entry.username);copy.uuid=entry.uuid;return copy;
    }
    static boolean tokenMatches(String token,String expected){return token!=null && expected!=null && MessageDigest.isEqual(digest(token).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));}
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(AccountNotice.TYPE,AccountNotice.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AccountIntent.TYPE,AccountIntent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AccountIntent.TYPE,(payload,context)->handle(context.player(),payload));
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var root=Commands.literal("account");
            for(int kind:new int[]{1,2}){
                String type=kind==1?"rename":"password";
                var branch=Commands.literal(type).executes(c->{open(c.getSource().getPlayerOrException(),kind);return 1;});
                for(String action:new String[]{"grant","revoke","status"})branch.then(Commands.literal(action).requires(AccountRequests::admin)
                    .then(Commands.argument("player",StringArgumentType.word()).executes(c->manage(c.getSource(),kind,action,StringArgumentType.getString(c,"player")))));
                root.then(branch);
            }
            d.register(root);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var root=server.getWorldPath(LevelResource.ROOT).resolve("holylois");file=root.resolve("account-requests.json");secretDirectory=root.resolve("private-recovery");backupDirectory=root.resolve("private-account-backups");
            try{if(Files.exists(file)){if(Files.size(file)>16*1024*1024)throw new java.io.IOException("Oversized request store");store=GSON.fromJson(Files.readString(file),Store.class);if(store==null || store.aliases==null || store.grants==null)throw new java.io.IOException("Invalid request store");store.aliases=new java.util.concurrent.ConcurrentHashMap<>(store.aliases);}}
            catch(Exception error){throw new IllegalStateException("Account request store could not load; refusing unsafe fallback");}
            // Only this bounded recovery payload is allowed before password authentication.
            if(!EasyAuth.extendedConfig.allowedCustomPackets.contains("holylois:account_intent"))EasyAuth.extendedConfig.allowedCustomPackets.add("holylois:account_intent");
            if(!EasyAuth.extendedConfig.allowedCommands.contains("account password"))EasyAuth.extendedConfig.allowedCommands.add("account password");
        });
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->{
            var p=h.player;var alias=store.aliases.get(lower(p.getGameProfile().name()));
            if(alias!=null && !alias.uuid().equals(p.getUUID()))h.disconnect(Component.literal("Account identity changed. Reconnect using your saved account name."));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->reminders.remove(h.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{reminders.clear();rate.clear();store=new Store();file=null;});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%20!=0)return;long now=System.currentTimeMillis();
            rate.entrySet().removeIf(entry->entry.getValue()<=now);
            for(var p:server.getPlayerList().getPlayers()){
                var password=pending(p.getUUID(),2);var rename=pending(p.getUUID(),1);var g=password!=null?password:rename;
                if(g==null || g.kind==1&&!PartySupport.ready(p) || now<reminders.getOrDefault(p.getUUID(),0L))continue;
                boolean first=!reminders.containsKey(p.getUUID());reminders.put(p.getUUID(),now+30_000);
                String command="/account "+(g.kind==1?"rename":"password");
                p.sendSystemMessage(reminder(g.kind));
                notice(p,g,first&&g.kind==2,"");
            }
        });
    }
    static Component reminder(int kind){
        String command="/account "+(kind==1?"rename":"password");
        var link=Component.translatableWithFallback("holylois.travel.account_open","[Open form]").withStyle(style->style.withColor(net.minecraft.ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)));
        return Component.translatableWithFallback("holylois.travel.account_reminder_link_"+kind,kind==1?"Choose your new name: %s or /account rename. Your progress stays with you.":"Set a new password: %s or /account password. Ask the admin for your recovery code if you cannot log in.",link).withStyle(net.minecraft.ChatFormatting.YELLOW);
    }
    private static int manage(CommandSourceStack source,int kind,String action,String name){
        if(!admin(source))return 0;
        try{
            if(!name.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException();
            var entry=EasyAuth.DB.getUserData(name);
            if(entry==null || (entry.password==null||entry.password.isEmpty()) && entry.onlineAccount!=PlayerEntryV1.OnlineAccount.TRUE){source.sendSystemMessage(Component.literal("No registered account has that name."));return 0;}
            UUID id=identity(entry,source.getServer());String key=key(id,kind);var old=store.grants.get(key);
            if(action.equals("status")){source.sendSystemMessage(Component.literal(pending(id,kind)==null?"No active request.":"One-use request pending until "+java.time.Instant.ofEpochMilli(old.expires)+"."));return 1;}
            if(action.equals("revoke")){store.grants.remove(key);save();if(old!=null)Files.deleteIfExists(secretDirectory.resolve(old.id+".token"));source.sendSystemMessage(Component.literal("Request revoked."));return 1;}
            var grant=new Grant();grant.id=UUID.randomUUID();grant.uuid=id;grant.kind=kind;grant.issuer=source.getPlayer()==null?"console":source.getPlayer().getUUID().toString();grant.expires=System.currentTimeMillis()+(kind==1?7*86_400_000L:3_600_000L);
            if(kind==2){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);grant.tokenHash=digest(token);atomic(secretDirectory.resolve(grant.id+".token"),token);}
            store.grants.put(key,grant);try{save();}catch(Exception error){if(old==null)store.grants.remove(key);else store.grants.put(key,old);Files.deleteIfExists(secretDirectory.resolve(grant.id+".token"));throw error;}
            if(old!=null)Files.deleteIfExists(secretDirectory.resolve(old.id+".token"));reminders.remove(id);
            source.sendSystemMessage(Component.literal("One-use request granted. "+(kind==2?"Private recovery code file: holylois/private-recovery/"+grant.id+".token. Deliver it privately after verifying ownership.":"The player chooses an available new name after authenticating.")));return 1;
        }catch(Exception error){source.sendSystemMessage(Component.literal("Account request failed. No password or progress was changed."));return 0;}
    }
    private static void notice(ServerPlayer p,Grant g,boolean open,String message){
        if(ServerPlayNetworking.canSend(p,AccountNotice.TYPE))ServerPlayNetworking.send(p,new AccountNotice(g.kind,g.id,(int)EasyAuth.extendedConfig.minPasswordLength,PartySupport.ready(p),open,message));
    }
    private static void open(ServerPlayer p,int kind){
        var grant=pending(p.getUUID(),kind);
        if(grant==null || kind==1&&!PartySupport.ready(p)){p.sendSystemMessage(Component.literal("No available request. Log in or ask an admin."));return;}
        if(!ServerPlayNetworking.canSend(p,AccountNotice.TYPE)){p.sendSystemMessage(Component.literal("Update your Holy Lois pack to use this private account form."));return;}notice(p,grant,true,"");
    }
    public static synchronized void handle(ServerPlayer p,AccountIntent intent){
        var grant=pending(p.getUUID(),intent.kind());if(grant==null || !grant.id.equals(intent.grant()))return;
        long now=System.currentTimeMillis();if(now<rate.getOrDefault(p.getUUID(),0L)||now<grant.nextAttemptAt)return;rate.put(p.getUUID(),now+1000);
        String error="failed";
        try{
            if(CombatTag.refuseAccountLock(p)){notice(p,grant,true,"failed");return;}
            if(intent.kind()==2){grant.nextAttemptAt=now+1000;save();}
            if(intent.kind()==1){
                if(!PartySupport.ready(p)){notice(p,grant,true,"login_first");return;}
                error=rename(p,intent.value(),grant);
            }else if(intent.kind()==2){
                if(!PartySupport.ready(p)&&!tokenMatches(intent.token(),grant.tokenHash)){
                    grant.attempts++;if(grant.attempts>=5){store.grants.remove(key(p.getUUID(),2));save();Files.deleteIfExists(secretDirectory.resolve(grant.id+".token"));}else save();notice(p,grant,true,"code_invalid");return;
                }
                if(!intent.value().equals(intent.confirmation()))error="password_mismatch";
                else if(intent.value().length()<EasyAuth.extendedConfig.minPasswordLength || intent.value().length()>passwordMaximum(EasyAuth.extendedConfig.maxPasswordLength))error="password_length";
                else if(EasyAuth.config.enableGlobalPassword&&!EasyAuth.config.singleUseGlobalPassword)error="failed";
                else{
                    var auth=(PlayerAuth)p;var original=auth.easyAuth$getPlayerEntryV1();
                    if(original==null)throw new IllegalStateException();
                    var changed=copy(original);char[] chars=intent.value().toCharArray();
                    try{changed.password=xyz.nikitacartes.easyauth.utils.AuthHelper.hashPassword(chars);}finally{Arrays.fill(chars,'\0');}
                    changed.lastAuthenticatedDate=java.time.ZonedDateTime.now().minusYears(1);
                    changed.lastIp="";changed.uuid=p.getUUID();changed.forcedUuid=p.getUUID().toString();changed.onlineAccount=PlayerEntryV1.OnlineAccount.FALSE;
                    grant.processing=true;save();
                    if(!EasyAuth.DB.updateUserData(changed))throw new IllegalStateException();
                    PlayersCache.put(changed.username,changed);auth.easyAuth$setPlayerEntryV1(changed);
                    // Revoke this session too: the player proves the new password through the normal login form.
                    auth.easyAuth$canSkipAuth(false);auth.easyAuth$setAuthenticated(false);error="";
                }
            }
            if(!error.isEmpty()){notice(p,grant,true,error);return;}
            store.grants.remove(key(p.getUUID(),intent.kind()));save();Files.deleteIfExists(secretDirectory.resolve(grant.id+".token"));reminders.remove(p.getUUID());
            if(ServerPlayNetworking.canSend(p,AccountNotice.TYPE)){
                ServerPlayNetworking.send(p,new AccountNotice(0,grant.id,0,false,false,""));
                if(intent.kind()==1)p.connection.disconnect(Component.literal("Your name is now "+intent.value()+". Reconnect with this name or your saved previous name. Both use the same protected profile and progress."));
                else p.connection.disconnect(Component.literal("Password changed. Reconnect and log in with your new password."));
            }
        }catch(Exception failure){notice(p,grant,true,"failed");}
    }
    static long passwordMaximum(long configured){return configured<0?100:Math.min(100,configured);}
    private static String rename(ServerPlayer p,String name,Grant grant)throws Exception{
        if(!name.matches("[A-Za-z0-9_]{3,16}") || !PartySupport.display(name).equals(name) || Set.of("admin","console","server","holylois").contains(lower(name)))return "name_invalid";
        var reserved=store.aliases.get(lower(name));
        if(reserved!=null&&!reserved.uuid().equals(p.getUUID()) || name.equalsIgnoreCase(p.getGameProfile().name()))return "name_taken";
        var destination=EasyAuth.DB.getUserData(name);
        if(destination!=null && (reserved==null||!reserved.uuid().equals(p.getUUID())) && ((destination.password!=null&&!destination.password.isEmpty()) || destination.onlineAccount==PlayerEntryV1.OnlineAccount.TRUE))return "name_taken";
        if(p.level().getServer().getPlayerList().getPlayerByName(name)!=null)return "name_taken";
        var old=((PlayerAuth)p).easyAuth$getPlayerEntryV1();if(old==null||old.password==null||old.password.isEmpty())return "password_first";
        if(!old.username.equalsIgnoreCase(p.getGameProfile().name()))return "failed";
        var backup=new com.google.gson.JsonObject();backup.addProperty("name",old.username);backup.addProperty("uuid",p.getUUID().toString());backup.add("data",com.google.gson.JsonParser.parseString(old.toJson()));atomic(backupDirectory.resolve(UUID.randomUUID()+".json"),backup.toString());
        var retained=copy(old);retained.uuid=p.getUUID();retained.forcedUuid=p.getUUID().toString();retained.onlineAccount=PlayerEntryV1.OnlineAccount.FALSE;
        var renamed=copy(retained);renamed.username=name;renamed.usernameLowerCase=lower(name);renamed.lastAuthenticatedDate=java.time.ZonedDateTime.now().minusYears(1);renamed.lastIp="";
        var alias=new Alias(p.getUUID(),old.username);
        store.aliases.put(lower(old.username),alias);store.aliases.put(lower(name),alias);grant.processing=true;
        // The durable mapping reserves both names before native cache/database writes.
        save();
        try{
            if(!EasyAuth.DB.updateUserData(retained))throw new IllegalStateException();
            EasyAuth.DB.registerUser(renamed);var confirmed=EasyAuth.DB.getUserData(name);
            if(confirmed==null||!p.getUUID().toString().equals(confirmed.forcedUuid)||!Objects.equals(renamed.password,confirmed.password))throw new IllegalStateException();
            PlayersCache.put(retained.username,retained);PlayersCache.put(name,renamed);
            if(p.level().getServer().getPlayerList().getWhiteList().isWhiteListed(p.nameAndId()))p.level().getServer().getPlayerList().getWhiteList().add(new net.minecraft.server.players.UserWhiteListEntry(new net.minecraft.server.players.NameAndId(p.getUUID(),name)));
            var committed=new Alias(p.getUUID(),name);store.aliases.replaceAll((key,value)->value.uuid().equals(p.getUUID())?committed:value);save();
            // Inventory, homes, claims, economy and permissions retain their UUID; no world files are rewritten.
            return "";
        }catch(Exception error){
            // Keep the new alias reserved to this same UUID for safe retry, never expose it as a fresh account.
            if(EasyAuth.DB.updateUserData(old)){PlayersCache.put(old.username,old);var held=new Alias(p.getUUID(),old.username);store.aliases.replaceAll((key,value)->value.uuid().equals(p.getUUID())?held:value);grant.processing=false;save();}throw error;
        }
    }
}
