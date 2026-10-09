package holylois.boombox;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fresh Animations (through EMF) rewrites arm angles while a model is drawn, after vanilla and Not Enough Animations have
 * posed them. For poses where the game's own arms matter (holding a map, eating or drinking, bows, crossbows, shields,
 * spyglass, horn, lanterns and torches, our two-armed fish carry), the angles from setupAnim are kept per render state and
 * forced back right before each arm is transformed (KeptArmsPartMixin), on the body and on its armor alike.
 * Everything else (walking, idle, legs, head, body) stays Fresh Animations.
 */
public final class KeptArms {
    private KeptArms() {}
    private static final Map<Object, float[]> KEPT = new WeakHashMap<>();
    private static ModelPart right, left;
    private static float[] active;

    /** Pose types where the game's arms win. */
    static boolean wins(HumanoidRenderState s) {
        if (FishData.twoHanded(s.getMainHandItemStack()) || s.isUsingItem) return true;
        if (special(s.rightArmPose) || special(s.leftArmPose)) return true;
        return held(s.rightHandItemStack) || held(s.leftHandItemStack);
    }

    private static boolean special(HumanoidModel.ArmPose pose) {
        return pose != null && pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM;
    }

    private static boolean held(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.FILLED_MAP) || stack.is(Items.MAP) || stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.REDSTONE_TORCH)) return true;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("lantern") || path.equals("boombox");
    }

    /** End of setupAnim (after Not Enough Animations): apply the fish carry, then remember the arms if they should win. */
    public static void capture(Object model, Object state) {
        if (!(model instanceof HumanoidModel<?> humanoid) || !(state instanceof HumanoidRenderState s)) return;
        FishCarry.pose(model, state);
        if (!wins(s)) { KEPT.remove(state); return; }
        var r = humanoid.rightArm; var l = humanoid.leftArm;
        KEPT.put(state, new float[]{r.xRot, r.yRot, r.zRot, l.xRot, l.yRot, l.zRot});
    }

    /** Put the kept angles on a model now (before held items are placed on the hands). */
    public static void restore(Object model, Object state) {
        var kept = KEPT.get(state);
        if (kept != null && model instanceof HumanoidModel<?> humanoid) set(humanoid.rightArm, humanoid.leftArm, kept);
    }

    public static void beginDraw(Object model, Object state) {
        var kept = KEPT.get(state);
        if (kept == null || !(model instanceof HumanoidModel<?> humanoid)) return;
        right = humanoid.rightArm; left = humanoid.leftArm; active = kept;
        set(right, left, kept);
    }

    public static void endDraw() { right = null; left = null; active = null; }

    public static void transforming(ModelPart part) {
        if (active == null) return;
        if (part == right) { part.xRot = active[0]; part.yRot = active[1]; part.zRot = active[2]; }
        else if (part == left) { part.xRot = active[3]; part.yRot = active[4]; part.zRot = active[5]; }
    }

    private static void set(ModelPart r, ModelPart l, float[] a) {
        r.xRot = a[0]; r.yRot = a[1]; r.zRot = a[2]; l.xRot = a[3]; l.yRot = a[4]; l.zRot = a[5];
    }
}
