package holylois.boombox;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class RelicTooltips {
    static void register(){
        ItemTooltipCallback.EVENT.register((stack,context,flags,lines)->{
            String key=switch(RelicPowers.id(stack)){
                case "loiss_lantern"->"lantern";
                case "staff_of_quiet_roads"->"staff";
                case "circlet_of_the_wanderer"->"circlet";
                default->null;
            };
            if(key==null)return;
            lines.add(Component.translatable(key.equals("circlet")?"item.modifiers.head":"holylois.relic.held").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("holylois.relic."+key).withStyle(ChatFormatting.DARK_GREEN));
            if(key.equals("lantern")){
                lines.add(Component.translatable("holylois.relic.lantern_aura").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("holylois.relic.lantern_range").withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("holylois.relic.no_stack").withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
