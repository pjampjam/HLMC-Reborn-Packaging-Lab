package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Map;

/** REI 26.3.823 uses shared mutable tag caches. Early loot tags can build in parallel. */
@Mixin(TagLoader.class)
public abstract class ReiTagBuildMixin {
    @Unique private static final Object holyLoisTagBuildLock = new Object();
    @Unique private static final boolean holyLoisNeedsTagLock = FabricLoader.getInstance()
        .getModContainer("roughlyenoughitems")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("26.3.823")).orElse(false);

    @WrapMethod(method = "build")
    private Map<?, ?> holyLoisSerialTagBuild(Map<?, ?> entries, Operation<Map<?, ?>> original) {
        if (!holyLoisNeedsTagLock) return original.call(entries);
        synchronized (holyLoisTagBuildLock) { return original.call(entries); }
    }
}
