package holylois.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nikitacartes.easyauth.EasyAuth;
import xyz.nikitacartes.easyauth.interfaces.PlayerAuth;

/** EasyAuth freezes movement/player ticks; vanilla must not time that frozen state as flight. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AuthFloatingMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;
    @Shadow private boolean clientVehicleIsFloating;
    @Shadow private int aboveGroundVehicleTickCount;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void holylois$authFloating(CallbackInfo callback) {
        if (player == null || EasyAuth.extendedConfig.allowMovement
            || ((PlayerAuth)player).easyAuth$isAuthenticated()) return;
        clientIsFloating = false;
        aboveGroundTickCount = 0;
        clientVehicleIsFloating = false;
        aboveGroundVehicleTickCount = 0;
    }
}
