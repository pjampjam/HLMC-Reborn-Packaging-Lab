package holylois;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;

/** Load vanilla glint textures before Iris asks for them inside an active render pass. */
public final class HolyLoisGlint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!client.isGameLoadFinished()) return;
            var textures = client.getTextureManager();
            textures.getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
            textures.getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR);
        });
    }
}
