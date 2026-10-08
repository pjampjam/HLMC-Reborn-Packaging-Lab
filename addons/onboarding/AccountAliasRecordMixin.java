package holylois.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;

/** Late native registrations cannot turn a reserved alias into an unrelated account. */
@Pseudo
@Mixin(targets={"xyz.nikitacartes.easyauth.storage.database.LevelDB","xyz.nikitacartes.easyauth.storage.database.SQLite","xyz.nikitacartes.easyauth.storage.database.MySQL","xyz.nikitacartes.easyauth.storage.database.PostgreSQL","xyz.nikitacartes.easyauth.storage.database.MongoDB"},remap=false)
public abstract class AccountAliasRecordMixin {
    @Inject(method="registerUser",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void holylois$keepAliasOwner(PlayerEntryV1 entry,CallbackInfo ci){if(!holylois.AccountRequests.acceptsRecord(entry))ci.cancel();}
    @Inject(method="deleteUserData",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void holylois$reservePreviousName(String name,CallbackInfoReturnable<Boolean> ci){if(holylois.AccountRequests.reserved(name))ci.setReturnValue(false);}
}
