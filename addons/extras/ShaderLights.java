package holylois.boombox;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * Dynamic lights follow the shader state. With a shader pack on, the pack lights what you hold in the right colour, so the
 * first-person light of LambDynamicLights is switched off; everything else (dropped torches, mobs) keeps its dynamic light.
 * With shaders off, dynamic lights are fully on again, including the first-person light. Dynamic lights are also switched back
 * on if they were off, because shipping Holy Lois means they should work. The state is applied when it changes (and once at
 * start), so a manual change in between is respected until the next switch. Reflection only: both mods are optional and client-side.
 */
final class ShaderLights {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("HolyLoisExtras");
    private static Method shadersOn, getMode, setMode, getSelf, getEntities, setValue;
    private static Object config, irisApi;
    private static Class<?> modeClass;
    private static Boolean last;
    private static int ticks;

    static void register() {
        var loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT || !loader.isModLoaded("iris") || !loader.isModLoaded("lambdynlights")) return;
        try {
            var iris = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = iris.getMethod("getInstance").invoke(null);
            shadersOn = iris.getMethod("isShaderPackInUse");
            var lamb = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            config = lamb.getField("config").get(lamb.getMethod("get").invoke(null));
            modeClass = Class.forName("dev.lambdaurora.lambdynlights.DynamicLightsMode");
            getMode = config.getClass().getMethod("getDynamicLightsMode");
            setMode = config.getClass().getMethod("setDynamicLightsMode", modeClass);
            getSelf = config.getClass().getMethod("getSelfLightSource");
            getEntities = config.getClass().getMethod("getEntitiesLightSource");
            setValue = Class.forName("dev.lambdaurora.lambdynlights.config.SettingEntry").getMethod("set", Object.class);
            Files.deleteIfExists(loader.getConfigDir().resolve("holylois-ldl-restore.txt")); // left by 1.3.1
        } catch (Throwable error) {
            LOG.warn("Shader-aware dynamic lights unavailable", error);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { if (++ticks % 10 == 0) update(); });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void update() {
        try {
            boolean shaders = (boolean) shadersOn.invoke(irisApi);
            if (last != null && last == shaders) return;
            last = shaders;
            if (((Enum<?>) getMode.invoke(config)).name().equals("OFF")) setMode.invoke(config, Enum.valueOf((Class) modeClass, "FANCY"));
            setValue.invoke(getEntities.invoke(config), Boolean.TRUE);
            setValue.invoke(getSelf.invoke(config), !shaders);
            LOG.info("Shader pack {}: dynamic lights on, first-person light {}", shaders ? "on" : "off", shaders ? "off (the shader lights what you hold)" : "on");
        } catch (Throwable error) {
            LOG.warn("Could not switch dynamic lights", error);
            ticks = Integer.MIN_VALUE / 2; // slow down after a failure
            last = null;
        }
    }
}
