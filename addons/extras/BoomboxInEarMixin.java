package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Simple Voice Chat 2.6.24 plays every client sound through writeSync(audio, volume, position, category, maxDistance); the
 * position drives its panning and distance volume. Boombox and disc music near you moves into your ears (BoomboxInEar).
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase", remap = false)
public abstract class BoomboxInEarMixin {
    @WrapMethod(method = "writeSync", remap = false)
    private void holyLoisInEar(short[] data, float volume, Vec3 position, String category, float maxDistance, Operation<Void> original) {
        original.call(data, volume, holylois.boombox.BoomboxInEar.blend(position, category), category, maxDistance);
    }
}
