package holylois.mixins;

import io.github.flemmli97.flan.player.PlayerClaimData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rewords only Flan's post-death unlock instruction. All claim notifications remain. */
@Mixin(value=PlayerClaimData.class, remap=false)
public abstract class DeathNoticeMixin {
    @Redirect(method="clone", at=@At(value="INVOKE",
        target="Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"), remap=false)
    private void holyLoisDeathNotice(ServerPlayer player, Component original, boolean overlay) {
        player.sendSystemMessage(Component.literal("Your dropped items are protected. Recover them within 30 minutes while the area is loaded.")
            .withStyle(ChatFormatting.GRAY), overlay);
    }
}
