package holylois;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import holylois.boombox.PartySupport;

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
        // The native root has no action; make the existing More commands button useful too.
        if(original.getCommand()==null)dispatcher.register(Commands.literal("oparties").executes(c->help(c.getSource())));
        for (String name : new String[] {"party", "group"}) {
            if (dispatcher.getRoot().getChild(name) != null) continue;
            var alias = Commands.literal(name).requires(original.getRequirement());
            for(var child:original.getChildren())if(!child.getName().equals("join"))alias.then(child);
            alias.then(Commands.literal("invite").then(Commands.argument("player",StringArgumentType.word())
                .executes(c->invite(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"player")))));
            alias.then(Commands.literal("join").then(Commands.argument("inviter",StringArgumentType.word())
                .executes(c->accept(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"inviter")))));
            alias.then(Commands.literal("help").executes(c->help(c.getSource())));
            alias.executes(context -> {
                var player=context.getSource().getPlayer();
                if(player!=null && TravelMenus.openParty(player))return 1;
                return original.getCommand()==null?0:original.getCommand().run(context);
            });
            dispatcher.register(alias);
        }
    }
    public static int invite(ServerPlayer p,String name){
        if(!PartySupport.ready(p))return 0;
        if(!name.matches("[A-Za-z0-9_]{3,16}"))return invalid(p);
        run(p,"oparties member invite "+name);return 1;
    }
    public static int accept(ServerPlayer p,String inviter){
        if(!PartySupport.ready(p))return 0;
        String id;
        try{id=java.util.UUID.fromString(inviter).toString();}
        catch(IllegalArgumentException error){
            if(!inviter.matches("[A-Za-z0-9_]{3,16}"))return invalid(p);
            var author=p.level().getServer().getPlayerList().getPlayerByName(inviter);
            var party=author==null?null:PartySupport.party(author);
            if(party==null){p.sendSystemMessage(Component.translatableWithFallback("holylois.travel.inviter_unavailable","That player must be online and in the party. You can also accept through the party menu (')."));return 0;}
            id=party.getId().toString();
        }
        // Native join checks the real invitation, membership and party limit.
        run(p,"oparties join "+id);return 1;
    }
    private static int invalid(ServerPlayer p){
        p.sendSystemMessage(Component.translatableWithFallback("holylois.travel.player_invalid","Enter a player name first."));return 0;
    }
    private static void run(ServerPlayer p,String command){p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),command);}
    private static int help(CommandSourceStack source){
        source.sendSystemMessage(Component.translatableWithFallback("holylois.travel.party_commands","Party: /party create | /party invite NAME | /party join NAME | /party leave\nOwners can disband with /party destroy, then /party destroy confirm. Members share land access."));return 1;
    }
}
