package holylois.boombox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** A server-granted rename or password form. Cancelling never consumes the grant. */
public final class AccountScreen extends TravelScreen {
    private AccountNotice state;private EditBox value,confirm,token;private boolean waiting;
    public AccountScreen(AccountNotice state){super(state.kind()==1?"account_rename":"account_password");this.state=state;}
    void update(AccountNotice next){boolean rebuild=waiting||!state.grant().equals(next.grant())||state.kind()!=next.kind()||state.authenticated()!=next.authenticated()||!next.message().isEmpty();state=next;if(rebuild){clearSecrets();waiting=false;rebuildWidgets();}}
    @Override protected void init(){
        panel();fit(state.kind()!=2?154:state.authenticated()?202:250);int x=left+12,w=panelWidth-24,y=top+68;
        value=field(x,y,w,state.kind()==1?"account_new_name":"account_new_password",state.kind()!=1);value.setMaxLength(state.kind()==1?16:100);
        if(state.kind()==2){confirm=field(x,y+48,w,"account_confirm_password",true);if(!state.authenticated())token=field(x,y+96,w,"account_code",true);}
        var save=button(x,top+panelHeight-34,w/2-4,"confirm",this::submit);save.active=!waiting;
        button(x+w/2+4,top+panelHeight-34,w/2-4,"cancel",()->{clearSecrets();minecraft.gui.setScreen(null);});setInitialFocus(value);
    }
    private void submit(){
        if(waiting)return;waiting=true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new AccountIntent(state.kind(),state.grant(),value.getValue(),confirm==null?"":confirm.getValue(),token==null?"":token.getValue()));
        clearSecrets();rebuildWidgets();
    }
    /** Enter sends the form, as on the login screen. */
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(event.key()==257||event.key()==335){submit();return true;}
        return super.keyPressed(event);
    }
    private EditBox field(int x,int y,int w,String key,boolean secret){
        var box=addRenderableWidget(new EditBox(font,x,y,w,22,text(key)));box.setMaxLength(secret?100:16);
        if(secret)box.addFormatter((s,offset)->FormattedCharSequence.forward("*".repeat(s.length()),Style.EMPTY));return box;
    }
    private void clearSecrets(){for(var box:new EditBox[]{value,confirm,token})if(box!=null)box.setValue("");}
    @Override public void onClose(){clearSecrets();super.onClose();}
    @Override public void removed(){clearSecrets();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.nextStratum();
        g.centeredText(font,text(state.kind()==1?"account_keep_progress":"account_private"),width/2,top+34,Ui.MUTED);
        g.text(font,text(state.kind()==1?"account_new_name":"account_new_password"),left+12,top+56,Ui.MUTED);
        if(state.kind()==2){g.text(font,text("account_confirm_password"),left+12,top+104,Ui.MUTED);if(!state.authenticated())g.text(font,text("account_code"),left+12,top+152,Ui.MUTED);}
        if(!state.message().isEmpty())g.centeredText(font,text("account_"+state.message()),width/2,top+panelHeight-55,Ui.DANGER);
    }
    public static void register(){
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(AccountNotice.TYPE,(payload,context)->{
            if(payload.kind()==0){if(context.client().gui.screen() instanceof AccountScreen)context.client().gui.setScreen(null);return;}
            if(context.client().gui.screen() instanceof AccountScreen screen)screen.update(payload);
            else if(payload.open())context.client().gui.setScreen(new AccountScreen(payload));
        });
    }
}
