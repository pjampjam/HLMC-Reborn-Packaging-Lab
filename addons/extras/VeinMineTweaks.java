package holylois.boombox;

import com.iamkaf.liteminer.api.event.LiteminerClientEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Client tweaks: LiteMiner shows only the selected block count, because the server allows only shapeless mining. */
public final class VeinMineTweaks implements ClientModInitializer {
    @Override public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("liteminer")) return;
        LiteminerClientEvents.MODIFY_HUD.register(context -> {
            var shapeName = context.selectedShape() == null ? null : context.selectedShape().displayName();
            if (shapeName != null) context.lines().removeIf(line -> line.getString().equals(shapeName.getString()));
        });
    }
}
