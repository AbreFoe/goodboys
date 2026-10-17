package com.goodboys.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A doghouse. Hollow inside: walls, a roof and a doorway on the front, so a dog can walk in and sleep there.
 * A dog told to wait nearby goes in at night; told to sit nearby, it goes in to nap.
 */
public class KennelBlock extends HorizontalDirectionalBlock implements DogRestBlock {
    public static final MapCodec<KennelBlock> CODEC = simpleCodec(KennelBlock::new);
    /** Facing north: doorway on the north side. */
    private static final VoxelShape NORTH = Shapes.or(
            box(0, 0, 1, 1, 14, 16),     // west wall
            box(15, 0, 1, 16, 14, 16),   // east wall
            box(1, 0, 15, 15, 14, 16),   // back wall
            box(0, 0, 0, 3, 14, 1),      // front, left of the door
            box(13, 0, 0, 16, 14, 1),    // front, right of the door
            box(0, 14, 0, 16, 16, 16));  // roof
    private static final VoxelShape OUTLINE = box(0, 0, 0, 16, 16, 16);

    public KennelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rotate(NORTH, state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false; // paths go round it; a dog walks in through the doorway on its own (DogRestSpotGoal)
    }

    @Override
    public Vec3 restPosition(BlockState state, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public Vec3 approachPosition(BlockState state, BlockPos pos) {
        BlockPos front = pos.relative(state.getValue(FACING));
        return new Vec3(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
    }

    @Override
    public float restYaw(BlockState state) {
        return state.getValue(FACING).toYRot(); // looking out of the doorway
    }

    @Override
    public int napStyle() {
        return 0; // sphinx, head on the paws
    }

    private static VoxelShape rotate(VoxelShape north, Direction facing) {
        if (facing == Direction.NORTH) {
            return north;
        }
        VoxelShape[] out = {Shapes.empty()};
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double ax1 = x1, az1 = z1, ax2 = x2, az2 = z2;
            for (int i = 0; i < turns; i++) { // 90 degrees clockwise seen from above: (x, z) -> (1 - z, x)
                double nx1 = 1 - az2, nz1 = ax1, nx2 = 1 - az1, nz2 = ax2;
                ax1 = nx1; az1 = nz1; ax2 = nx2; az2 = nz2;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax1, y1, az1, ax2, y2, az2));
        });
        return out[0];
    }
}
