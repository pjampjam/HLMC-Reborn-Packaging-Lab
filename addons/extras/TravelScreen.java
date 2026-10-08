package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Set;

/** Shared compact panel in the Holy Lois look (Ui), with vanilla keyboard/escape behaviour. */
abstract class TravelScreen extends Screen {
    /** Main actions get the gold button, destructive ones red text (as in the launcher). */
    private static final Set<String> PRIMARY = Set.of("confirm", "create", "accept", "invite", "request_travel", "rally_set");
    private static final Set<String> DANGER = Set.of("delete", "disband", "leave", "confirm_delete");
    int left,top,panelWidth,panelHeight;
    TravelScreen(String key){super(text(key));}
    static Component text(String key,Object...args){return Component.translatable("holylois.travel."+key,args);}
    void panel(){panelWidth=Math.min(350,width-20);panelHeight=Math.min(310,height-16);left=(width-panelWidth)/2;top=(height-panelHeight)/2;}
    Button button(int x,int y,int w,String key,Runnable run){
        return addRenderableWidget(new TravelButton(x,y,w,text(key),b->run.run(),PRIMARY.contains(key)?Ui.Kind.PRIMARY:DANGER.contains(key)?Ui.Kind.DANGER:Ui.Kind.NORMAL));
    }
    Button buttonText(int x,int y,int w,Component title,Runnable run){return addRenderableWidget(new TravelButton(x,y,w,title,b->run.run(),Ui.Kind.NORMAL));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float tick){g.fill(0,0,width,height,Ui.alpha(Ui.CANVAS,0.78f));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        Ui.panel(g,font,left,top,panelWidth,panelHeight,title);
        g.nextStratum();super.extractRenderState(g,x,y,tick);
    }
    private final class TravelButton extends Button {
        private final Ui.Kind kind;
        TravelButton(int x,int y,int w,Component label,OnPress press,Ui.Kind kind){super(x,y,w,22,label,press,DEFAULT_NARRATION);this.kind=kind;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float delta){
            Ui.button(g,font,getX(),getY(),getWidth(),getHeight(),getMessage(),isHovered(),isFocused(),active,kind);
        }
    }
}
