package holylois.boombox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Small server-authoritative bonuses for existing relic ids; no stack replacement or renderer dependence. */
public final class RelicPowers {
    static String id(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString(Legends.LEGEND_KEY).orElse("");}
    private static boolean held(ServerPlayer p,String id){return id(p.getMainHandItem()).equals(id)||id(p.getOffhandItem()).equals(id);}
    private static void modifier(ServerPlayer p,Holder<Attribute> attribute,String key,double amount,AttributeModifier.Operation op,boolean wanted){
        var value=p.getAttribute(attribute);if(value==null)return;var token=Identifier.fromNamespaceAndPath("holylois",key);
        if(wanted&&!value.hasModifier(token))value.addTransientModifier(new AttributeModifier(token,amount,op));
        else if(!wanted&&value.hasModifier(token))value.removeModifier(token);
    }
    public static void equipped(ServerPlayer p){
        boolean ready=PartySupport.ready(p)&&!p.isSpectator();
        modifier(p,Attributes.BLOCK_BREAK_SPEED,"relic_lantern_mining",.10,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,ready&&held(p,"loiss_lantern"));
        modifier(p,Attributes.MOVEMENT_SPEED,"relic_staff_speed",.08,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,ready&&held(p,"staff_of_quiet_roads"));
        modifier(p,Attributes.LUCK,"relic_circlet_luck",1,AttributeModifier.Operation.ADD_VALUE,ready&&id(p.getItemBySlot(EquipmentSlot.HEAD)).equals("circlet_of_the_wanderer"));
    }
    static boolean outOfCombat(ServerPlayer p){
        try{return (long)Class.forName("holylois.CombatTag").getMethod("teleportWait",UUID.class).invoke(null,p.getUUID())==0;}
        catch(ClassNotFoundException ignored){return true;}
        catch(ReflectiveOperationException error){return false;}
    }
    public static boolean aura(ServerPlayer p){
        if(!PartySupport.ready(p)||p.isSpectator()||!outOfCombat(p)||p.getHealth()>=p.getMaxHealth())return false;
        var level=p.level();
        for(var pos:PlacedRelics.lanternsNear(level,p.position(),4)){
            var hit=level.clip(new ClipContext(p.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS && (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||!block.getBlockPos().equals(pos)))continue;
            p.heal(1);level.sendParticles(ParticleTypes.WAX_ON,p.getX(),p.getY()+.6,p.getZ(),2,.2,.2,.2,0);
            return true;
        }
        return false;
    }
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%10!=0)return;
            for(var player:server.getPlayerList().getPlayers()){
                equipped(player);if(server.getTickCount()%100==0)aura(player);
            }
            if(server.getTickCount()%100==0)for(var level:server.getAllLevels()){
                var seen=new HashSet<net.minecraft.core.BlockPos>();
                for(var p:level.players())if(PartySupport.ready(p))for(var pos:PlacedRelics.lanternsNear(level,p.position(),4))if(seen.add(pos)){
                    if(seen.size()>16)break;
                    for(int i=0;i<8;i++){double angle=i*Math.PI/4;level.sendParticles(ParticleTypes.WAX_ON,pos.getX()+.5+4*Math.cos(angle),pos.getY()+.12,pos.getZ()+.5+4*Math.sin(angle),1,0,0,0,0);}
                }
            }
        });
    }
}
