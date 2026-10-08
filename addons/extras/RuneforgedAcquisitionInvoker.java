package holylois.boombox.mixins;

import com.ultimatearpg.advancement.ArpgAdvancementService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ArpgAdvancementService.class, remap = false)
public interface RuneforgedAcquisitionInvoker {
    @Invoker("handleAcquisitionSource")
    static void holyLoisAcquired(ServerPlayer player, ItemStack stack) { throw new AssertionError(); }
}
