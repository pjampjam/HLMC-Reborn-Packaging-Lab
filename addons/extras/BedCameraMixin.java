package holylois.boombox.mixins;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In bed the first-person camera sits inside your own head and sees the inside of the model. The camera is lifted a little above
 * the pillow and moved a little forward, so the view is clear while the bed stays the bed. First person only, own player only.
 */
@Mixin(Camera.class)
public abstract class BedCameraMixin {
    @Shadow private Entity entity;
    @Shadow private boolean detached;
    @Shadow protected abstract void move(float forwards, float up, float left);

    @Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
    private void holyLoisBedView(float partialTicks, CallbackInfo callback) {
        if (!detached && entity instanceof LivingEntity living && living.isSleeping()
            && entity == net.minecraft.client.Minecraft.getInstance().player) move(0.3f, 0.3f, 0f);
    }
}
