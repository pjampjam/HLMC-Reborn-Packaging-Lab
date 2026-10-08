package holylois.boombox;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

final class RareCatchHud {
    private static RareCatchNotice notice;private static long arrived,shown;
    private static final java.nio.file.Path FILE=java.nio.file.Path.of("config","holylois-catch-ui.json");
    private static boolean enabled=true;
    static void register(){
        try{if(java.nio.file.Files.exists(FILE))enabled=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(FILE),Boolean.class);}catch(Exception ignored){}
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d,a)->d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("catchcards")
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on").executes(c->setting(true)))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes(c->setting(false)))));
        ClientPlayNetworking.registerGlobalReceiver(RareCatchNotice.TYPE,(v,c)->{
            if(!enabled)return;notice=v;arrived=System.currentTimeMillis();shown=0;
            if(c.client().player!=null)c.client().player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.25f,1.4f);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->notice=null);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,Identifier.fromNamespaceAndPath("holylois","rare_catch"),(g,t)->{
            var mc=Minecraft.getInstance();if(notice==null||mc.player==null||mc.level==null)return;long now=System.currentTimeMillis();
            if(now-arrived>60_000){notice=null;return;}if(mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;
            if(shown==0)shown=now;if(now-shown>5000){notice=null;return;}
            var id=Identifier.tryParse(notice.item());if(id==null){notice=null;return;}
            var item=BuiltInRegistries.ITEM.getValue(id);if(item==null){notice=null;return;}var stack=new ItemStack(item);
            // Centred just above the action bar line and the health/food rows, so it never covers the hotbar or item names.
            int w=Math.min(200,mc.getWindow().getGuiScaledWidth()-20),h=40,x=(mc.getWindow().getGuiScaledWidth()-w)/2,y=Math.max(mc.getWindow().getGuiScaledHeight()-126,Math.round(mc.getWindow().getGuiScaledHeight()*0.62f));
            int color=FishLook.color(notice.rarity());if(color==0)color=Ui.GOLD;
            float fade=Math.min(1,Math.min((now-shown)/150f,(5000-(now-shown))/400f));
            boolean mythic=notice.rarity().equals("mythic");
            int border=mythic?Ui.alpha(color,0.55f+0.45f*(float)Math.abs(Math.sin((now-shown)/180.0))):Ui.alpha(color,0.8f);
            Ui.box(g,x,y,w,h,Ui.alpha(Ui.SURFACE,0.94f*fade),Ui.alpha(border,fade));
            Ui.box(g,x+6,y+8,24,24,Ui.alpha(color,0.16f*fade),Ui.alpha(color,0.45f*fade));
            g.item(stack,x+10,y+12);
            g.text(mc.font,Component.translatable("holylois.catch."+notice.rarity()),x+38,y+7,Ui.alpha(color,fade),false);
            g.text(mc.font,mc.font.plainSubstrByWidth(stack.getHoverName().getString(),w-46),x+38,y+18,Ui.alpha(Ui.TEXT,fade),false);
            g.text(mc.font,String.format(Locale.ROOT,"%.2f kg",notice.kilograms()),x+38,y+29,Ui.alpha(Ui.MUTED,fade),false);
        });
    }
    private static int setting(boolean value){
        try{java.nio.file.Files.createDirectories(FILE.getParent());java.nio.file.Files.writeString(FILE,new com.google.gson.Gson().toJson(value));enabled=value;notice=null;}
        catch(java.io.IOException error){var p=Minecraft.getInstance().player;if(p!=null)p.sendSystemMessage(Component.translatable("holylois.party.save_failed"));return 0;}
        var p=Minecraft.getInstance().player;if(p!=null)p.sendSystemMessage(Component.translatable("holylois.catch."+(value?"enabled":"disabled")));return 1;
    }
}
