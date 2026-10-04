package holylois.boombox.mixins;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
import java.util.UUID;

/** Zone titles sit below the boss bars, so they need to know how many are showing. */
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> holyLois$events();
}
