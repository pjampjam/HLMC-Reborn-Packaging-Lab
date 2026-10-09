package holylois.boombox;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * A fish of 10 kg and more is lifted over the head with both arms (owner 2026-10-09): arms straight up and steady (no walk
 * sway), the fish lying flat on the fists above the head, tail on one side and head on the other (FishLook.carryPose
 * places it between the fists as drawn). The angles win over Fresh Animations through KeptArms.
 */
public final class FishCarry {
    private FishCarry() {}
    /**
     * Arms straight up beside the head (radians): no inward tilt, or the fists sink into the head and the first-person camera
     * clips through them (owner round 4).
     */
    public static float raise = -2.95f, inward = 0f;

    static boolean carrying(Object state) {
        return state instanceof HumanoidRenderState s && FishData.twoHanded(s.getMainHandItemStack());
    }

    public static void pose(Object model, Object state) {
        if (!(model instanceof HumanoidModel<?> humanoid) || !carrying(state)) return;
        set(humanoid.rightArm, true); set(humanoid.leftArm, false);
    }

    private static void set(ModelPart arm, boolean right) {
        arm.xRot = raise; arm.yRot = 0; arm.zRot = right ? inward : -inward;
    }
}
