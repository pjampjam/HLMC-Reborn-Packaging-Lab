package holylois.mixins;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.types.NamedLocationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native collection access permits atomic rename without consuming an extra home slot. */
@Mixin(value=PlayerData.class,remap=false)
public interface HomeStorageAccess {
    @Accessor("homes") NamedLocationStorage holylois$homes();
}
