package holylois;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import holylois.boombox.HomeState;
import holylois.boombox.TravelIntent;
import holylois.boombox.PartySupport;
import holylois.boombox.MenuOpen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** UI actions use existing native storage, command permissions and teleport queues. */
public final class TravelMenus {
    private static final Map<UUID,Long> nextAction=new HashMap<>();
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(HomeState.TYPE,HomeState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MenuOpen.TYPE,MenuOpen.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TravelIntent.TYPE,TravelIntent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TravelIntent.TYPE,(payload,context)-> handle(context.player(),payload));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->nextAction.clear());
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("homes").executes(c->{open(c.getSource().getPlayerOrException());return 1;})));
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("rally").then(Commands.literal("menu").executes(c->{
            var p=c.getSource().getPlayerOrException();if(PartySupport.ready(p)&&ServerPlayNetworking.canSend(p,MenuOpen.TYPE))ServerPlayNetworking.send(p,new MenuOpen(2));return 1;
        }))));
    }
    public static boolean safeName(String name) { return name!=null && name.matches("[A-Za-z0-9_.+\\-]{1,32}"); }
    private static String existing(PlayerData data,String name) {
        return data.getHomeNames().stream().filter(n->n.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public static HomeState snapshot(ServerPlayer p,boolean open) {
        var data=PlayerData.access(p);var names=data.getHomeNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        long revision=0xcbf29ce484222325L;var homes=new ArrayList<HomeState.Home>();
        for(String name:names) {
            var location=data.getHomeLocation(name);
            String key=name+'\0'+location.dim().identifier()+'\0'+location.x()+'\0'+location.y()+'\0'+location.z()+'\0'+location.pitch()+'\0'+location.headYaw();
            for(char c:key.toCharArray()) revision=(revision^c)*0x100000001b3L;
            if(homes.size()<128)homes.add(new HomeState.Home(name,location.dim().identifier().toString()));
        }
        return new HomeState(open,Math.min(128,Math.max(3,homes.size())),revision,List.copyOf(homes));
    }
    public static void open(ServerPlayer p) {
        if(!PartySupport.ready(p))return;
        if(ServerPlayNetworking.canSend(p,HomeState.TYPE)) ServerPlayNetworking.send(p,snapshot(p,true));
        else run(p,"home list");
    }
    private static void refresh(ServerPlayer p){if(ServerPlayNetworking.canSend(p,HomeState.TYPE))ServerPlayNetworking.send(p,snapshot(p,false));}
    public static boolean openParty(ServerPlayer p) {
        if(!PartySupport.ready(p) || !ServerPlayNetworking.canSend(p,MenuOpen.TYPE))return false;
        ServerPlayNetworking.send(p,new MenuOpen(1));return true;
    }
    private static void reply(ServerPlayer p,String key) { p.sendSystemMessage(Component.translatable("holylois.travel."+key)); }
    private static void run(ServerPlayer p,String command) { p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),command); }
    private static boolean allowed(ServerPlayer p,String command) {
        var parsed=p.level().getServer().getCommands().getDispatcher().parse(command,p.createCommandSourceStack());
        return parsed.getExceptions().isEmpty() && !parsed.getReader().canRead() && parsed.getContext().getCommand()!=null;
    }
    public static boolean rename(PlayerData data,String oldName,String newName) throws Exception {
        String old=existing(data,oldName);
        if(old==null || !safeName(newName))return false;
        String collision=existing(data,newName);
        if(collision!=null && !collision.equals(old))return false;
        if(old.equals(newName))return true;
        if(!safeName(old) || !allowed(data.getPlayer(),"home set "+newName) || !allowed(data.getPlayer(),"home delete "+old))return false;
        var storage=((holylois.mixins.HomeStorageAccess)(Object)data).holylois$homes();
        var location=storage.get(old);
        // Rename in the native collection without consuming a fourth slot.
        storage.putCommand(newName,location);
        if(!old.equals(newName))storage.remove(old);
        data.setDirty();
        try { data.save();return true; }
        catch(Exception e) { storage.remove(newName);storage.put(old,location);data.setDirty();try{data.save();}catch(Exception restore){e.addSuppressed(restore);}throw e; }
    }
    public static void handle(ServerPlayer p,TravelIntent intent) {
        if(!PartySupport.ready(p))return;
        long now=System.currentTimeMillis();
        if(now<nextAction.getOrDefault(p.getUUID(),0L)){
            p.sendSystemMessage(Component.translatableWithFallback("holylois.travel.busy","Please wait a moment, then try again."));return;
        }
        nextAction.put(p.getUUID(),now+300);
        try {
            int action=intent.action();
            if(action==TravelIntent.HOMES){open(p);return;}
            if(action>=TravelIntent.SAVE && action<=TravelIntent.DELETE) {
                var data=PlayerData.access(p);String name=existing(data,intent.name());
                if(snapshot(p,false).revision()!=intent.revision()){reply(p,"changed");refresh(p);return;}
                if(action==TravelIntent.SAVE) {
                    if(!safeName(intent.name()) || name!=null){reply(p,"name_invalid");refresh(p);return;}
                    if(data.getHomeNames().size()>=3){reply(p,"full");refresh(p);return;}
                    run(p,"home set "+intent.name());refresh(p);return;
                }
                if(name==null){reply(p,"changed");refresh(p);return;}
                if(action==TravelIntent.RENAME) {
                    if(!rename(data,name,intent.nextName()))reply(p,"name_invalid");
                    else reply(p,"renamed");refresh(p);return;
                }
                if(!safeName(name)){reply(p,"legacy_name");return;}
                if(action==TravelIntent.DELETE){run(p,"home delete "+name);refresh(p);return;}
                if(CombatTag.refuseTeleport(p))return;
                run(p,"home tp "+name);return;
            }
            switch(action) {
                case TravelIntent.CREATE_PARTY -> run(p,"oparties create");
                case TravelIntent.INVITE -> PartyAliases.invite(p,intent.name());
                case TravelIntent.ACCEPT -> PartyAliases.accept(p,intent.name());
                case TravelIntent.LEAVE -> run(p,"oparties leave");
                case TravelIntent.DESTROY_PARTY -> {
                    var party=PartySupport.party(p);
                    if(party!=null && party.getOwner().getUUID().equals(p.getUUID()))run(p,"oparties destroy confirm");
                }
                case TravelIntent.RALLY -> PartySupport.rally(p,false);
                case TravelIntent.JOIN_RALLY -> {
                    var group=PartySupport.party(p);if(group==null)return;
                    var mark=PartySupport.snapshot(p,group,now).rally();if(mark==null || !mark.author().toString().equals(intent.name())){reply(p,"rally_gone");return;}
                    var target=p.level().getServer().getPlayerList().getPlayer(mark.author());
                    if(target==null || target==p || !PartySupport.ready(target) || !PartySupport.teammates(p,target)
                            || !target.level().dimension().equals(p.level().dimension()) || !target.level().dimension().identifier().toString().equals(mark.dimension())){reply(p,"rally_gone");return;}
                    if(CombatTag.refuseTeleport(p) || CombatTag.refuseTeleport(target))return;
                    run(p,"tpa "+target.getGameProfile().name());
                }
                default -> {}
            }
        } catch(Exception error) { reply(p,"failed");org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Travel menu action failed; native storage retained",error); }
    }
}
