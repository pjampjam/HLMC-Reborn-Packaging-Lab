package holylois.mixins;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH3.3.4 can bind before Chunky's provider exists during dedicated-server level loading. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractChunkyAccessor",remap=false)
public abstract class ChunkyStartupMixin {
    @Inject(method="tryRunFirstTimeSetup",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void holylois$waitForChunky(CallbackInfo ci) {
        if(!FabricLoader.getInstance().getModContainer("distanthorizons").map(m->m.getMetadata().getVersion().getFriendlyString().startsWith("3.3.4")).orElse(false))return;
        try { Class.forName("org.popcraft.chunky.ChunkyProvider").getMethod("get").invoke(null); }
        catch(java.lang.reflect.InvocationTargetException error) {
            if(error.getCause() instanceof IllegalStateException)ci.cancel();
        } catch(ReflectiveOperationException ignored) {}
    }
}
