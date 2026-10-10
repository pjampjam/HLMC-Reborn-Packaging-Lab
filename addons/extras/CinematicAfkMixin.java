package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Ji AFK Cinematic 2.3.2: while boombox music plays nearby, one idle minute fades into the cinematic (owner 2026-10-10). */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.afk.AFKDetector", remap = false)
public abstract class CinematicAfkMixin {
    @org.spongepowered.asm.mixin.Unique private static final int MUSIC_IDLE_TICKS = 20 * 60;

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "FIELD", target = "Lcom/ji/afkcinematic/config/ModConfig;afkThresholdTicks:I"), remap = false)
    private static int holyLoisMusicMinute(int configured) {
        return holylois.boombox.BoomboxPulse.musicNearby() ? Math.min(configured, MUSIC_IDLE_TICKS) : configured;
    }
}
