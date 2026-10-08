package holylois;

import com.mojang.authlib.GameProfile;
import holylois.boombox.AccountIntent;
import java.util.UUID;

/** Pure model checks use synthetic identities only, never an account database. */
public final class AccountRequestsTest {
    private static int checked;
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checked++;}
    public static void main(String[] args)throws Exception{
        var field=AccountRequests.class.getDeclaredField("store");field.setAccessible(true);var original=field.get(null);
        try{
            var store=new AccountRequests.Store();var id=UUID.randomUUID();var grant=new AccountRequests.Grant();grant.id=UUID.randomUUID();grant.uuid=id;grant.kind=2;grant.expires=System.currentTimeMillis()+60_000;grant.tokenHash=AccountRequests.digest("synthetic-recovery-token");
            store.grants.put(id+":2",grant);store.aliases.put("previous",new AccountRequests.Alias(id,"CurrentName"));store.aliases.put("currentname",new AccountRequests.Alias(id,"CurrentName"));field.set(null,store);
            var profile=new GameProfile(id,"Previous");var renamed=AccountRequests.canonical(profile);
            check(renamed.id().equals(id)&&renamed.name().equals("CurrentName"),"rename preserves UUID and canonicalises an old alias");
            check(renamed.properties()==profile.properties(),"selected texture properties remain intact");
            var stranger=new GameProfile(UUID.randomUUID(),"Previous");check(AccountRequests.canonical(stranger)==stranger,"an alias cannot rebind an unrelated UUID");
            check(AccountRequests.reserved("PREVIOUS")&&AccountRequests.reserved("CurrentName"),"old and new names remain reserved without case bypass");
            var accepted=AccountRequests.GSON.fromJson("{\"username\":\"CurrentName\",\"uuid\":\""+id+"\"}",xyz.nikitacartes.easyauth.storage.PlayerEntryV1.class);
            var nativeCopy=AccountRequests.copy(accepted);
            check(nativeCopy.uuid.equals(id)&&nativeCopy.username.equals("CurrentName"),"native account serializer retains identity during a clone");
            check(AccountRequests.acceptsRecord(accepted),"native registration accepts the legitimate stable identity");accepted.uuid=UUID.randomUUID();check(!AccountRequests.acceptsRecord(accepted),"late registration cannot claim another account's alias");
            accepted.forcedUuid=id.toString();check(AccountRequests.acceptsRecord(accepted),"native reload keeps the forced identity even when its derived name UUID differs");
            check(AccountRequests.tokenMatches("synthetic-recovery-token",grant.tokenHash)&&!AccountRequests.tokenMatches("wrong",grant.tokenHash),"recovery challenge rejects a wrong code");
            var pending=AccountRequests.class.getDeclaredMethod("pending",UUID.class,int.class);pending.setAccessible(true);
            check(pending.invoke(null,id,2)==grant,"a valid grant is available");grant.processing=true;check(pending.invoke(null,id,2)==null,"persisted in-flight recovery cannot be replayed");grant.processing=false;grant.expires=0;check(pending.invoke(null,id,2)==null,"expired code cannot be used");
            grant.nextAttemptAt=System.currentTimeMillis()+1000;var copy=AccountRequests.GSON.fromJson(AccountRequests.GSON.toJson(store),AccountRequests.Store.class);check(copy.aliases.get("previous").uuid().equals(id)&&copy.grants.get(id+":2").tokenHash.equals(grant.tokenHash),"grant and alias metadata round-trip across restart");
            check(copy.grants.get(id+":2").nextAttemptAt==grant.nextAttemptAt,"recovery cooldown survives store reload");
            check(!new AccountIntent(2,grant.id,"synthetic-password","synthetic-password","synthetic-recovery-token").toString().contains("synthetic"),"payload diagnostics never print secrets");
            var reminder=AccountRequests.reminder(1);var link=(net.minecraft.network.chat.Component)((net.minecraft.network.chat.contents.TranslatableContents)reminder.getContents()).getArgs()[0];
            check(link.getStyle().isUnderlined()&&net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.AQUA).equals(link.getStyle().getColor())&&link.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand&&reminder.getStyle().getClickEvent()==null,"the form link is distinct and only the link is clickable");
            check(AccountRequests.passwordMaximum(-1)==100 && AccountRequests.passwordMaximum(32)==32,"unlimited native password maximum remains bounded by the safe packet limit");
        }finally{field.set(null,original);}
        System.out.println("Passed "+checked+" account alias/challenge/expiry/replay/persistence/privacy checks; no database used.");
    }
}
