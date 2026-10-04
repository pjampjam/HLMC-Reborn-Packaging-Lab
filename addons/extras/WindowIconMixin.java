package holylois.boombox.mixins;

import com.mojang.blaze3d.platform.Window;
import holylois.boombox.CrownIcon;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.io.InputStream;
import java.util.List;

/** The game window and taskbar show the Holy Lois crown instead of the grass block. */
@Mixin(Window.class)
public abstract class WindowIconMixin {
    @ModifyVariable(method = "setIcon(Ljava/util/List;)V", at = @At("HEAD"), argsOnly = true, require = 1)
    private List<IoSupplier<InputStream>> holyLoisCrown(List<IoSupplier<InputStream>> icons) {
        var crown = CrownIcon.sizes();
        return crown == null ? icons : crown;
    }
}
