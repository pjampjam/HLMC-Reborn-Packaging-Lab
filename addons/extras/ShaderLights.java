package holylois.boombox;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shader packs light held and dropped torches themselves, in the right colour. LambDynamicLights adds its own white light on top,
 * so while a shader pack is on it steps aside, and the player's own mode comes back when shaders are switched off. The original
 * mode is remembered in a small file so a crash or restart never leaves dynamic lights off for good. Reflection only: both mods
 * are optional and client-side.
 */
final class ShaderLights {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("HolyLoisExtras");
    private static Method shadersOn, getMode, setMode;
    private static Object config;
    private static Class<?> modeClass;
    private static Path marker;
    private static int ticks;

    static void register() {
        var loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT || !loader.isModLoaded("iris") || !loader.isModLoaded("lambdynlights")) return;
        try {
            var iris = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            shadersOn = iris.getMethod("isShaderPackInUse");
            var lamb = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            var instance = lamb.getMethod("get").invoke(null);
            config = lamb.getField("config").get(instance);
            modeClass = Class.forName("dev.lambdaurora.lambdynlights.DynamicLightsMode");
            getMode = config.getClass().getMethod("getDynamicLightsMode");
            setMode = config.getClass().getMethod("setDynamicLightsMode", modeClass);
            marker = loader.getConfigDir().resolve("holylois-ldl-restore.txt");
        } catch (Throwable error) {
            LOG.warn("Shader-aware dynamic lights unavailable", error);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { if (++ticks % 10 == 0) update(); });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void update() {
        try {
            boolean shaders = (boolean) shadersOn.invoke(iris());
            String current = ((Enum<?>) getMode.invoke(config)).name();
            if (shaders && !current.equals("OFF")) {
                Files.writeString(marker, current);
                setMode.invoke(config, Enum.valueOf((Class) modeClass, "OFF"));
                LOG.info("Shader pack on: dynamic lights paused (was {})", current);
            } else if (!shaders && Files.exists(marker)) {
                String saved = Files.readString(marker).trim();
                Files.delete(marker);
                setMode.invoke(config, Enum.valueOf((Class) modeClass, saved));
                LOG.info("Shader pack off: dynamic lights back to {}", saved);
            }
        } catch (Throwable error) {
            LOG.warn("Could not switch dynamic lights", error);
            ticks = Integer.MIN_VALUE / 2; // slow down after a failure
        }
    }

    private static Object iris() throws Exception {
        return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
    }
}
