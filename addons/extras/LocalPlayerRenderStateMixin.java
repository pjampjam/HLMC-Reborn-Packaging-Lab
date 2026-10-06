package holylois.boombox.mixins;

import holylois.boombox.LocalPlayerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LocalPlayerRenderStateMixin implements LocalPlayerRenderState {
    @Unique private boolean holyLoisLocalPlayer;
    public boolean holyLoisLocalPlayer() { return holyLoisLocalPlayer; }
    public void holyLoisLocalPlayer(boolean value) { holyLoisLocalPlayer = value; }
}
