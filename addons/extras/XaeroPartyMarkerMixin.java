package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader;

/** Xaero centers the through-wall party member icon on their feet; in the world view it sits on their head instead (minimap unchanged). */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.element.render.world.MinimapElementWorldRendererHandler")
public abstract class XaeroPartyMarkerMixin {
    private static final double HOLY_LOIS_FALLBACK_EYES = 1.62;

    @WrapOperation(method = "transformAndRenderForRenderer", at = @At(value = "INVOKE",
        target = "Lxaero/hud/minimap/element/render/MinimapElementReader;getRenderY(Ljava/lang/Object;Ljava/lang/Object;F)D"))
    private double holyLoisHeadHeight(MinimapElementReader<?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original) {
        double y = original.call(reader, element, context, partialTicks);
        if (!(reader instanceof PlayerTrackerMinimapElementReader) || !(element instanceof PlayerTrackerMinimapElement<?> tracked)) return y;
        var level = Minecraft.getInstance().level;
        var player = level == null ? null : level.getPlayerByUUID(tracked.getPlayerId());
        return y + (player == null ? HOLY_LOIS_FALLBACK_EYES : player.getEyeHeight());
    }
}
