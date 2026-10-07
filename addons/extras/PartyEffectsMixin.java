package holylois.boombox.mixins;

import holylois.boombox.PartySupport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep helpful potions; reject harmful effects with a known party-player source. */
@Mixin(LivingEntity.class)
public abstract class PartyEffectsMixin {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisPartyEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> result) {
        var attacker = PartySupport.owner(source);
        if ((Object)this instanceof ServerPlayer player && attacker != null && !effect.getEffect().value().isBeneficial() && PartySupport.teammates(attacker, player)) result.setReturnValue(false);
    }
}
