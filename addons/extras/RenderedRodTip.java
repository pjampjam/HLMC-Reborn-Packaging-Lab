package holylois.boombox;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tr7zw.firstperson.FirstPersonModelCore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Capture the vanilla rod's metal tip after the actual arm/item transforms, before geometry is queued. */
public final class RenderedRodTip {
    private static final ThreadLocal<Boolean> CAPTURE = ThreadLocal.withInitial(() -> false);
    private static Vec3 tip;
    private static Object level;
    private static long capturedAt;
    public static long captures, attachments;
    public static boolean bodyView() {
        var client = Minecraft.getInstance();
        if (client.player == null || !client.options.getCameraType().isFirstPerson()
            || !FabricLoader.getInstance().isModLoaded("firstperson")) return false;
        var core = FirstPersonModelCore.instance;
        return core != null && core.getLogicHandler().shouldApplyThirdPerson(false) && !core.getLogicHandler().hideArmsAndItems();
    }
    public static boolean rodView() {
        var client = Minecraft.getInstance();
        return client.player != null && (!client.options.getCameraType().isFirstPerson() || bodyView());
    }
    public static boolean shadowPass() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return false;
        try {
            var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return (boolean)type.getMethod("isRenderingShadowPass").invoke(type.getMethod("getInstance").invoke(null));
        } catch (ReflectiveOperationException error) { return false; }
    }
    public static boolean begin(ItemStack stack, HumanoidArm arm, boolean localBody) {
        boolean previous = CAPTURE.get();
        var player = Minecraft.getInstance().player;
        boolean selected = player != null && arm == (player.getMainHandItem().is(Items.FISHING_ROD) ? player.getMainArm() : player.getMainArm().getOpposite());
        CAPTURE.set(stack.is(Items.FISHING_ROD) && selected && rodView() && localBody && !shadowPass());
        return previous;
    }
    public static void end(boolean previous) { CAPTURE.set(previous); }
    public static void capture(PoseStack stack) {
        if (!CAPTURE.get()) return;
        // The metal tip occupies pixels 13-14 at row 1 of the vanilla 16x16 rod sprite.
        var point = stack.last().pose().transformPosition(new Vector3f(14f / 16f, 14.5f / 16f, 0.5f));
        if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z) || point.lengthSquared() > 64) return;
        var client = Minecraft.getInstance();
        tip = client.gameRenderer.mainCamera().position().add(point.x, point.y, point.z);
        level = client.level;
        capturedAt = System.nanoTime();
        captures++;
    }
    public static Vec3 current() {
        if (!rodView() || shadowPass() || level != Minecraft.getInstance().level || System.nanoTime() - capturedAt > 250_000_000L) return null;
        return tip;
    }
}
