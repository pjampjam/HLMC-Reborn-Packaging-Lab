package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class HomesScreen extends TravelScreen {
    private HomeState state;private int page;private int edit=-1;private String old="";private EditBox input;
    public HomesScreen(HomeState state){super("homes_title");this.state=state;}
    void update(HomeState next){state=next;edit=-1;rebuildWidgets();}
    private int rows(){return Math.max(1,Math.min(4,(panelHeight-105)/46));}
    @Override protected void init(){
        panel();int x=left+12,w=panelWidth-24,y=top+48;
        if(edit>=0){
            input=addRenderableWidget(new EditBox(font,x,y+18,w,22,text("name")));input.setMaxLength(32);input.setValue(old);
            setInitialFocus(input);
            button(x,y+55,w/2-4,"confirm",()->{
                TravelClient.send(edit,edit==TravelIntent.SAVE?input.getValue():old,edit==TravelIntent.RENAME?input.getValue():"",state.revision());
            });
            button(x+w/2+4,y+55,w/2-4,"cancel",()->{edit=-1;rebuildWidgets();});
        }else{
            int count=Math.max(3,state.homes().size()),maxPage=Math.max(0,(count-1)/rows());page=Math.min(page,maxPage);
            for(int i=0;i<rows();i++){
                int index=page*rows()+i;if(index>=count)break;
                int row=y+i*46;
                if(index<state.homes().size()){
                    var h=state.homes().get(index);
                    buttonText(x,row,w-112,Component.literal(h.name()),()->{TravelClient.send(TravelIntent.GO,h.name(),"",state.revision());minecraft.gui.setScreen(null);});
                    button(x+w-106,row,50,"rename",()->{edit=TravelIntent.RENAME;old=h.name();rebuildWidgets();});
                    button(x+w-52,row,52,"delete",()->{edit=TravelIntent.DELETE;old=h.name();confirmDelete();});
                }else buttonText(x,row,w,text("empty",index+1),()->{edit=TravelIntent.SAVE;old="";rebuildWidgets();});
            }
            if(maxPage>0){button(x,top+panelHeight-56,45,"previous",()->{page=Math.max(0,page-1);rebuildWidgets();});button(x+51,top+panelHeight-56,45,"next",()->{page=Math.min(maxPage,page+1);rebuildWidgets();});}
        }
        button(x,top+panelHeight-30,w/2-4,"party_title",()->minecraft.gui.setScreen(new PartyScreen()));
        button(x+w/2+4,top+panelHeight-30,w/2-4,"close",()->minecraft.gui.setScreen(null));
    }
    private void confirmDelete(){
        clearWidgets();panel();int x=left+12,w=panelWidth-24;
        button(x,top+100,w/2-4,"confirm_delete",()->TravelClient.send(TravelIntent.DELETE,old,"",state.revision()));
        button(x+w/2+4,top+100,w/2-4,"cancel",()->{edit=-1;rebuildWidgets();});
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.nextStratum();
        g.centeredText(font,text(edit==TravelIntent.DELETE?"delete_warning":edit>=0?"name_hint":"homes_hint"),width/2,top+32,0xFFAAAAAA);
        if(edit==TravelIntent.DELETE)g.centeredText(font,old,width/2,top+68,0xFFFFDD88);
        if(edit==-1)for(int i=0;i<rows();i++){int index=page*rows()+i;if(index<state.homes().size())g.text(font,font.plainSubstrByWidth(state.homes().get(index).dimension(),panelWidth-24),left+14,top+73+i*46,0xFF8B8D91);}
    }
}
