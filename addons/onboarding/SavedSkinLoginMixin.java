package holylois.mixins;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compatibility for SkinsRestorer15.12.6 on26.3, upstream issue2162/PR2163. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class SavedSkinLoginMixin {
    @Shadow private GameProfile authenticatedProfile;

    @Inject(method = "finishLoginAndWaitForClient", at = @At("HEAD"), require = 1)
    private void holylois$keepSelectedTextures(GameProfile profile, CallbackInfo ci) {
        // SkinsRestorer passes a new profile to this method; vanilla uses the field for the player.
        if (FabricLoader.getInstance().getModContainer("skinsrestorer")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("15.12.6")).orElse(false)
                && authenticatedProfile != null && profile != null
                && authenticatedProfile.id().equals(profile.id())) authenticatedProfile = profile;
    }
}
