package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared compact Minecraft-style travel panel, with vanilla keyboard/escape behaviour. */
abstract class TravelScreen extends Screen {
    int left,top,panelWidth,panelHeight;
    TravelScreen(String key){super(text(key));}
    static Component text(String key,Object...args){return Component.translatable("holylois.travel."+key,args);}
    void panel(){panelWidth=Math.min(350,width-20);panelHeight=Math.min(310,height-16);left=(width-panelWidth)/2;top=(height-panelHeight)/2;}
    Button button(int x,int y,int w,String key,Runnable run){return addRenderableWidget(new TravelButton(x,y,w,text(key),b->run.run()));}
    Button buttonText(int x,int y,int w,Component title,Runnable run){return addRenderableWidget(new TravelButton(x,y,w,title,b->run.run()));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float tick){g.fill(0,0,width,height,0xB8000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        g.fill(left,top,left+panelWidth,top+panelHeight,0xFF151619);g.outline(left,top,panelWidth,panelHeight,0xFF68615B);
        g.fill(left,top,left+panelWidth,top+2,0xFFF6D85E);g.centeredText(font,title,width/2,top+12,0xFFFFE37B);
        g.nextStratum();super.extractRenderState(g,x,y,tick);
    }
    private final class TravelButton extends Button {
        TravelButton(int x,int y,int w,Component label,OnPress press){super(x,y,w,22,label,press,DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float delta){
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isHoveredOrFocused()?0xFF3B3425:0xFF252629);
            g.outline(getX(),getY(),getWidth(),getHeight(),active?0xFFB4A372:0xFF4C4D50);
            g.centeredText(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()-10),getX()+getWidth()/2,getY()+7,active?0xFFF3E5B2:0xFF777777);
        }
    }
}
