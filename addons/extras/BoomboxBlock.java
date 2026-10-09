package holylois.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A boombox placed on the ground. The block state keeps the station and whether it plays, so no block entity is
 * needed and clients can see a playing speaker. Right-click: play or next station. Sneak + empty hand: off.
 */
public final class BoomboxBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
    static final BooleanProperty WATERLOGGED = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
    static final BooleanProperty PLAYING = BooleanProperty.create("playing");
    static final IntegerProperty STATION = IntegerProperty.create("station", 0, 15);
    static final IntegerProperty VOLUME = IntegerProperty.create("volume", 1, 10);
    private static final VoxelShape NORTH_SOUTH = Block.box(1, 0, 5, 15, 8, 11), EAST_WEST = Block.box(5, 0, 1, 11, 8, 15);

    BoomboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PLAYING, false).setValue(STATION, 0).setValue(VOLUME, Boombox.DEFAULT_VOLUME).setValue(WATERLOGGED,false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING, STATION, VOLUME, WATERLOGGED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        int station = Math.min(15, Math.max(0, Boombox.station(context.getItemInHand())));
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(STATION, station)
            .setValue(VOLUME, Boombox.volume(context.getItemInHand()))
            .setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER)
                && context.getLevel().getFluidState(context.getClickedPos()).isSource());
    }

    @Override protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state){
        return state.getValue(WATERLOGGED)?net.minecraft.world.level.material.Fluids.WATER.getSource(false):super.getFluidState(state);
    }
    @Override protected BlockState updateShape(BlockState state,net.minecraft.world.level.LevelReader level,net.minecraft.world.level.ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,net.minecraft.world.level.material.Fluids.WATER,net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        return super.updateShape(state,level,ticks,pos,direction,neighborPos,neighbor,random);
    }

    /** Music notes rise from a playing boombox, a few more when it is turned up; while its audio is heard they follow the beat (BoomboxPulse). */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PLAYING) || BoomboxPulse.live(pos) || random.nextInt(14) >= 2 + state.getValue(VOLUME) / 2) return;
        level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.75,
            pos.getZ() + 0.2 + random.nextDouble() * 0.6, random.nextInt(25) / 24.0, 0, 0);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) Boombox.useSpeaker(serverPlayer, level, pos, state);
        return InteractionResult.SUCCESS;
    }
}
