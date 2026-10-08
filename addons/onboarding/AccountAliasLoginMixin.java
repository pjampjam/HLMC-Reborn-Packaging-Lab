package holylois.mixins;

import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** EasyAuth supplies the stable UUID; aliases resolve to the canonical name before player data/authentication. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class AccountAliasLoginMixin {
    @Shadow private GameProfile authenticatedProfile;
    @ModifyVariable(method="finishLoginAndWaitForClient",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private GameProfile holylois$canonicalName(GameProfile profile){
        var canonical=holylois.AccountRequests.canonical(profile);
        if(canonical!=profile)authenticatedProfile=canonical;
        return canonical;
    }
}
