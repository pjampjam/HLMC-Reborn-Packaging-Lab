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
    /** Shiny jingle: a rising chime arpeggio after the catch sound (ms after arrival, pitch). */
    private static final float[][] JINGLE={{260,1.19f},{390,1.5f},{520,1.78f},{700,2f}};private static int jingleNext=JINGLE.length;
    private static final java.nio.file.Path FILE=java.nio.file.Path.of("config","holylois-catch-ui.json");
    private static boolean enabled=true;
    static void register(){
        try{if(java.nio.file.Files.exists(FILE))enabled=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(FILE),Boolean.class);}catch(Exception ignored){}
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d,a)->d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("catchcards")
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on").executes(c->setting(true)))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes(c->setting(false)))));
        ClientPlayNetworking.registerGlobalReceiver(RareCatchNotice.TYPE,(v,c)->{
            if(!enabled)return;notice=v;arrived=System.currentTimeMillis();shown=0;jingleNext=v.shiny()?0:JINGLE.length;
            // A sound per rarity, loud enough to hear over the sea (the Mythic fanfare comes from the server on top).
            var p=c.client().player;if(p!=null)switch(v.rarity()){
                case "rare"->p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,1f,1.3f);
                case "epic"->{p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,1f,1.2f);p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,1f,1.6f);}
                case "legendary"->p.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,0.8f,1.1f);
                default->p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,1f,1f);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->notice=null);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(notice==null||mc.player==null||jingleNext>=JINGLE.length)return;
            if(System.currentTimeMillis()-arrived>=JINGLE[jingleNext][0])mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),0.9f,JINGLE[jingleNext++][1]);
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,Identifier.fromNamespaceAndPath("holylois","rare_catch"),(g,t)->{
            var mc=Minecraft.getInstance();if(notice==null||mc.player==null||mc.level==null)return;long now=System.currentTimeMillis();
            if(now-arrived>60_000){notice=null;return;}if(mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;
            if(shown==0)shown=now;long age=now-shown;if(age>LIFE){notice=null;return;}
            var id=Identifier.tryParse(notice.item());if(id==null||!BuiltInRegistries.ITEM.containsKey(id)){notice=null;return;}
            draw(g,mc,new ItemStack(BuiltInRegistries.ITEM.getValue(id)),age);
        });
    }
    private static final long LIFE=5500;
    /**
     * Loot toast in the style of the WoW toast addons: icon well with a rarity border, rarity label, the name as the main line,
     * weight quiet underneath, a time bar. Slides up and fades in; Epic and up get a shine sweep, Mythic a pulsing border and a
     * repeating sweep, Shiny twinkles around the icon. Centred above the health and food rows.
     */
    private static void draw(net.minecraft.client.gui.GuiGraphicsExtractor g,Minecraft mc,ItemStack stack,long age){
        var font=mc.font;String rarity=notice.rarity();
        int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();
        int w=Math.min(224,sw-20),h=46,x=(sw-w)/2;
        float in=Math.min(1,age/180f),fade=Math.min(in,Math.min(1,(LIFE-age)/450f));if(fade<=0.04f)return;
        int y=sh-76-h+Math.round((1-in)*(1-in)*10);
        int color=FishData.color(rarity);if(color==0)color=Ui.GOLD;
        boolean mythic=rarity.equals("mythic"),epicUp=mythic||rarity.equals("legendary")||rarity.equals("epic");
        float pulse=mythic?0.55f+0.45f*(float)Math.abs(Math.sin(age/180.0)):0.85f;
        Ui.box(g,x,y,w,h,Ui.alpha(Ui.SURFACE,0.95f*fade),Ui.alpha(color,pulse*fade));
        // Icon well.
        Ui.box(g,x+7,y+7,32,32,Ui.alpha(color,0.18f*fade),Ui.alpha(color,0.7f*fade));
        if(fade>0.5f){
            float pop=Math.min(1,age/220f),scale=1.25f*(0.6f+0.4f*pop)+0.15f*(float)Math.sin(pop*Math.PI);
            g.pose().pushMatrix();g.pose().translate(x+23,y+23);g.pose().scale(scale,scale);g.item(stack,-8,-8);g.pose().popMatrix();
        }
        // Shine: a soft white band crossing the card (once for Epic/Legendary, every 1.6 s for a Mythic).
        if(epicUp){
            long cycle=mythic?1600:100_000,local=(age-200)%cycle;
            if(age>200&&local<700){
                int band=x-30+Math.round((w+60)*local/700f);
                g.enableScissor(x+1,y+1,x+w-1,y+h-1);
                for(int i=-6;i<=6;i++){int a=Math.round(70*(1-Math.abs(i)/7f)*fade);g.fill(band+i*2,y,band+i*2+2,y+h,(a<<24)|0xFFFFFF);}
                g.disableScissor();
            }
        }
        // Shiny: small stars twinkling around the icon well.
        if(notice.shiny()){
            for(int i=0;i<5;i++){
                double phase=age/260.0+i*1.3;float tw=(float)Math.max(0,Math.sin(phase));if(tw<0.2f)continue;
                int sx=x+7+(int)(16+17*Math.cos(i*1.26+age/900.0)),sy=y+7+(int)(16+17*Math.sin(i*1.26+age/900.0));
                g.text(font,"✦",sx-2,sy-4,Ui.alpha(0xFFFFFFFF,tw*fade),false);
            }
        }
        String label=Component.translatable("holylois.catch."+rarity).getString().toUpperCase(Locale.ROOT)+(notice.shiny()?"  ✧ "+Component.translatable("holylois.catch.shiny").getString().toUpperCase(Locale.ROOT):"");
        g.text(font,font.plainSubstrByWidth(label,w-56),x+47,y+7,Ui.alpha(color,fade),false);
        var name=Component.literal(font.plainSubstrByWidth(stack.getHoverName().getString(),w-56)).withStyle(s->FishData.shine(s,rarity));
        g.text(font,name,x+47,y+19,Ui.alpha(Ui.TEXT,fade),false);
        g.text(font,String.format(Locale.ROOT,"%.2f kg",notice.kilograms()),x+47,y+31,Ui.alpha(Ui.MUTED,fade),false);
        // Time left.
        int bar=Math.round((w-2)*(1-Math.min(1,age/(float)LIFE)));
        g.fill(x+1,y+h-2,x+1+bar,y+h-1,Ui.alpha(color,0.6f*fade));
    }
    private static int setting(boolean value){
        try{java.nio.file.Files.createDirectories(FILE.getParent());java.nio.file.Files.writeString(FILE,new com.google.gson.Gson().toJson(value));enabled=value;notice=null;}
        catch(java.io.IOException error){var p=Minecraft.getInstance().player;if(p!=null)p.sendSystemMessage(Component.translatable("holylois.party.save_failed"));return 0;}
        var p=Minecraft.getInstance().player;if(p!=null)p.sendSystemMessage(Component.translatable("holylois.catch."+(value?"enabled":"disabled")));return 1;
    }
}
