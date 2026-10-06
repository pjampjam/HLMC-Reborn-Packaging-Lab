package holylois.boombox.mixins;

import holylois.boombox.FishingLineState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FishingHookRenderState.class)
public abstract class FishingLineStateMixin implements FishingLineState {
    @Unique private boolean holyLoisLocalOwner;
    public boolean holyLoisLocalOwner() { return holyLoisLocalOwner; }
    public void holyLoisLocalOwner(boolean value) { holyLoisLocalOwner = value; }
}
