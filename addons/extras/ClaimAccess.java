package holylois.boombox;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Effective OPAC rules, including trusted players and operator mode. */
public final class ClaimAccess {
    public static boolean canUse(Entity actor, ServerLevel level, BlockPos pos) {
        if (!FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) return true;
        var api = OpenPACServerAPI.get(level.getServer());
        var claim = api.getServerClaimsManager().get(level.dimension().identifier(), pos.getX() >> 4, pos.getZ() >> 4);
        if (claim == null) return true;
        var config = api.getChunkProtection().getConfig(claim);
        return !Boolean.TRUE.equals(config.getEffective(xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS))
            || api.getChunkProtection().hasChunkAccess(actor, level.dimension().identifier(), pos.getX() >> 4, pos.getZ() >> 4);
    }
}
