package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Map;

/**
 * REI's TagLoader hook (823 and 824) walks shared static tag maps. Farmer's Delight builds early loot tags on parallel
 * loot workers, so two builds at once crash startup ("Failed to load datapacks"). Any REI version gets the lock.
 */
@Mixin(TagLoader.class)
public abstract class ReiTagBuildMixin {
    @Unique private static final Object holyLoisTagBuildLock = new Object();
    @Unique private static final boolean holyLoisNeedsTagLock = FabricLoader.getInstance().isModLoaded("roughlyenoughitems");

    @WrapMethod(method = "build")
    private Map<?, ?> holyLoisSerialTagBuild(Map<?, ?> entries, Operation<Map<?, ?>> original) {
        if (!holyLoisNeedsTagLock) return original.call(entries);
        synchronized (holyLoisTagBuildLock) { return original.call(entries); }
    }
}
