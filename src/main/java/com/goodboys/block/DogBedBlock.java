package com.goodboys.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A round pet cushion. Dogs curl up on it. */
public class DogBedBlock extends Block implements DogRestBlock {
    public static final MapCodec<DogBedBlock> CODEC = simpleCodec(DogBedBlock::new);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 4, 15);

    public DogBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    public Vec3 restPosition(BlockState state, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 3 / 16.0, pos.getZ() + 0.5);
    }

    @Override
    public float restYaw(BlockState state) {
        return Float.NaN;
    }

    @Override
    public int napStyle() {
        return 2; // curled up
    }
}
