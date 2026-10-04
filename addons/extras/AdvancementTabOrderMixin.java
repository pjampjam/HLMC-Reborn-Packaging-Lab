package holylois.boombox.mixins;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Advancement tabs follow the order their roots enter the tree. Holy Lois comes first, then the vanilla tabs in
 * the order of play (Minecraft, Nether, End, Adventure, Husbandry), then mod tabs alphabetically.
 */
@Mixin(AdvancementTree.class)
public abstract class AdvancementTabOrderMixin {
    private static final List<String> ORDER = List.of("minecraft:story/root", "minecraft:nether/root", "minecraft:end/root",
        "minecraft:adventure/root", "minecraft:husbandry/root");

    @ModifyVariable(method = "addAll", at = @At("HEAD"), argsOnly = true, require = 1)
    private Iterable<AdvancementHolder> holyLoisTabOrder(Iterable<AdvancementHolder> holders) {
        var list = new ArrayList<AdvancementHolder>();
        holders.forEach(list::add);
        list.sort(Comparator.comparingInt(AdvancementTabOrderMixin::rank).thenComparing(holder -> rank(holder) == 50 ? holder.id().toString() : ""));
        return list;
    }

    private static int rank(AdvancementHolder holder) {
        if (holder.value().parent().isPresent()) return 100;
        String id = holder.id().toString();
        if (holder.id().getNamespace().equals("holylois")) return 0;
        int vanilla = ORDER.indexOf(id);
        return vanilla >= 0 ? 1 + vanilla : 50;
    }
}
