package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

public final class RallyScreen extends TravelScreen {
    private net.minecraft.client.gui.components.Button request;private long received;private PartyState.Rally last;
    public RallyScreen(){super("rally_title");}
    @Override protected void init(){
        panel();fit(206);int x=left+12,w=panelWidth-24,y=top+116;
        request=button(x,y,w,"request_travel",()->{
            var mark=mark();if(mark!=null && TravelClient.send(TravelIntent.JOIN_RALLY,mark.author().toString(),"",0))minecraft.gui.setScreen(null);
        });
        button(x,y+28,w,"rally_set",()->TravelClient.send(TravelIntent.RALLY,"","",0));
        button(x,y+56,w/2-4,"party_title",()->minecraft.gui.setScreen(new PartyScreen()));
        button(x+w/2+4,y+56,w/2-4,"collapse",()->{PartyHud.collapseRally();minecraft.gui.setScreen(null);});
    }
    private PartyState.Rally mark(){
        var next=PartyHud.current().rally();if(next!=last){last=next;received=System.currentTimeMillis();}
        return next!=null && next.seconds()*1000L>System.currentTimeMillis()-received?next:null;
    }
    @Override public void tick(){var p=mark();request.active=p!=null && minecraft.player!=null && !p.author().equals(minecraft.player.getUUID()) && p.dimension().equals(minecraft.player.level().dimension().identifier().toString());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.nextStratum();var p=mark();
        g.centeredText(font,text("request_hint"),width/2,top+32,Ui.MUTED);
        if(p==null)g.centeredText(font,text("rally_gone"),width/2,top+65,Ui.MUTED);
        else{
            g.centeredText(font,p.name(),width/2,top+58,Ui.GOLD);
            boolean same=p.dimension().equals(minecraft.player.level().dimension().identifier().toString());
            int left=Math.max(0,p.seconds()-(int)((System.currentTimeMillis()-received)/1000));
            String line=same?text("rally_away",String.format(Locale.ROOT,"%.0f",minecraft.player.position().distanceTo(new net.minecraft.world.phys.Vec3(p.x()+.5,p.y(),p.z()+.5))),String.format(Locale.ROOT,"%d:%02d",left/60,left%60)).getString():p.dimension();
            g.centeredText(font,line,width/2,top+77,Ui.SUCCESS);
            g.centeredText(font,text("request_author"),width/2,top+94,Ui.MUTED);
        }
    }
}
