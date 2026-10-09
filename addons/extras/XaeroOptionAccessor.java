package holylois.boombox.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The translation key of a Xaero world map right-click entry (e.g. gui.xaero_right_click_map_teleport). */
@Pseudo
@Mixin(targets = "xaero.map.gui.dropdown.rightclick.RightClickOption")
public interface XaeroOptionAccessor {
    @Accessor("name") String holylois$name();
}
