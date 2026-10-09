package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class HomesScreen extends TravelScreen {
    private HomeState state;private int page;private int edit=-1;private String old="";private EditBox input;
    public HomesScreen(HomeState state){super("homes_title");this.state=state;}
    void update(HomeState next){state=next;edit=-1;rebuildWidgets();}
    /** Rows per page: 5, fewer on small screens. Slots past the player's limit show as grey boxes (unlocked later). */
    private int rows(){return Math.max(1,Math.min(5,(panelHeight-110)/ROW));}
    private static final int ROW=32,BUTTON=26;
    /** Shown slots: the unlocked ones, filled up with locked boxes to a full page (so 3 homes show 2 locked). */
    private int slots(){int unlocked=Math.max(state.limit(),state.homes().size());return Math.max(unlocked,((unlocked+rows()-1)/rows())*rows());}
    @Override protected void init(){
        panel();int x=left+12,w=panelWidth-24,y=top+44;
        if(edit>=0){
            input=addRenderableWidget(new EditBox(font,x,y+18,w,22,text("name")));input.setMaxLength(32);input.setValue(old);
            setInitialFocus(input);
            button(x,y+55,w/2-4,"confirm",this::confirm);
            button(x+w/2+4,y+55,w/2-4,"cancel",()->{edit=-1;rebuildWidgets();});
        }else{
            int count=slots(),maxPage=Math.max(0,(count-1)/rows());page=Math.min(page,maxPage);
            for(int i=0;i<rows();i++){
                int index=page*rows()+i;if(index>=count)break;
                int row=y+i*ROW;
                if(index<state.homes().size()){
                    var h=state.homes().get(index);
                    buttonText(x,row,w-112,BUTTON,Component.literal(h.name()),()->{TravelClient.send(TravelIntent.GO,h.name(),"",state.revision());minecraft.gui.setScreen(null);});
                    button(x+w-106,row,50,BUTTON,"rename",()->{edit=TravelIntent.RENAME;old=h.name();rebuildWidgets();});
                    button(x+w-52,row,52,BUTTON,"delete",()->{edit=TravelIntent.DELETE;old=h.name();confirmDelete();});
                }else if(index<Math.max(state.limit(),state.homes().size()))buttonText(x,row,w,BUTTON,text("empty",index+1),()->{edit=TravelIntent.SAVE;old="";rebuildWidgets();});
            }
            if(maxPage>0){button(x,top+panelHeight-60,45,"previous",()->{page=Math.max(0,page-1);rebuildWidgets();});button(x+51,top+panelHeight-60,45,"next",()->{page=Math.min(maxPage,page+1);rebuildWidgets();});}
        }
        button(x,top+panelHeight-32,w/2-4,"party_title",()->minecraft.gui.setScreen(new PartyScreen()));
        button(x+w/2+4,top+panelHeight-32,w/2-4,"close",()->minecraft.gui.setScreen(null));
    }
    private void confirm(){TravelClient.send(edit,edit==TravelIntent.SAVE?input.getValue():old,edit==TravelIntent.RENAME?input.getValue():"",state.revision());}
    /** Enter confirms a name, as in every other form. */
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(edit>=0&&edit!=TravelIntent.DELETE&&(event.key()==257||event.key()==335)){confirm();return true;}
        return super.keyPressed(event);
    }
    private void confirmDelete(){
        clearWidgets();panel();int x=left+12,w=panelWidth-24;
        button(x,top+100,w/2-4,"confirm_delete",()->TravelClient.send(TravelIntent.DELETE,old,"",state.revision()));
        button(x+w/2+4,top+100,w/2-4,"cancel",()->{edit=-1;rebuildWidgets();});
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.nextStratum();
        g.centeredText(font,text(edit==TravelIntent.DELETE?"delete_warning":edit>=0?"name_hint":"homes_hint"),width/2,top+30,Ui.MUTED);
        if(edit==TravelIntent.DELETE)g.centeredText(font,old,width/2,top+68,Ui.GOLD);
        if(edit!=-1)return;
        int unlocked=Math.max(state.limit(),state.homes().size());
        for(int i=0;i<rows();i++){
            int index=page*rows()+i,row=top+44+i*ROW;if(index>=slots())break;
            if(index>=unlocked){Ui.box(g,left+12,row,panelWidth-24,BUTTON,Ui.alpha(Ui.CONTROL,0.45f),Ui.alpha(Ui.LINE,0.6f));continue;}
            if(index<state.homes().size()){
                // The dimension sits at the right end of the home button, muted.
                String where=font.plainSubstrByWidth(state.homes().get(index).dimension(),70);
                g.text(font,where,left+12+panelWidth-24-112-6-font.width(where),row+9,Ui.MUTED,false);
            }
        }
    }
}
