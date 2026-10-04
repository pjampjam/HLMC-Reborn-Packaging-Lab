package holylois;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Death drops: tagged right after a player's inventory drops (DeathDropsMixin). Normal deaths keep the drops 30 minutes
 * and owner-only for the first 5; PvP deaths and combat logouts leave them unlocked for whoever wins the fight.
 */
public final class DeathLoot {
    private DeathLoot() {}
    public static final String DROP_TAG = "holylois:death_drop", GRACE_TAG = "holylois:death_grace";
    public static final int OWNER_ONLY_TICKS = 6000;

    public static void markDrops(ServerLevel level, ServerPlayer player) {
        try {
            boolean locked = !CombatTag.diedInPvp(player);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3),
                    entity -> entity.tickCount == 0 && !entity.entityTags().contains(DROP_TAG))) {
                item.addTag(DROP_TAG);
                if (locked) item.setTarget(player.getUUID());
            }
        } catch (RuntimeException error) {
            // Never let this stop a death: the drops simply stay vanilla.
            org.slf4j.LoggerFactory.getLogger("HolyLois").error("Holy Lois death drops failed", error);
        }
    }
}
