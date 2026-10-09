package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Set;

/** Shared compact panel in the Holy Lois look (Ui), with vanilla keyboard/escape behaviour. */
abstract class TravelScreen extends Screen {
    /** Yes-actions are green, cancel and destructive ones red, like a traffic light; everything else stays neutral. */
    private static final Set<String> CONFIRM = Set.of("confirm", "create", "accept", "invite", "request_travel", "rally_set");
    private static final Set<String> DANGER = Set.of("delete", "disband", "leave", "confirm_delete", "cancel");
    int left,top,panelWidth,panelHeight;
    TravelScreen(String key){super(text(key));}
    static Component text(String key,Object...args){return Component.translatable("holylois.travel."+key,args);}
    void panel(){panelWidth=Math.min(350,width-20);panelHeight=Math.min(310,height-16);left=(width-panelWidth)/2;top=(height-panelHeight)/2;}
    /** Shrinks the panel to its content (no empty band at the bottom) and centres it again. */
    void fit(int content){panelHeight=Math.min(content,height-16);top=(height-panelHeight)/2;}
    Button button(int x,int y,int w,String key,Runnable run){
        return addRenderableWidget(new TravelButton(x,y,w,text(key),b->run.run(),CONFIRM.contains(key)?Ui.Kind.CONFIRM:DANGER.contains(key)?Ui.Kind.DANGER:Ui.Kind.NORMAL));
    }
    Button button(int x,int y,int w,int h,String key,Runnable run){
        return addRenderableWidget(new TravelButton(x,y,w,h,text(key),b->run.run(),CONFIRM.contains(key)?Ui.Kind.CONFIRM:DANGER.contains(key)?Ui.Kind.DANGER:Ui.Kind.NORMAL));
    }
    Button buttonText(int x,int y,int w,Component title,Runnable run){return addRenderableWidget(new TravelButton(x,y,w,title,b->run.run(),Ui.Kind.NORMAL));}
    Button buttonText(int x,int y,int w,int h,Component title,Runnable run){return addRenderableWidget(new TravelButton(x,y,w,h,title,b->run.run(),Ui.Kind.NORMAL));}
    /** A list row: label on the left (room on the right for details drawn by the screen). */
    Button rowButton(int x,int y,int w,int h,Component title,Runnable run){var b=new TravelButton(x,y,w,h,title,p->run.run(),Ui.Kind.NORMAL);b.leftLabel=true;return addRenderableWidget(b);}
    /** Enter in this screen; true when it did something. */
    boolean enter(){return false;}
    // 26.3 input uses SDL scancodes (Enter 40, keypad Enter 88): ask the event, never compare old GLFW numbers.
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(event.isConfirmation()&&enter())return true;
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float tick){g.fill(0,0,width,height,Ui.alpha(Ui.CANVAS,0.78f));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        Ui.panel(g,font,left,top,panelWidth,panelHeight,title);
        g.nextStratum();super.extractRenderState(g,x,y,tick);
    }
    private final class TravelButton extends Button {
        private final Ui.Kind kind;boolean leftLabel;
        TravelButton(int x,int y,int w,Component label,OnPress press,Ui.Kind kind){this(x,y,w,22,label,press,kind);}
        TravelButton(int x,int y,int w,int h,Component label,OnPress press,Ui.Kind kind){super(x,y,w,h,label,press,DEFAULT_NARRATION);this.kind=kind;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float delta){
            if(!leftLabel){Ui.button(g,font,getX(),getY(),getWidth(),getHeight(),getMessage(),isHovered(),isFocused(),active,kind);return;}
            Ui.button(g,font,getX(),getY(),getWidth(),getHeight(),Component.empty(),isHovered(),isFocused(),active,kind);
            g.text(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()*3/5),getX()+9,getY()+(getHeight()-8)/2,Ui.TEXT,false);
        }
    }
}
