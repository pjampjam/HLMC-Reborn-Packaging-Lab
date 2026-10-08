package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class PartyScreen extends TravelScreen {
    private EditBox name;private boolean wasMember;private int page;private boolean confirmLeave,confirmDestroy;
    private int rows(){return Math.max(1,Math.min(5,(panelHeight-186)/15));}
    @Override protected void init(){
        panel();var state=PartyHud.current();wasMember=!state.members().isEmpty();int x=left+12,w=panelWidth-24;
        if(confirmLeave || confirmDestroy){
            button(x,top+86,w/2-4,confirmDestroy?"disband":"leave",()->{TravelClient.send(confirmDestroy?TravelIntent.DESTROY_PARTY:TravelIntent.LEAVE,"","",0);confirmLeave=false;confirmDestroy=false;rebuildWidgets();});
            button(x+w/2+4,top+86,w/2-4,"cancel",()->{confirmLeave=false;confirmDestroy=false;rebuildWidgets();});return;
        }
        int y=top+panelHeight-122;
        name=addRenderableWidget(new EditBox(font,x,y,w,20,text("player_name")));name.setMaxLength(16);
        if(wasMember){
            button(x,y+25,w/2-4,"invite",()->TravelClient.send(TravelIntent.INVITE,name.getValue(),"",0));
            boolean owner=state.members().stream().anyMatch(m->m.owner() && m.id().equals(minecraft.player.getUUID()));
            button(x+w/2+4,y+25,w/2-4,owner?"disband":"leave",()->{confirmDestroy=owner;confirmLeave=!owner;rebuildWidgets();});
        }else{
            button(x,y+25,w/2-4,"create",()->TravelClient.send(TravelIntent.CREATE_PARTY,"","",0));
            button(x+w/2+4,y+25,w/2-4,"accept",()->TravelClient.send(TravelIntent.ACCEPT,name.getValue(),"",0));
        }
        button(x,y+53,w/2-4,"homes_title",TravelClient::openHomes);
        var rally=button(x+w/2+4,y+53,w/2-4,"rally_title",()->minecraft.gui.setScreen(new RallyScreen()));rally.active=wasMember;
        button(x,y+83,w/2-4,"advanced",()->{minecraft.getConnection().sendCommand("party help");minecraft.gui.setScreen(null);});
        button(x+w/2+4,y+83,w/2-4,"close",()->minecraft.gui.setScreen(null));
        if(state.members().size()>rows()){
            button(x,top+46+rows()*15,42,"previous",()->{page=Math.max(0,page-1);rebuildWidgets();});
            button(x+48,top+46+rows()*15,42,"next",()->{page=Math.min((state.members().size()-1)/rows(),page+1);rebuildWidgets();});
        }
    }
    public PartyScreen(){super("party_title");}
    @Override public void tick(){if(wasMember!=!PartyHud.current().members().isEmpty()){confirmLeave=false;confirmDestroy=false;rebuildWidgets();}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.nextStratum();
        var state=PartyHud.current();int w=panelWidth-24;
        g.centeredText(font,text(confirmDestroy?"disband_warning":confirmLeave?"leave_warning":"party_warning"),width/2,top+32,Ui.MUTED);
        if(confirmLeave || confirmDestroy)return;
        if(state.members().isEmpty())g.centeredText(font,text("no_party"),width/2,top+58,Ui.MUTED);
        else for(int i=0;i<rows();i++){
            int index=page*rows()+i;if(index>=state.members().size())break;var m=state.members().get(index);
            String details=m.online()?String.format(Locale.ROOT,"%.0f/%.0f  %d/20",m.health(),m.maximum(),m.food()):text("offline").getString();
            g.text(font,font.plainSubstrByWidth(m.name(),Math.max(40,w-100)),left+12,top+48+i*15,Ui.TEXT);
            g.text(font,details,left+panelWidth-108,top+48+i*15,m.online()?Ui.SUCCESS:Ui.MUTED);
        }
        g.text(font,text(wasMember?"invite_hint":"accept_hint"),left+12,top+panelHeight-135,Ui.MUTED);
    }
}
