package holylois.boombox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lanterns have no block entity. Save their complete relic stack alongside the dimension. */
public final class PlacedRelics extends SavedData {
    private record Entry(long position, ItemStack stack) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("position").forGetter(Entry::position),
            ItemStack.CODEC.fieldOf("stack").forGetter(Entry::stack)
        ).apply(i, Entry::new));
    }
    private static final Codec<PlacedRelics> CODEC = Entry.CODEC.listOf().xmap(PlacedRelics::new,
        data -> data.items.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
    private static final SavedDataType<PlacedRelics> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("holylois", "placed_relics"), PlacedRelics::new, CODEC, null);
    private final Map<Long, ItemStack> items = new HashMap<>();
    private record Removed(ItemStack stack, int tick) {}
    private final Map<Long, Removed> removed = new HashMap<>();

    private PlacedRelics() {}
    private PlacedRelics(List<Entry> entries) { entries.forEach(e -> items.put(e.position(), e.stack())); }
    private static PlacedRelics get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }

    public static void remember(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem block) || !level.getBlockState(pos).is(block.getBlock())) return;
        var data = get(level);
        data.removed.remove(pos.asLong());
        if (!stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(Legends.LEGEND_KEY)) {
            if (data.items.remove(pos.asLong()) != null) data.setDirty();
            return;
        }
        data.items.put(pos.asLong(), stack.copyWithCount(1));
        data.setDirty();
    }

    public static void changed(ServerLevel level, BlockPos pos) {
        var data = get(level);
        ItemStack stack = data.items.get(pos.asLong());
        if (stack == null || stack.getItem() instanceof BlockItem block && level.getBlockState(pos).is(block.getBlock())) return;
        data.items.remove(pos.asLong());
        // Player mining removes the block before asking its old state for drops.
        int tick = level.getServer().getTickCount();
        data.removed.entrySet().removeIf(e -> e.getValue().tick() != tick);
        data.removed.put(pos.asLong(), new Removed(stack, tick));
        data.setDirty();
    }

    public static List<ItemStack> restore(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
        if (drops.isEmpty()) return drops;
        var data = get(level);
        ItemStack stack = data.items.get(pos.asLong());
        boolean retired = stack == null;
        if (retired) {
            var old = data.removed.get(pos.asLong());
            if (old != null && old.tick() == level.getServer().getTickCount()) stack = old.stack();
        }
        if (stack == null || !(stack.getItem() instanceof BlockItem block) || !state.is(block.getBlock())) return drops;
        // Respect the tool, gamerules and explosion loot decision. Replace only a matching block item drop.
        for (int i = 0; i < drops.size(); i++) if (drops.get(i).is(stack.getItem())) {
            var result = new java.util.ArrayList<>(drops);
            result.set(i, stack.copyWithCount(1));
            if (retired) data.removed.remove(pos.asLong());
            return result;
        }
        return drops;
    }
}
