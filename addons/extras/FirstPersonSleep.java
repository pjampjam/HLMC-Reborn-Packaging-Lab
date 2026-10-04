package holylois.boombox;

import dev.tr7zw.firstperson.api.ActivationHandler;
import dev.tr7zw.firstperson.api.FirstPersonAPI;
import net.minecraft.client.Minecraft;

/** In bed the FirstPerson body is skipped, as in vanilla, so the camera is not inside the head. Loaded only with FirstPerson. */
final class FirstPersonSleep {
    static void register() {
        FirstPersonAPI.registerPlayerHandler((ActivationHandler) () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.isSleeping();
        });
    }
}
