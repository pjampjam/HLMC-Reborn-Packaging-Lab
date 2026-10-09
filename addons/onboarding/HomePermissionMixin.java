package holylois.mixins;

import com.fibermc.essentialcommands.EssentialCommands;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restore declared level0 fallback for own homes and travel requests, retaining explicit overrides. */
@Pseudo
@Mixin(targets="com.fibermc.essentialcommands.ECPerms",remap=false)
public abstract class HomePermissionMixin {
    private static boolean holylois$affectedVersion(){
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("essential_commands")
            .map(mod->mod.getMetadata().getVersion().getFriendlyString().equals("0.42.0-mc26.3")).orElse(false);
    }
    @Inject(method="check(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/resources/Identifier;I)Z",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private static void holylois$ownHomes(CommandSourceStack source,Identifier permission,int fallback,CallbackInfoReturnable<Boolean> ci){
        if(!holylois$affectedVersion()||fallback!=0||source.getPlayer()==null||!EssentialCommands.CONFIG.USE_PERMISSIONS_API)return;
        String path=permission.getPath();
        if(java.util.Set.of("essentialcommands:home.tp","essentialcommands:home.set","essentialcommands:home.delete").contains(permission.toString())
                || permission.getNamespace().equals("essentialcommands") && (path.equals("tpa") || path.startsWith("tpa.") || path.equals("tpahere") || path.equals("tpaccept") || path.equals("tpdeny")))
            ci.setReturnValue(((PermissionContextOwner)(Object)source).checkPermission(permission,PermissionLevel.byId(0)));
    }

    @Inject(method="getHighestNumericPermission",at=@At("RETURN"),cancellable=true,require=1,remap=false)
    private static void holylois$starterHomes(CommandSourceStack source,Identifier[] group,CallbackInfoReturnable<Integer> ci){
        if(!holylois$affectedVersion() || ci.getReturnValue()!=-1 || source.getPlayer()==null || !EssentialCommands.CONFIG.USE_PERMISSIONS_API)return;
        if(group.length==0 || java.util.Arrays.stream(group).anyMatch(node->!node.toString().startsWith("essentialcommands:home.limit.")))return;
        var starter=Identifier.fromNamespaceAndPath("essentialcommands","home.limit.3");
        if(java.util.Arrays.asList(group).contains(starter)
                && ((PermissionContextOwner)(Object)source).checkPermission(starter)==net.fabricmc.fabric.api.util.TriState.DEFAULT)
            ci.setReturnValue(3);
    }
}
